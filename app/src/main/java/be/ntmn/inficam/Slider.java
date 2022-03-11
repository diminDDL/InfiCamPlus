package be.ntmn.inficam;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.SeekBar;

/* Subclass the SeekBar widget so it looks right when the minimum is below 0, add step increment
 *   option.
 */
public class Slider extends androidx.appcompat.widget.AppCompatSeekBar {
	final Rect rect = new Rect();
	final Paint paint = new Paint();
	final static int vExtent = 6 / 2; /* Half of the bar height. */
	final static int dotSize = 18; /* Size of the dot. */
	final static int dotSizePressed = 27;
	int colorBg, colorAccent;
	OnSeekBarChangeListener listener;
	int min = 0, max = 100, step = 1;

	public Slider(Context context) {
		super(context);
		init(context);
	}

	public Slider(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public Slider(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context ctx) {
		TypedValue typedValue = new TypedValue();
		Resources.Theme theme = ctx.getTheme();
		theme.resolveAttribute(android.R.attr.colorButtonNormal, typedValue, true);
		colorBg = typedValue.data;
		theme.resolveAttribute(android.R.attr.colorControlActivated, typedValue, true);
		colorAccent = typedValue.data;
		super.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if (listener == null)
					return;
				int p = i;
				if (i != min && i != max)
					p = i - i % step;
				if (p != i)
					setProgress(p);
				listener.onProgressChanged(seekBar, p, b);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				if (listener != null)
					listener.onStartTrackingTouch(seekBar);
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (listener != null)
					listener.onStopTrackingTouch(seekBar);
			}
		});
	}

	@Override
	public synchronized void setMin(int min) {
		this.min = min;
		super.setMin(min);
	}

	@Override
	public synchronized void setMax(int max) {
		this.max = max;
		super.setMax(max);
	}

	public void setStep(int step) {
		this.step = step;
		setKeyProgressIncrement(step);
	}

	@Override
	public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
		listener = l;
	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		int thumbX = getPaddingLeft() + getThumb().getBounds().left;
		int centerY = getHeight() / 2;
		int width = getWidth();
		int left = getPaddingLeft();
		int right = getPaddingRight();
		int zeroX = left - min * (width - left - right) / (getMax() - min);

		paint.setColor(colorBg);
		rect.set(left, centerY - vExtent, width - right, centerY + vExtent);
		canvas.drawRect(rect, paint);

		paint.setColor(colorAccent);
		rect.set(thumbX, centerY - vExtent, zeroX, centerY + vExtent);
		canvas.drawRect(rect, paint);
		canvas.drawCircle(thumbX, centerY, isPressed() ? dotSizePressed : dotSize, paint);
	}
}
