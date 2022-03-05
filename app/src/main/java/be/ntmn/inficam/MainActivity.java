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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.w3c.dom.Text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	SurfaceMuxer surfaceMuxer;
	SurfaceView cameraView;
	InfiCam infiCam = new InfiCam();
	boolean isConnected = false; /* Whether a device is connected. */
	boolean haveDevice = false;
	UsbDeviceConnection usbConnection = null;
	SurfaceMuxer.OutputSurface outputSurface;
	SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
	SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
	SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */

	USBConnector usbConnector = new USBConnector(this) {
		@Override
		public boolean deviceFilter(UsbDevice dev) {
			if (!haveDevice) {
				haveDevice = true;
				return true;
			}
			return false;
		}

		@Override
		public void onConnect(UsbDevice dev, UsbDeviceConnection conn) {
			// TODO this is bad, we don't want to ignore and leave behind open connections
			try {
				Log.e("CONN", "TryConnect " + this + " " + Thread.currentThread().getName());
				if (surfaceMuxer != null && !isConnected) {
					Log.e("CONN", "Connect");
					((TextView) findViewById(R.id.message)).setText("TEST");
					infiCam.connect(conn.getFileDescriptor());
					usbConnection = conn;
					isConnected = true;
					//infiCam.setSurface(inputSurface.getSurface());
					infiCam.startStream();
					handler.postDelayed(() -> infiCam.calibrate(), 1000);
					Log.e("OSURFACES", "n = " + surfaceMuxer.outputSurfaces.size());
				} else {
					Log.e("CONN", "NOConnect " + surfaceMuxer + " " + isConnected);
					conn.close();
				}
			} catch (Exception e) {
				Log.e("CONN", "ERRConnect");
				e.printStackTrace();
				Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_LONG).show();
			}
		}

		@Override
		public void onPermissionDenied(UsbDevice dev) {
			Toast.makeText(ctx, R.string.permdenied_usb, Toast.LENGTH_LONG).show();
		}
	};

	SurfaceHolder.Callback shcallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			if (surfaceMuxer != null) { // TODO what if too early?
				outputSurface = new SurfaceMuxer.OutputSurface(surfaceMuxer, surfaceHolder.getSurface());
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

		/*Bitmap bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bmp);
		Paint p = new Paint();
		p.setColor(Color.TRANSPARENT);
		c.drawRect(new Rect(0, 0, 640, 480), p);
		Paint p2 = new Paint();
		p2.setColor(Color.RED);
		c.drawLine(0, 0, 640, 480, p2);

		SurfaceTexture st = sm.createInputSurfaceTexture();
		st.setDefaultBufferSize(640, 480);
		//st.setOnFrameAvailableListener(et2);
		Surface s = new Surface(st);
		Canvas cvs = s.lockCanvas(null);
		//cvs.drawBitmap(bmp, 0, 0, null);
		cvs.drawLine(0, 0, 640, 480, p2);
		s.unlockCanvasAndPost(cvs);*/

		askPermission(Manifest.permission.CAMERA, granted -> {
			if (granted) {
				usbConnector.start(); /* Connecting to a UVC device needs camera permission. */
			} else {
				Toast.makeText(this, R.string.permdenied_cam, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e("ONRESUME", "START");
		surfaceMuxer = new SurfaceMuxer();
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
		surfaceMuxer.inputSurfaces.clear();
		surfaceMuxer.inputSurfaces.add(inputSurface);
		inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
		try {
			Log.e("ONRESUME", "Setsurf");
			//infiCam.stopStream();
			infiCam.setSurface(inputSurface.getSurface());
			//infiCam.startStream();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// TODO we recreate the outputsurface here because the callback only runs once but this is silly, we can find a better way
		if (outputSurface != null) {
			cameraView = findViewById(R.id.cameraView);
			SurfaceHolder sh = cameraView.getHolder();
			outputSurface = new SurfaceMuxer.OutputSurface(surfaceMuxer, sh.getSurface());
			outputSurface.setSize(1280, 960);
			surfaceMuxer.outputSurfaces.clear();
			surfaceMuxer.outputSurfaces.add(outputSurface);
		}

		/*askPermission(Manifest.permission.CAMERA, granted -> {
			if (granted) {
				SurfaceTexture ist = surfaceMuxer.createInputSurfaceTexture();
				ist.setDefaultBufferSize(1280, 960);
				CameraTest ct = new CameraTest();
				ct.initCamera2(this, new Surface(ist));
				//ist.setOnFrameAvailableListener(surfaceMuxer); // TODO set the right one
			} else {
				Toast.makeText(this, R.string.permdenied_cam, Toast.LENGTH_LONG).show();
			}
		});*/
	}

	@Override
	protected void onPause() {
		Log.e("ONPAUSE", "DISCONNECT");
		isConnected = false;
		haveDevice = false;
		infiCam.stopStream();
		infiCam.disconnect();
		if (usbConnection != null) {
			usbConnection.close();
			usbConnection = null;
		}
		inputSurface.getSurfaceTexture().setOnFrameAvailableListener(null); // TODO this is crap
		surfaceMuxer.release();
		surfaceMuxer = null;
		super.onPause();
	}
}
