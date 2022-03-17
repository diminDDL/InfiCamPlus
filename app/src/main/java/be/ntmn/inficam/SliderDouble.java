package be.ntmn.inficam;

import static java.lang.Math.round;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.RangeSlider;

import java.util.List;

/* RangeSlider is annoying as fuck, throwing exceptions if you don't baby it, I try to fix it. */
public class SliderDouble extends RangeSlider {
	private float from = 0.0f, to = 1.0f;

	public SliderDouble(@NonNull Context context) {
		super(context);
	}

	public SliderDouble(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public SliderDouble(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void setValues(@NonNull Float... values) {
		float ofrom = super.getValueFrom();
		float oto = super.getValueTo();
		float step = super.getStepSize();
		float mfrom = from;
		if (to < from)
			mfrom = to;
		for (int i = 0; i < values.length; ++i) {
			values[i] -= mfrom;
			values[i] = (step == 0) ? values[i] : (round(values[i] / step) * step);
			if (values[i] < ofrom)
				values[i] = ofrom;
			if (values[i] > oto)
				values[i] = oto;
		}
		super.setValues(values);
	}

	@Override
	public void setValueFrom(float valueFrom) {
		from = valueFrom;
		updateFromTo();
	}

	@Override
	public void setValueTo(float valueTo) {
		to = valueTo;
		updateFromTo();
	}

	private void updateFromTo() {
		float step = getStepSize();
		float mfrom = from;
		float mto = to;
		if (to < from) {
			mfrom = to;
			mto = from;
		}
		float fto = mto - mfrom; //round((mto - mfrom) / step) * step;
		if (fto > 0.0f) {
			super.setValueFrom(0.0f);
			super.setValueTo(fto);
		}
	}

	/* We can't override superclass method, it uses it -_-. */
	public List<Float> getValuesCorrected() {
		List<Float> values = super.getValues();
		float mfrom = from;
		float mto = to;
		if (to < from) {
			mfrom = to;
			mto = from;
		}
		for (int i = 0; i < values.size(); ++i) {
			float val = values.get(i);
			if (from < to)
				val = mto - mfrom - val;
			val += mfrom;
			values.set(i, val);
		}
		return values;
	}
}
