package be.ntmn.inficam;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.AttributeSet;
import androidx.annotation.Nullable;

public class SettingsMain extends Settings {


    private void updateAutoShutterSettings(){
        act.infiCam.setAutoShutterSettings(
                ((SettingSliderInt)getSetting("maxshutinterval")).get() > 0,
                (int)((SettingSliderInt)getSetting("minshutinterval")).get(),
                (int)((SettingSliderInt)getSetting("maxshutinterval")).get()*1000); //must be ms
    }
    public boolean overtempEnabled = true;

    public SettingsMain(Context context) {
        super(context, "PREFS", R.string.dialog_set_main);
        init();
    }

    public SettingsMain(Context context, @Nullable AttributeSet attrs) {
        super(context, "PREFS", R.string.dialog_set_main, attrs);
        init();
    }

    public SettingsMain(
        Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr
    ) {
        super(context, "PREFS", R.string.dialog_set_main, attrs, defStyleAttr);
        init();
    }

    private abstract class SettingResolution extends SettingRadio {

        SettingResolution(String name, int res) {
            super(name, res, 6, new int[] {
                R.string.picres_320,
                R.string.picres_640,
                R.string.picres_800,
                R.string.picres_1024,
                R.string.picres_1600,
                R.string.picres_720w,
                R.string.picres_1080w,
            });
        }

        @Override
        void onSet(int i) {
            switch (i) {
                case 0:
                    onSetRes(320, 240);
                    break;
                case 1:
                    onSetRes(640, 480);
                    break;
                case 2:
                    onSetRes(800, 600);
                    break;
                case 3:
                    onSetRes(1024, 768);
                    break;
                case 4:
                    onSetRes(1600, 1200);
                    break;
                case 5:
                    onSetRes(1280, 720);
                    break;
                case 6:
                    onSetRes(1920, 1080);
                    break;
            }
        }

        abstract void onSetRes(int w, int h);
    }

    private void init() {
        settings = new Setting[] {
            new SettingSliderInt(
                "minshutinterval",
                R.string.set_minshutinterval,
                7500,
                0,
                10000,
                100
            ) {
                @Override
                void onSet(int i) {
                    updateAutoShutterSettings();
                }
            },
            new SettingSliderInt(
                "maxshutinterval",
                R.string.set_maxshutinterval,
                180,
                0,
                1000,
                10
            ) {
                @Override
                void onSet(int i) {
                    if (i == 0) title.setText(
                        getContext().getString(R.string.set_maxshutinterval_never)
                    );
                    updateAutoShutterSettings();
                }
            },
            new SettingBool("overtemplock", R.string.set_overtemplock, true) {
                @Override
                void onSet(boolean value) {
                    overtempEnabled = value; //TODO: clean up
                }
            },
            new SettingBool("rotate180", R.string.set_rotate180, false) {
                @Override
                void onSet(boolean value) {
                    act.setRotate(value);
                }
            },
            new SettingBool("mirror", R.string.set_mirror, false) {
                @Override
                void onSet(boolean value) {
                    act.setMirror(value);
                }
            },
            new SettingRadio("imode", R.string.set_imode, 1, new int[] {
                R.string.imode_nearest,
                R.string.imode_linear,
                R.string.imode_cubic,
                R.string.imode_cmrom,
                R.string.imode_adaptive,
            }) {
                @Override
                void onSet(int i) {
                    final int[] imodes = new int[] {
                        SurfaceMuxer.DM_NEAREST,
                        SurfaceMuxer.DM_LINEAR,
                        SurfaceMuxer.DM_CUBIC,
                        SurfaceMuxer.DM_CMROM,
                        SurfaceMuxer.DM_FADAPTIVE,
                    };
                    act.setIMode(imodes[i]);
                }
            },
            new SettingSliderFloat(
                "sharpening",
                R.string.set_sharpening,
                20,
                0,
                100,
                5,
                100
            ) {
                @Override
                void onSet(float f) {
                    act.setSharpening(f);
                }
            },
            new SettingBool("recordaudio", R.string.set_recordaudio, true) {
                @Override
                void onSet(boolean value) {
                    act.setRecordAudio(value);
                }
            },
            new SettingBool("fullscreen", R.string.set_fullscreen, true) {
                @Override
                void onSet(boolean value) {
                    act.setFullscreen(value);
                }
            },
            new SettingBool(
                "hide_navigation",
                R.string.set_hide_navigation,
                true
            ) {
                @Override
                void onSet(boolean value) {
                    act.setHideNavigation(value);
                }
            },
            new SettingBool(
                "keep_screen_on",
                R.string.set_keep_screen_on,
                true
            ) {
                @Override
                void onSet(boolean value) {
                    act.setKeepScreenOn(value);
                }
            },
            new SettingBool(
                "show_bat_level",
                R.string.set_show_bat_level,
                true
            ) {
                @Override
                void onSet(boolean value) {
                    act.setShowBatLevel(value);
                }
            },
            new SettingBool(
                "swap_controls",
                R.string.set_swap_controls,
                false
            ) {
                @Override
                void onSet(boolean value) {
                    act.setSwapControls(value);
                }
            },
            new SettingRadio("pic_type", R.string.set_pic_type, 0, new int[] {
                R.string.img_type_png,
                R.string.img_type_png565,
                R.string.img_type_jpeg,
            }) {
                @Override
                void onSet(int i) {
                    act.setImgType(i);
                }
            },
            new SettingSliderInt(
                "pic_quality",
                R.string.set_pic_quality,
                100,
                0,
                100,
                1
            ) {
                @Override
                void onSet(int i) {
                    act.setImgQuality(i);
                }
            },
            new SettingResolution("pic_res", R.string.set_pic_res) {
                @Override
                void onSetRes(int w, int h) {
                    act.setPicSize(w, h);
                }
            },
            new SettingResolution("vid_res", R.string.set_vid_res) {
                @Override
                void onSetRes(int w, int h) {
                    act.setVidSize(w, h);
                }
            },
            new SettingRadio(
                "orientation",
                R.string.set_orientation,
                0,
                new int[] {
                    R.string.orientation_auto,
                    R.string.orientation_landscape,
                    R.string.orientation_portrait,
                    R.string.orientation_reverse,
                }
            ) {
                @Override
                void onSet(int i) {
                    act.setOrientation(
                        new int[] {
                            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                        }[i]
                    );
                }
            },
            new SettingRadio("unit", R.string.set_unit, 0, new int[] {
                R.string.unit_celsius,
                R.string.unit_fahrenheit,
                R.string.unit_kelvin,
                R.string.unit_rankine,
            }) {
                @Override
                void onSet(int i) {
                    final int[] units = new int[] {
                        Util.TEMPUNIT_CELSIUS,
                        Util.TEMPUNIT_FAHRENHEIT,
                        Util.TEMPUNIT_KELVIN,
                        Util.TEMPUNIT_RANKINE,
                    };
                    act.setTempUnit(units[i]);
                }
            },
            settingDefaults,
        };
    }
}
