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

		@Override
		public void onPermissionGranted(UsbDevice dev) {
			if (inputSurface != null && usbConnection == null) {
				try {
					Log.e("CONN", "TryConnect " + this + " " + usbConnection);
						UsbDeviceConnection conn = usbMonitor.connect(dev);
						Log.e("CONN", "Connect");
						infiCam.connect(conn.getFileDescriptor());
						messageView.showMessage(R.string.msg_connected, false);
						usbConnection = conn;
						infiCam.setSurface(inputSurface.getSurface());
						infiCam.startStream();
						handler.postDelayed(() -> infiCam.calibrate(), 1000);
						Log.e("OSURFACES", "n = " + surfaceMuxer.outputSurfaces.size());
				} catch (Exception e) {
					Log.e("CONN", "ERRConnect");
					e.printStackTrace();
					messageView.showMessage(e.getMessage(), true);
				}
			} else {
				Log.e("CONN", "NOConnect " + surfaceMuxer + " " + usbConnection);
			}
		}

		@Override
		public void onPermissionDenied(UsbDevice dev) {
			messageView.showMessage(R.string.permdenied_usb, true);
		}

		@Override
		public void onDisconnect(UsbDevice dev) {
			infiCam.stopStream();
			infiCam.disconnect();
			if (usbConnection != null)
				usbConnection.close();
			usbConnection = null;
			device = null;
			Log.e("DISCONNECT", "DISCONNECT");
			messageView.showMessage(R.string.msg_disconnected, true);
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
		usbMonitor.start(this);
		infiCam.setPalette(Palette.Ironbow.getData());

		// TODO very temporary
		cameraView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				infiCam.calibrate();
			}
		});

		try {
			Log.e("ONCREATE", "Setsurf");
			//infiCam.stopStream();
			infiCam.setSurface(inputSurface.getSurface());
			//infiCam.startStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();
		Log.e("ONRESUME", "START");

		askPermission(Manifest.permission.CAMERA, granted -> {
			if (granted) {
				Log.e("USBCONN", "start USBMonitor");
				// TODO maybe we should do it in onStart(), but try connect to already existing devices in onRresume()
				 /* Connecting to a UVC device needs camera permission. */
				usbMonitor.scan();
			} else {
				messageView.showMessage(R.string.permdenied_cam, true);
			}
		});
	}

	@Override
	protected void onPause() {
		Log.e("ONPAUSE", "pause");
		infiCam.stopStream();
		infiCam.disconnect();
		device = null;
		if (usbConnection != null) {
			usbConnection.close();
			usbConnection = null;
		}
		Log.e("DISCONNECT", "DISCONNECT because pause");
		messageView.showMessage(R.string.msg_disconnected, true);
		surfaceMuxer.deinit();
		super.onPause();
	}

	@Override
	protected void onStop() {
		usbMonitor.stop();
		super.onStop();
	}
}
