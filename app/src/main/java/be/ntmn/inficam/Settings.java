package be.ntmn.inficam;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ViewGroup;
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

	static abstract class Setting {
		String name;
		int res;

		Setting(String name, int res) {
			this.name = name;
			this.res = res;
		}

		abstract void init(Settings set);
		abstract void load();
	}

	abstract class SettingBool extends Setting {
		boolean def;
		CheckBox box;

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

		abstract void onSet(boolean value);
	}

	abstract class SettingRadio extends Setting {
		int def;
		RadioGroup rg;
		int[] items;

		SettingRadio(String name, int res, int def, int[] items) {
			super(name, res);
			this.def = def; /* RadioGroup indexes from 1, wtf... */
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
			rg.setOnCheckedChangeListener((view, i) -> {
				ed.putInt(name, i - 1);
				ed.commit();
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
			rg.check(value + 1);
			onSet(value);
		}

		abstract void onSet(int value);
	}

	abstract class SettingSlider extends Setting {
		int def, min, max, step;
		Slider slider;
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
			title.setText(getContext().getString(res, def));
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
					title.setText(getContext().getString(res, i));
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
			title.setText(getContext().getString(res, value));
			onSet(value);
		}

		abstract void onSet(int i);
	}

	public Settings(Context context) {
		super(context);
	}

	public Settings(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public Settings(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	void init(MainActivity act) {
		this.act = act;
		sp = act.getSharedPreferences(getSPName(), MODE_PRIVATE);
		ed = sp.edit();
		removeAllViews();
		Setting[] settings = getSettings();
		for (Setting setting : settings)
			setting.init(this);
	}

	void load() {
		Setting[] settings = getSettings();
		for (Setting setting : settings)
			setting.load();
	}

	public abstract Setting[] getSettings();
	public abstract String getSPName(); /* Name for "shared preferences file". */
}
