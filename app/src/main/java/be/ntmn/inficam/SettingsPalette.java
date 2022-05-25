package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsPalette extends Settings {
	private static final String SP_NAME = "PREFS_THERM";
	private static final int name = R.string.dialog_set_palette;

	public class SettingPalette extends SettingRadio {
		SettingPalette() {
			super("palette", R.string.set_palette, 6, new int[] {});
			items = new int[Palette.palettes.length];
			for (int i = 0; i < Palette.palettes.length; ++i)
				items[i] = Palette.palettes[i].name;
		}

		@Override
		void onSet(int i) { act.setPalette(Palette.palettes[i].getData()); }
	}

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

	public SettingPalette palette = new SettingPalette();

	private final Setting[] settings = { palette, settingDefaults };
}
