/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device

 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package be.ntmn.XthermDemo;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.lang.Math.abs;
import static java.lang.Thread.sleep;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.res.ResourcesCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import be.ntmn.InfiCam;
import be.ntmn.MyApp;
import be.ntmn.USBMonitor;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public final class MainActivity extends BaseActivity {
    private static final boolean DEBUG = false;    // TODO set false on release
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_CHOOSE = 23;

    /**
     * set true if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     * by almost same way as USBCameratest2)
     * set false if you want to record movie using MediaVideoEncoder
     */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     */
    private static final int PREVIEW_WIDTH = 384;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     */
    private static final int PREVIEW_HEIGHT = 292;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 0;

    /**
     * for accessing USB
     */
    private USBMonitor mUSBMonitor;
    /**
     * Handler to execute camera related methods sequentially on private thread
     */
    private InfiCam infiCam;
    /**
     * for camera preview display
     */
    private SurfaceView surfaceView;

    /*
     * for open&start / stop&close camera preview
     */
    //private ToggleButton mCameraButton;
    /**
     * button for start/stop recording
     */
    private boolean settingsIsShow = false;
    private RadioGroup paletteRadioGroup, temperatureUnitsRadioGroup, languageRadioGroup;
    private TextView SN, PN, sotfVersion, productSoftVersion;
    private LinearLayout rightmenu;
    private ImageButton mCaptureButton, mPhotographButton, mSetButton;
    private ImageButton pointModeButton, lineModeButton, rectangleModeButton, MakeReportButton, ChangeRangeButton;
    private Switch mSysCameraSwitch, mWatermarkSwitch;
    private ImageView mImageView;
    private ImageButton mThumbnailButton;
    private SurfaceView mSfv, mRightSfv;
    private String brand, model, hardware;
    private SurfaceHolder mSfh, mRightSfh;
    private TextView emissivityText, correctionText, reflectionText, ambtempText, humidityText, distanceText;
    private float Fix = 0, Refltmp = 0, Airtmp = 0, humi = 0, emiss = 0;
    private short distance = 0;
    private String stFix, stRefltmp, stAirtmp, stHumi, stEmiss, stDistance, stProductSoftVersion;
    private Button saveButton;
    private LinearLayout mMenuRight;
    private ImageView mTempbutton, mZoomButton;
    private SeekBar emissivitySeekbar, correctionSeekbar, reflectionSeekbar, ambtempSeekbar, humiditySeekbar, distanceSeekbar;
    private int mLeft, mRight, mTop, mBottom;
    private int mRightSurfaceLeft, mRightSurfaceRight, mRightSurfaceTop, mRightSurfaceBottom;
    private int indexOfPoint = 0;
    private int temperatureAnalysisMode;
    private boolean isTemperaturing, isSettingBadPixel;
    private Bitmap mCursorBlue, mCursorRed, mCursorYellow, mCursorGreen, mWatermarkLogo;
    private Bitmap icon, iconPalette; //建立一个空的图画板
    private Canvas canvas;//初始化画布绘制的图像到icon上
    private Paint photoPaint; //建立画笔
    int posx, posy;
    private PopupWindow temperatureAnalysisWindow;
    volatile boolean isOnRecord;

    private Context context;
    //	private BitmapDrawable mCursor;l
    private SharedPreferences sharedPreferences;
    private int UnitTemperature = 0, palette;
    private int TemperatureRange = 120;
    private boolean IsAlreadyOnCreate = false;
    private AlertDialog ConnectOurDeviceAlert;
    private Timer timerEveryTime;
    private DisplayMetrics metrics;
    private Configuration configuration;
    private int language, isWatermark;
    private boolean XthermAlreadyConnected = false;
    private boolean isPreviewing = false;
    private SensorManager mSensorManager;
    private Sensor mSensorMagnetic, mAccelerometer;
    int oldRotation = 0;
    UsbDevice mUsbDevice;
    private boolean isFirstRun;
    LinearLayout rl_tip, rl_tip_kaka, rl_tip_setting, menu_palette_layout, rl_tip_setting1;
    RelativeLayout ll_tip_temp, ll_tip_temp1;
    String locale_language;
    private int isOpened = 0;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.e(TAG, "onCreating:");
//        Bugly.init(getApplicationContext(), "cd64123500", false);
        if (!IsAlreadyOnCreate) {
            sharedPreferences = getSharedPreferences("setting_share", 0);
            configuration = getResources().getConfiguration();
            metrics = getResources().getDisplayMetrics();

            locale_language = Locale.getDefault().getLanguage();
            language = sharedPreferences.getInt("Language", -1);
//            Log.e(TAG, "Language:" + language);
            switch (language) {
                case -1:
                    switch (locale_language) {
                    case "zh":
                            sharedPreferences.edit().putInt("Language", 0).apply();
                            break;
                        case "en":
                            sharedPreferences.edit().putInt("Language", 1).apply();
                            break;
                        case "ru":
                            sharedPreferences.edit().putInt("Language", 2).apply();
                            break;
                    }
                    break;
                case 0:
                    sharedPreferences.edit().putInt("Language", 0).apply();
                    configuration.locale = Locale.SIMPLIFIED_CHINESE;
                    configuration.setLayoutDirection(Locale.SIMPLIFIED_CHINESE);
                    getResources().updateConfiguration(configuration, metrics);
                    break;
                case 1:
                    sharedPreferences.edit().putInt("Language", 1).apply();
                    configuration.locale = Locale.ENGLISH;
                    configuration.setLayoutDirection(Locale.ENGLISH);
                    getResources().updateConfiguration(configuration, metrics);
                    break;
                case 2:
                    sharedPreferences.edit().putInt("Language", 2).apply();
                    configuration.locale = new Locale("ru", "RU");
                    getResources().updateConfiguration(configuration, metrics);
                    Log.e(TAG, "Language2:" + language);
                    break;
            }
            IsAlreadyOnCreate = true;
            super.onCreate(savedInstanceState);
            Log.e(TAG, "onCreate:");
            this.context = this;
            Log.e(TAG, "onCreate:" + this);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            if (DEBUG) Log.v(TAG, "onCreate:");
            setContentView(R.layout.activity_main);

            surfaceView = findViewById(R.id.infiCamView);
            rightmenu = findViewById(R.id.rightmenu_list);
            rightmenu.setVisibility(INVISIBLE);
            mCaptureButton = findViewById(R.id.button_video);
            mCaptureButton.setOnClickListener(mOnClickListener);
            mCaptureButton.setVisibility(VISIBLE);
            mSetButton = findViewById(R.id.button_set);
            mSetButton.setOnClickListener(mOnClickListener);
            mSetButton.setVisibility(VISIBLE);
            mThumbnailButton = findViewById(R.id.imageview_thumbnail);
            mThumbnailButton.setOnClickListener(mOnClickListener);
            mThumbnailButton.setVisibility(VISIBLE);
            saveButton = findViewById(R.id.save_button);
            saveButton.setOnClickListener(mOnClickListener);
            paletteRadioGroup = findViewById(R.id.palette_radio_group);
            paletteRadioGroup.setOnCheckedChangeListener(mOnCheckedChangeListener);
            languageRadioGroup = findViewById(R.id.language_radio_group);
            languageRadioGroup.setOnCheckedChangeListener(mOnCheckedChangeListener);
            temperatureUnitsRadioGroup = findViewById(R.id.temperature_units_radio_group);
            temperatureUnitsRadioGroup.setOnCheckedChangeListener(mOnCheckedChangeListener);
            emissivitySeekbar = findViewById(R.id.emissivity_seekbar);
            emissivitySeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            emissivityText = findViewById(R.id.emissivity_text);
            correctionSeekbar = findViewById(R.id.correction_seekbar);
            correctionSeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            correctionText = findViewById(R.id.correction_text);
            reflectionSeekbar = findViewById(R.id.reflection_seekbar);
            reflectionSeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            reflectionText = findViewById(R.id.reflection_text);
            ambtempSeekbar = findViewById(R.id.amb_temp_seekbar);
            ambtempSeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            ambtempText = findViewById(R.id.amb_temp_text);
            humiditySeekbar = findViewById(R.id.humidity_seekbar);
            humiditySeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            mSysCameraSwitch = findViewById(R.id.sys_camera_swtich);
            mSysCameraSwitch.setOnCheckedChangeListener(mSwitchListener);
            mWatermarkSwitch = findViewById(R.id.watermark_swtich);
            mWatermarkSwitch.setOnCheckedChangeListener(mSwitchListener);
            humidityText = findViewById(R.id.humidity_text);
            distanceSeekbar = findViewById(R.id.distance_seekbar);
            distanceSeekbar.setOnSeekBarChangeListener(mOnEmissivitySeekBarChangeListener);
            distanceText = findViewById(R.id.distance_text);

            SN = findViewById(R.id.product_SN);
            PN = findViewById(R.id.product_name);
            sotfVersion = findViewById(R.id.soft_version);
            productSoftVersion = findViewById(R.id.product_soft_version);

            rl_tip = findViewById(R.id.rl_tip);
            rl_tip_kaka = findViewById(R.id.rl_tip_kaka);
            rl_tip_setting = findViewById(R.id.rl_tip_setting);
            rl_tip_setting1 = findViewById(R.id.rl_tip_setting1);
            menu_palette_layout = findViewById(R.id.menu_palette_layout);
            ll_tip_temp = findViewById(R.id.ll_tip_temp);
            ll_tip_temp1 = findViewById(R.id.ll_tip_temp1);

            rl_tip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    rl_tip.setVisibility(View.GONE);
                    ll_tip_temp.setVisibility(View.VISIBLE);
                }
            });
            ll_tip_temp.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ll_tip_temp.setVisibility(View.GONE);
                    rl_tip_kaka.setVisibility(View.VISIBLE);
                }
            });
            rl_tip_kaka.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    rl_tip_kaka.setVisibility(View.GONE);
                    rl_tip_setting.setVisibility(View.VISIBLE);
                }
            });
            rl_tip_setting.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPreferences.edit().putBoolean("isFirstRun", false).apply();
                    rl_tip_setting.setVisibility(View.GONE);
                }
            });

            rl_tip_setting1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    rl_tip_setting1.setVisibility(View.GONE);
                    rl_tip_kaka.setVisibility(View.VISIBLE);
                }
            });

            ll_tip_temp1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPreferences.edit().putBoolean("isFirstRun", false).apply();
                    ll_tip_temp1.setVisibility(View.GONE);
                }
            });
            mPhotographButton = findViewById(R.id.button_camera);
            mPhotographButton.setOnTouchListener(mChangPicListener);
            mPhotographButton.setOnClickListener(mOnClickListener);
            mPhotographButton.setVisibility(VISIBLE);
            mImageView = findViewById(R.id.frame_image);
            mZoomButton = findViewById(R.id.button_shut);
            mZoomButton.setOnTouchListener(mChangPicListener);
            mZoomButton.setOnClickListener(mOnClickListener);
            mZoomButton.setVisibility(VISIBLE);
            mMenuRight = findViewById(R.id.menu_layout);
            mTempbutton = findViewById(R.id.button_temp);
            mTempbutton.setOnClickListener(mOnClickListener);
            mTempbutton.setVisibility(VISIBLE);
            mSfv = this.findViewById(R.id.surface_view);
            mSfh = mSfv.getHolder();
            mSfv.setZOrderOnTop(true);
            mSfh.setFormat(PixelFormat.TRANSLUCENT);
            mSfv.setOnTouchListener(listener);
            mRightSfv = this.findViewById(R.id.surfaceView_right);
            mRightSfh = mRightSfv.getHolder();
            mRightSfv.setZOrderOnTop(true);
            mRightSfh.setFormat(PixelFormat.TRANSLUCENT);
            isTemperaturing = false;
            isOnRecord = false;
            isSettingBadPixel = false;
            mCursorYellow = BitmapFactory.decodeResource(getResources(), R.mipmap.cursoryellow);
            mCursorRed = BitmapFactory.decodeResource(getResources(), R.mipmap.cursorred);
            mCursorBlue = BitmapFactory.decodeResource(getResources(), R.mipmap.cursorblue);
            mCursorGreen = BitmapFactory.decodeResource(getResources(), R.mipmap.cursorgreen);
            mWatermarkLogo = BitmapFactory.decodeResource(getResources(), R.mipmap.xtherm);
            XXPermissions.with(MainActivity.this)
                    .permission(Permission.RECORD_AUDIO)
//                    .permission(Permission.WRITE_EXTERNAL_STORAGE)
//                    .permission(Permission.CAMERA)
                    //.constantRequest() //可设置被拒绝后继续申请，直到用户授权或者永久拒绝
//                    .permission(Permission.SYSTEM_ALERT_WINDOW, Permission.REQUEST_INSTALL_PACKAGES) //支持请求6.0悬浮窗权限8.0请求安装权限
                    .permission(Permission.RECORD_AUDIO, Permission.WRITE_EXTERNAL_STORAGE, Permission.CAMERA) //不指定权限则自动获取清单中的危险权限
                    .request(new OnPermission() {

                        @Override
                        public void hasPermission(List<String> granted, boolean isAll) {
                            infiCam = new InfiCam();
                            mUSBMonitor = new USBMonitor(MainActivity.this, mOnDeviceConnectListener);
                            mUSBMonitor.addDeviceFilter(new USBMonitor.DeviceFilter() {
                                @Override
                                public boolean matches(final UsbDevice device) {
                                    String pn = device.getProductName();
                                    if (pn == null || device.getDeviceClass() != 239 || device.getDeviceSubclass() != 2)
                                        return false;
                                    // TODO (netman) We should figure out a better way to determine what devices are supported.
                                    boolean b = pn.contains("FX3") || pn.contains("Xtherm") || pn.contains("Xmodule") || pn.contains("S0") || pn.contains("T2") || pn.contains("DL") || pn.contains("T3") || pn.contains("DP");
                                    return b;
                                }
                            });
                        }

                        @Override
                        public void noPermission(List<String> denied, boolean quick) {

                        }
                    });
            //currentapiVersion=android.os.Build.VERSION.SDK_INT;

            //	mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
            //			0, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE,ahITemperatureCallback,currentapiVersion);
            mSensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            //获取Sensor
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public static boolean isZh(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        return language.endsWith("zh");
    }

    private final View.OnTouchListener mChangPicListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (v.getId()) {
                case R.id.button_camera:
                    if (event.getAction() == MotionEvent.ACTION_DOWN) //按下重新设置背景图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.camera2, null));
                    } else if (event.getAction() == MotionEvent.ACTION_UP) //松手恢复原来图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.camera1, null));
                    }
                    return false;
//				case R.id.button_video:
//					if (event.getAction() == MotionEvent.ACTION_DOWN) //按下重新设置背景图片
//					{
//						((ImageButton) v).setImageDrawable(getResources().getDrawable(R.mipmap.video2));
//					} else if (event.getAction() == MotionEvent.ACTION_UP) //松手恢复原来图片
//					{
//						((ImageButton) v).setImageDrawable(getResources().getDrawable(R.mipmap.video1));
//					}
//					return false;
                case R.id.button_temp:
                    if (event.getAction() == MotionEvent.ACTION_DOWN) //按下重新设置背景图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.temp2, null));
                    } else if (event.getAction() == MotionEvent.ACTION_UP) //松手恢复原来图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.temp1, null));
                    }
                    return false;
                case R.id.button_shut:
                    if (event.getAction() == MotionEvent.ACTION_DOWN) //按下重新设置背景图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.shut2, null));
                    } else if (event.getAction() == MotionEvent.ACTION_UP) //松手恢复原来图片
                    {
                        ((ImageButton) v).setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.mipmap.shut1, null));
                    }
                    return false;
            }
            return false;
        }
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            onStop();
            finish();
            //不执行父类点击事件
            return true;
        }
        //继续执行父类其他点击事件
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop:");
        /*if (mCamera2Helper != null) {
            if (mCamera2Helper.getState()) {
                mCameraHandler.closeSystemCamera();
//                mSysCameraSwitch.setChecked(false);
            }
        }*/
        if (mUSBMonitor != null) {
            if (mUSBMonitor.isRegistered()) {
                mUSBMonitor.unregister();
            }
        }
        if (ConnectOurDeviceAlert != null) {
            ConnectOurDeviceAlert.dismiss();
        }
        mSensorManager.unregisterListener(mSensorListener, mSensorMagnetic);
        mSensorManager.unregisterListener(mSensorListener, mAccelerometer);
        //System.exit(0);
        /*if (mUVCCameraView != null)
            mUVCCameraView.onPause();*/
        isTemperaturing = false;
        //whenCloseClearCanvas();
        if (isOnRecord) {
            isOnRecord = false;
            mCaptureButton.setImageDrawable(getResources().getDrawable(R.mipmap.video1));
            //mCameraHandler.stopRecording();
        }
        //setCameraButton(false);
        /*if (mCameraHandler != null) {
            mCameraHandler.close();
        }*/
        super.onStop();
    }

    @Override
    protected void onPause() {
        /*if (mCamera2Helper != null) {
            if (mCamera2Helper.getState()) {
                mCameraHandler.closeSystemCamera();
                mSysCameraSwitch.setChecked(false);
            }
        }*/
        if (isTemperaturing) {
            //mCameraHandler.stopTemperaturing();
            isTemperaturing = false;
            pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
            lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line));
            rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle));
        }
        Log.e(TAG, "onPause:");
        super.onPause();
    }

    //Activity从后台重新回到前台时被调用
    @Override
    protected void onRestart() {
        Log.e(TAG, "onRestart:");
        /*if (mUVCCameraView != null)
            mThumbnailButton.setVisibility(VISIBLE);*/
        currentSecond = 0;
        //mUVCCameraView.onResume();
        isOpened = 0;
        super.onRestart();
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume:");
        if (mUSBMonitor != null) {
            if (!mUSBMonitor.isRegistered()) {
                mUSBMonitor.register();
            }
//            showAgreeMent();

            //mTouchPoint.clear();//点测温清屏
            mSensorManager.registerListener(mSensorListener, mSensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(mSensorListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            List<UsbDevice> mUsbDeviceList = mUSBMonitor.getDeviceList();
            for (UsbDevice udv : mUsbDeviceList) {
//            System.out.println(udv.toString());
                XthermAlreadyConnected = true;
                String deviceName = udv.getProductName();
                MyApp.isT3 = deviceName.contains("DL") || deviceName.contains("DV") || deviceName.contains("DP");
                isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
                if (isFirstRun) {
                    if (isZh(this)) {
                        rl_tip.setVisibility(View.VISIBLE);
                    }
                } else {
                    rl_tip.setVisibility(View.GONE);
                }
                break; // TODO (netman) loop is silly, only one device at a time is supported
            }
            if (!XthermAlreadyConnected) {
                ConnectOurDeviceAlert = new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getResources().getString(R.string.Tip_to_connect))
                        .setPositiveButton(getResources().getString(R.string.Tip_to_connect_wait), null)
                        .setNegativeButton(getResources().getString(R.string.Tip_to_connect_cancel), null).create();
                ConnectOurDeviceAlert.setCancelable(false);   //设置点击空白区域不消失
                ConnectOurDeviceAlert.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        //确定按键
                        Button positiveButton = ConnectOurDeviceAlert.getButton(AlertDialog.BUTTON_POSITIVE);
                        positiveButton.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                List<UsbDevice> mUsbDeviceList2 = mUSBMonitor.getDeviceList();
                                for (UsbDevice udv : mUsbDeviceList2) {
                                    Log.e(TAG, "onClick AlertDialog.BUTTON_POSITIVE: " + udv.getProductName());
                                    if (mUSBMonitor.hasPermission(udv)) {
                                        XthermAlreadyConnected = true;
                                        Log.e(TAG, "onClick hasPermission ");
                                        ConnectOurDeviceAlert.dismiss();
                                    } else {
                                        mUSBMonitor.requestPermission(udv);
                                    }
                                    break; // TODO (netman) loop is silly, only one device at a time is supported
                                }
                            }
                        });

                        //取消按键
                        Button negativeButton = ConnectOurDeviceAlert.getButton(AlertDialog.BUTTON_NEGATIVE);
                        negativeButton.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                System.exit(0);
                            }
                        });
                    }
                });

                ConnectOurDeviceAlert.show();
            }
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
//        sharedPreferences.edit().putBoolean("cameraPreview", false).commit();
        Log.e(TAG, "onDestroy:");
        /*if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }*/
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        //mUVCCameraView = null;
        //mCameraButton = null;
        mCaptureButton = null;
        mSetButton = null;
        //textMax=null;
        //textMin=null;
        mPhotographButton = null;
//        mSysCameraSwitch.setChecked(false);
//        sharedPreferences.edit().putBoolean("cameraPreview", false).commit();
        super.onDestroy();
    }

    private void getTempPara() {
        InfiCam.FrameInfo tempPara;
        /*tempPara = mCameraHandler.getTemperaturePara(128);
        //Log.e(TAG, "getByteArrayTemperaturePara:" + tempPara[16] + "," + tempPara[17] + "," + tempPara[18] + "," + tempPara[19] + "," + tempPara[20] + "," + tempPara[21]);

        Fix = tempPara.correction;
        Refltmp = tempPara.temp_reflected;
        Airtmp = tempPara.temp_air;
        humi = tempPara.humidity;
        emiss = tempPara.emissivity;
        distance = (short) tempPara.distance; // TODO is not short anymore
        stFix = String.valueOf(Fix);
        stRefltmp = String.valueOf(Refltmp);
        stAirtmp = String.valueOf(Airtmp);
        stHumi = String.valueOf(humi);
        stEmiss = String.valueOf(emiss);
        stDistance = String.valueOf(distance);*/
        // TODO (netman) get version etc
        //stProductSoftVersion = new String(tempPara, 128 - 16, 16);
    }

    SeekBar.OnSeekBarChangeListener mOnEmissivitySeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int id = seekBar.getId();
            switch (id) {
                case R.id.emissivity_seekbar:
                    float emiss = progress / 100.0f;
                    emissivityText.setText(String.valueOf(emiss));
                    break;
                case R.id.correction_seekbar:
                    float correction = (progress - 30) / 10.0f;
                        correctionText.setText(correction + "°C");
                    break;
                case R.id.reflection_seekbar:
                    int reflection = (progress - 10);
                    reflectionText.setText(reflection + "°C");
                    break;
                case R.id.amb_temp_seekbar:
                    int ambtemp = (progress - 10);
                    ambtempText.setText(ambtemp + "°C");
                    break;
                case R.id.humidity_seekbar:
                    humidityText.setText(progress + "%");
                    break;
                case R.id.distance_seekbar:
                    distanceText.setText(progress + "m");
                    break;
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Intentionally empty.
        }

        //private TextView emissivityText,correctionText,reflectionText,ambtempText,humidityText,distanceText,textMax, textMin;
        //private SeekBar mSettingSeekbar,emissivitySeekbar,correctionSeekbar,reflectionSeekbar,ambtempSeekbar,humiditySeekbar,distanceSeekbar;
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int id = seekBar.getId();
            switch (id) {
                case R.id.emissivity_seekbar:
                    int currentProgressEm = seekBar.getProgress();
                    float fiputEm = currentProgressEm / 100.0f;
                    infiCam.setEmissivity(fiputEm);
                    break;
                case R.id.correction_seekbar:
                    int currentProgressCo = seekBar.getProgress();
                    float fiputCo = (currentProgressCo - 30) / 10.0f;
                    infiCam.setCorrection(fiputCo);
                    break;
                case R.id.reflection_seekbar:
                    int currentProgressRe = seekBar.getProgress();
                    float fiputRe = currentProgressRe - 10.0f;
                    infiCam.setTempReflected(fiputRe);
                    break;
                case R.id.amb_temp_seekbar:
                    int currentProgressAm = seekBar.getProgress();
                    float fiputAm = currentProgressAm - 10.0f;
                    infiCam.setTempAir(fiputAm);
                    break;
                case R.id.humidity_seekbar:
                    int currentProgressHu = seekBar.getProgress();
                    float fiputHu = currentProgressHu / 100.0f;
                    infiCam.setHumidity(fiputHu);
                    break;
                case R.id.distance_seekbar:
                    int currentProgressDi = seekBar.getProgress();
                    float fltDi = currentProgressDi;
                    infiCam.setDistance(fltDi);
                    break;

            }
        }
    };


    public static class Check {
        // 两次点击按钮之间的点击间隔不能少于1000毫秒
        private static final int MIN_CLICK_DELAY_TIME = 1000;
        private static long lastClickTime;

        public static boolean isFastClick() {
            boolean flag = false;
            long curClickTime = System.currentTimeMillis();
            if ((curClickTime - lastClickTime) >= MIN_CLICK_DELAY_TIME) {
                flag = true;
            }
            lastClickTime = curClickTime;
            return flag;
        }
    }

    private final CompoundButton.OnCheckedChangeListener mSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            /*switch (compoundButton.getId()) {
                case R.id.sys_camera_swtich:
                    if (!Check.isFastClick()) {
                        return;
                    }
                    if (b) {
                        if (mCameraHandler.isOpened()) {
                            if (PermissionCheck.hasCamera(context)) {
                                //旋转mTextureView
                                mTextureView.setPivotX(0);
                                mTextureView.setPivotY(0);
                                mTextureView.setRotation(-90);
//                                mTextureView.setTranslationY(480);

                                mCamera2Helper = Camera2Helper.getInstance();
                                mCamera2Helper.setContext(context);
                                mCamera2Helper.setTexture(mTextureView);
                                mCameraHandler.openSystemCamera();
                                mTextureView.setVisibility(View.VISIBLE);
//                                sharedPreferences.edit().putBoolean("cameraPreview", true).commit();
                                compoundButton.setChecked(true);
                            } else {
                                checkPermissionCamera();
                                compoundButton.setChecked(false);
                                mTextureView.setVisibility(View.INVISIBLE);
//                                sharedPreferences.edit().putBoolean("cameraPreview", false).commit();
                                return;
                            }
                        }

                    } else {
                        if (mCameraHandler.isOpened() && (mCamera2Helper != null)) {
                            mCameraHandler.closeSystemCamera();
                            compoundButton.setChecked(false);
                            mTextureView.setVisibility(View.INVISIBLE);
//                            sharedPreferences.edit().putBoolean("cameraPreview", false).commit();
                        }
                    }
                    break;
                case R.id.watermark_swtich:
                    if (b) {
                        if (mCameraHandler.isOpened()) {
                            isWatermark = 1;
                        }
                    } else {
                        isWatermark = 0;
                    }
                    mCameraHandler.watermarkOnOff(isWatermark);
                    sharedPreferences.edit().putInt("Watermark", isWatermark).apply();
                    break;
            }*/
        }
    };

    float[] geomagnetic = new float[3];//用来保存地磁传感器的值
    private final SensorEventListener mSensorListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = event.values;
            }
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float x = event.values[SensorManager.DATA_X];
                float y = event.values[SensorManager.DATA_Y];
                relayout(x, y);
            }
        }
    };

    protected void relayout(float x, float y) {
        Drawable drawable;
        if (x > -2.5 && x <= 2.5 && y > 7.5 && y <= 10 && oldRotation != 270) {
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.camera1left, null);
            mPhotographButton.setImageDrawable(drawable);
            /*if (mCameraHandler.isRecording()) {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video2up, null);
            } else {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video1left, null);
            }*/
            mCaptureButton.setImageDrawable(drawable);
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.fileleft, null);
            mThumbnailButton.setImageDrawable(drawable);
            oldRotation = 270;
            rightmenu.setRotation(0);
            menu_palette_layout.setRotation(0);
            if (temperatureAnalysisWindow != null && temperatureAnalysisWindow.isShowing()) {
                temperatureAnalysisWindow.getContentView().setRotation(0);
            }
        } else if (x > 7.5 && x <= 10 && y > -2.5 && y <= 2.5 && oldRotation != 0) {
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.camera1, null);
            mPhotographButton.setImageDrawable(drawable);
            /*if (mCameraHandler.isRecording()) {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video2, null);
            } else {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video1, null);
            }*/
            mCaptureButton.setImageDrawable(drawable);
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.file, null);
            mThumbnailButton.setImageDrawable(drawable);
            oldRotation = 0;
            rightmenu.setRotation(oldRotation);
            menu_palette_layout.setRotation(oldRotation);
            if (temperatureAnalysisWindow != null && temperatureAnalysisWindow.isShowing()) {
                temperatureAnalysisWindow.getContentView().setRotation(oldRotation);
            }
        } else if (x > -2.5 && x <= 2.5 && y > -10 && y <= -7.5 && oldRotation != 90) {
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.camera1right, null);
            mPhotographButton.setImageDrawable(drawable);
            /*if (mCameraHandler.isRecording()) {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video2down, null);
            } else {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video1right, null);
            }*/
            mCaptureButton.setImageDrawable(drawable);
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.fileright, null);
            mThumbnailButton.setImageDrawable(drawable);
            oldRotation = 90;
            rightmenu.setRotation(180);
            menu_palette_layout.setRotation(180);
            if (temperatureAnalysisWindow != null && temperatureAnalysisWindow.isShowing()) {
                temperatureAnalysisWindow.getContentView().setRotation(180);
            }
        } else if (x > -10 && x <= -7.5 && y > -2.5 && y < 2.5 && oldRotation != 180) {
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.camera1down, null);
            mPhotographButton.setImageDrawable(drawable);
            /*if (mCameraHandler.isRecording()) {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video2left, null);
            } else {
                drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.video1down, null);
            }*/
            mCaptureButton.setImageDrawable(drawable);
            drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.filedown, null);
            mThumbnailButton.setImageDrawable(drawable);
            oldRotation = 180;
            rightmenu.setRotation(oldRotation);
            menu_palette_layout.setRotation(oldRotation);
            if (temperatureAnalysisWindow != null && temperatureAnalysisWindow.isShowing()) {
                temperatureAnalysisWindow.getContentView().setRotation(oldRotation);
            }
        } else {
            return;
        }
        Log.e(TAG, "oldRotation:" + oldRotation);
        //mCameraHandler.relayout(oldRotation);
    }

    private final RadioGroup.OnCheckedChangeListener mOnCheckedChangeListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            switch (checkedId) {
                case R.id.whitehot_radio_button:
                    //mCameraHandler.changePalette(0);
                    sharedPreferences.edit().putInt("palette", 0).apply();
                    break;
                case R.id.blackhot_radio_button:
                    //mCameraHandler.changePalette(1);
                    sharedPreferences.edit().putInt("palette", 1).apply();
                    break;
                case R.id.iron_rainbow_radio_button:
                    //mCameraHandler.changePalette(2);
                    sharedPreferences.edit().putInt("palette", 2).apply();
                    break;
                case R.id.rainbow_radio_button:
                    //mCameraHandler.changePalette(3);
                    sharedPreferences.edit().putInt("palette", 3).apply();
                    break;
                case R.id.three_primary_radio_button:
                    //mCameraHandler.changePalette(4);
                    sharedPreferences.edit().putInt("palette", 4).apply();
                    break;
                case R.id.iron_gray_radio_button:
                    //mCameraHandler.changePalette(5);
                    sharedPreferences.edit().putInt("palette", 5).apply();
                    break;
                case R.id.temperature_units_c_radio_button:
                    /*if (mUVCCameraView != null) {
                        mUVCCameraView.setUnitTemperature(0);
                        sharedPreferences.edit().putInt("UnitTemperature", 0).apply();
                    }*/
                    break;
                case R.id.temperature_units_f_radio_button:
                    /*if (mUVCCameraView != null) {
                        mUVCCameraView.setUnitTemperature(1);
                        sharedPreferences.edit().putInt("UnitTemperature", 1).apply();
                    }*/
                    break;
                case R.id.chinese_radio_button:
                    language = sharedPreferences.getInt("Language", -1);
                    if (language != 0) {
                        sharedPreferences.edit().putInt("Language", 0).apply();
                        changeAppLanguage(Locale.SIMPLIFIED_CHINESE);
                    }
                    break;
                case R.id.english_radio_button:
                    language = sharedPreferences.getInt("Language", -1);
                    if (language != 1) {
                        sharedPreferences.edit().putInt("Language", 1).apply();
                        changeAppLanguage(Locale.ENGLISH);
                    }
                    break;
            }
        }
    };

    /**
     * event handler when click camera / capture button
     */
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.button_video:
                    if (!Check.isFastClick()) {
                        return;
                    }
                    /*if (mCameraHandler.isOpened()) {
                        if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                            Thread thread = new Thread(timeRunable);
                            if (!mCameraHandler.isRecording()) {
                                isOnRecord = true;
                                ((ImageButton) view).setImageDrawable(getResources().getDrawable(R.mipmap.video2));    // turn red
                                mThumbnailButton.setVisibility(INVISIBLE);
                                mCameraHandler.startRecording();
                                thread.start();
                            } else {
                                ((ImageButton) view).setImageDrawable(getResources().getDrawable(R.mipmap.video1));    // return to default color
                                isOnRecord = false;
                                thread.interrupt();
                                mThumbnailButton.setVisibility(VISIBLE);
                                mCameraHandler.stopRecording();
                            }
                        }
                    }*/
                    break;
                case R.id.button_temp:
                    createTemperaturePopupWindow();
                    if (!temperatureAnalysisWindow.isShowing()) {
                        if (oldRotation == 90 || oldRotation == 180) {
                            int offsetX = -(menu_palette_layout.getWidth() + temperatureAnalysisWindow.getWidth());
                            temperatureAnalysisWindow.showAsDropDown(menu_palette_layout, offsetX, 0, Gravity.START);
                            temperatureAnalysisWindow.getContentView().setRotation(180);
                        }
                        if (oldRotation == 0 || oldRotation == 270) {
                            int offsetX = -temperatureAnalysisWindow.getWidth();
                            temperatureAnalysisWindow.showAsDropDown(menu_palette_layout, offsetX, 0, Gravity.START);
                            temperatureAnalysisWindow.getContentView().setRotation(0);
                        }
                        WindowManager.LayoutParams wlp = getWindow().getAttributes();
                        wlp.alpha = 0.7f;
                        getWindow().setAttributes(wlp);
                    } else {
                        temperatureAnalysisWindow.dismiss();
                    }
                    break;
                case R.id.button_shut:
                    //if (isTemperaturing) {
                        calibrate();
                    //}
                    //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8000);
                    break;
                case R.id.button_set:
                    if (settingsIsShow == false) {
                        if (mUsbDevice != null) {
                            settingsIsShow = true;
                            palette = sharedPreferences.getInt("palette", 0);
                            UnitTemperature = sharedPreferences.getInt("UnitTemperature", 0);
                            switch (palette) {
                                case 0:
                                    paletteRadioGroup.check(R.id.whitehot_radio_button);
                                    break;
                                case 1:
                                    paletteRadioGroup.check(R.id.blackhot_radio_button);
                                    break;
                                case 2:
                                    paletteRadioGroup.check(R.id.iron_rainbow_radio_button);
                                    break;
                                case 3:
                                    paletteRadioGroup.check(R.id.rainbow_radio_button);
                                    break;
                                case 4:
                                    paletteRadioGroup.check(R.id.three_primary_radio_button);
                                    break;
                                case 5:
                                    paletteRadioGroup.check(R.id.iron_gray_radio_button);
                                    break;
                            }
                            switch (UnitTemperature) {
                                case 0:
                                    temperatureUnitsRadioGroup.check(R.id.temperature_units_c_radio_button);
                                    break;
                                case 1:
                                    temperatureUnitsRadioGroup.check(R.id.temperature_units_f_radio_button);
                                    break;
                            }
                            switch (language) {
                                case -1:
                                    if (locale_language.equals("zh")) {
                                        languageRadioGroup.check(R.id.chinese_radio_button);
                                    } else if (locale_language.equals("en")) {
                                        languageRadioGroup.check(R.id.english_radio_button);
                                    }
                                    break;
                                case 0:
                                    languageRadioGroup.check(R.id.chinese_radio_button);
                                    break;
                                case 1:
                                    languageRadioGroup.check(R.id.english_radio_button);
                                    break;
                            }
                            mWatermarkSwitch.setChecked(isWatermark == 1);

                            getTempPara();
                            emissivityText.setText(stEmiss);
                            emissivitySeekbar.setProgress((int) (emiss * 100.0f));
                            correctionText.setText(stFix + "°C");
                            correctionSeekbar.setProgress((int) (Fix * 10.0f + 30));
                            reflectionText.setText(stRefltmp + "°C");
                            reflectionSeekbar.setProgress((int) (Refltmp + 10.0f));
                            ambtempText.setText(stAirtmp + "°C");
                            ambtempSeekbar.setProgress((int) (Airtmp + 10.0f));
                            humidityText.setText(stHumi);
                            humiditySeekbar.setProgress((int) (humi * 100.0f));
                            distanceText.setText(stDistance);
                            distanceSeekbar.setProgress((int) distance);
                            PN.setText(mUsbDevice.getProductName());
                            // PID.setText(mUsbDevice.getProductId());
                            SN.setText(mUsbDevice.getSerialNumber());
                            sotfVersion.setText(getVersionName(context));
                            productSoftVersion.setText(stProductSoftVersion);

                            rightmenu.setVisibility(VISIBLE);
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.waittoclick, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        settingsIsShow = false;
                        rightmenu.setVisibility(INVISIBLE);
                        //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x80ff);
                    }

                    break;
                case R.id.save_button:
                    infiCam.storeParams();
                    Toast.makeText(getApplicationContext(), "保存成功", Toast.LENGTH_SHORT).show();
                    break;
                case R.id.button_camera:
                    if (!Check.isFastClick()) {
                        return;
                    }
                    /*if (mCameraHandler != null) {
                        if (mCameraHandler.isOpened()) {
                            if (checkPermissionWriteExternalStorage()) {
                                mCameraHandler.captureStill(MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png").toString());
                            }
                        }
                    }*/
                    break;
                case R.id.make_report_button:
                    /*if (mCameraHandler.isOpened() && isTemperaturing) {
                        if (checkPermissionWriteExternalStorage()) {
                            //String path=
                            temperatureAnalysisWindow.dismiss();
                            Toast.makeText(getApplication(), "报告生成成功，请去相册目录查看", Toast.LENGTH_SHORT).show();
                        }
                    }*/
                    break;

                case R.id.point_mode_button:
                    if (!isTemperaturing) {
                        //mUVCCameraView.setVertices(2);
                        //mUVCCameraView.setBitmap(mCursorRed,mCursorGreen,mCursorBlue,mCursorYellow);
                        /*if (mCameraHandler.isOpened()) {
                            if (!mCameraHandler.isTemperaturing()) {
                                temperatureAnalysisMode = 0;
                                mUVCCameraView.setTemperatureAnalysisMode(0);
                                mCameraHandler.startTemperaturing();
                                isTemperaturing = true;
                                Handler handler0 = new Handler();
                                handler0.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        calibrate();
                                    }
                                }, 300);

                                pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point1));
                                temperatureAnalysisWindow.dismiss();
                            }
                        }*/
                    } else if (temperatureAnalysisMode != 0) {
                        /*mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);
                        temperatureAnalysisMode = 0;
                        isTemperaturing = true;
                        mUVCCameraView.setTemperatureAnalysisMode(0);
                        pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point1));
                        lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line));
                        rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle));
                        temperatureAnalysisWindow.dismiss();*/
                    } else {
                        isTemperaturing = false;
                        /*mCameraHandler.stopTemperaturing();
                        mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);*/
                        pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
                        temperatureAnalysisWindow.dismiss();
                    }
                    break;
                case R.id.line_mode_button:
                    if (!isTemperaturing) {
                        //mUVCCameraView.setVertices(2);
                        //mUVCCameraView.setBitmap(mCursorRed,mCursorGreen,mCursorBlue,mCursorYellow);
                        /*if (mCameraHandler.isOpened()) {
                            if (!mCameraHandler.isTemperaturing()) {
                                temperatureAnalysisMode = 1;
                                mUVCCameraView.setTemperatureAnalysisMode(1);
                                mCameraHandler.startTemperaturing();
                                isTemperaturing = true;
                                Handler handler0 = new Handler();
                                handler0.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        calibrate();
                                    }
                                }, 300);

                                pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
                                lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line1));
                                rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle));
                                temperatureAnalysisWindow.dismiss();
                            }
                        }*/
                    } else if (temperatureAnalysisMode != 1) {
                        /*mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);
                        temperatureAnalysisMode = 1;
                        mUVCCameraView.setTemperatureAnalysisMode(1);*/
                        pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
                        lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line1));
                        rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle));
                        temperatureAnalysisWindow.dismiss();
                    } else {
                        isTemperaturing = false;
                        /*mCameraHandler.stopTemperaturing();
                        mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);*/
                        lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line));
                        temperatureAnalysisWindow.dismiss();
                    }


                    break;
                case R.id.rectangle_mode_button:
                    if (!isTemperaturing) {
                        //mUVCCameraView.setVertices(2);
                        //mUVCCameraView.setBitmap(mCursorRed,mCursorGreen,mCursorBlue,mCursorYellow);
                        /*if (mCameraHandler.isOpened()) {
                            if (!mCameraHandler.isTemperaturing()) {
                                temperatureAnalysisMode = 2;
                                mUVCCameraView.setTemperatureAnalysisMode(2);
                                mCameraHandler.startTemperaturing();
                                isTemperaturing = true;
                                Handler handler0 = new Handler();
                                handler0.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        calibrate();
                                    }
                                }, 300);

                                pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
                                lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line));
                                rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle1));
                                temperatureAnalysisWindow.dismiss();
                            }
                        }*/
                    } else if (temperatureAnalysisMode != 2) {
                        /*mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);
                        temperatureAnalysisMode = 2;
                        mUVCCameraView.setTemperatureAnalysisMode(2);
                        pointModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.point));
                        lineModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.line));
                        rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle1));
                        temperatureAnalysisWindow.dismiss();*/
                    } else {
                        isTemperaturing = false;
                        /*mCameraHandler.stopTemperaturing();
                        mTouchPoint.clear();
                        mUVCCameraView.setTouchPoint(mTouchPoint);*/
                        rectangleModeButton.setImageDrawable(getResources().getDrawable(R.mipmap.rectangle));
                        temperatureAnalysisWindow.dismiss();
                    }

                    break;
                case R.id.change_range_button:

                    //if (mCameraHandler.isOpened()) {
                        if (TemperatureRange != 400) {
                            TemperatureRange = 400;
                            isTemperaturing = true;
                            Handler handler1 = new Handler();
                            handler1.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //whenShutRefresh();
                                    //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8021);//400。C
                                    infiCam.setRange(400);
                                }
                            }, 100);
                            Handler handler4 = new Handler();
                            handler4.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    calibrate();
                                    //    mCameraHandler.whenChangeTempPara();
                                }
                            }, 1500);
                            ChangeRangeButton.setImageDrawable(getResources().getDrawable(R.mipmap.range_120));
                        } else {
                            TemperatureRange = 120;
                            isTemperaturing = true;
                            Handler handler1 = new Handler();
                            handler1.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //whenShutRefresh();
                                    //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8020);//120。C
                                    infiCam.setRange(120);
                                }
                            }, 100);
                            Handler handler4 = new Handler();
                            handler4.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    calibrate();
                                    //    mCameraHandler.whenChangeTempPara();
                                }
                            }, 1500);
                            ChangeRangeButton.setImageDrawable(getResources().getDrawable(R.mipmap.range_400));
                        }
                        temperatureAnalysisWindow.dismiss();
                    //}
                    break;
                case R.id.imageview_thumbnail:
                    /*if (PermissionCheck.hasReadExternalStorage(context) && PermissionCheck.hasWriteExternalStorage(context)) {
                        Matisse.from(MainActivity.this)
                                .choose(MimeType.ofAll(), false)
                                .theme(R.style.Matisse_Dracula)
                                .countable(false)
                                .addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                                .maxSelectable(9)
                                .originalEnable(true)
                                .maxOriginalSize(10)
                                //.imageEngine(new GlideEngine())
                                .imageEngine(new PicassoEngine())
                                .forResult(REQUEST_CODE_CHOOSE);
                    } else {
                        checkPermissionWriteExternalStorage();
                    }*/
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setType("image/*");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    break;
            }

        }

    };

    /**
     * 更改应用语言
     *
     * @param locale
     */
    public void changeAppLanguage(Locale locale) {
//        metrics = getResources().getDisplayMetrics();
//        configuration = getResources().getConfiguration();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//            configuration.setLocale(locale);
//        } else {
//            configuration.locale = locale;
//        }
//        getResources().updateConfiguration(configuration, metrics);
        //重新启动Activity
//        Log.e(TAG, "changeAppLanguage: ");
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        this.finish();
        startActivity(intent);
    }

    private void createTemperaturePopupWindow() {
        if (temperatureAnalysisWindow == null) {
            View contentView = LayoutInflater.from(MainActivity.this).inflate(R.layout.temperature_analysis_layout, null);
            temperatureAnalysisWindow = new PopupWindow(contentView, (int) (mMenuRight.getHeight() / 5.806f),
                    mMenuRight.getHeight());

//            if (MyApp.isT3) {
//                contentView.setRotation(oldRotation);
//                Log.e(TAG,"oldRotation1"+oldRotation);
//            }
            //temperatureAnalysisWindow = new PopupWindow(contentView);
            //temperatureAnalysisWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
            // temperatureAnalysisWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
            //temperatureAnalysisWindow.setContentView(contentView);
            //设置各个控件的点击响应
            pointModeButton = contentView.findViewById(R.id.point_mode_button);
            lineModeButton = contentView.findViewById(R.id.line_mode_button);
            rectangleModeButton = contentView.findViewById(R.id.rectangle_mode_button);
            ChangeRangeButton = contentView.findViewById(R.id.change_range_button);

            MakeReportButton = contentView.findViewById(R.id.make_report_button);
            pointModeButton.setOnClickListener(mOnClickListener);
            lineModeButton.setOnClickListener(mOnClickListener);
            rectangleModeButton.setOnClickListener(mOnClickListener);
            ChangeRangeButton.setOnClickListener(mOnClickListener);
            MakeReportButton.setOnClickListener(mOnClickListener);
            //显示PopupWindow
            temperatureAnalysisWindow.setFocusable(true);
            // temperatureAnalysisWindow.setAnimationStyle(R.style.DialogAnimation);
            temperatureAnalysisWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            temperatureAnalysisWindow.setOutsideTouchable(true);
            temperatureAnalysisWindow.setTouchable(true);
            temperatureAnalysisWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    WindowManager.LayoutParams wlp = getWindow().getAttributes();
                    wlp.alpha = 1.0f;
                    getWindow().setAttributes(wlp);
                }
            });
        }
    }

    View.OnTouchListener listener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (settingsIsShow) {
                settingsIsShow = false;
                rightmenu.setVisibility(INVISIBLE);
            } else if (isTemperaturing) {
                //mScaleGestureDetector.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        /*TouchPoint currentPoint = new TouchPoint();
                        currentPoint.x = event.getX();
                        currentPoint.y = event.getY();
//                        Log.e(TAG, "OnTouchListener" + currentPoint.x + ",," + currentPoint.y);
                        float fviewX = currentPoint.x / mSfv.getWidth();
                        float fviewY = currentPoint.y / mSfv.getHeight();
                        currentPoint.x = fviewX;
                        currentPoint.y = fviewY;
                        if (temperatureAnalysisMode == 0) {
                            if (indexOfPoint >= 5) {
                                indexOfPoint = 0;
                            }
                            currentPoint.numOfPoint = indexOfPoint;
                            if (mTouchPoint.size() <= 5) {
                                mTouchPoint.add(currentPoint);
                            } else {
                                mTouchPoint.set(indexOfPoint, currentPoint);
                            }
                            mUVCCameraView.setTouchPoint(mTouchPoint);
                            indexOfPoint++;
                        }
                        if (temperatureAnalysisMode == 1) {
                            mTouchPoint.clear();
                            indexOfPoint = 0;
                            currentPoint.numOfPoint = indexOfPoint;
                            mTouchPoint.add(currentPoint);

                        }

                        if (temperatureAnalysisMode == 2) {
                            mTouchPoint.clear();
                            indexOfPoint = 0;
                            currentPoint.numOfPoint = indexOfPoint;
                            mTouchPoint.add(currentPoint);

                        }
                        if (isSettingBadPixel) {//用户盲元表
                            int viewX1 = (int) (fviewX * mCameraHandler.getWidth());
                            int viewY1 = (int) (fviewY * (mCameraHandler.getHeight() - 4));
                            posx = 0xec00 | (0xffff & viewX1);
                            posy = 0xee00 | (0xffff & viewY1);
                            Handler handler1 = new Handler();
                            handler1.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //setValue(UVCCamera.CTRL_ZOOM_ABS, posx);
                                }
                            }, 10);
                            Handler handler2 = new Handler();
                            handler2.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    //setValue(UVCCamera.CTRL_ZOOM_ABS, posy);
                                }
                            }, 40);
                        }*/
                        break;
                    case MotionEvent.ACTION_MOVE:
                        /*TouchPoint currentPoint2 = new TouchPoint();
                        currentPoint2.x = event.getX();
                        currentPoint2.y = event.getY();
//                        Log.e(TAG, "OnTouchListener" + currentPoint2.x + ",," + currentPoint2.y);
                        float fviewX2 = currentPoint2.x / mSfv.getWidth();
                        float fviewY2 = currentPoint2.y / mSfv.getHeight();
                        currentPoint2.x = fviewX2;
                        currentPoint2.y = fviewY2;
                        if (temperatureAnalysisMode == 1) {
                            if (indexOfPoint >= 2) {
                                TouchPoint LastTouch = mTouchPoint.get(mTouchPoint.size() - 1);
                                LastTouch.x = currentPoint2.x;
                                LastTouch.y = currentPoint2.y;
                            } else {
                                currentPoint2.numOfPoint = 1;
                                mTouchPoint.add(currentPoint2);
                            }
                            mUVCCameraView.setTouchPoint(mTouchPoint);

                        }

                        if (temperatureAnalysisMode == 2) {
                            if (indexOfPoint >= 2) {
                                TouchPoint LastTouch = mTouchPoint.get(mTouchPoint.size() - 1);
                                LastTouch.x = currentPoint2.x;
                                LastTouch.y = currentPoint2.y;
                            } else {
                                currentPoint2.numOfPoint = 1;
                                mTouchPoint.add(currentPoint2);
                            }
                            mUVCCameraView.setTouchPoint(mTouchPoint);
                        }*/
                        break;
                    case MotionEvent.ACTION_UP:
                        break;

                    default:

                }

            }

            return true;
        }

    };

    SurfaceMuxer et2;

    private void startPreview() {
        mLeft = mImageView.getLeft();
        mTop = mImageView.getTop();
        mBottom = mImageView.getBottom();
        mRight = mImageView.getRight();

        mRightSurfaceLeft = mRightSfv.getLeft();
        mRightSurfaceRight = mRightSfv.getRight();
        mRightSurfaceTop = mRightSfv.getTop();
        mRightSurfaceBottom = mRightSfv.getBottom();
        //mUVCCameraView.iniTempBitmap(mRight - mLeft, mBottom - mTop);
        icon = Bitmap.createBitmap(mRight - mLeft, mBottom - mTop, Bitmap.Config.ARGB_8888); //建立一个空的图画板
        int iconPaletteWidth = abs(mRightSurfaceRight - mRightSurfaceLeft);
        int iconPaletteHeight = abs(mRightSurfaceBottom - mRightSurfaceTop);
        iconPalette = Bitmap.createBitmap(iconPaletteWidth > 0 ? iconPaletteWidth : 10, iconPaletteHeight > 0 ? iconPaletteHeight : 10, Bitmap.Config.ARGB_8888);
        //sfh.lockCanvas()
        photoPaint = new Paint(); //建立画笔
        //photoPaint.setStyle(Paint.Style.FILL);
        //dstHighTemp=new Rect(0,0,60,60);
        //dstLowTemp=new Rect(0,0,60,60);
        //dstHighTemp.set(20,50,20+mCursor.getWidth(),50+mCursor.getHeight());//int left, int top, int right, int bottom
        //dstLowTemp.set(40,100,40+mCursor.getWidth(),100+mCursor.getHeight());//int left, int top, int right, int bottom

//        Log.e(TAG, "startPreview: getSurfaceTexture");
        //final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        // TODO (netman) following line is original, next is how i patched in other Surface
        //mCameraHandler.startPreview(new Surface(st));
        final SurfaceView sv = findViewById(R.id.infiCamView);
        //Log.e("TEXTOOR", "this: " + sv.getSurf());

        //SurfaceTexture surf = sv.getSurf();
        //mCameraHandler.startPreview(new Surface(surf));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    et2 = new SurfaceMuxer();
                    //et2.testEncodeVideoToMp4(sv.getHolder().getSurface(), sv);
                    et2.addOutputSurface(sv.getHolder().getSurface());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                SurfaceTexture ist = et2.createInputSurfaceTexture();
                //mCameraHandler.startPreview(new Surface(ist));
                ist.setOnFrameAvailableListener(et2);

                Bitmap bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Paint p = new Paint();
                p.setColor(Color.TRANSPARENT);
                c.drawRect(new Rect(0, 0, 640, 480), p);
                Paint p2 = new Paint();
                p2.setColor(Color.RED);
                c.drawLine(0, 0, 640, 480, p2);

                SurfaceTexture st = et2.createInputSurfaceTexture();
                st.setDefaultBufferSize(640, 480);
                //st.setOnFrameAvailableListener(et2);
                Surface s = new Surface(st);
                Canvas cvs = s.lockCanvas(null);
                //cvs.drawBitmap(bmp, 0, 0, null);
                cvs.drawLine(0, 0, 640, 480, p2);
                s.unlockCanvasAndPost(cvs);

                try {
                    final SurfaceRecorder sr = new SurfaceRecorder(640, 480);
                    Surface rec = sr.getInputSurface();
                    /*et2.addOutputSurface(rec);
                    sr.startRecording();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sr.stopRecording();
                        }
                    }, 10000);*/
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    infiCam.startStream(new Surface(ist));
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        /*final CameraRenderer cr = new CameraRenderer(this, surf, 640, 480);
        //cr.initGL();
        cr.setOnRendererReadyListener(new CameraRenderer.OnRendererReadyListener() {
            @Override
            public void onRendererReady() {
                cr.startRecording("/sdcard/videocapture_example/test.mp4");
            }

            @Override
            public void onRendererFinished() {

            }
        });
        cr.start();*/

//        Log.e(TAG, "startPreview: getSurfaceTexture2");
        //mCameraHandler.startPreview(null);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPhotographButton.setVisibility(VISIBLE);
                mSetButton.setVisibility(VISIBLE);
                mCaptureButton.setVisibility(VISIBLE);
                mZoomButton.setVisibility(VISIBLE);
                mTempbutton.setVisibility(VISIBLE);
            }
        });

    }


    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (device.getDeviceClass() == 239 && device.getDeviceSubclass() == 2) { // TODO (netman) so this is the device class we look for
                //  Toast.makeText(MainActivity.this,device.getProductName(), Toast.LENGTH_SHORT).show();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mUSBMonitor.requestPermission(device);
                    }
                }, 100);
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            //  Toast.makeText(MainActivity.this,"onConnect", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onConnect:");
            if (isOpened == 0) {
                // Toast.makeText(MainActivity.this, "XthermDemo onConnect", Toast.LENGTH_SHORT).show();
                try {
                    infiCam.connect(ctrlBlock.getFileDescriptor());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!XthermAlreadyConnected) {
                    ConnectOurDeviceAlert.dismiss();
                }
                //	(mOnOff).setImageDrawable(getResources().getDrawable(R.mipmap.open2));
                startPreview();
                isPreviewing = true;
                palette = sharedPreferences.getInt("palette", 0);
                UnitTemperature = sharedPreferences.getInt("UnitTemperature", 0);
                //mUVCCameraView.setUnitTemperature(UnitTemperature);
                Handler handler = new Handler();
                /*handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8004);//切换数据输出8004原始8005yuv,80ff保存
                        //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8005);//切换数据输出8004原始8005yuv,80ff保存
                    }
                }, 300);*/
                //mUVCCameraView.setBitmap(mCursorRed, mCursorGreen, mCursorBlue, mCursorYellow, mWatermarkLogo);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //mCameraHandler.changePalette(palette);
                    }
                }, 200);
                timerEveryTime = new Timer();
                timerEveryTime.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        if (isPreviewing) {
                            //setValue(UVCCamera.CTRL_ZOOM_ABS, 0x8000);//每隔三分钟打一次快门
                            //if (isTemperaturing) {
                                calibrate();
                            //}
                            Log.e(TAG, "每隔3分钟执行一次操作");
                        }
                    }
                }, 1000, 380000);

                isWatermark = sharedPreferences.getInt("Watermark", 1);
                sharedPreferences.edit().putInt("Watermark", isWatermark).apply();
                //mCameraHandler.watermarkOnOff(isWatermark);
                mUsbDevice = device;
                isOpened = 1;
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            Log.e(TAG, "onDisconnect:");
            //System.exit(0);
            /*if (mCameraHandler != null) {
                if (isTemperaturing) {
                    mCameraHandler.stopTemperaturing();
                    isTemperaturing = false;
                }
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mCameraHandler.close();
                        isPreviewing = false;
                        //mCameraHandler.stopPreview();
                    }
                }, 0);
                //setCameraButton(false);
            }*/
            infiCam.disconnect();
            timerEveryTime.cancel();
            icon.recycle();
            iconPalette.recycle();
            //	Toast.makeText(MainActivity.this, "XthermDemo Disconnect", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Log.e(TAG, "onDettach:");
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onPause();
                    onStop();
                    onDestroy();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                }
            }, 100);

        }

        @Override
        public void onCancel(final UsbDevice device) {
            Log.e(TAG, "onCancel:");
            System.exit(0);
        }
    };

    //================================================================================

    private void calibrate() {
        infiCam.calibrate();
    }

    /*****************计时器*******************/

    //计时器
    private final Handler Timehandle = new Handler();
    private long currentSecond = 0;//当前毫秒数
    private final Runnable timeRunable = new Runnable() {
        @Override
        public void run() {
//            if (isOnRecord) {
            String RecordTime = getFormatHMS(currentSecond);
            canvas = mSfh.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }

            if (isOnRecord && (canvas != null)) {

                photoPaint = new Paint(); //建立画笔
                // bitcanvas.drawBitmap(mCursorBlue, icon.getWidth() / 384.0f * minx1 - mCursorBlue.getWidth() / 2.0f, icon.getHeight() / 288.0f * miny1 - mCursorBlue.getHeight() / 2.0f, photoPaint);
                photoPaint.setStrokeWidth(4);
                photoPaint.setTextSize(40);
                photoPaint.setColor(Color.RED);
                Rect bounds = new Rect();
                photoPaint.getTextBounds(RecordTime, 0, RecordTime.length(), bounds);

                if (MyApp.isT3) {
                    canvas.rotate(180, bounds.height() * 7, bounds.height());
                    canvas.drawText(RecordTime, bounds.height() * 7, bounds.height(), photoPaint);
                } else {
                    canvas.drawText(RecordTime, icon.getWidth() - bounds.height() * 7, icon.getHeight() - bounds.height(), photoPaint);
                }
            }
            if (canvas != null) {
                canvas.save();
                mSfh.unlockCanvasAndPost(canvas);
            }
            // timerText.setText(TimeUtil.getFormatHMS(currentSecond));

            currentSecond = currentSecond + 1000;
            if (currentSecond % 180000 == 0) {
                //mCameraHandler.stopRecording(); // TODO
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //mCameraHandler.startRecording(); // TODO
            }
//            }
            if (isOnRecord) {
                //递归调用本runable对象，实现每隔一秒一次执行任务
                Timehandle.postDelayed(this, 1000);
            } else {
                // TODO
                /*if (mCameraHandler != null) {
                    mCameraHandler.stopRecording();
                    currentSecond = 0;
                }*/
            }
        }
    };

    /**
     * 根据毫秒返回时分秒
     *
     * @param time
     * @return
     */
    public static String getFormatHMS(long time) {
        time = time / 1000;//总秒数
        int s = (int) (time % 60);//秒
        int m = (int) (time / 60);//分
        int h = (int) (time / 3600);//秒
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /*****************计时器*******************/

    public static synchronized String getVersionName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
