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

	Setting[] settings = {
			new SettingSlider("firstshutdelay", R.string.set_firstshutdelay, 1000, 0, 2000, 100) {
				@Override
				void onSet(int i) {
					act.setShutterIntervalInitial(i);
				}
			},
			new SettingSlider("shutinterval", R.string.set_shutinterval, 380, 0, 1000, 10) {
				@Override
				void onSet(int i) {
					if (i == 0)
						title.setText(getContext().getString(R.string.set_shutinterval_never));
					act.setShutterInterval((long) i * 1000);
				}
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
			new SettingBool("hide_navigation", R.string.set_hide_navigation, true) {
				@Override
				void onSet(boolean value) {
					act.setHideNavigation(value);
				}
			},
			new SettingBool("keep_screen_on", R.string.set_keep_screen_on, true) {
				@Override
				void onSet(boolean value) {
					act.setKeepScreenOn(value);
				}
			}
	};
}
