package be.ntmn.inficam;

import static java.lang.Float.NaN;
import static java.lang.Float.isNaN;

import android.Manifest;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.io.IOException;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	/* These are public for Settings things to access them. */
	public final InfiCam infiCam = new InfiCam();
	public SurfaceMuxer.InputSurface inputSurface; /* Input surface for the thermal image. */

	private OverlayMuxer outScreen, outRecord;
	private final Overlay.Data overlayData = new Overlay.Data();

	private UsbDevice device;
	private UsbDeviceConnection usbConnection;
	private SurfaceMuxer surfaceMuxer;
	private SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera. */
	private final Object frameLock = new Object();
	private int picWidth = 1024, picHeight = 768;
	private boolean takePic = false;
	private volatile boolean disconnecting = false;
	private final SurfaceRecorder recorder = new SurfaceRecorder();
	private boolean recordAudio;

	private MessageView messageView;
	private ViewGroup dialogBackground;
	private SettingsMain settings;
	private SettingsTherm settingsTherm;
	private SettingsMeasure settingsMeasure;
	private ViewGroup.LayoutParams buttonsLeftLayout, buttonsRightLayout;

	private long shutterIntervalInitial; /* These are set by Settings class later. */
	private long shutterInterval; /* Xtherm does it 1 sec after connect and then every 380 sec. */
	private final Runnable timedShutter = new Runnable() {
		@Override
		public void run() {
			infiCam.calibrate(); /* No harm when not connected. */
			if (shutterInterval > 0)
				handler.postDelayed(timedShutter, shutterInterval);
		}
	};

	private final USBMonitor usbMonitor = new USBMonitor() {
		@Override
		public void onDeviceFound(UsbDevice dev) {
			if (device != null)
				return;
			device = dev;
			/* Connecting to a UVC device needs camera permission. */
			askPermission(Manifest.permission.CAMERA, granted -> {
				if (!granted) {
					messageView.showMessage(R.string.msg_permdenied_cam);
					return;
				}
				connect(dev, new ConnectCallback() {
					@Override
					public void onConnected(UsbDevice dev, UsbDeviceConnection conn) {
						disconnect(); /* Important! Frame callback not allowed during connect. */
						usbConnection = conn;
						disconnecting = false;
						try {
							infiCam.connect(conn.getFileDescriptor());
							/* Size is only important for cubic interpolation. */
							inputSurface.setSize(infiCam.getWidth(), infiCam.getHeight());
							handler.removeCallbacks(timedShutter); /* Before stream starts! */
							infiCam.startStream();
							handler.postDelayed(timedShutter, shutterIntervalInitial);
							messageView.clearMessage();
							messageView.showMessage(R.string.msg_connected);
						} catch (Exception e) {
							usbConnection.close();
							usbConnection = null;
							messageView.showMessage(getString(R.string.msg_connect_failed));
						}
					}

					@Override
					public void onPermissionDenied(UsbDevice dev) {
						messageView.showMessage(R.string.msg_permdenied_usb);
					}

					@Override
					public void onFailed(UsbDevice dev) {
						messageView.showMessage(getString(R.string.msg_connect_failed));
					}
				});
			});
		}

		@Override
		public void onDisconnect(UsbDevice dev) {
			disconnect();
		}
	};

	private final SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			outScreen.setOutputSurface(surfaceHolder.getSurface());
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int w, int h) {
			final Rect r = new Rect();
			inputSurface.getRect(r, w, h);
			outScreen.setSize(w, h);
			outScreen.setRect(r);
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
			outScreen.setOutputSurface(null);
		}
	};

	private final NormalCamera normalCamera = new NormalCamera() {
		@Override
		public void onStarted() {
			surfaceMuxer.inputSurfaces.add(videoSurface);
		}

		@Override
		public void onStopped() {
			surfaceMuxer.inputSurfaces.remove(videoSurface);
		}

		@Override
		public void onStartFailed(String message) {
			surfaceMuxer.inputSurfaces.remove(videoSurface);
			messageView.showMessage(message);
		}
	};

	/* The way this works is that first the thermal image on inputSurface gets written, then
	 *   the frame callback runs, we copy over the info to overlayData, ask handleFrame() to be
	 *   called on the main thread and then we hold off on returning from the callback until that
	 *   frame and the matching lastFi and lastTemp have been dealt with, after which the frameLock
	 *   should be notified.
	 * The point of it is to make sure we have a matching lastFi and lastTemp with the last
	 *   frame that don't get overwritten by the next run of this callback. The contents of the
	 *   inputSurface texture etc are less of a concern since they don't get updated until
	 *   updateTexImage() is called. We can't just do everything on the callback thread because
	 *   we need our EGL context and EGL contexts are stuck to a particular thread.
	 */
	private final InfiCam.FrameCallback frameCallback = new InfiCam.FrameCallback() {
		/* To avoid creating a new lambda object every frame we store one here. */
		private final Runnable handleFrameRunnable = () -> handleFrame();

		@Override
		public void onFrame(InfiCam.FrameInfo fi, float[] temp) {
			synchronized (frameLock) { /* Note this is called from another thread. */
				overlayData.fi = fi;
				overlayData.temp = temp;
				handler.post(handleFrameRunnable);
				if (disconnecting)
					return;
				/* Now we wait until the main thread has finished drawing the frame, so lastFi and
				 *   lastTemp don't get overwritten before they've been used.
				 */
				try {
					frameLock.wait();
				} catch (Exception e) {
					e.printStackTrace(); /* Not the end of the world, we do try to continue. */
				}
			}
		}
	};

	private void handleFrame() {
		synchronized (frameLock) {
			/* At this point we are certain the frame and the lastFi and lastTemp are matched up
			 *   with eachother, so now we can do stuff like taking a screenshot, "the frame" here
			 *   meaning what's in the SurfaceTexture buffers after the updateTexImage() calls
			 *   surfaceMuxer should have done.
			 */

			/* We use the inputSurface because it has the most relevant timestamp. */
			surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());

			if (takePic) {
				/* For taking picture, we substitute in another overlay surface so that we can draw
				 *   it at the exact resolution the image is saved, to make it look nice. The video
				 *   surface(s) come in at whatever resolution they are and are scaled by the muxer
				 *   regardless, so we don't need to worry about those.
				 */

	/*			overlay.setSize(picWidth, picHeight);
				overlaySurface.beforeRender(picWidth, picHeight);
				overlay.draw(lastFi, lastTemp, palette, rangeMin, rangeMax);
				overlaySurface.getSurfaceTexture().updateTexImage();
				Bitmap bitmap = surfaceMuxer.getBitmap(picWidth, picHeight);
				Util.writePNG(this, bitmap);
				overlay.setSize(cameraView.getWidth(), cameraView.getHeight());
				takePic = false;
				messageView.shortMessage(getString(R.string.msg_captured));*/ // TODO
			}

			/* Now we allow another frame to come in */
			frameLock.notify();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		SurfaceView cameraView = findViewById(R.id.cameraView);
		messageView = findViewById(R.id.message);
		surfaceMuxer = new SurfaceMuxer(this);

		/* Create and set up the InputSurface for thermal image, imode setting is not final. */
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, SurfaceMuxer.IMODE_NEAREST) {
			@Override
			public void getRect(Rect r, int w, int h) {
				int sw = w, sh = h;
				if (infiCam.getHeight() * w / infiCam.getWidth() > h)
					sw = infiCam.getWidth() * h / infiCam.getHeight();
				else sh = infiCam.getHeight() * w / infiCam.getWidth();
				r.set(w / 2 - sw / 2, h / 2 - sh / 2,
						w / 2 - sw / 2 + sw, h / 2 - sh / 2 + sh);
			}
		};
		surfaceMuxer.inputSurfaces.add(inputSurface);
		infiCam.setSurface(inputSurface.getSurface());
		cameraView.getHolder().addCallback(surfaceHolderCallback);

		/* Create and set up the OverlayMuxers. */
		outScreen = new OverlayMuxer(this, overlayData);
		outScreen.attachInput(surfaceMuxer);
		outRecord = new OverlayMuxer(this, overlayData);
		outRecord.attachInput(surfaceMuxer);

		/* We use it later. */
		videoSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, SurfaceMuxer.IMODE_LINEAR);

		/* This one will run every frame. */
		infiCam.setFrameCallback(frameCallback);

		cameraView.setOnClickListener(view -> {
			/* Allow to retry if connecting failed or permission denied. */
			if (usbConnection == null) {
				device = null;
				usbMonitor.scan();
				return;
			}
			//infiCam.calibrate();
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
			settingsTherm.palette.set((settingsTherm.palette.current + 1) %
					settingsTherm.palette.items.length);
			messageView.showMessage(Palette.palettes[settingsTherm.palette.current].name);
		});

		ImageButton buttonLock = findViewById(R.id.buttonLock);
		buttonLock.setOnClickListener(view -> {
			synchronized (frameLock) {
				if (isNaN(overlayData.rangeMin) && isNaN(overlayData.rangeMax)) {
					overlayData.rangeMin = overlayData.fi.min;
					overlayData.rangeMax = overlayData.fi.max;
					infiCam.lockRange(overlayData.rangeMin, overlayData.rangeMax);
					buttonLock.setImageResource(R.drawable.ic_baseline_lock_24);
				} else {
					overlayData.rangeMin = overlayData.rangeMax = NaN;
					infiCam.lockRange(NaN, NaN);
					buttonLock.setImageResource(R.drawable.ic_baseline_lock_open_24);
				}
			}
		});

		ImageButton buttonVideo = findViewById(R.id.buttonVideo);
		buttonVideo.setOnClickListener(view -> toggleRecording());

		dialogBackground = findViewById(R.id.dialogBackground);
		dialogBackground.setOnClickListener(view -> dialogBackground.setVisibility(View.GONE));
		settings = findViewById(R.id.settings);
		settings.init(this);
		settingsTherm = findViewById(R.id.settingsTherm);
		settingsTherm.init(this);
		settingsMeasure = findViewById(R.id.settingsMeasure);
		settingsMeasure.init(this);

		ImageButton buttonSettings = findViewById(R.id.buttonSettings);
		buttonSettings.setOnClickListener(view -> openDialog(settings));

		ImageButton buttonSettingsTherm = findViewById(R.id.buttonSettingsTherm);
		buttonSettingsTherm.setOnClickListener(view -> openDialog(settingsTherm));

		ImageButton buttonSettingsMeasure = findViewById(R.id.buttonSettingsMeasure);
		buttonSettingsMeasure.setOnClickListener(view -> openDialog(settingsMeasure));

		LinearLayout left = findViewById(R.id.buttonsLeft);
		LinearLayout right = findViewById(R.id.buttonsRight);
		buttonsLeftLayout = left.getLayoutParams();
		buttonsRightLayout = right.getLayoutParams();
	}

	@Override
	protected void onStart() {
		super.onStart();
		settings.load();
		settingsTherm.load();
		settingsMeasure.load();

		/* Beware that we can't call these in onResume as they'll ask permission with dialogs and
		 *   thus trigger another onResume().
		 */
		usbMonitor.start(this);
		usbMonitor.scan();

		// TODO
		/*videoSurface.getSurfaceTexture().setDefaultBufferSize(1024, 768); // TODO don't hardcode, also what about aspect?
		videoSurface.setSize(1024, 768); // TODO also don't hardcode this one
		videoSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer); // TODO is it not needed? should we separately update tex images?
		videoSurface.setIMode(SurfaceMuxer.IMODE_EDGE);
		normalCamera.start(this, videoSurface.getSurface());*/
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();
		outScreen.muxer.init();
	}

	@Override
	protected void onPause() {
		outScreen.muxer.deinit();
		surfaceMuxer.deinit();
		super.onPause();
	}

	@Override
	protected void onStop() {
		stopRecording();
		disconnect();
		usbMonitor.stop();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		surfaceMuxer.release();
		super.onDestroy();
	}

	private void openDialog(View dialog) {
		FrameLayout dialogs = dialogBackground.findViewById(R.id.dialogs);
		for (int i = 0; i < dialogs.getChildCount(); ++i)
			dialogs.getChildAt(i).setVisibility(View.GONE);
		dialog.setVisibility(View.VISIBLE);
		dialogBackground.setVisibility(View.VISIBLE);
	}

	private void disconnect() {
		stopRecording();
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

	private void toggleRecording() {
		if (!recorder.isRecording()) {
			askPermission(Manifest.permission.CAMERA, granted -> {
				if (granted) {
					if (!recordAudio) {
						startRecording(false);
						return;
					}
					askPermission(Manifest.permission.RECORD_AUDIO, audiogranted -> {
						if (!audiogranted) {
							messageView.showMessage(R.string.msg_permdenied_audio);
							return;
						}
						startRecording(recordAudio);
					});
				} else messageView.showMessage(R.string.msg_permdenied_cam);
			});
		} else stopRecording();
	}

	/* Request audio permission first when necessary! */
	private void startRecording(boolean recordAudio) {
		try {
			final Rect r = new Rect();
			outRecord.setSize(picWidth, picHeight);
			inputSurface.getRect(r, picWidth, picHeight);
			outRecord.setRect(r);
			Surface rsurface = recorder.start(this, picWidth, picHeight, recordAudio);
			outRecord.setOutputSurface(rsurface);
			ImageButton buttonVideo = findViewById(R.id.buttonVideo);
			buttonVideo.setColorFilter(Color.RED);
		} catch (IOException e) {
			e.printStackTrace();
			messageView.showMessage(R.string.msg_failrecord);
		}
	}

	private void stopRecording() {
		ImageButton buttonVideo = findViewById(R.id.buttonVideo);
		buttonVideo.clearColorFilter();
		recorder.stop();
		outRecord.setOutputSurface(null);
	}

	/*
	 * Following are routines called by the settings class.
	 */

	public void setShutterIntervalInitial(long value) {
		shutterIntervalInitial = value;
	}

	public void setShutterInterval(long value) {
		shutterInterval = value;
		handler.removeCallbacks(timedShutter);
		if (shutterInterval > 0)
			handler.postDelayed(timedShutter, shutterInterval);
	}

	public void setIMode(int value) {
		inputSurface.setIMode(value);
	}

	public void setRecordAudio(boolean value) {
		recordAudio = value;
	}

	public void setRange(int range) {
		infiCam.setRange(range);
		requestReinit();
	}

	/* For settings that need a calibration, defers the initial click. */
	public void requestReinit() {
		handler.removeCallbacks(timedShutter);
		handler.postDelayed(timedShutter, shutterIntervalInitial);
	}

	public void setSwapControls(boolean value) {
		LinearLayout left = findViewById(R.id.buttonsLeft);
		LinearLayout right = findViewById(R.id.buttonsRight);
		if (value) {
			left.setLayoutParams(buttonsRightLayout);
			right.setLayoutParams(buttonsLeftLayout);
		} else {
			left.setLayoutParams(buttonsLeftLayout);
			right.setLayoutParams(buttonsRightLayout);
		}
	}

	public void setPalette(int[] data) {
		overlayData.palette = data;
		infiCam.setPalette(data);
	}

	public void setRotate(boolean value) {
		synchronized (frameLock) {
			overlayData.rotate = value;
		}
	}

	public void setMirror(boolean value) {
		synchronized (frameLock) {
			overlayData.mirror = value;
		}
	}

	public void setShowCenter(boolean value) {
		synchronized (frameLock) {
			overlayData.showCenter = value;
		}
	}

	public void setShowMax(boolean value) {
		synchronized (frameLock) {
			overlayData.showMax = value;
		}
	}

	public void setShowMin(boolean value) {
		synchronized (frameLock) {
			overlayData.showMin = value;
		}
	}

	public void setShowPalette(boolean value) {
		synchronized (frameLock) {
			overlayData.showPalette = value;
		}
	}
}
