package be.ntmn.inficam;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.Set;

public class Settings extends LinearLayout {
	private static final String SP_NAME = "PREFS";
	MainActivity act;
	SharedPreferences sp;
	SharedPreferences.Editor ed;
	CheckBox setSmooth;

	abstract class Setting {
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

			box.setOnCheckedChangeListener((compoundButton, b) -> {
				ed.putBoolean(name, b);
				ed.commit();
				onSet(b);
			});
		}

		@Override
		void load() {
			boolean value = sp.getBoolean(name, def);
			box.setChecked(value);
			onSet(value);
		}

		abstract void onSet(boolean value);
	}

	Setting[] settings = {
			new SettingBool("smooth", R.string.set_smooth, true) {
				@Override
				void onSet(boolean value) {
					act.setSmooth(value);
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
		for (int i = 0; i < settings.length; ++i)
			settings[i].init(this);
	}

	void load() {
		for (int i = 0; i < settings.length; ++i)
			settings[i].load();
	}
}
