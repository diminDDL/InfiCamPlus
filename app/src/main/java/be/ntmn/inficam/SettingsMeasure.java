package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsMeasure extends Settings {
	private static final String SP_NAME = "PREFS_MEASURE";
	private static final int name = R.string.dialog_set_measure;

	public SettingsMeasure(Context context) {
		super(context, "PREFS_MEASURE", R.string.dialog_set_measure);
		init();
	}
	public SettingsMeasure(Context context, @Nullable AttributeSet attrs) {
		super(context, "PREFS_MEASURE", R.string.dialog_set_measure, attrs);
		init();
	}
	public SettingsMeasure(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(
				context,
				"PREFS_MEASURE",
				R.string.dialog_set_measure,
				attrs,
				defStyleAttr
		);
		init();
	}

	private void init() {
		settings = new Setting[] {
				new SettingBool("showcenter", R.string.set_show_center, true) {
					@Override
					void onSet(boolean value) {
						act.setShowCenter(value);
					}
				},
				new SettingBool("showmax", R.string.set_show_max, true) {
					@Override
					void onSet(boolean value) {
						act.setShowMax(value);
					}
				},
				new SettingBool("showmin", R.string.set_show_min, true) {
					@Override
					void onSet(boolean value) {
						act.setShowMin(value);
					}
				},
				new SettingBool("showpalette", R.string.set_show_palette, true) {
					@Override
					void onSet(boolean value) {
						act.setShowPalette(value);
					}
				},
				settingDefaults,
		};
	}
}
