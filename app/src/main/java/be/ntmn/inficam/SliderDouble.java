package be.ntmn.inficam;

import static java.lang.Math.abs;
import static java.lang.Math.round;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.slider.RangeSlider;

import java.util.List;

/* RangeSlider is annoying as fuck, throwing exceptions if you don't baby it, I try to fix it and
 *   there is also fluff to make it work vertically too.
 */
public class SliderDouble extends RangeSlider {
	private float from = 0.0f, to = 1.0f;
	boolean invert = false, vertical = true;

	public SliderDouble(@NonNull Context context) {
		super(context);
		init();
	}

	public SliderDouble(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public SliderDouble(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		/* Halo won't draw when vertical if background is RippleDrawable and super() initializes
		 *   it to that. */
		setBackground(null);
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

	public void setVertical(boolean vertical) {
		this.vertical = vertical;
		requestLayout();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (vertical) {
			super.onMeasure(heightMeasureSpec, widthMeasureSpec);
			setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
		} else super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (vertical)
			super.onSizeChanged(h, w, oldh, oldw);
		else super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		canvas.save();
		if (vertical) {
			canvas.translate(getWidth(), getHeight());
			canvas.rotate(90);
			canvas.scale(-1, 1);
		}
		super.onDraw(canvas);
		canvas.restore();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (vertical)
			ev.setLocation(getHeight() - ev.getY() - 1, ev.getX());
		return super.dispatchTouchEvent(ev);
	}
}
