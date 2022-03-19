package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsMeasure extends Settings {
	private static final String SP_NAME = "PREFS_MEASURE";
	private static final int name = R.string.dialog_set_measure;

	public SettingsMeasure(Context context) {
		super(context);
	}

	public SettingsMeasure(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public SettingsMeasure(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public String getSPName() { return SP_NAME; }

	@Override
	public int getName() { return name; }

	@Override
	public Setting[] getSettings() { return settings; }

	private final Setting[] settings = {
			new SettingBool("showcenter", R.string.set_show_center, true) {
				@Override
				void onSet(boolean value) { act.setShowCenter(value); }
			},
			new SettingBool("showmax", R.string.set_show_max, true) {
				@Override
				void onSet(boolean value) { act.setShowMax(value); }
			},
			new SettingBool("showmin", R.string.set_show_min, true) {
				@Override
				void onSet(boolean value) { act.setShowMin(value); }
			},
			new SettingBool("showpalette", R.string.set_show_palette, true) {
				@Override
				void onSet(boolean value) { act.setShowPalette(value); }
			},
			settingDefaults
	};
}
