package be.ntmn.inficam;

import android.Manifest;
import android.content.pm.PackageManager;
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
	boolean usbPermissionAsked = false, usbPermissionAquired = false;
	InfiCam infiCam = new InfiCam();
	Overlay overlay;
	SurfaceMuxer surfaceMuxer = new SurfaceMuxer();
	SurfaceMuxer.OutputSurface outputSurface;
	SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
	SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
	SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */

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
					usbPermissionAquired = true;
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

		/* Create and set up the InputSurface for annotations overlay. */
		overlaySurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.add(overlaySurface);
		overlaySurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		overlay = new Overlay(overlaySurface, 1280, 960); // TODO decide the size
		infiCam.setFrameCallback((fi, temp) -> overlay.draw(fi, temp));

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
		if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
			if (!usbPermissionAsked || usbPermissionAquired)
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
