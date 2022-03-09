package be.ntmn.inficam;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import java.io.IOException;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	SurfaceView cameraView;
	MessageView messageView;
	UsbDevice device;
	UsbDeviceConnection usbConnection;
	boolean usbPermissionAsked = false, usbPermissionAcquired = false;
	InfiCam infiCam = new InfiCam();
	Overlay overlay;
	SurfaceMuxer surfaceMuxer;
	SurfaceMuxer.OutputSurface outputSurface;
	SurfaceMuxer.OutputSurface recordSurface;
	SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
	SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
	SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */
	InfiCam.FrameInfo lastFi;
	float[] lastTemp;
	final Object frameLock = new Object();
	int picWidth = 640, picHeight = 480;
	boolean takePic = false;
	volatile boolean disconnecting = false;
	int currentPalette = 3;
	SurfaceRecorder recorder = new SurfaceRecorder();

	ViewGroup dialogBackground;
	View dialogSettings;
	Settings settings;

	USBMonitor usbMonitor = new USBMonitor() {
		@Override
		public void onDeviceFound(UsbDevice dev) {
			if (device != null)
				return;
			device = dev;
			usbPermissionAsked = true;
			connect(dev, new ConnectCallback() {
				@Override
				public void onConnected(UsbDevice dev, UsbDeviceConnection conn) {
					usbPermissionAcquired = true;
					usbConnection = conn;
					try {
						infiCam.connect(conn.getFileDescriptor());
						infiCam.startStream();
						handler.postDelayed(() -> infiCam.calibrate(), 1000);
						messageView.showMessage(R.string.msg_connected);
					} catch (Exception e) {
						usbConnection.close();
						messageView.setMessage(getString(R.string.msg_connect_failed));
					}
				}

				@Override
				public void onPermissionDenied(UsbDevice dev) {
					messageView.setMessage(R.string.msg_permdenied_usb);
				}

				@Override
				public void onFailure(UsbDevice dev) {
					messageView.setMessage(R.string.msg_connect_failed);
				}
			});
		}

		@Override
		public void onDisconnect(UsbDevice dev) {
			disconnect();
		}
	};

	SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			outputSurface =
					new SurfaceMuxer.OutputSurface(surfaceMuxer, surfaceHolder.getSurface(), false);
			surfaceMuxer.outputSurfaces.add(outputSurface);
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int w, int h) {
			outputSurface.setSize(w, h);
			overlay.setSize(w, h);
			// TODO redraw more proper, perhaps also redraw when dirty
			surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
			surfaceMuxer.outputSurfaces.remove(outputSurface);
			outputSurface.release();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		cameraView = findViewById(R.id.cameraView);
		messageView = findViewById(R.id.message);
		surfaceMuxer = new SurfaceMuxer(this);

		/* Create and set up the InputSurface for thermal image. */
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.add(inputSurface);
		//inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		infiCam.setSurface(inputSurface.getSurface());
		cameraView.getHolder().addCallback(surfaceHolderCallback);
		infiCam.setPalette(Palette.Ironbow.getData()); // TODO UI to choose

		/* Create and set up the InputSurface for annotations overlay. */
		overlaySurface = new SurfaceMuxer.InputSurface(surfaceMuxer, false);
		surfaceMuxer.inputSurfaces.add(overlaySurface);
		overlay = new Overlay(overlaySurface, cameraView.getWidth(), cameraView.getHeight());

		// TODO this is just test for interpolation
		SurfaceMuxer.InputSurface test = new SurfaceMuxer.InputSurface(surfaceMuxer, false);
		test.getSurfaceTexture().setDefaultBufferSize(8, 6);
		Canvas tcvs = test.getSurface().lockCanvas(null);
		Paint p = new Paint();
		tcvs.drawColor(Color.YELLOW);
		p.setColor(Color.BLUE);
		tcvs.drawLine(0, 6, 8, 0, p);
		p.setColor(Color.RED);
		tcvs.drawLine(0, 0, 8, 6, p);
		test.getSurface().unlockCanvasAndPost(tcvs);
		surfaceMuxer.inputSurfaces.add(test);
		surfaceMuxer.onFrameAvailable(test.getSurfaceTexture());

		/* Now we set the frame callback, the way this works is that first the thermal image on
		 *   inputSurface gets written, then the frame callback runs, we copy over the info to
		 *   lastFi and lastTemp, ask onFrame() to be called on the main thread and then we hold
		 *   off on returning from the callback until that frame and the matching lastFi and
		 *   lastTemp have been dealt with, after which the frameLock should be notified.
		 * The point of it is to make sure we have a matching lastFi and lastTemp with the last
		 *   frame that don't get overwritten by the next run of this callback. The contents of the
		 *   inputSurface texture etc are less of a concern since they don't get updated until
		 *   updateTexImage() is called. We can't just do everything on the callback thread because
		 *   we need our EGL context and EGL contexts are stuck to a particular thread.
		 */
		infiCam.setFrameCallback((fi, temp) -> { /* Note this is called from another thread. */
			lastFi = fi; /* Save for taking picture and the likes. */
			lastTemp = temp;
			handler.post(this::onFrame);
			/* Now we wait until the main thread has finished drawing the frame, so lastFi and
			 *   lastTemp don't get overwritten before they've been used.
			 */
			synchronized (frameLock) {
				if (disconnecting)
					return;
				try {
					frameLock.wait();
				} catch (Exception e) {
					e.printStackTrace(); /* Not the end of the world, we do try to continue. */
				}
			}
		});

		/* Connecting to a UVC device needs camera permission. */
		askPermission(Manifest.permission.CAMERA, granted -> {
			if (granted)
				usbMonitor.start(this);
			else messageView.setMessage(R.string.msg_permdenied_cam);
		});

		cameraView.setOnClickListener(view -> {
			/* Allow to retry if connecting failed or permission denied. */
			if (usbConnection == null) {
				device = null;
				askPermission(Manifest.permission.CAMERA, granted -> {
					if (granted) {
						usbMonitor.start(this);
						usbMonitor.scan();
					} else messageView.setMessage(R.string.msg_permdenied_cam);
				});
				return;
			}
			infiCam.calibrate();
		});

		ImageButton buttonShutter = findViewById(R.id.buttonShutter);
		buttonShutter.setOnClickListener(view -> infiCam.calibrate());

		ImageButton buttonPhoto = findViewById(R.id.buttonPhoto);
		buttonPhoto.setOnClickListener(view -> {
			if (usbConnection != null)
				takePic = true;
		});

		ImageButton buttonPalette = findViewById(R.id.buttonPalette);
		buttonPalette.setOnClickListener(view -> {
			if (++currentPalette == Palette.palettes.length)
				currentPalette = 0;
			infiCam.setPalette(Palette.palettes[currentPalette].getData());
			messageView.showMessage(Palette.palettes[currentPalette].name);
		});

		ImageButton buttonVideo = findViewById(R.id.buttonVideo);
		buttonVideo.setOnClickListener(view -> toggleRecording());

		dialogBackground = findViewById(R.id.dialogBackground);
		dialogBackground.setOnClickListener(view -> dialogBackground.setVisibility(View.GONE));
		dialogSettings = findViewById(R.id.dialogSettings);
		settings = findViewById(R.id.settings);
		settings.init(this);

		ImageButton buttonSettings = findViewById(R.id.buttonSettings);
		buttonSettings.setOnClickListener(view -> openDialog(dialogSettings));
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();
		settings.load();

		/* Do not ask permission with dialogs from onResume(), they'd trigger more onResume(). */
		if (checkPermission(Manifest.permission.CAMERA)) {
			if (!usbPermissionAsked || usbPermissionAcquired)
				usbMonitor.scan();
			else messageView.setMessage(R.string.msg_permdenied_usb);
		} else messageView.setMessage(R.string.msg_permdenied_cam);
	}

	public void onFrame() {
		/* We use the inputSurface for the listener because it has the most relevant timestamp. */
		surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
		/* At this point we are certain the frame and the lastFi and lastTemp are matched up with
		 *   eachother, so now we can do stuff like taking a screenshot, "the frame" here meaning
		 *   what's in the SurfaceTexture buffers after the updateTexImage() calls surfaceMuxer
		 *   should have done.
		 */
		overlay.draw(lastFi, lastTemp);
		if (takePic) {
			/* For taking picture, we substitute in another overlay surface so that we can draw
			 *   it at the exact resolution the image is saved, to make it look nice. The video
			 *   surface(s) come in at whatever resolution they are and are scaled by the muxer
			 *   regardless, so we don't need to worry about those.
			 */
			overlay.setSize(picWidth, picHeight);
			overlay.draw(lastFi, lastTemp);
			overlaySurface.getSurfaceTexture().updateTexImage();
			Bitmap bitmap = surfaceMuxer.getBitmap(picWidth, picHeight);
			Util.writePNG(this, bitmap);
			overlay.setSize(cameraView.getWidth(), cameraView.getHeight());
			takePic = false;
			messageView.shortMessage(getString(R.string.msg_captured));
		}

		/* Now we allow another frame to come in */
		synchronized (frameLock) {
			frameLock.notify();
		}
	}

	void openDialog(View dialog) {
		for (int i = 0; i < dialogBackground.getChildCount(); ++i)
			dialogBackground.getChildAt(i).setVisibility(View.GONE);
		dialog.setVisibility(View.VISIBLE);
		dialogBackground.setVisibility(View.VISIBLE);
	}

	void disconnect() {
		synchronized (frameLock) { /* Make sure the frameLock thing doesn't deadlock. */
			disconnecting = true;
			frameLock.notify();
		}
		infiCam.stopStream();
		infiCam.disconnect();
		if (usbConnection != null)
			usbConnection.close();
		usbConnection = null;
		device = null;
		messageView.setMessage(R.string.msg_disconnected);
	}

	void toggleRecording() {
		if (recordSurface == null) {
			try {
				Surface rsurface = recorder.startRecording(this, picWidth, picHeight);
				recordSurface = new SurfaceMuxer.OutputSurface(surfaceMuxer, rsurface, false);
				recordSurface.setSize(picWidth, picHeight);
				surfaceMuxer.outputSurfaces.add(recordSurface);
				ImageButton buttonVideo = findViewById(R.id.buttonVideo);
				buttonVideo.setColorFilter(Color.RED);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else stopRecording();
	}

	void stopRecording() {
		if (recordSurface != null) {
			ImageButton buttonVideo = findViewById(R.id.buttonVideo);
			buttonVideo.clearColorFilter();
			recorder.stopRecording();
			surfaceMuxer.outputSurfaces.remove(recordSurface);
			recordSurface.release();
			recordSurface = null;
		}
	}

	@Override
	protected void onPause() {
		stopRecording();
		disconnect();
		surfaceMuxer.deinit();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		usbMonitor.stop();
		super.onDestroy();
	}

	/*
	 * Following are routines called by the settings class.
	 */

	public void setSmooth(boolean smooth) {
		inputSurface.setSmooth(smooth);
	}
}
