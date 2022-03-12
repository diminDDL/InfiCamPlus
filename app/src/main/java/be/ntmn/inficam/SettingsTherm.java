package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class SettingsTherm extends Settings {
	private static final String SP_NAME = "PREFS_THERM";

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

	class SettingPalette extends SettingRadio {
		SettingPalette() {
			super("palette", R.string.set_palette, 2, new int[] {});
			items = new int[Palette.palettes.length];
			for (int i = 0; i < Palette.palettes.length; ++i)
				items[i] = Palette.palettes[i].name;
		}

		@Override
		void onSet(int value) {
			act.setPalette(Palette.palettes[value].getData());
		}
	}

	public SettingPalette palette = new SettingPalette();

	private final Setting[] settings = {
		new SettingSliderFloat("emissivity", R.string.set_emissivity, 100, 0, 100, 1, 100) {
			@Override
			void onSet(float f) {
				act.infiCam.setEmissivity(f);
				act.infiCam.updateTable();
			}
		},
		new SettingSliderFloat("temp_reflected", R.string.set_temp_reflected,
				200, -100, 400, 5, 10) {
			@Override
			void onSet(float f) {
				act.infiCam.setTempReflected(f);
				act.infiCam.updateTable();
			}
		},
		new SettingSliderFloat("temp_ambient", R.string.set_temp_ambient, 200, -100, 400, 5, 10) {
			@Override
			void onSet(float f) {
				act.infiCam.setTempAir(f);
				act.infiCam.updateTable();
			}
		},
		new SettingSliderInt("humidity", R.string.set_humidity, 50, 0, 100, 1) {
			@Override
			void onSet(int i) {
				act.infiCam.setHumidity((float) i / 100.0f);
				act.infiCam.updateTable();
			}
		},
		new SettingSliderInt("distance", R.string.set_distance, 1, 0, 100, 1) {
			@Override
			void onSet(int i) {
				act.infiCam.setDistance(i);
				act.infiCam.updateTable();
			}
		},
		new SettingSliderFloat("correction", R.string.set_correction, 0, -200, 200, 5, 10) {
			@Override
			void onSet(float f) {
				act.infiCam.setCorrection(f);
				act.infiCam.updateTable();
			}
		},
		new SettingRadio("range", R.string.set_range, 0, new int[] {
				R.string.set_range_120,
				R.string.set_range_400
			}) {
			@Override
			void onSet(int value) {
				switch (value) {
					case 0:
						act.setRange(120);
						break;
					case 1:
						act.setRange(400);
						break;
				}
			}
		},
		palette,
		new SettingButton(R.string.set_defaults) {
			@Override
			void onPress() { setDefaults(); }
		}
	};
}
