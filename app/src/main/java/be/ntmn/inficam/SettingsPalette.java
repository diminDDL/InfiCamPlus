package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsPalette extends Settings {
	private static final String SP_NAME = "PREFS_THERM";
	private static final int name = R.string.dialog_set_palette;

	public SettingsPalette(Context context) { super(context); }
	public SettingsPalette(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
	public SettingsPalette(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public String getSPName() { return SP_NAME; }

	@Override
	public int getName() { return name; }

	@Override
	public Setting[] getSettings() { return settings; }

	private final Setting[] settings = { new SettingsTherm.SettingPalette() };
}
