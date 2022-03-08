package be.ntmn.inficam;

import android.Manifest;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	SurfaceView cameraView;
	MessageView messageView;
	UsbDevice device;
	UsbDeviceConnection usbConnection;
	boolean usbPermissionAsked = false, usbPermissionAcquired = false;
	InfiCam infiCam = new InfiCam();
	Overlay overlay;
	SurfaceMuxer surfaceMuxer = new SurfaceMuxer();
	SurfaceMuxer.OutputSurface outputSurface;
	SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
	SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
	SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */
	InfiCam.FrameInfo lastFi;
	float[] lastTemp;
	int picWidth = 640, picHeight = 480;

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
						messageView.showMessage(R.string.msg_connected, false);
					} catch (Exception e) {
						usbConnection.close();
						Log.e("TESTROT", "" + e.getMessage());
						messageView.showMessage(getString(R.string.msg_connect_failed), true);
					}
				}

				@Override
				public void onPermissionDenied(UsbDevice dev) {
					messageView.showMessage(R.string.msg_permdenied_usb, true);
				}

				@Override
				public void onFailure(UsbDevice dev) {
					messageView.showMessage(R.string.msg_connect_failed, true);
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

		/* Create and set up the InputSurface for thermal image. */
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.add(inputSurface);
		//inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		infiCam.setSurface(inputSurface.getSurface());
		cameraView.getHolder().addCallback(surfaceHolderCallback);
		infiCam.setPalette(Palette.Ironbow.getData()); // TODO UI to choose

		/* Create and set up the InputSurface for annotations overlay.
		 * We also set the frame callback for this one to be the surface muxer, the way this works
		 *   is that first the thermal image on inputSurface gets written, then the frame callback
		 *   runs, the frame callback draws the overlay, and when we flip the buffer of the overlay
		 *   the output surface(s) get written by the muxer. Only once the frame callback returns
		 *   can another frame be processed upstream.
		 */
		overlaySurface = new SurfaceMuxer.InputSurface(surfaceMuxer, false);
		surfaceMuxer.inputSurfaces.add(overlaySurface);
		overlaySurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		overlay = new Overlay(overlaySurface, 1280, 960); // TODO decide the size
		infiCam.setFrameCallback((fi, temp) -> {
			synchronized (this) { /* This is called from another thread. */
				lastFi = fi; /* Save for taking picture and the likes. */
				lastTemp = temp;
				overlay.draw(fi, temp);
			}
		});

		/* Connecting to a UVC device needs camera permission. */
		askPermission(Manifest.permission.CAMERA, granted -> {
			if (granted)
				usbMonitor.start(this);
			else messageView.showMessage(R.string.msg_permdenied_cam, true);
		});

		cameraView.setOnClickListener(view -> {
			/* Allow to retry if connecting failed or permission denied. */
			if (usbConnection == null) {
				device = null;
				askPermission(Manifest.permission.CAMERA, granted -> {
					if (granted) {
						usbMonitor.start(this);
						usbMonitor.scan();
					} else messageView.showMessage(R.string.msg_permdenied_cam, true);
				});
				return;
			}
			infiCam.calibrate();
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();

		/* Do not ask permission with dialogs from onResume(), they'd trigger more onResume(). */
		if (checkPermission(Manifest.permission.CAMERA)) {
			if (!usbPermissionAsked || usbPermissionAcquired)
				usbMonitor.scan();
			else messageView.showMessage(R.string.msg_permdenied_usb, true);
		} else messageView.showMessage(R.string.msg_permdenied_cam, true);
	}

	@Override
	protected void onPause() {
		disconnect();
		surfaceMuxer.deinit();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		usbMonitor.stop();
		super.onDestroy();
	}

	void takePic() {
		synchronized (this) { /* Stop the frame callback from interfering. */
			/* For taking picture, we substitute in another overlay surface so that we can draw
			 *   it at the exact resolution the image is saved, to make it look nice. The video
			 *   surface(s) come in at whatever resolution they are and are scaled by the muxer
			 *   regardless, so we don't need to worry about those.
			 *
			 * TODO make sure the FrameInfo actually matches the exact frame, we only sync to the
			 *  frame callback but the surfaces could get updated if onFrameAvailable() is called
			 *  before our routine here. Or if it hasn't yet been called?
			 */
			SurfaceMuxer.InputSurface tmpOverlaySurf =
					new SurfaceMuxer.InputSurface(surfaceMuxer, false);
			Overlay tmpOverlay = new Overlay(tmpOverlaySurf, picWidth, picHeight);
			surfaceMuxer.inputSurfaces.remove(overlaySurface);
			surfaceMuxer.inputSurfaces.add(tmpOverlaySurf);
			tmpOverlay.draw(lastFi, lastTemp);
			tmpOverlaySurf.getSurfaceTexture().updateTexImage();
			Bitmap bitmap = surfaceMuxer.getBitmap(picWidth, picHeight);
			Util.writePNG(this, bitmap);
			surfaceMuxer.inputSurfaces.remove(tmpOverlaySurf);
			surfaceMuxer.inputSurfaces.add(overlaySurface);
			tmpOverlaySurf.release();
		}
	}

	void disconnect() {
		infiCam.stopStream();
		infiCam.disconnect();
		if (usbConnection != null)
			usbConnection.close();
		usbConnection = null;
		device = null;
		messageView.showMessage(R.string.msg_disconnected, true);
	}
}
