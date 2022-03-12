package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsMeasure extends Settings {
	static final String SP_NAME = "PREFS_MEASURE";

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
	public Setting[] getSettings() { return settings; }

	Setting[] settings = {
			new SettingBool("showcenter", R.string.set_show_center, true) {
				@Override
				void onSet(boolean value) { act.overlay.showCenter = value; }
			},
			new SettingBool("showmax", R.string.set_show_max, true) {
				@Override
				void onSet(boolean value) { act.overlay.showMax = value; }
			},
			new SettingBool("showmin", R.string.set_show_min, true) {
				@Override
				void onSet(boolean value) { act.overlay.showMin = value; }
			},
			new SettingButton(R.string.set_defaults) {
				@Override
				void onPress() { setDefaults(); }
			}
	};
}
