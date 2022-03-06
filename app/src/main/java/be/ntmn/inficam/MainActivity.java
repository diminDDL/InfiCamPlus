package be.ntmn.inficam;

import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	SurfaceView cameraView;
	MessageView messageView;
	UsbDevice device;
	UsbDeviceConnection usbConnection;
	InfiCam infiCam = new InfiCam();
	SurfaceMuxer surfaceMuxer = new SurfaceMuxer();
	SurfaceMuxer.OutputSurface outputSurface;
	SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
	SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
	SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */

	USBMonitor usbMonitor = new USBMonitor() {
		@Override
		public boolean onDeviceFound(UsbDevice dev) {
			if (device == null) {
				device = dev;
				return true;
			}
			return false;
		}

		@Override // TODO what if this arrives before onResume?
		public void onPermissionGranted(UsbDevice dev) {
			Log.e("CONNECTION", "USB permission granted");
			if (usbConnection != null)
				return;
			Log.e("CONNECTION", "requesting camera permission");
			/* Connecting to a UVC device needs camera permission. */
			askPermission(Manifest.permission.CAMERA, granted -> {
				if (granted) {
					Log.e("CONNECTION", "CONNECT");
					usbConnection = usbMonitor.connect(dev);
					infiCam.connect(usbConnection.getFileDescriptor());
					infiCam.startStream();
					handler.postDelayed(() -> infiCam.calibrate(), 1000);
					messageView.showMessage(R.string.msg_connected, false);
				} else {
					messageView.showMessage(R.string.permdenied_cam, true);
				}
			});
		}

		@Override
		public void onPermissionDenied(UsbDevice dev) {
			messageView.showMessage(R.string.permdenied_usb, true);
		}

		@Override
		public void onDisconnect(UsbDevice dev) {
			Log.e("CONNECTION", "DISCONNECT due to broadcast");
			disconnect();
		}
	};

	SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			outputSurface =
					new SurfaceMuxer.OutputSurface(surfaceMuxer, surfaceHolder.getSurface());
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
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.add(inputSurface);
		inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		infiCam.setSurface(inputSurface.getSurface());
		cameraView.getHolder().addCallback(surfaceHolderCallback);
		infiCam.setPalette(Palette.Ironbow.getData()); // TODO UI to choose
		usbMonitor.start(this);

		// TODO very temporary
		cameraView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				infiCam.calibrate();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e("ONRESUME", "resuming");
		surfaceMuxer.init();
		usbMonitor.scan();
	}

	@Override
	protected void onPause() {
		Log.e("ONPAUSE", "pauseing");
		Log.e("CONNECTION", "DISCONNECT due to pause");
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
