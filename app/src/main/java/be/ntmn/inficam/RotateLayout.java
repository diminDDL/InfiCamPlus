package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RotateLayout extends FrameLayout {
	private boolean enabled = true;

	public RotateLayout(@NonNull Context context) {
		super(context);
		setEnabled(true);
	}

	public RotateLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		setEnabled(true);
	}

	public RotateLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setEnabled(true);
	}

	public void setEnabled(boolean value) {
		enabled = value;
		requestLayout();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setPivotX(0);
		setPivotY(0);
		if (enabled) {
			super.onMeasure(heightMeasureSpec, widthMeasureSpec);
			setTranslationX(getMeasuredHeight());
			setRotation(90.0f);
		} else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setTranslationX(0);
			setRotation(0.0f);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (enabled) {
			/* We manually flip X because rotating -90/270 gives me clipping issues. */
			canvas.translate(getWidth(), 0);
			canvas.scale(-1, 1);
		}
		super.onDraw(canvas);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (enabled)
			ev.setLocation(getWidth() - ev.getX() - 1, ev.getY());
		return super.dispatchTouchEvent(ev);
	}
}
