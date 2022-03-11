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
import android.widget.TextView;

import androidx.annotation.Nullable;

/* TODO To add profiles i want to just make a function to load and store all the sharedprefs to a
 *   JSON array that is stored in sharedprefs. Should palette and termometry parameters be part of
 *   profile, or option to choose what is part of it or how?
 */
public class Settings extends LinearLayout {
	private static final String SP_NAME = "PREFS";
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
			box = new CheckBox(set.getContext());
			box.setText(res);
			box.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			box.setOnCheckedChangeListener((compoundButton, b) -> {
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
			rg = new RadioGroup(set.getContext());
			TextView title = new TextView(set.getContext());
			title.setText(res);
			rg.addView(title);
			rg.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			rg.setOnCheckedChangeListener((compoundButton, i) -> {
				ed.putInt(name, i - 1);
				ed.commit();
				onSet(i - 1);
			});
			for (int item : items) {
				RadioButton rb = new RadioButton(set.getContext());
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

	Setting[] settings = {
			new SettingRadio("imode", R.string.set_imode, 2, new int[] {
					R.string.imode_nearest,
					R.string.imode_linear,
					R.string.imode_cubic
				}) {
				@Override
				void onSet(int value) {
					final int[] imodes = new int[] {
							SurfaceMuxer.IMODE_NEAREST,
							SurfaceMuxer.IMODE_LINEAR,
							SurfaceMuxer.IMODE_BICUBIC
					};
					act.setIMode(imodes[value]);
				}
			},
			new SettingBool("recordaudio", R.string.set_recordaudio, true) {
				@Override
				void onSet(boolean value) {
					act.setRecordAudio(value);
				}
			},
			new SettingBool("fullscreen", R.string.set_fullscreen, true) {
				@Override
				void onSet(boolean value) {
					act.setFullscreen(value);
				}
			},
			new SettingBool("hide_navigation", R.string.set_hide_navigation, true) {
				@Override
				void onSet(boolean value) {
					act.setHideNavigation(value);
				}
			},
			new SettingBool("keep_screen_on", R.string.set_keep_screen_on, true) {
				@Override
				void onSet(boolean value) {
					act.setKeepScreenOn(value);
				}
			}
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

	void init(MainActivity act) {
		this.act = act;
		sp = act.getSharedPreferences(SP_NAME, MODE_PRIVATE);
		ed = sp.edit();
		for (Setting setting : settings)
			setting.init(this);
	}

	void load() {
		for (Setting setting : settings)
			setting.load();
	}
}
