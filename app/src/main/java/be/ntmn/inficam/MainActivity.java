package be.ntmn.inficam;

import static java.lang.Float.NaN;
import static java.lang.Float.isInfinite;
import static java.lang.Float.isNaN;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	/* These are public for Settings things to access them. */
	public final InfiCam infiCam = new InfiCam();
	public ThermalRenderer thermalRenderer;

	private SurfaceMuxer surfaceMuxer;
	private SurfaceMuxer.InputSurface inputSurface; /* Input surface for the thermal image. */
	private SurfaceMuxer.ThroughSurface thruSurface; /* We sharpen separately to do it lo-res. */
	private SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera. */
	private Overlay overlayScreen, overlayRecord, overlayPicture;
	private SurfaceMuxer.OutputSurface outScreen, outRecord;
	private final Overlay.Data overlayData = new Overlay.Data();
	private int iMode;

	private volatile UsbDevice usb_device;
	private volatile UsbDeviceConnection usbConnection;
	public final Object frameLock = new Object();
	private int picWidth = 1024, picHeight = 768;
	private int vidWidth = 1024, vidHeight = 768;
	private boolean takePic = false;
	private volatile boolean disconnecting = false;
	private final SurfaceRecorder recorder = new SurfaceRecorder();
	private boolean recordAudio;
	private final Rect rect = new Rect(); /* To use during frames, to avoid allocating it there. */

	private CameraView cameraView;
	private MessageView messageView;
	private ViewGroup dialogBackground;
	private Settings activeSettingsDialog;
	private SettingsMain settings;
	private SettingsTherm settingsTherm;
	private SettingsMeasure settingsMeasure;
	private SettingsPalette settingsPalette;
	private LinearLayout buttonsLeft, buttonsRight;
	private ConstraintLayout.LayoutParams buttonsLeftLayout, buttonsRightLayout;
	private SliderDouble rangeSlider;
	private ImageButton buttonPhoto;
	private boolean rotate = false;
	private int orientation = 0;
	private boolean swapControls = false;
	private volatile boolean applyLocalCorrection = true;
	private volatile float localCorrection = 0.0f;
	private volatile int connectGeneration = 0;
	private volatile boolean suppressCalibrationRequest = false;
	private boolean pendingCalibrationAfterThermDialog = false;
	private boolean calibrationUiActive = false;
	private volatile boolean overTempLockoutActive = false;
	private int calibrationMessageStep = 0;
	private float scale = 1.0f;
	private int imgType;
	private int imgQuality;

	private final Runnable calibrationMessageRunnable = new Runnable() {
		@Override
		public void run() {
			if (!calibrationUiActive)
				return;
			calibrationMessageStep = (calibrationMessageStep + 1) % 3;
			String dots = calibrationMessageStep == 0 ? "." :
					calibrationMessageStep == 1 ? ".." : "...";
			messageView.setMessage(getString(R.string.msg_calibrating) + dots);
			handler.postDelayed(this, 400);
		}
	};

	private Bitmap imgCompressBitmap;

	private class ImgCompressThread extends Thread {
		private volatile boolean stop = false;
		public final ReentrantLock lock = new ReentrantLock();
		public final Condition cond = lock.newCondition();

		@Override
		public void run() {
			lock.lock();
			while (true) {
				try {
					cond.await();
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				if (stop)
					break;
				try {
					Util.writeImage(getApplicationContext(), imgCompressBitmap, imgType,
							imgQuality);
					imgCompressBitmap.recycle();
				} catch (Exception e) {
					handler.post(() -> messageView.showMessage(e.getMessage()));
				}
				handler.postDelayed(() -> {
					buttonPhoto.setEnabled(true);
					buttonPhoto.setColorFilter(null);
				},200); //keep button visibly activated for a short time, or you can't see it.
			}
			lock.unlock();
		}

		public void shutdown() {
			lock.lock();
			stop = true;
			cond.signal();
			lock.unlock();
			try {
				join();
			} catch (Exception e) { e.printStackTrace(); }
		}
	}
	private ImgCompressThread imgCompressThread;


	private final USBMonitor usbMonitor = new USBMonitor() {
		@Override
		public void onDeviceFound(UsbDevice p_usb_device) {
			if(p_usb_device.getProductName() == null){
				return;
			}
			//From original app, yes it's big.
			boolean is_ours =
				!p_usb_device.getProductName().contains("Search") &&
				(p_usb_device.getProductName().contains("FX3") ||
					p_usb_device.getProductName().contains("PNS") ||
					p_usb_device.getProductName().contains("T5") ||
					p_usb_device.getProductName().contains("T2_V2") ||
					p_usb_device.getProductName().contains("T2S+") ||
					p_usb_device.getProductName().contains("T2-Mg_V2") ||
					p_usb_device.getProductName().contains("Xtherm") ||
					p_usb_device.getProductName().contains("Xmodule") ||
					p_usb_device.getProductName().contains("S0") ||
					p_usb_device.getProductName().contains("S1") ||
					p_usb_device.getProductName().contains("T2L") ||
					p_usb_device.getProductName().contains("T2S") ||
					p_usb_device.getProductName().contains("DL") ||
					p_usb_device.getProductName().contains("DV") ||
					p_usb_device.getProductName().contains("T3S") ||
					p_usb_device.getProductName().contains("T3H") ||
					p_usb_device.getProductName().contains("T3-612") ||
					p_usb_device.getProductName().contains("T3Pro") ||
					p_usb_device.getProductName().contains("T3C") ||
					p_usb_device.getProductName().contains("DP") ||
					p_usb_device.getProductName().contains("T19") ||
					p_usb_device.getProductName().contains("DX300"));
			if (!is_ours) {
				Log.e("inficam","Device is not recognized: "+p_usb_device.getProductName());
				return;
			}

			usb_device = p_usb_device;

			/* Connecting to a UVC device needs camera permission. */
			askPermission(Manifest.permission.CAMERA, granted -> {
				if (!granted) {
					messageView.showMessage(R.string.msg_permdenied_cam);
					return;
				}
				connect(usb_device, new ConnectCallback() {
						@Override
						public void onConnected(UsbDevice dev, UsbDeviceConnection conn) {
							disconnect(); /* Important! Frame callback not allowed during connect. */
							usb_device = dev;
							usbConnection = conn;
							disconnecting = false;
							int token = ++connectGeneration;
							setCalibrationUi(true);
							startCameraConnectThread(dev, conn, token);
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
			if (dev.equals(usb_device)) { disconnect(); }
		}
	};

	private final SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
			outScreen =
					new SurfaceMuxer.OutputSurface(surfaceMuxer, surfaceHolder.getSurface());
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int w, int h) {
			outScreen.setSize(w, h);
			overlayScreen.setSize(w, h);
		}

			@Override
			public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
				outScreen.release();
				outScreen = null;
			}
		};

	/* If the orientation changes between 0 and 180 or 90 and 270 suddenly, onDisplayChanged()
	 *	 is called, but not onConfigurationChanged().
	 */
	private final DisplayManager.DisplayListener displayListener =
		new DisplayManager.DisplayListener() {
			@Override
			public void onDisplayAdded(int displayId) { /* Empty. */ }

		@Override
		public void onDisplayChanged(int displayId) { updateOrientation(); }

		@Override
		public void onDisplayRemoved(int displayId) { /* Empty. */ }
	};

	private final BroadcastReceiver batteryRecevier = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) { updateBatLevel(intent); }
	};

	/* This is called by infiCam to run every frame, it calls the thermal renderer which writes
	 *   the surface, it's good to do the work like applying palette and doing
	 *   complicated measurements here to avoid blocking the main thread. Once this is done we fill
	 *   overlayData with the info needed to draw the overlays and then post handleFrame() to run
	 *   on the main UI thread to do the work that should happen there (everything involving the
	 *   EGL context we've created there).
	 */
	private final InfiCam.FrameCallback frameCallback = new InfiCam.FrameCallback() {
			/* To avoid creating a new lambda object every frame we store one here. */
			private final Runnable handleFrameRunnable = () -> handleFrame();

			@Override
			public void onFrame(InfiCam.FrameInfo fi, float[] temp) {
				/* Note this is called from another thread. */
				synchronized (frameLock) {
					applyLocalCorrection(temp);
					overlayData.fi = fi;
					overlayData.temp = temp;

					if (scale > 1.0f) {
						float lost = (1.0f - 1.0f / scale) / 2.0f;
						overlayData.mmac = Overlay.computeMmacRect(
							temp,
							(int) (lost * fi.width),
							(int) (lost * fi.height),
							(int) ((1.0f - lost) * fi.width) + 1,
							(int) ((1.0f - lost) * fi.height) + 1,
							fi.width
						);
					} else {
						overlayData.mmac = Overlay.computeMmac(temp, fi.width, fi.height);
					}

					if (!overTempLockoutActive &&
							!infiCam.isCalibrating() &&
							!isNaN(overlayData.mmac.max) && overlayData.mmac.max > settingsTherm.getRange()[1] && //over max of the range
							settings.overtempEnabled){ //setting enabled
						Log.e("inficam", "Over temperature protection triggered at "+ overlayData.mmac.max + "C");
						handler.post(() -> overTempLockout());
					}

					if(inputSurface.surface == null) { return; } //We exited the app

					handler.post(() -> thermalRenderer.renderTemperatures(inputSurface.surface,
							settingsPalette.paletteMap,
							temp,
							Float.isNaN(overlayData.rangeMin) ? overlayData.mmac.min : overlayData.rangeMin,
							Float.isNaN(overlayData.rangeMax) ? overlayData.mmac.max : overlayData.rangeMax,
							getCorrectedMaxTempClipping(fi.settings.max_temp_clipping))
					);
					handler.post(handleFrameRunnable);

				}
			}
		};

	private void applyLocalCorrection(float[] temp) {
		if (!applyLocalCorrection || localCorrection == 0.0f)
			return;
		for (int i = 0; i < temp.length; ++i)
			temp[i] += localCorrection;
	}

	private float getCorrectedMaxTempClipping(float maxTempClipping) {
		if (!applyLocalCorrection)
			return maxTempClipping;
		return maxTempClipping + localCorrection;
	}

	private final InfiCam.SettingsCallback settingsCallback =
		new InfiCam.SettingsCallback() {
			@Override
			/* Note this is called from another thread. */
			public void onSettings(InfiCam.CamSettings camSettings) {
				if(settingsTherm == null){
					return;
				}
				synchronized (frameLock) {
					//run on main thread for UI changes
					handler.post(() -> settingsTherm.setSettings(
							camSettings.emissivity,
							camSettings.temp_reflected,
							camSettings.temp_air,
							camSettings.humidity,
							camSettings.distance,
							camSettings.correction,
							camSettings.range
					));
				}
			}
		};

	private boolean isCurrentConnection(int token, UsbDeviceConnection conn) {
		return connectGeneration == token && usbConnection == conn && !disconnecting;
	}

	private void runOnUiThreadSync(Runnable runnable) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		RuntimeException[] exception = new RuntimeException[1];
		handler.post(() -> {
			try {
				runnable.run();
			} catch (RuntimeException e) {
				exception[0] = e;
			} finally {
				latch.countDown();
			}
		});
		latch.await();
		if (exception[0] != null)
			throw exception[0];
	}

	private void startCameraConnectThread(UsbDevice dev, UsbDeviceConnection conn, int token) {
		new Thread(() -> {
			try {
				infiCam.connect(conn.getFileDescriptor());
				int width = infiCam.getWidth();
				int height = infiCam.getHeight();
				float[][] ranges = infiCam.getRanges();

				runOnUiThreadSync(() -> {
					if (!isCurrentConnection(token, conn))
						return;
					/* Size is only important for cubic interpolation. */
					inputSurface.setSize(width, height);
					thruSurface.setSize(width, height);
					settingsTherm.init(MainActivity.this, ranges);
					thermalRenderer = new ThermalRenderer(width, height);
				});
				if (!isCurrentConnection(token, conn))
					return;

				infiCam.startStream();
				if (!isCurrentConnection(token, conn))
					return;

				suppressCalibrationRequest = true;
				try {
					runOnUiThreadSync(() -> {
						if (isCurrentConnection(token, conn))
							settingsTherm.load(); //needs stream to communicate with the camera
					});
				} finally {
					suppressCalibrationRequest = false;
				}
				if (!isCurrentConnection(token, conn))
					return;

				infiCam.calibrateBlocking();
				handler.post(() -> {
					if (!isCurrentConnection(token, conn))
						return;
					setCalibrationUi(false);
					messageView.clearMessage();
					messageView.showMessage(getString(R.string.msg_connected,
							dev.getProductName()));
					/* We are ready to accept frames */
					infiCam.setFrameCallback(frameCallback);
				});
			} catch (Exception e) {
				String message = e.getMessage() == null ? getString(R.string.msg_connect_failed) :
						e.getMessage();
				handler.post(() -> {
					if (!isCurrentConnection(token, conn))
						return;
					disconnect();
					messageView.showMessage(message);
				});
			}
		}, "InfiCam connect").start();
	}

	private void setViewTreeEnabled(View view, boolean enabled) {
		if (view == null)
			return;
		view.setEnabled(enabled);
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); ++i)
				setViewTreeEnabled(group.getChildAt(i), enabled);
		}
	}

	private void setCalibrationUi(boolean active) {
		if (calibrationUiActive == active)
			return;
		calibrationUiActive = active;
		handler.removeCallbacks(calibrationMessageRunnable);

		setViewTreeEnabled(buttonsLeft, !active);
		setViewTreeEnabled(buttonsRight, !active);
		setViewTreeEnabled(rangeSlider, !active);
		setViewTreeEnabled(dialogBackground, !active);
		if (cameraView != null)
			cameraView.setEnabled(!active);
		if (dialogBackground != null && active)
			hideSettingsDialog();
		if (buttonsLeft != null)
			buttonsLeft.setAlpha(active ? 0.35f : 1.0f);
		if (buttonsRight != null)
			buttonsRight.setAlpha(active ? 0.35f : 1.0f);
		if (rangeSlider != null)
			rangeSlider.setAlpha(active ? 0.35f : 1.0f);

		if (active) {
			calibrationMessageStep = 2;
			calibrationMessageRunnable.run();
		} else if (messageView != null) {
			messageView.clearMessage();
		}
	}

	private void waitForCalibrationDone() {
		new Thread(() -> {
			try {
				while (infiCam.isCalibrating())
					Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			handler.post(() -> {
				if (!infiCam.isCalibrating())
					setCalibrationUi(false);
			});
		}, "InfiCam calibration wait").start();
	}

	private void getRect(Rect r, int w, int h) { /* Git rekt! */
		int sw = w, sh = h, iw = infiCam.getWidth(), ih = infiCam.getHeight();
		if (w == 0 || ih == 0) { iw = 4; ih = 3; }
		if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
			ih ^= iw;
			iw ^= ih;
			ih ^= iw;
		}
		if (ih * w / iw > h)
			sw = iw * h / ih;
		else sh = ih * w / iw;
		r.set(w / 2 - sw / 2, h / 2 - sh / 2,
				w / 2 - sw / 2 + sw, h / 2 - sh / 2 + sh);
	}


	private void drawFrame(SurfaceMuxer.OutputSurface os, Overlay overlay, boolean swap) {
		getRect(rect, os.width, os.height);
		os.clear(0, 0, 0, 1);
		thruSurface.draw(
			os,
			iMode,
			rect.left,
			rect.top,
			rect.width(),
			rect.height()
		);
		overlay.draw(overlayData, settingsPalette, rect);
		overlay.surface.draw(os, SurfaceMuxer.DM_LINEAR);
		// TODO draw normal video if needed
		if (swap) {
			os.setPresentationTime(inputSurface.surfaceTexture.getTimestamp());
			os.swapBuffers();
		}
	}

	/*
	must be called ONLY when frameLock in locked
	 */
	private void handleFrame() {
		if (disconnecting) {
			/* Don't try stuff when disconnected. */
			return;
		}

		/* At this point we are certain the frame and the overlayData are matched up with
		 *   each-other, so now we can do stuff like taking a picture, "the frame" here
		 *   meaning what's in the SurfaceTexture buffers after the updateTexImage() calls
		 *   surfaceMuxer should do.
		 */
		inputSurface.draw(thruSurface, SurfaceMuxer.DM_SHARPEN);
		thruSurface.swapBuffers();

		if (takePic && imgCompressThread == null) {
			messageView.showMessage(R.string.msg_permdenied_storage);
		} else if (takePic && imgCompressThread.lock.tryLock()) {
			int w = picWidth, h = picHeight;
			if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
				h ^= w;
				w ^= h;
				h ^= w;
			}
			SurfaceMuxer.OutputSurface outPicture =
				new SurfaceMuxer.OutputSurface(surfaceMuxer, null, w, h);
			overlayPicture.setSize(w, h);
			drawFrame(outPicture, overlayPicture, false);
			imgCompressBitmap = outPicture.getBitmap();
			outPicture.release();
			imgCompressThread.cond.signal();
			imgCompressThread.lock.unlock();
			takePic = false;
			messageView.shortMessage(R.string.msg_captured);
			buttonPhoto.setEnabled(false);
			buttonPhoto.setColorFilter(Color.RED);
		}

		if (outScreen != null) drawFrame(outScreen, overlayScreen, true);
		if (outRecord != null) drawFrame(outRecord, overlayRecord, true);

	}

	private void overTempLockout() {
		if (overTempLockoutActive)
			return;
		overTempLockoutActive = true;
		messageView.showMessage(R.string.msg_overtemp);
		infiCam.lockShutter();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// TODO this is probably bad
		Thread.setDefaultUncaughtExceptionHandler((paramThread, paramThrowable) -> {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			paramThrowable.printStackTrace(pw);
			Intent sendIntent = new Intent();
			sendIntent.setAction(Intent.ACTION_SEND);
			sendIntent.putExtra(Intent.EXTRA_TEXT, sw.toString());
			sendIntent.setType("text/plain");
			Intent shareIntent = Intent.createChooser(sendIntent,
					"Inficam has crashed, share crash dump?");
			startActivity(shareIntent);
			System.exit(2);
		});

		setContentView(R.layout.activity_main);
		cameraView = findViewById(R.id.cameraView);
		messageView = findViewById(R.id.message);
		surfaceMuxer = new SurfaceMuxer(this);

		/* Create and set up the InputSurface for thermal image, imode setting is not final. */
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer);
		thruSurface = new SurfaceMuxer.ThroughSurface(surfaceMuxer);

		cameraView.getHolder().addCallback(surfaceHolderCallback);

		/* Create and set up the Overlays. */
		overlayScreen = new Overlay(this,
				new SurfaceMuxer.InputSurface(surfaceMuxer));
		overlayRecord = new Overlay(this,
				new SurfaceMuxer.InputSurface(surfaceMuxer));
		overlayPicture = new Overlay(this,
				new SurfaceMuxer.InputSurface(surfaceMuxer));

		/* We use it later. */
		videoSurface = new SurfaceMuxer.InputSurface(surfaceMuxer);

		/* This one runs when the camera settings have ACTUALLY been changed. */
		infiCam.setSettingsCallback(settingsCallback);

		cameraView.setOnClickListener(view -> {
			/* Allow to retry if connecting failed or permission denied. */
			if (usbConnection == null) {
				usb_device = null;
				usbMonitor.scan();
			}
		});
		final ScaleGestureDetector.OnScaleGestureListener scaleListener =
			new ScaleGestureDetector.OnScaleGestureListener() {
				private float scaleStart;

			@Override
			public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
				TextView zl = findViewById(R.id.zoomLevel);
				scale = scaleStart * scaleGestureDetector.getScaleFactor();
				if (scale < 1.0f) {
					scale = 1.0f;
					zl.setVisibility(View.INVISIBLE);
				} else zl.setVisibility(View.VISIBLE);
				if (scale >= 10.0f)
					scale = 10.0f;
				overlayData.scale = scale;
				thruSurface.scale_x = thruSurface.scale_y = scale;
				messageView.shortMessage(getString(R.string.msg_zoom, (int) (scale * 100.0f)));
				zl.setText(getString(R.string.zoomlevel, (int) (scale * 100.0f)));
				return false;
			}

			@Override
			public boolean onScaleBegin(@NonNull ScaleGestureDetector scaleGestureDetector) {
				scaleStart = scale;
				return true;
			}

			@Override
			public void onScaleEnd(@NonNull ScaleGestureDetector scaleGestureDetector) { /* Empty. */ }
		};
		cameraView.setScaleListener(scaleListener);

		ImageButton buttonShutter = findViewById(R.id.buttonShutter);

		buttonShutter.setOnClickListener(view -> {
			overTempLockoutActive = false;
			buttonShutter.setColorFilter(Color.RED);
			view.postDelayed(() -> buttonShutter.setColorFilter(null), 500);
			infiCam.unlockShutter(); //unlock shutter if locked
			calibrate(false);
		});

		buttonPhoto = findViewById(R.id.buttonPhoto);
		buttonPhoto.setOnClickListener(view -> {
			if (usbConnection != null) {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
					askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
						if (!granted) {
							messageView.showMessage(R.string.msg_permdenied_storage);
							return;
						}
						takePic = true;
					});
				} else takePic = true;
			}
		});

		ImageButton buttonPalette = findViewById(R.id.buttonPalette);
		buttonPalette.setOnClickListener(view -> {
			settingsPalette.getPalette().setTo((settingsPalette.getPalette().get() + 1) %
					settingsPalette.getPalette().getItems().length);
			messageView.showMessage(Palette.palettes[settingsPalette.getPalette().get()].name);
		});
		buttonPalette.setOnLongClickListener(view -> {
			showSettings(settingsPalette);
			return true;
		});

		ImageButton buttonLock = findViewById(R.id.buttonLock);
		buttonLock.setOnClickListener(view -> {
			synchronized (frameLock) {
				if (isNaN(overlayData.rangeMin) && isNaN(overlayData.rangeMax)) { //range is not set
					overlayData.rangeMin = overlayData.mmac.min;
					overlayData.rangeMax = overlayData.mmac.max;
					buttonLock.setImageResource(R.drawable.ic_baseline_lock_24);
					rangeSlider.setVisibility(View.VISIBLE);
					float start = settingsTherm.getRange()[0];
					float end = settingsTherm.getRange()[1];
					if (overlayData.rangeMin < start)
						start = (float) floor(overlayData.rangeMin);
					if (overlayData.rangeMax > end)
						end = (float) ceil(overlayData.rangeMax);
					if (isNaN(overlayData.rangeMin) || isInfinite(overlayData.rangeMin))
						overlayData.rangeMin = start;
					if (isNaN(overlayData.rangeMax) || isInfinite(overlayData.rangeMax))
						overlayData.rangeMax = end;
					rangeSlider.setValueFrom(start);
					rangeSlider.setValueTo(end);
					rangeSlider.setValues(overlayData.rangeMin, overlayData.rangeMax);
				} else {
					overlayData.rangeMin = overlayData.rangeMax = NaN;
					buttonLock.setImageResource(R.drawable.ic_baseline_lock_open_24);
					rangeSlider.setVisibility(View.GONE);
				}
			}
		});

		rangeSlider = findViewById(R.id.rangeSlider);
		rangeSlider.setStepSize(1.0f);
		rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
			List<Float> v = rangeSlider.getValuesCorrected();
			if (v.size() < 2 || !fromUser)
				return;
			if (value == slider.getValues().get(0))
				overlayData.rangeMin = v.get(0);
			else overlayData.rangeMax = v.get(1);
		});

		ImageButton buttonVideo = findViewById(R.id.buttonVideo);
		buttonVideo.setOnClickListener(view -> toggleRecording());

		dialogBackground = findViewById(R.id.dialogBackground);
		dialogBackground.setOnClickListener(view -> hideSettingsDialog());
		settings = findViewById(R.id.settings);
		settings.init(this);
		settingsTherm = findViewById(R.id.settingsTherm); //This one has to be initialized later when we know the camera model
		settingsMeasure = findViewById(R.id.settingsMeasure);
		settingsMeasure.init(this);
		settingsPalette = findViewById(R.id.settingsPalette);
		settingsPalette.init(this);

		ImageButton buttonSettings = findViewById(R.id.buttonSettings);
		buttonSettings.setOnClickListener(view -> showSettings(settings));

		ImageButton buttonSettingsTherm = findViewById(R.id.buttonSettingsTherm);
		buttonSettingsTherm.setOnClickListener(view -> showSettings(settingsTherm));

		ImageButton buttonSettingsMeasure = findViewById(R.id.buttonSettingsMeasure);
		buttonSettingsMeasure.setOnClickListener(view -> showSettings(settingsMeasure));

		ImageButton buttonGallery = findViewById(R.id.buttonGallery);
		buttonGallery.setOnClickListener(view -> {
			buttonGallery.setColorFilter(Color.RED);
			view.postDelayed(() -> buttonGallery.setColorFilter(null), 200);

			String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
					? Manifest.permission.READ_MEDIA_IMAGES
					: Manifest.permission.READ_EXTERNAL_STORAGE;

			askPermission(perm, granted -> {
				if (!granted) {
					messageView.showMessage(R.string.msg_permdenied_storage);
					return;
				}
				Util.openGallery(this, msg -> messageView.showMessage(msg));
			});
		});

		buttonsLeft = findViewById(R.id.buttonsLeft);
		buttonsRight = findViewById(R.id.buttonsRight);
		buttonsLeftLayout = (ConstraintLayout.LayoutParams) buttonsLeft.getLayoutParams();
		buttonsRightLayout = (ConstraintLayout.LayoutParams) buttonsRight.getLayoutParams();
	}

	@Override
	protected void onStart() {
		super.onStart();
		settings.load();
		settingsMeasure.load();
		settingsPalette.load();
		DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
		displayManager.registerDisplayListener(displayListener, handler);
		IntentFilter batIFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			batteryStatus = registerReceiver(
				batteryRecevier,
				batIFilter,
				Context.RECEIVER_NOT_EXPORTED
			);
		} else {
			batteryStatus = registerReceiver(batteryRecevier, batIFilter);
		}
		if(batteryStatus != null) {
			updateBatLevel(batteryStatus);
		}

		/* Beware that we can't call these in onResume as they'll ask permission with dialogs and
		 *	 thus trigger another onResume().
		 */
		usbMonitor.start(this);
		usbMonitor.scan();

		imgCompressThread = new ImgCompressThread();
		imgCompressThread.start();
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();
	}

	@Override
	protected void onPause() {
		surfaceMuxer.deinit();
		takePic = false;
		super.onPause();
	}

	@Override
	protected void onStop() {
		imgCompressThread.shutdown();
		imgCompressThread = null;
		unregisterReceiver(batteryRecevier);
		DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
		displayManager.unregisterDisplayListener(displayListener);
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

	@Override
	public void onBackPressed() {
		if (dialogBackground.getVisibility() == View.VISIBLE)
			hideSettingsDialog();
		else super.onBackPressed();
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void updateOrientation() { /* Called on start by SettingsMain. */
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		orientation = wm.getDefaultDisplay().getRotation();
		ConstraintLayout.LayoutParams rlp = (ConstraintLayout.LayoutParams) rangeSlider.getLayoutParams();
		if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
			thruSurface.rotate90 = true;
			buttonsLeft.setOrientation(LinearLayout.HORIZONTAL);
			buttonsRight.setOrientation(LinearLayout.HORIZONTAL);
			buttonsLeftLayout.width = ViewGroup.LayoutParams.MATCH_PARENT;
			buttonsLeftLayout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			buttonsLeftLayout.topToTop = R.id.mainLayout;
			buttonsLeftLayout.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
			buttonsLeftLayout.leftToLeft = R.id.mainLayout;
			buttonsLeftLayout.rightToRight = R.id.mainLayout;
			buttonsRightLayout.width = ViewGroup.LayoutParams.MATCH_PARENT;
			buttonsRightLayout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			buttonsRightLayout.topToTop = ConstraintLayout.LayoutParams.UNSET;
			buttonsRightLayout.bottomToBottom = R.id.mainLayout;
			buttonsRightLayout.leftToLeft = R.id.mainLayout;
			buttonsRightLayout.rightToRight = R.id.mainLayout;
			buttonsLeft.setLayoutParams(buttonsLeftLayout);
			buttonsRight.setLayoutParams(buttonsRightLayout);
			buttonsLeft.setLayoutParams(buttonsLeftLayout);
			buttonsRight.setLayoutParams(buttonsRightLayout);
			buttonsLeft.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			buttonsRight.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			rlp.rightToLeft = ConstraintLayout.LayoutParams.UNSET;
			rlp.topToTop = ConstraintLayout.LayoutParams.UNSET;
			//noinspection SuspiciousNameCombination
			rlp.topToBottom = R.id.buttonsLeft;
			rlp.leftToRight = ConstraintLayout.LayoutParams.UNSET;
			rlp.leftToLeft = R.id.mainLayout;
			rlp.width = WindowManager.LayoutParams.MATCH_PARENT;
			rlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			rangeSlider.setLayoutParams(rlp);
			rangeSlider.setVertical(false);
		} else {
			thruSurface.rotate90 = false;
			buttonsLeft.setOrientation(LinearLayout.VERTICAL);
			buttonsRight.setOrientation(LinearLayout.VERTICAL);
			buttonsLeftLayout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			buttonsLeftLayout.height = ViewGroup.LayoutParams.MATCH_PARENT;
			buttonsLeftLayout.topToTop = ConstraintLayout.LayoutParams.UNSET;
			buttonsLeftLayout.bottomToBottom = R.id.mainLayout;
			buttonsLeftLayout.leftToLeft = R.id.mainLayout;
			buttonsLeftLayout.rightToRight = R.id.mainLayout;
			buttonsRightLayout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			buttonsRightLayout.height = ViewGroup.LayoutParams.MATCH_PARENT;
			buttonsRightLayout.topToTop = R.id.mainLayout;
			buttonsRightLayout.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
			buttonsRightLayout.leftToLeft = R.id.mainLayout;
			buttonsRightLayout.rightToRight = R.id.mainLayout;
			rlp.topToTop = R.id.mainLayout;
			rlp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
			rlp.leftToRight = ConstraintLayout.LayoutParams.UNSET;
			rlp.rightToLeft = ConstraintLayout.LayoutParams.UNSET;
			rlp.leftToLeft = ConstraintLayout.LayoutParams.UNSET;
			rlp.width = WindowManager.LayoutParams.WRAP_CONTENT;
			rlp.height = WindowManager.LayoutParams.MATCH_PARENT;
			if (swapControls) {
				rlp.rightToLeft = R.id.buttonsLeft;
				buttonsLeft.setLayoutParams(buttonsRightLayout);
				buttonsRight.setLayoutParams(buttonsLeftLayout);
				buttonsLeft.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
				buttonsRight.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			} else {
				rlp.leftToRight = R.id.buttonsLeft;
				buttonsLeft.setLayoutParams(buttonsLeftLayout);
				buttonsRight.setLayoutParams(buttonsRightLayout);
				buttonsLeft.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
				buttonsRight.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
			}
			rangeSlider.setLayoutParams(rlp);
			rangeSlider.setVertical(true);
		}
		synchronized (frameLock) {
			overlayData.rotate90 = orientation == Surface.ROTATION_0 ||
					orientation == Surface.ROTATION_180;
			if (orientation == Surface.ROTATION_270 || orientation == Surface.ROTATION_180) {
				overlayData.rotate = !rotate;
				thruSurface.rotate = !rotate;
			} else {
				overlayData.rotate = rotate;
				thruSurface.rotate = rotate;
			}
		}
	}

	private void hideSettingsDialog() {
		boolean wasThermDialog = activeSettingsDialog == settingsTherm;
		activeSettingsDialog = null;
		dialogBackground.setVisibility(View.GONE);
		boolean changedThermSettings = wasThermDialog && settingsTherm.endDeferredCameraUpdates();
		boolean pendingNativeCalibration = wasThermDialog &&
				infiCam.setCalibrationSuppressed(false);
		if (changedThermSettings || pendingCalibrationAfterThermDialog ||
				pendingNativeCalibration) {
			pendingCalibrationAfterThermDialog = false;
			calibrate(false);
		}
	}

	private void showSettings(Settings settings) {
		if (activeSettingsDialog == settingsTherm && activeSettingsDialog != settings) {
			activeSettingsDialog = null;
			boolean changedThermSettings = settingsTherm.endDeferredCameraUpdates();
			boolean pendingNativeCalibration = infiCam.setCalibrationSuppressed(false);
			if (changedThermSettings || pendingCalibrationAfterThermDialog ||
					pendingNativeCalibration) {
				pendingCalibrationAfterThermDialog = false;
				calibrate(false);
			}
		}
		FrameLayout dialogs = dialogBackground.findViewById(R.id.dialogs);
		for (int i = 0; i < dialogs.getChildCount(); ++i)
			dialogs.getChildAt(i).setVisibility(View.GONE);
		settings.setVisibility(View.VISIBLE);
		activeSettingsDialog = settings;
		if (settings == settingsTherm) {
			settingsTherm.beginDeferredCameraUpdates();
			infiCam.setCalibrationSuppressed(true);
		}
		dialogBackground.setVisibility(View.VISIBLE);
		TextView title = findViewById(R.id.dialogTitle);
		title.setText(settings.getName());
	}

	private void disconnect() {
		connectGeneration++;
		setCalibrationUi(false);
		overTempLockoutActive = false;
		stopRecording();
		infiCam.setFrameCallback(null); //disable frames coming in
		disconnecting = true;
		infiCam.stopStream();
		infiCam.disconnect();
		if (usbConnection != null) { usbConnection.close(); }
		usbConnection = null;
		usb_device = null;
		messageView.setMessage(R.string.msg_disconnected);
	}

	private void toggleRecording() {
		if (!recorder.isRecording() && usbConnection != null) {
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

	private void startRecording(boolean recordAudio) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
				if (!granted)
					messageView.showMessage(R.string.msg_permdenied_storage);
				else _startRecording(recordAudio);
			});
		} else _startRecording(recordAudio);
	}

	/* Request audio permission first when necessary! */
	private void _startRecording(boolean recordAudio) {
		try {
			int w = vidWidth, h = vidHeight;
			if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
				h ^= w;
				w ^= h;
				h ^= w;
			}
			Surface rsurface = recorder.start(this, w, h, recordAudio);
			outRecord = new SurfaceMuxer.OutputSurface(surfaceMuxer, rsurface);
			outRecord.setSize(w, h);
			overlayRecord.setSize(w, h);
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
		if (outRecord != null) {
			outRecord.release();
			outRecord = null;
		}
	}

	public void updateBatLevel(Intent batteryStatus) {
		int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
				status == BatteryManager.BATTERY_STATUS_FULL;
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		BatteryLevel batLevel = findViewById(R.id.batLevel);
		batLevel.setLevel(scale, level, isCharging);
	}

	/*
	 * Following are routines called by the settings class.
	 */

	public void setIMode(int value) { iMode = value; }
	public void setSharpening(float value) { inputSurface.sharpening = value; }

	public void setRecordAudio(boolean value) { recordAudio = value; }

	public void setSwapControls(boolean value) {
		swapControls = value;
		updateOrientation();
	}

	public void setApplyLocalCorrection(boolean value) {
		applyLocalCorrection = value;
	}

	public void setLocalCorrection(float value) {
		localCorrection = value;
	}

	public void setShowBatLevel(boolean value) {
		BatteryLevel batLevel = findViewById(R.id.batLevel);
		batLevel.setVisibility(value ? View.VISIBLE : View.GONE);
	}

	public void setRotate(boolean value) {
		rotate = value;
		updateOrientation();
	}

	public void setMirror(boolean value) {
		synchronized (frameLock) {
			overlayData.mirror = value;
			thruSurface.mirror = value;
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

	public void setPicSize(int w, int h) {
		picWidth = w; /* No need to sync, only used on UI thread. */
		picHeight = h;
	}

	public void setVidSize(int w, int h) {
		vidWidth = w;
		vidHeight = h;
	}

	public void setOrientation(int i) {
		setRequestedOrientation(i);
		updateOrientation();
	}

	public void setImgType(int i) {
		if (imgCompressThread != null)
			imgCompressThread.lock.lock();
		imgType = i;
		if (imgCompressThread != null)
			imgCompressThread.lock.unlock();
	}

	public void setImgQuality(int i) {
		if (imgCompressThread != null)
			imgCompressThread.lock.lock();
		imgQuality = i;
		if (imgCompressThread != null)
			imgCompressThread.lock.unlock();
	}

	public void setTempUnit(int i) {
		synchronized (frameLock) {
			overlayData.tempUnit = i;
		}
		settings.setTempUnit(i);
		settingsTherm.setTempUnit(i);
		settingsMeasure.setTempUnit(i);
		settingsPalette.setTempUnit(i);
	}

	public void calibrate(boolean blocking) {
		if(blocking){
			infiCam.calibrateBlocking();
		} else {
			if (suppressCalibrationRequest)
				return;
			if (activeSettingsDialog == settingsTherm) {
				pendingCalibrationAfterThermDialog = true;
				return;
			}
			setCalibrationUi(true);
			infiCam.calibrate();
			waitForCalibrationDone();
		}
	}
}
