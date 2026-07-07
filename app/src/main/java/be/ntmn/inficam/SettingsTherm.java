package be.ntmn.inficam;

import static java.lang.Math.round;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;

import java.util.Arrays;

/*
 * TODO: This may be fragile and bugs can trigger infinite loops.
 *
 * When a setting is changed in the UI, we call set___() in libinficam.
 * Libinficam then does a callback to us when the setting was actually changed.
 * This callbacks calls setSettings(), which changes the UI values to match.
 *
 * This allows to keep libinficam and the UI in sync in case the camera adjusts or refuses the new setting.
 * (Mostly. TODO: If the camera ignores the changes, there is no callback, thus we still don't undo the changes in the UI).
 *
 * But the UI doesn't know if changes come from a callback and may call libinficam's set___() again.
 * Libinficam shouldn't trigger the callback because the settings are the same as before.
 * We also don't call set___() if the settings are the same as before.
 * But if libinficam calls us back with different settings due to a bug, this turns into an infinite loop.
 */
public class SettingsTherm extends Settings {

	public float[][] thermal_ranges;
	private boolean deferCameraUpdates = false;
	private boolean hasDeferredCameraUpdates = false;
	private boolean displayOnlyChange = false;
	private boolean applyLocalCorrection = true;

	public SettingsTherm(Context context) {
		super(context, "PREFS_THERM", R.string.dialog_set_therm);
	}

	public SettingsTherm(Context context, @Nullable AttributeSet attrs) {
		super(context, "PREFS_THERM", R.string.dialog_set_therm, attrs);
	}

	public SettingsTherm(
		Context context,
		@Nullable AttributeSet attrs,
		int defStyleAttr
	) {
		super(
			context,
			"PREFS_THERM",
			R.string.dialog_set_therm,
			attrs,
			defStyleAttr
		);
	}

	public float[] getRange() {
		assert(((SettingRadioDynamic)getSetting("range")).get() >= 0 &&
				((SettingRadioDynamic)getSetting("range")).get() < thermal_ranges.length);
		return thermal_ranges[((SettingRadioDynamic)getSetting("range")).get()];
	}

	private boolean shouldApplyCameraUpdate() {
		if (deferCameraUpdates) {
			hasDeferredCameraUpdates = true;
			return false;
		}
		return true;
	}

	public void beginDeferredCameraUpdates() {
		deferCameraUpdates = true;
		hasDeferredCameraUpdates = false;
	}

	public boolean endDeferredCameraUpdates() {
		if (!deferCameraUpdates)
			return false;
		deferCameraUpdates = false;
		if (!hasDeferredCameraUpdates)
			return false;
		applyCameraSettings();
		hasDeferredCameraUpdates = false;
		return true;
	}

	private void applyCameraSettings() {
		act.infiCam.setEmissivity(((SettingSliderFloat)getSetting("emissivity")).get());
		act.infiCam.setTempReflected(((SettingSliderTemp)getSetting("temp_reflected")).get());
		act.infiCam.setTempAir(((SettingSliderTemp)getSetting("temp_air")).get());
		act.infiCam.setHumidity(((SettingSliderInt)getSetting("humidity")).get() / 100.0f);
		act.infiCam.setDistance((short)(int)((SettingSliderInt)getSetting("distance")).get());
		if (!applyLocalCorrection)
			act.infiCam.setCorrection(((SettingSliderTemp)getSetting("correction")).get());
		act.infiCam.setRange(((SettingRadioDynamic)getSetting("range")).get());
	}

	//Triggers when the settings have been taken into account by the camera
	public void setSettings(
		float emissivity,
		float temp_reflected,
		float temp_air,
		float humidity,
		int distance,
		float correction,
		int range
	) {
		if (deferCameraUpdates)
			return;
		if (((SettingSliderFloat)getSetting("emissivity")).get() != emissivity) {
			Log.d("inficam","emissivity change "+((SettingSliderFloat)getSetting("emissivity")).get() +" to "+ emissivity);
			((SettingSliderFloat)getSetting("emissivity")).setTo(emissivity);
		}
		if (((SettingSliderTemp)getSetting("temp_reflected")).get() != temp_reflected) {
			Log.d("inficam","temp_reflected change "+((SettingSliderTemp)getSetting("temp_reflected")).get() +" to "+ temp_reflected);
			((SettingSliderTemp)getSetting("temp_reflected")).setTo(temp_reflected);
		}
		if (((SettingSliderTemp)getSetting("temp_air")).get() != temp_air) {
			Log.d("inficam","temp_air change "+((SettingSliderTemp)getSetting("temp_air")).get() +" to "+ temp_air);
			SettingSliderTemp t = ((SettingSliderTemp)getSetting("temp_air"));
			Log.d("inficam","set_to");
			t.setTo(temp_air);
		}
		int int_humidity = round(humidity*100);
		if (((SettingSliderInt)getSetting("humidity")).get() != int_humidity) {
			Log.d("inficam","humidity change "+((SettingSliderInt)getSetting("humidity")).get() +" to "+ int_humidity);
			((SettingSliderInt)getSetting("humidity")).setTo(int_humidity);
		}
		if (((SettingSliderInt)getSetting("distance")).get() != distance) {
			Log.d("inficam","distance change "+((SettingSliderInt)getSetting("distance")).get() +" to "+ distance);
			((SettingSliderInt)getSetting("distance")).setTo(distance);
		}
		if (!applyLocalCorrection && ((SettingSliderTemp)getSetting("correction")).get() != correction) {
			Log.d("inficam","correction change "+((SettingSliderTemp)getSetting("correction")).get() +" to "+ correction);
			Log.d("inficam","correction delta "+(((SettingSliderTemp)getSetting("correction")).get() - correction));
			((SettingSliderTemp)getSetting("correction")).setTo(correction);
		}
		if (((SettingRadioDynamic)getSetting("range")).get() != range) {
			Log.d("inficam","range change "+((SettingRadioDynamic)getSetting("range")).get() +" to "+ range);
			((SettingRadioDynamic)getSetting("range")).setTo(range);
		}
	}

	@SuppressLint("DefaultLocale")
	public void init(MainActivity act, float[][] p_thermal_ranges) {
		thermal_ranges = p_thermal_ranges;
		final SettingSliderTemp[] correctionSetting = new SettingSliderTemp[1];
		String[] range_text = Arrays.stream(thermal_ranges)
			.map(
				x -> String.format("%.0fC - %.0fC (%.0fF - %.0fF)", x[0], x[1], //TODO: fix IDE complaining
				x[0] * (9 / 5.0) + 32,
				x[1] * (9 / 5.0) + 32)
			)
			.toArray(String[]::new);

		settings = new Setting[] {
			new SettingSliderFloat(
				"emissivity",
				R.string.set_emissivity,
				95,
				0,
				100,
				1,
				100
			) {
				@Override
				void onSet(float f) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setEmissivity(f);
				}
			},
			new SettingSliderTemp(
				"temp_reflected",
				R.string.set_temp_reflected,
				200,
				-100,
				400
			) {
				@Override
				void onSet(float f) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setTempReflected(f);
				}
			},
			new SettingSliderTemp(
				"temp_air",
				R.string.set_temp_ambient,
				200,
				-100,
				400
			) {
				@Override
				void onSet(float f) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setTempAir(f);
				}
			},
			new SettingSliderInt(
				"humidity",
				R.string.set_humidity,
				50,
				0,
				100,
				1
			) {
				@Override
				void onSet(int i) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setHumidity((float) i/100);
				}
			},
			new SettingSliderInt(
				"distance",
				R.string.set_distance,
				1,
				0,
				100,
				1
			) {
				@Override
				void onSet(int i) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setDistance((short) i);
				}
			},
			correctionSetting[0] = new SettingSliderTemp(
				"correction",
				R.string.set_correction,
				0,
				-1000,
				1000
			) {
				@Override
				void setText(int i) {
					title.setText(getContext().getString(res,
							Util.formatTemp((float) i / div, Util.TEMPUNIT_CELSIUS)));
				}

				@Override
				void onSet(float f) {
					act.setLocalCorrection(f);
					if (applyLocalCorrection) {
						displayOnlyChange = true;
					} else if (shouldApplyCameraUpdate()) {
						act.infiCam.setCorrection(f);
					}
				}
			},
			new SettingBool("apply_correction_local", R.string.set_apply_correction_local, true) {
				@Override
				void onSet(boolean value) {
					applyLocalCorrection = value;
					act.setApplyLocalCorrection(value);
					act.setLocalCorrection(correctionSetting[0].get());
					displayOnlyChange = true;
					if (!value && shouldApplyCameraUpdate())
						act.infiCam.setCorrection(correctionSetting[0].get());
				}
			},
			new Setting(null, R.string.set_correction) {
				@Override
				void init(Settings set) {
					LinearLayout buttons = new LinearLayout(getContext());
					buttons.setOrientation(LinearLayout.HORIZONTAL);
					buttons.setLayoutParams(new LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.WRAP_CONTENT));

					Button minus = new Button(getContext());
					minus.setText(R.string.set_correction_minus);
					minus.setLayoutParams(new LinearLayout.LayoutParams(
							0,
							ViewGroup.LayoutParams.WRAP_CONTENT,
							1.0f));
					minus.setOnClickListener(view -> adjustCorrection(-0.5f));
					buttons.addView(minus);

					Button plus = new Button(getContext());
					plus.setText(R.string.set_correction_plus);
					plus.setLayoutParams(new LinearLayout.LayoutParams(
							0,
							ViewGroup.LayoutParams.WRAP_CONTENT,
							1.0f));
					plus.setOnClickListener(view -> adjustCorrection(0.5f));
					buttons.addView(plus);

					set.addView(buttons);
				}

				private void adjustCorrection(float delta) {
					float next = Math.max(-100.0f,
							Math.min(100.0f, correctionSetting[0].get() + delta));
					correctionSetting[0].setTo(next);
					SettingsTherm.this.handleChange();
				}

				@Override
				void load() { /* Empty. */ }

				@Override
				void setDefault() { /* Empty. */ }
			},
			new SettingRadioDynamic("range", R.string.set_range, 0, range_text) {
				@Override
				void onSet(int i) {
					if (shouldApplyCameraUpdate())
						act.infiCam.setRange(i);
				}
			},
			settingDefaults,
		};
		init(act);
	}

	public void handleChange(){
		if (displayOnlyChange) {
			displayOnlyChange = false;
		} else if (deferCameraUpdates) {
			hasDeferredCameraUpdates = true;
		} else {
			act.calibrate(false);
		}
	}
}
