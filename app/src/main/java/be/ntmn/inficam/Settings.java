package be.ntmn.inficam;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

/* TODO To add profiles i want to just make a function to load and store all the sharedprefs to a
 *   JSON array that is stored in sharedprefs. Should palette and termometry parameters be part of
 *   profile, or option to choose what is part of it or how?
 */
public abstract class Settings extends LinearLayout {
	MainActivity act;
	SharedPreferences sp;
	SharedPreferences.Editor ed;

	public static abstract class Setting {
		String name;
		int res;

		Setting(String name, int res) {
			this.name = name;
			this.res = res;
		}

		abstract void init(Settings set);
		abstract void load();
		abstract void setDefault();
	}

	public abstract class SettingBool extends Setting {
		private final boolean def;
		private CheckBox box;

		SettingBool(String name, int res, boolean def) {
			super(name, res);
			this.def = def;
		}

		@Override
		void init(Settings set) {
			box = new CheckBox(getContext());
			box.setText(res);
			box.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			box.setOnCheckedChangeListener((view, b) -> {
				ed.putBoolean(name, b);
				ed.commit();
				onSet(b);
			});
			box.setVisibility(VISIBLE);
			set.addView(box);
		}

		@Override
		void load() {
			boolean value = sp.getBoolean(name, def);
			box.setChecked(value);
			onSet(value);
		}

		@Override
		void setDefault() {
			ed.putBoolean(name, def);
			ed.commit();
			load();
		}

		abstract void onSet(boolean value);
	}

	public abstract class SettingRadio extends Setting {
		private final int def;
		private RadioGroup rg;
		public int[] items;
		public int current;

		SettingRadio(String name, int res, int def, int[] items) {
			super(name, res);
			this.def = def; /* RadioGroup indexes from 1, wtf... Ah! Because our TextView xD. */
			this.items = items;
		}

		@Override
		void init(Settings set) {
			rg = new RadioGroup(getContext());
			TextView title = new TextView(getContext());
			title.setText(res);
			rg.addView(title);
			rg.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			rg.setOnCheckedChangeListener((view, id) -> {
				int i = rg.indexOfChild(rg.findViewById(id));
				ed.putInt(name, i - 1);
				ed.commit();
				current = i - 1;
				onSet(i - 1);
			});
			for (int item : items) {
				RadioButton rb = new RadioButton(getContext());
				rb.setText(item);
				rb.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT));
				rg.addView(rb);
			}
			rg.setVisibility(VISIBLE);
			set.addView(rg);
		}

		@Override
		void load() {
			int value = sp.getInt(name, def);
			try {
				((RadioButton) rg.getChildAt(value + 1)).setChecked(true);
			} catch (Exception e) {
				value = def;
			}
			onSet(value);
		}

		@Override
		void setDefault() {
			ed.putInt(name, def);
			ed.commit();
			load();
		}

		public void set(int i) {
			((RadioButton) rg.getChildAt(i + 1)).setChecked(true);
		}

		abstract void onSet(int i);
	}

	public abstract class SettingSlider extends Setting {
		private final int def, min, max, step;
		private Slider slider;
		TextView title;

		SettingSlider(String name, int res, int def, int min, int max, int step) {
			super(name, res);
			this.def = def;
			this.min = min;
			this.max = max;
			this.step = step;
		}

		@Override
		void init(Settings set) {
			title = new TextView(getContext());
			setText(def);
			set.addView(title);
			slider = new Slider(getContext());
			slider.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			slider.setMin(min);
			slider.setMax(max);
			slider.setStep(step);
			slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
					ed.putInt(name, i);
					ed.commit();
					setText(i);
					onSet(i);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) { /* Empty. */ }

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) { /* Empty. */ }
			});
			slider.setVisibility(VISIBLE);
			set.addView(slider);
		}

		@Override
		void load() {
			int value = sp.getInt(name, def);
			slider.setProgress(value);
			setText(value);
			onSet(value);
		}

		@Override
		void setDefault() {
			ed.putInt(name, def);
			ed.commit();
			load();
		}

		abstract void setText(int i);
		abstract void onSet(int i);
	}

	public abstract class SettingSliderInt extends SettingSlider {
		SettingSliderInt(String name, int res, int def, int min, int max, int step) {
			super(name, res, def, min, max, step);
		}

		@Override
		void setText(int i) {
			title.setText(getContext().getString(res, i));
		}
	}

	public abstract class SettingSliderFloat extends SettingSlider {
		private final int div;

		SettingSliderFloat(String name, int res, int def, int min, int max, int step, int div) {
			super(name, res, def, min, max, step);
			this.div = div;
		}

		@Override
		void setText(int i) {
			title.setText(getContext().getString(res, (float) i / div));
		}

		@Override
		void onSet(int i) {
			onSet((float) i / div);
		}

		abstract void onSet(float f);
	}

	public abstract class SettingButton extends Setting {
		SettingButton(int res) {
			super(null, res);
		}

		@Override
		void init(Settings set) {
			Button button = new Button(getContext());
			button.setText(res);
			button.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			button.setOnClickListener(view -> onPress());
			button.setVisibility(VISIBLE);
			set.addView(button);
		}

		@Override
		void load() { /* Empty. */ }

		@Override
		void setDefault() { /* Empty. */ }

		abstract void onPress();
	}

	final SettingButton settingDefaults = new SettingButton(R.string.set_defaults) {
		@Override
		void onPress() { setDefaults(); }
	};

	public Settings(Context context) {
		super(context);
	}

	public Settings(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public Settings(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void init(MainActivity act) {
		this.act = act;
		sp = act.getSharedPreferences(getSPName(), MODE_PRIVATE);
		ed = sp.edit();
		removeAllViews();
		Setting[] settings = getSettings();
		for (Setting setting : settings)
			setting.init(this);
	}

	public void load() {
		Setting[] settings = getSettings();
		for (Setting setting : settings)
			setting.load();
	}

	public void setDefaults() {
		Setting[] settings = getSettings();
		for (Setting setting : settings)
			setting.setDefault();
	}

	public abstract Setting[] getSettings();
	public abstract String getSPName(); /* Name for "shared preferences file". */
	public abstract int getName();
}
