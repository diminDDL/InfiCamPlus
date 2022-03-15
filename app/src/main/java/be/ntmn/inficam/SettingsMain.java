package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsMain extends Settings {
	static final String SP_NAME = "PREFS";

	public SettingsMain(Context context) {
		super(context);
	}

	public SettingsMain(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public SettingsMain(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public String getSPName() { return SP_NAME; }

	@Override
	public Setting[] getSettings() { return settings; }

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
		void onSet(int value) {
			switch (value) {
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

	private final Setting[] settings = {
		new SettingSliderInt("firstshutdelay", R.string.set_firstshutdelay, 1000, 0, 2000, 100) {
			@Override
			void onSet(int i) { act.setShutterIntervalInitial(i); }
		},
		new SettingSliderInt("shutinterval", R.string.set_shutinterval, 380, 0, 1000, 10) {
			@Override
			void onSet(int i) {
				if (i == 0)
					title.setText(getContext().getString(R.string.set_shutinterval_never));
				act.setShutterInterval((long) i * 1000);
			}
		},
		new SettingBool("rotate180", R.string.set_rotate180, false) {
			@Override
			void onSet(boolean value) { act.setRotate(value); }
		},
		new SettingBool("mirror", R.string.set_mirror, false) {
			@Override
			void onSet(boolean value) { act.setMirror(value); }
		},
		new SettingRadio("imode", R.string.set_imode, 2, new int[] {
				R.string.imode_nearest,
				R.string.imode_linear,
				R.string.imode_cubic
			}) {
			@Override
			void onSet(int value) {
				final int[] imodes = new int[] {
						SurfaceMuxer.IMODE_NEAREST,
						SurfaceMuxer.IMODE_LINEAR,
						SurfaceMuxer.IMODE_BICUBIC
				};
				act.setIMode(imodes[value]);
			}
		},
		new SettingBool("recordaudio", R.string.set_recordaudio, true) {
			@Override
			void onSet(boolean value) { act.setRecordAudio(value); }
		},
		new SettingBool("fullscreen", R.string.set_fullscreen, true) {
			@Override
			void onSet(boolean value) { act.setFullscreen(value); }
		},
		new SettingBool("hide_navigation", R.string.set_hide_navigation, true) {
			@Override
			void onSet(boolean value) { act.setHideNavigation(value); }
		},
		new SettingBool("keep_screen_on", R.string.set_keep_screen_on, true) {
			@Override
			void onSet(boolean value) { act.setKeepScreenOn(value); }
		},
		new SettingBool("show_bat_level", R.string.set_show_bat_level, true) {
			@Override
			void onSet(boolean value) { act.setShowBatLevel(value); }
		},
		new SettingBool("swap_controls", R.string.set_swap_controls, false) {
			@Override
			void onSet(boolean value) { act.setSwapControls(value); }
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
		settingDefaults
	};
}
