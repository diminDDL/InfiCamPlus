package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsPalette extends Settings {

	public int[] paletteMap;
	public Bitmap paletteBitmap;
	private final SettingPalette settingPalette = new SettingPalette();

	public SettingsPalette(Context context) {
		super(context, "PREFS_PALETTE", R.string.dialog_set_palette);
		init();
	}

	public SettingsPalette(Context context, @Nullable AttributeSet attrs) {
		super(context, "PREFS_PALETTE", R.string.dialog_set_palette, attrs);
		init();
	}

	public SettingsPalette(
		Context context,
		@Nullable AttributeSet attrs,
		int defStyleAttr
	) {
		super(
			context,
			"PREFS_PALETTE",
			R.string.dialog_set_palette,
			attrs,
			defStyleAttr
		);
		init();
	}

	private void init() {
		settings = new Setting[] { settingPalette, settingDefaults };
	}

	public class SettingPalette extends SettingRadio {
		SettingPalette() {
			super("palette", R.string.set_palette, 6, new int[] {});
			items = new int[Palette.palettes.length];
			for (int i = 0; i < Palette.palettes.length; ++i)
				items[i] = Palette.palettes[i].name;
		}

		@Override
		void onSet(int i) {
			paletteMap = Palette.palettes[i].getMap();
			paletteBitmap = Palette.palettes[i].getBitmap();
		}
	}

	SettingPalette getPalette(){ return settingPalette; }
}
