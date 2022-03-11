package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsTherm extends Settings {
	static final String SP_NAME = "PREFS_THERM";

	public SettingsTherm(Context context) {
		super(context);
	}

	public SettingsTherm(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public SettingsTherm(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public String getSPName() { return SP_NAME; }

	@Override
	public Setting[] getSettings() { return settings; }

	Setting[] settings = {
		new SettingSliderFloat("emissivity", R.string.set_firstshutdelay, 100, 0, 100, 1, 100) {
			@Override
			void onSet(float f) {
				// TODO
			}
		},
	};
}
