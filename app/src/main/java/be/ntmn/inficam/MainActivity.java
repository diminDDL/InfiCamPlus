package be.ntmn.inficam;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	SurfaceView cameraView;
	MessageView messageView;
	UsbDevice device;
	UsbDeviceConnection usbConnection;
	InfiCam infiCam = new InfiCam();
	SurfaceMuxer surfaceMuxer;
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
			// TODO this is bad, we don't want to ignore and leave behind open connections
			try {
				Log.e("CONN", "TryConnect " + this + " " + usbConnection);
				if (surfaceMuxer != null && usbConnection == null) {
					UsbDeviceConnection conn = usbMonitor.connect(dev);
					Log.e("CONN", "Connect");
					infiCam.connect(conn.getFileDescriptor());
					messageView.showMessage(R.string.msg_connected, false);
					usbConnection = conn;
					infiCam.setSurface(inputSurface.getSurface());
					infiCam.startStream();
					handler.postDelayed(() -> infiCam.calibrate(), 1000);
					Log.e("OSURFACES", "n = " + surfaceMuxer.outputSurfaces.size());
				} else {
					Log.e("CONN", "NOConnect " + surfaceMuxer + " " + usbConnection);
				}
			} catch (Exception e) {
				Log.e("CONN", "ERRConnect");
				e.printStackTrace();
				messageView.showMessage(e.getMessage(), true);
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
			/*if (inputSurface != null)
				inputSurface.getSurfaceTexture().setOnFrameAvailableListener(null); // TODO this is crap
			inputSurface = null;*/
			Log.e("DISCONNECT", "DISCONNECT");
			messageView.showMessage(R.string.msg_disconnected, true);
		}
	};

	SurfaceHolder.Callback shcallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			if (surfaceMuxer != null) { // TODO what if too early?
				outputSurface = new SurfaceMuxer.OutputSurface(surfaceMuxer, surfaceHolder.getSurface());
				surfaceMuxer.outputSurfaces.clear();
				surfaceMuxer.outputSurfaces.add(outputSurface);
				Log.e("SURFACE", "created");
			}
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int w, int h) {
			outputSurface.setSize(w, h); // TODO this gets called before surfaceCreated -_-
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
			if (surfaceMuxer != null) {
				surfaceMuxer.outputSurfaces.remove(outputSurface);
				Log.e("SURFACE", "removed");
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		cameraView = findViewById(R.id.cameraView);
		messageView = findViewById(R.id.message);
		SurfaceHolder sh = cameraView.getHolder();
		sh.addCallback(shcallback);

		/* Generate ironbow palette. */
		byte[] palette = new byte[InfiCam.paletteLen * 4];
		for (int i = 0; i + 4 <= palette.length; i += 4) {
			float x = (float) i / (float) palette.length;
			palette[i + 0] = (byte) round(255.0 * sqrt(x));
			palette[i + 1] = (byte) round(255.0 * pow(x, 3));
			palette[i + 2] = (byte) round(255.0 * max(0.0, sin(2.0 * PI * x)));
			palette[i + 3] = (byte) 255;
		}
		IntBuffer ib = ByteBuffer.wrap(palette).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		int[] intPalette = new int[ib.remaining()];
		ib.get(intPalette);
		infiCam.setPalette(intPalette);

		// TODO very temporary
		cameraView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				infiCam.calibrate();
			}
		});

		surfaceMuxer = new SurfaceMuxer();
		surfaceMuxer.init(); // TODO only needed yet to get a valid surfacetexture...
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.clear();
		surfaceMuxer.inputSurfaces.add(inputSurface);
		inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
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
				usbMonitor.start(this); /* Connecting to a UVC device needs camera permission. */
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
