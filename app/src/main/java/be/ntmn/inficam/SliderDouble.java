package be.ntmn.inficam;

import static java.lang.Math.abs;
import static java.lang.Math.round;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.RangeSlider;

import java.util.List;

/* RangeSlider is annoying as fuck, throwing exceptions if you don't baby it, I try to fix it. */
public class SliderDouble extends RangeSlider {
	private float from = 0.0f, to = 1.0f;
	boolean invert;

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
		float step = super.getStepSize();
		float mto = super.getValueTo();
		for (int i = 0; i < values.length; ++i) {
			values[i] = abs(values[i] - from);
			values[i] = (step == 0) ? (values[i]) : (round(values[i] / step) * step);
			if (values[i] < 0.0f)
				values[i] = 0.0f;
			if (values[i] > mto)
				values[i] = mto;
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
		invert = to < from;
		float fto = abs((step == 0) ? (to - from) : (round((to - from) / step) * step));
		if (fto > 0.0f) {
			super.setValueFrom(0.0f);
			super.setValueTo(fto);
		}
	}

	/* We can't override superclass method, it uses it -_-. */
	public List<Float> getValuesCorrected() {
		List<Float> values = super.getValues();
		for (int i = 0; i < values.size(); ++i)
			values.set(i, (invert ? -values.get(i) : values.get(i)) + from);
		return values;
	}

	private boolean enabled = true;

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setPivotX(0);
		setPivotY(0);
		if (enabled) {
			//setTranslationX(getMeasuredHeight());
			//setTranslationY(getMeasuredWidth());
			//setTranslationX(getMeasuredHeight());
			//setTranslationY(getMeasuredWidth());
			//setTranslationX(getMeasuredHeight());
			//setRotation(0.0f);
			super.onMeasure(heightMeasureSpec, widthMeasureSpec);
			setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
			Log.e("TEST", "w = " + getMeasuredWidth() + " h = " + getMeasuredHeight());
		} else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setTranslationX(0);
			setRotation(0.0f);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(getHeight(), w, oldh, oldw);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		canvas.translate(getWidth(), 0);
		canvas.rotate(90);
		canvas.drawColor(Color.RED);
		super.onDraw(canvas);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (enabled)
			ev.setLocation(ev.getY(), ev.getX());
		return super.dispatchTouchEvent(ev);
	}
}
