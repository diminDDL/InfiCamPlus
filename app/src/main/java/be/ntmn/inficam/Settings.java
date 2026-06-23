package be.ntmn.inficam;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log;
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
    int tempUnit = Util.TEMPUNIT_CELSIUS;

    protected String SP_NAME;
    protected int ui_name;
    protected Setting[] settings;

    public abstract static class Setting {

        String name;
        int res;

        protected Setting[] settings;

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
        private boolean not_user = false;

        SettingBool(String name, int res, boolean def) {
            super(name, res);
            this.def = def;
        }

        @Override
        void init(Settings set) {
            box = new CheckBox(getContext());
            box.setText(res);
            box.setLayoutParams(
                new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            );
            box.setOnCheckedChangeListener((view, b) -> {
                ed.putBoolean(name, b);
                ed.commit();
                onSet(b);
                if(!not_user){ set.handleChange(); }
            });
            box.setVisibility(VISIBLE);
            set.addView(box);
        }

        void setTo(boolean value) {
            not_user = true;
            box.setChecked(value);
            not_user = false;
            onSet(value);
        }

        @Override
        void load() {
            boolean value = sp.getBoolean(name, def);
            setTo(value);
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
        protected int[] items;
        protected int current;

        boolean not_user = false; //to ignore setTo() not triggered by the user

        SettingRadio(String name, int res, int def, int[] items) {
            super(name, res);
            this.def =
                def; /* RadioGroup indexes from 1, wtf... Ah! Because our TextView xD. */
            this.items = items;
        }

        @Override
        void init(Settings set) {
            rg = new RadioGroup(getContext());
            TextView title = new TextView(getContext());
            title.setText(res);
            rg.addView(title);
            rg.setLayoutParams(
                new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            );
            rg.setOnCheckedChangeListener((view, id) -> {
                int i = rg.indexOfChild(rg.findViewById(id));
                ed.putInt(name, i - 1);
                ed.commit();
                current = i - 1;
                onSet(i - 1);
                if(!not_user){ set.handleChange(); }
            });
            for (int item : items) {
                RadioButton rb = new RadioButton(getContext());
                rb.setText(item);
                rb.setLayoutParams(
                    new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                );
                rg.addView(rb);
            }
            rg.setVisibility(VISIBLE);
            set.addView(rg);
        }

        void setTo(int value) {
            not_user = true;
            try {
                ((RadioButton) rg.getChildAt(value + 1)).setChecked(true);
            } catch (Exception e) {
                value = def;
            }
            not_user = false;
            onSet(value);
        }

        @Override
        void load() {
            int value = sp.getInt(name, def);
            setTo(value);
        }

        @Override
        void setDefault() {
            ed.putInt(name, def);
            ed.commit();
            load();
        }

        public int get() {
            for (int i = 0 ; i < rg.getChildCount() ; i++){
                try {
                    if(((RadioButton) rg.getChildAt(i + 1)).isChecked()){
                        return i;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return -1;
        }

        public int[] getItems() {return items;}

        abstract void onSet(int i);
    }

//TODO: Do we really need to duplicate SettingRadio to allow for dynamically generated item names ?
    public abstract class SettingRadioDynamic extends Setting {

        private final int def;
        private RadioGroup rg;
        protected String[] items;
        protected int current;

        boolean not_user = false; //to ignore setTo() not triggered by the user

        SettingRadioDynamic(String name, int res, int def, String[] items) {
            super(name, res);
            this.def =
                    def; /* RadioGroup indexes from 1, wtf... Ah! Because our TextView xD. */
            this.items = items;
        }

        @Override
        void init(Settings set) {
            rg = new RadioGroup(getContext());
            TextView title = new TextView(getContext());
            title.setText(res);
            rg.addView(title);
            rg.setLayoutParams(
                    new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            rg.setOnCheckedChangeListener((view, id) -> {
                int i = rg.indexOfChild(rg.findViewById(id));
                ed.putInt(name, i - 1);
                ed.commit();
                current = i - 1;
                onSet(i - 1);
                if(!not_user){ set.handleChange(); }
            });
            for (String item : items) {
                RadioButton rb = new RadioButton(getContext());
                rb.setText(item);
                rb.setLayoutParams(
                        new LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
                rg.addView(rb);
            }
            rg.setVisibility(VISIBLE);
            set.addView(rg);
        }

        void setTo(int value) {
            not_user = true;
            try {
                ((RadioButton) rg.getChildAt(value + 1)).setChecked(true);
            } catch (Exception e) {
                value = def;
            }
            not_user = false;
            onSet(value);
        }

        @Override
        void load() {
            int value = sp.getInt(name, def);
            setTo(value);
        }

        @Override
        void setDefault() {
            ed.putInt(name, def);
            ed.commit();
            load();
        }

        public int get() {
            for (int i = 0 ; i < rg.getChildCount() ; i++){
                try {
                    if(((RadioButton) rg.getChildAt(i + 1)).isChecked()){
                        return i;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return -1;
        }
        public String[] getItems() {return items;}

        abstract void onSet(int i);
    }

    public abstract class SettingSlider extends Setting {

        private final int def, min, max, step;

        protected int value;

        private Slider slider;
        TextView title;

        SettingSlider(
            String name,
            int res,
            int def,
            int min,
            int max,
            int step
        ) {
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
            slider.setLayoutParams(
                new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            );
            slider.setMin(min);
            slider.setMax(max);
            slider.setStep(step);
            slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                        SeekBar seekBar,
                        int i,
                        boolean b
                    ) {
                        ed.putInt(name, i);
                        ed.commit();
                        setText(i);
                        onSet(i);
                        value = i;
                        if(b){ //if from user only
                            set.handleChange();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        /* Empty. */
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        /* Empty. */
                    }
                }
            );
            slider.setVisibility(VISIBLE);
            set.addView(slider);
        }

        void setTo(int value) {
            slider.setProgress(value);
            setText(value);
            onSet(value);
        }

        @Override
        void load() {
            int value = sp.getInt(name, def);
            setTo(value);
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

        SettingSliderInt(
            String name,
            int res,
            int def,
            int min,
            int max,
            int step
        ) {
            super(name, res, def, min, max, step);
        }

        float get() { return (float) value;}

        @Override
        void setText(int i) {
            title.setText(getContext().getString(res, i));
        }
    }

    public abstract class SettingSliderFloat extends SettingSlider {

        final int div;

        SettingSliderFloat(
            String name,
            int res,
            int def,
            int min,
            int max,
            int step,
            int div
        ) {
            super(name, res, def, min, max, step);
            this.div = div;
        }

        @Override
        void setText(int i) {
            title.setText(getContext().getString(res,  (float)i / div));
        }

        void setTo(float value) {
            setTo((int) (value * div));
        }

        @Override
        void onSet(int i) {
            onSet((float) i / div);
        }

        float get() { return (float) value /div;}

        abstract void onSet(float f);
    }

    public abstract class SettingSliderTemp extends SettingSliderFloat {

        SettingSliderTemp(String name, int res, int def, int min, int max) {
            super(name, res, def, min, max, 5, 10);
        }

        @Override
        void setText(int i) {
            title.setText(
                getContext().getString(
                    res,
                    Util.formatTemp((float) i / div, tempUnit)
                )
            );
        }
    }

    public abstract class SettingButton extends Setting {

        SettingButton(int res) {
            super(null, res);
        }

        protected Settings set;
        @Override
        void init(Settings set) {
            this.set = set;
            Button button = new Button(getContext());
            button.setText(res);
            button.setLayoutParams(
                new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            );
            button.setOnClickListener(view -> onPress());
            button.setVisibility(VISIBLE);
            set.addView(button);
        }

        @Override
        void load() {
            /* Empty. */
        }

        @Override
        void setDefault() {
            /* Empty. */
        }

        abstract void onPress();
    }

    final SettingButton settingDefaults = new SettingButton(
        R.string.set_defaults
    ) {
        @Override
        void onPress() {
            setDefaults();
            this.set.handleChange();
        }
    };

    public Settings(Context context, String SP_NAME, int ui_name) {
        super(context);
        this.SP_NAME = SP_NAME;
        this.ui_name = ui_name;
    }

    public Settings(
        Context context,
        String SP_NAME,
        int ui_name,
        @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        this.SP_NAME = SP_NAME;
        this.ui_name = ui_name;
    }

    public Settings(
        Context context,
        String SP_NAME,
        int ui_name,
        @Nullable AttributeSet attrs,
        int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        this.SP_NAME = SP_NAME;
        this.ui_name = ui_name;
    }

    public void init(MainActivity act) {
        this.act = act;
        sp = act.getSharedPreferences(getSPName(), MODE_PRIVATE);
        ed = sp.edit();
        removeAllViews();
        Setting[] settings = getSettings();
        for (Setting setting : settings) setting.init(this);
    }

    public void load() {
        Setting[] settings = getSettings();
        for (Setting setting : settings){
            Log.d("InfiCam","Loading setting : \""+setting.name+"\"");
            setting.load();
        }
        handleChange();
    }

    public void setDefaults() {
        Setting[] settings = getSettings();
        for (Setting setting : settings) setting.setDefault();
    }

    public void setTempUnit(int unit) {
        if (unit != tempUnit) {
            /* For when this is triggered from a setting. */
            tempUnit = unit;
            load();
        }
    }

    Setting getSetting(String name) {
        for (Setting setting : settings) {
            if(setting.name == null){ continue; }
            if (setting.name.equals(name)) {
                return setting;
            }
        }
        throw new IllegalArgumentException("No setting named " + name);
    }

    public Setting[] getSettings() {
        return settings;
    }

    public String getSPName() {
        /* Name for "shared preferences file". */
        return SP_NAME;
    }

    /*
        Called ONCE per event of one or more settings being changed (slider moved, multiple settings being loaded)
     */
    public void handleChange(){
       /* no-op by default */
    }


    public int getName() {
        return ui_name;
    }
}
