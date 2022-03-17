package be.ntmn.inficam;

import static java.lang.Float.NaN;
import static java.lang.Float.isNaN;
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
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.slider.RangeSlider;

import java.io.IOException;

import be.ntmn.libinficam.InfiCam;

public class MainActivity extends BaseActivity {
	/* These are public for Settings things to access them. */
	public final InfiCam infiCam = new InfiCam();

	private SurfaceMuxer surfaceMuxer;
	private SurfaceMuxer.InputSurface inputSurface; /* Input surface for the thermal image. */
	private SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera. */
	private OverlayMuxer outScreen, outRecord, outPicture;
	private final Overlay.Data overlayData = new Overlay.Data();

	private UsbDevice device;
	private UsbDeviceConnection usbConnection;
	private final Object frameLock = new Object();
	private int picWidth = 1024, picHeight = 768;
	private int vidWidth = 1024, vidHeight = 768;
	private boolean takePic = false;
	private volatile boolean disconnecting = false;
	private final SurfaceRecorder recorder = new SurfaceRecorder();
	private boolean recordAudio;

	private SurfaceView cameraView;
	private MessageView messageView;
	private ViewGroup dialogBackground;
	private SettingsMain settings;
	private SettingsTherm settingsTherm;
	private SettingsMeasure settingsMeasure;
	private LinearLayout buttonsLeft, buttonsRight;
	private ConstraintLayout.LayoutParams buttonsLeftLayout, buttonsRightLayout;
	private boolean rotate = false;
	private int orientation = 0;
	private boolean swapControls = false;

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
							messageView.showMessage(getString(R.string.msg_connected,
									dev.getProductName()));
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
			if (takePic) {
				final Rect r = new Rect();
				int w = picWidth, h = picHeight;
				if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
					int tmp = w;
					w = h;
					h = tmp;
				}
				inputSurface.getRect(r, w, h);
				outPicture.setSize(w, h);
				outPicture.setRect(r);
				outPicture.attachInput(surfaceMuxer);
				surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
				Bitmap bitmap = outPicture.getBitmap();
				Util.writePNG(this, bitmap);
				outPicture.attachInput(null);
				takePic = false;
				messageView.shortMessage(R.string.msg_captured);
			} else {
				/* We use the inputSurface because it has the most relevant timestamp. */
				surfaceMuxer.onFrameAvailable(inputSurface.getSurfaceTexture());
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

		/* Create and set up the InputSurface for thermal image, imode setting is not final. */
		inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, SurfaceMuxer.IMODE_LINEAR) {
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
		surfaceMuxer.inputSurfaces.add(inputSurface);
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
				if (overlayData.fi == null)
					return;
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
		buttonSettings.setOnClickListener(view -> showSettings(settings));

		ImageButton buttonSettingsTherm = findViewById(R.id.buttonSettingsTherm);
		buttonSettingsTherm.setOnClickListener(view -> showSettings(settingsTherm));

		ImageButton buttonSettingsMeasure = findViewById(R.id.buttonSettingsMeasure);
		buttonSettingsMeasure.setOnClickListener(view -> showSettings(settingsMeasure));

		buttonsLeft = findViewById(R.id.buttonsLeft);
		buttonsRight = findViewById(R.id.buttonsRight);
		buttonsLeftLayout = (ConstraintLayout.LayoutParams) buttonsLeft.getLayoutParams();
		buttonsRightLayout = (ConstraintLayout.LayoutParams) buttonsRight.getLayoutParams();

		RangeSlider rangeSlider = findViewById(R.id.rangeSlider);
		rangeSlider.setValues(0.5f, 1.0f);
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
	}

	@Override
	protected void onResume() {
		super.onResume();
		surfaceMuxer.init();
		outScreen.init();
		outRecord.init();
		outPicture.init();
	}

	@Override
	protected void onPause() {
		outScreen.deinit();
		outRecord.deinit();
		outPicture.deinit();
		surfaceMuxer.deinit();
		super.onPause();
	}

	@Override
	protected void onStop() {
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
		if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
			inputSurface.setRotate90(true);
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
		} else {
			inputSurface.setRotate90(false);
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
			if (swapControls) {
				buttonsLeft.setLayoutParams(buttonsRightLayout);
				buttonsRight.setLayoutParams(buttonsLeftLayout);
				buttonsLeft.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
				buttonsRight.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			} else {
				buttonsLeft.setLayoutParams(buttonsLeftLayout);
				buttonsRight.setLayoutParams(buttonsRightLayout);
				buttonsLeft.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
				buttonsRight.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
			}
		}
		synchronized (frameLock) {
			if (orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180)
				overlayData.rotate90 = true;
			else overlayData.rotate90 = false;
			if (orientation == Surface.ROTATION_270 || orientation == Surface.ROTATION_180) {
				overlayData.rotate = !rotate;
				inputSurface.setRotate(!rotate);
			} else {
				overlayData.rotate = rotate;
				inputSurface.setRotate(rotate);
			}
			/* Sometimes full 180 rotation doesn't trigger onSurfaceChanged() so we update the rect
			 *   here too.
			 */
			final Rect r = new Rect();
			inputSurface.getRect(r, cameraView.getWidth(), cameraView.getHeight());
			outScreen.setRect(r);
			inputSurface.getRect(r, outRecord.getWidth(), outRecord.getHeight());
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

	/* Request audio permission first when necessary! */
	private void startRecording(boolean recordAudio) {
		try {
			final Rect r = new Rect();
			inputSurface.getRect(r, vidWidth, vidHeight);
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

	public void setIMode(int value) {
		inputSurface.setIMode(value);
	}

	public void setSharpening(float value) {
		inputSurface.setSharpening(value);
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
			inputSurface.setMirror(value);
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
}
