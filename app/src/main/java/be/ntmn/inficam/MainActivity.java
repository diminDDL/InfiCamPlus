package be.ntmn.inficam;

import static java.lang.Float.NaN;
import static java.lang.Float.isInfinite;
import static java.lang.Float.isNaN;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.round;

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
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	/* These are public for Settings things to access them. */
	public final InfiCam infiCam = new InfiCam();

	private SurfaceMuxer surfaceMuxer;
	private SurfaceMuxer sharpenMuxer; /* We sharpen separately so we can do it lo-res. */
	private SurfaceMuxer.InputSurface inputSurface; /* Input surface for the thermal image. */
	private SurfaceMuxer.OutputSurface sharpOSurface; /* From sharpenMuxer. */
	private SurfaceMuxer.InputSurface sharpISurface; /* To outputMuxer. */
	private SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera. */
	private OverlayMuxer outScreen, outRecord, outPicture;
	private final Overlay.Data overlayData = new Overlay.Data();
	private int range = 120;

	private UsbDevice device;
	private UsbDeviceConnection usbConnection;
	private final Object frameLock = new Object();
	private int picWidth = 1024, picHeight = 768;
	private int vidWidth = 1024, vidHeight = 768;
	private boolean takePic = false;
	private volatile boolean disconnecting = false;
	private final SurfaceRecorder recorder = new SurfaceRecorder();
	private boolean recordAudio;

	private CameraView cameraView;
	private MessageView messageView;
	private ViewGroup dialogBackground;
	private SettingsMain settings;
	private SettingsTherm settingsTherm;
	private SettingsMeasure settingsMeasure;
	private LinearLayout buttonsLeft, buttonsRight;
	private ConstraintLayout.LayoutParams buttonsLeftLayout, buttonsRightLayout;
	private SliderDouble rangeSlider;
	private ImageButton buttonPhoto;
	private boolean rotate = false;
	private int orientation = 0;
	private boolean swapControls = false;
	private float scale = 1.0f;
	private int imgType;
	private int imgQuality;

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
				handler.post(() -> {
					buttonPhoto.setEnabled(true);
					buttonPhoto.setColorFilter(null);
				});
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
			/* Both Infiray cameras and HTI HT-301 report VID 0x1514 I believe. Note that the class
			 *   and subclass are checked because older android versions don't filter for us.
			 */
			if (device != null || dev.getDeviceClass() != 239 || dev.getDeviceSubclass() != 2 ||
					dev.getVendorId() != 0x1514)
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
						device = dev;
						usbConnection = conn;
						disconnecting = false;
						try {
							infiCam.connect(conn.getFileDescriptor());
							/* Size is only important for cubic interpolation. */
							inputSurface.setSize(infiCam.getWidth(), infiCam.getHeight());
							sharpOSurface.setSize(infiCam.getWidth(), infiCam.getHeight());
							sharpISurface.setSize(infiCam.getWidth(), infiCam.getHeight());
							sharpISurface.getSurfaceTexture().setDefaultBufferSize(infiCam.getWidth(), infiCam.getHeight());
							handler.removeCallbacks(timedShutter); /* Before stream starts! */
							infiCam.startStream();
							handler.postDelayed(timedShutter, shutterIntervalInitial);
							messageView.clearMessage();
							messageView.showMessage(getString(R.string.msg_connected,
									dev.getProductName()));
						} catch (Exception e) {
							disconnect();
							messageView.showMessage(e.getMessage());
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
			if (dev.equals(device))
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
			sharpISurface.getRect(r, w, h);
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

	/* If the orientation changes between 0 and 180 or 90 and 270 suddenly, onDisplayChanged()
	 *   is called, but not onConfigurationChanged().
	 */
	private final DisplayManager.DisplayListener displayListener =
			new DisplayManager.DisplayListener() {
		@Override
		public void onDisplayAdded(int displayId) { /* Empty. */}

		@Override
		public void onDisplayChanged(int displayId) { updateOrientation(); }

		@Override
		public void onDisplayRemoved(int displayId) { /* Empty. */ }
	};

	private final BroadcastReceiver batteryRecevier = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) { updateBatLevel(intent); }
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
			if (disconnecting) { /* Don't try stuff when disconnected. */
				frameLock.notify();
				return;
			}

			/* At this point we are certain the frame and the overlayData are matched up with
			 *   eachother, so now we can do stuff like taking a picture, "the frame" here
			 *   meaning what's in the SurfaceTexture buffers after the updateTexImage() calls
			 *   surfaceMuxer should do.
			 */
			if (takePic && imgCompressThread == null) {
				messageView.showMessage(R.string.msg_permdenied_storage);
			} else if (takePic && imgCompressThread.lock.tryLock()) {
				final Rect r = new Rect();
				int w = picWidth, h = picHeight;
				if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
					int tmp = w;
					w = h;
					h = tmp;
				}
				sharpISurface.getRect(r, w, h);
				outPicture.setSize(w, h);
				outPicture.setRect(r);
				outPicture.attachInput(surfaceMuxer);
				/* We must call the onFrameAvailable() ourselves, otherwise it waits for this
				 *   function to exit first.
				 */
				sharpenMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
				surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
				outPicture.onFrameAvailable(inputSurface.getSurfaceTexture());
				imgCompressBitmap = outPicture.getBitmap();
				imgCompressThread.cond.signal();
				imgCompressThread.lock.unlock();
				outPicture.attachInput(null);
				takePic = false;
				messageView.shortMessage(R.string.msg_captured);
				buttonPhoto.setEnabled(false);
				buttonPhoto.setColorFilter(Color.GRAY);
			} else {
				/* We use the inputSurface because it has the most relevant timestamp. */
				sharpenMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
			}

			/* Now we allow another frame to come in */
			frameLock.notify();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		cameraView = findViewById(R.id.cameraView);
		messageView = findViewById(R.id.message);
		surfaceMuxer = new SurfaceMuxer(this);
		sharpenMuxer = new SurfaceMuxer(this);

		/* Create and set up the InputSurface for thermal image, imode setting is not final. */
		inputSurface = new SurfaceMuxer.InputSurface(sharpenMuxer, SurfaceMuxer.IMODE_SHARPEN);
		sharpenMuxer.inputSurfaces.add(inputSurface);
		sharpISurface = new SurfaceMuxer.InputSurface(surfaceMuxer, SurfaceMuxer.IMODE_LINEAR) {
			@Override
			public void getRect(Rect r, int w, int h) {
				int sw = w, sh = h, iw = 4, ih = 3;
				/* Make size 4:3 aspect ratio for the thermal image, I'd check the actual camera
				 *   dimensions but this function gets used before the camera is connected.
				 */
				if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
					ih = 4;
					iw = 3;
				}
				if (ih * w / iw > h)
					sw = iw * h / ih;
				else sh = ih * w / iw;
				r.set(w / 2 - sw / 2, h / 2 - sh / 2,
						w / 2 - sw / 2 + sw, h / 2 - sh / 2 + sh);
			}
		};
		surfaceMuxer.inputSurfaces.add(sharpISurface);
		sharpOSurface =
				new SurfaceMuxer.OutputSurface(sharpenMuxer, sharpISurface.getSurface(), false);
		sharpenMuxer.outputSurfaces.add(sharpOSurface);
		sharpISurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);

		infiCam.setSurface(inputSurface.getSurface());
		cameraView.getHolder().addCallback(surfaceHolderCallback);

		/* Create and set up the OverlayMuxers. */
		outScreen = new OverlayMuxer(this, overlayData);
		outScreen.attachInput(surfaceMuxer);
		outRecord = new OverlayMuxer(this, overlayData);
		outPicture = new OverlayMuxer(this, overlayData);

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
				sharpISurface.setScale(scale, scale);
				messageView.shortMessage(getString(R.string.msg_zoom, (int) (scale * 100.0f)));
				zl.setText(getString(R.string.zoomlevel, (int) (scale * 100.0f)));
				return false;
			}

			@Override
			public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
				scaleStart = scale;
				return true;
			}

			@Override
			public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) { /* Empty. */ }
		};
		cameraView.setScaleListener(scaleListener);

		ImageButton buttonShutter = findViewById(R.id.buttonShutter);
		buttonShutter.setOnClickListener(view -> infiCam.calibrate());

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
					buttonLock.setImageResource(R.drawable.ic_baseline_lock_24);
					rangeSlider.setVisibility(View.VISIBLE);
					float start = -20.0f, end = 120.0f;
					if (range == 400) {
						start = 100.0f;
						end = 400.0f;
					}
					if (overlayData.rangeMin < start)
						start = (float) floor(overlayData.rangeMin);
					if (overlayData.rangeMax > end)
						end = (float) ceil(overlayData.rangeMax);
					if (isNaN(overlayData.rangeMin) || isInfinite(overlayData.rangeMin))
						overlayData.rangeMin = start;
					if (isNaN(overlayData.rangeMax) || isInfinite(overlayData.rangeMax))
						overlayData.rangeMax = end;
					infiCam.lockRange(overlayData.rangeMin, overlayData.rangeMax);
					rangeSlider.setValueFrom(start);
					rangeSlider.setValueTo(end);
					rangeSlider.setValues(overlayData.rangeMin, overlayData.rangeMax);
				} else {
					overlayData.rangeMin = overlayData.rangeMax = NaN;
					infiCam.lockRange(NaN, NaN);
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
			infiCam.lockRange(overlayData.rangeMin, overlayData.rangeMax);
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
		buttonSettings.setOnClickListener(view -> showSettings(settings));

		ImageButton buttonSettingsTherm = findViewById(R.id.buttonSettingsTherm);
		buttonSettingsTherm.setOnClickListener(view -> showSettings(settingsTherm));

		ImageButton buttonSettingsMeasure = findViewById(R.id.buttonSettingsMeasure);
		buttonSettingsMeasure.setOnClickListener(view -> showSettings(settingsMeasure));

		ImageButton buttonGallery = findViewById(R.id.buttonGallery);
		buttonGallery.setOnClickListener(view -> {
			Intent intent = new Intent();
			intent.setAction(android.content.Intent.ACTION_VIEW);
			intent.setType("image/*");
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
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
		settingsTherm.load();
		settingsMeasure.load();
		DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
		displayManager.registerDisplayListener(displayListener, handler);
		IntentFilter batIFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = registerReceiver(batteryRecevier, batIFilter);
		updateBatLevel(batteryStatus);

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
		//inputSurface.setScale(2.0f, 2.0f); // TODO

		imgCompressThread = new ImgCompressThread();
		imgCompressThread.start();
	}

	@Override
	protected void onResume() {
		super.onResume();
		sharpenMuxer.init();
		surfaceMuxer.init();
		outScreen.init();
		outRecord.init();
		outPicture.init();

		// TODO this is just test for interpolation
		/*SurfaceMuxer.InputSurface test = new SurfaceMuxer.InputSurface(surfaceMuxer, SurfaceMuxer.IMODE_SHARPEN);
		test.getSurfaceTexture().setDefaultBufferSize(8, 6);
		test.setSize(8, 6);
		Canvas tcvs = test.getSurface().lockCanvas(null);
		Paint p = new Paint();
		tcvs.drawColor(Color.YELLOW);
		p.setColor(Color.BLUE);
		tcvs.drawLine(0, 6, 8, 0, p);
		p.setColor(Color.RED);
		tcvs.drawLine(0, 0, 8, 6, p);
		test.getSurface().unlockCanvasAndPost(tcvs);
		test.setSharpening(1.0f);
		surfaceMuxer.inputSurfaces.add(test);
		handler.postDelayed(() -> surfaceMuxer.onFrameAvailable(test.getSurfaceTexture()), 500);*/
	}

	@Override
	protected void onPause() {
		outScreen.deinit();
		outRecord.deinit();
		outPicture.deinit();
		surfaceMuxer.deinit();
		sharpenMuxer.deinit();
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
		outScreen.release();
		surfaceMuxer.release();
		sharpenMuxer.release();
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if (dialogBackground.getVisibility() == View.VISIBLE)
			dialogBackground.setVisibility(View.GONE);
		else super.onBackPressed();
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void updateOrientation() { /* Called on start by SettingsMain. */
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		orientation = wm.getDefaultDisplay().getRotation();
		ConstraintLayout.LayoutParams rlp = (ConstraintLayout.LayoutParams) rangeSlider.getLayoutParams();
		if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
			sharpISurface.setRotate90(true);
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
			rlp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
			rlp.rightToLeft = ConstraintLayout.LayoutParams.UNSET;
			rlp.topToTop = ConstraintLayout.LayoutParams.UNSET;
			rlp.topToBottom = R.id.buttonsLeft;
			rlp.leftToRight = ConstraintLayout.LayoutParams.UNSET;
			rlp.leftToLeft = R.id.mainLayout;
			rlp.width = WindowManager.LayoutParams.MATCH_PARENT;
			rlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
			rangeSlider.setLayoutParams(rlp);
			rangeSlider.setVertical(false);
		} else {
			sharpISurface.setRotate90(false);
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
				sharpISurface.setRotate(!rotate);
			} else {
				overlayData.rotate = rotate;
				sharpISurface.setRotate(rotate);
			}
			/* Sometimes full 180 rotation doesn't trigger onSurfaceChanged() so we update the rect
			 *   here too.
			 */
			final Rect r = new Rect();
			sharpISurface.getRect(r, cameraView.getWidth(), cameraView.getHeight());
			outScreen.setRect(r);
			sharpISurface.getRect(r, outRecord.getWidth(), outRecord.getHeight());
			outRecord.setRect(r); /* Also the rect for video recording should be updated. */
		}
	}

	private void showSettings(Settings settings) {
		FrameLayout dialogs = dialogBackground.findViewById(R.id.dialogs);
		for (int i = 0; i < dialogs.getChildCount(); ++i)
			dialogs.getChildAt(i).setVisibility(View.GONE);
		settings.setVisibility(View.VISIBLE);
		dialogBackground.setVisibility(View.VISIBLE);
		TextView title = findViewById(R.id.dialogTitle);
		title.setText(settings.getName());
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
			final Rect r = new Rect();
			sharpISurface.getRect(r, vidWidth, vidHeight);
			outRecord.setSize(vidWidth, vidHeight);
			outRecord.setRect(r);
			outRecord.attachInput(surfaceMuxer);
			Surface rsurface = recorder.start(this, vidWidth, vidHeight, recordAudio);
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
		outRecord.attachInput(null);
	}

	public void updateBatLevel(Intent batteryStatus) {
		int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
				status == BatteryManager.BATTERY_STATUS_FULL;
		int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
		TextView batLevel = findViewById(R.id.batLevel);
		batLevel.setText(getString(isCharging ? R.string.batlevel_charging : R.string.batlevel,
				round(level * 100 / (float) scale)));
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

	public void setIMode(int value) { sharpISurface.setIMode(value); }

	public void setSharpening(float value) { inputSurface.setSharpening(value); }

	public void setRecordAudio(boolean value) {
		recordAudio = value;
	}

	public void setRange(int range) {
		infiCam.setRange(range);
		this.range = range;
		requestReinit();
	}

	/* For settings that need a calibration, defers the initial click. */
	public void requestReinit() {
		handler.removeCallbacks(timedShutter);
		handler.postDelayed(timedShutter, shutterIntervalInitial);
	}

	public void setSwapControls(boolean value) {
		swapControls = value;
		updateOrientation();
	}

	public void setShowBatLevel(boolean value) {
		TextView batLevel = findViewById(R.id.batLevel);
		batLevel.setVisibility(value ? View.VISIBLE : View.GONE);
	}

	public void setPalette(int[] data) {
		overlayData.palette = data;
		infiCam.setPalette(data);
	}

	public void setRotate(boolean value) {
		rotate = value;
		updateOrientation();
	}

	public void setMirror(boolean value) {
		synchronized (frameLock) {
			overlayData.mirror = value;
			sharpISurface.setMirror(value);
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
		settingsMeasure.setTempUnit(i);
		settingsTherm.setTempUnit(i);
	}
}
