package be.ntmn.inficam;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.SeekBar;

/* Subclass the SeekBar widget so it looks right when the minimum is below 0, add step increment
 *   option.
 */
public class Slider extends androidx.appcompat.widget.AppCompatSeekBar {
	private final Rect rect = new Rect();
	private final Paint paint = new Paint();
	private int colorBg, colorAccent;
	private OnSeekBarChangeListener listener;
	private int min = 0, max = 100, step = 1;

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
		TypedArray ta = ctx.obtainStyledAttributes(new int[] {
				android.R.attr.colorControlHighlight,
				android.R.attr.colorControlActivated
		});
		colorBg = ta.getColor(0, Color.RED);
		colorAccent = ta.getColor(1, Color.RED);
		ta.recycle();
		paint.setAntiAlias(true);
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
	public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) { listener = l; }

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		int thumbX = getPaddingLeft() + getThumb().getBounds().left;
		int width = getWidth();
		int height = getHeight();
		int left = getPaddingLeft();
		int right = getPaddingRight();
		int zeroX = left - min * (width - left - right) / (getMax() - min);
		float dotSizePressed = getHeight() / 2.0f;
		float dotSize = dotSizePressed * 2 / 3;
		int barTop = height * 4 / 9;
		int barBot = height - barTop;

		paint.setColor(colorBg);
		rect.set(left, barTop, width - right, barBot);
		canvas.drawRect(rect, paint);

		paint.setColor(colorAccent);
		rect.set(thumbX, barTop, zeroX, barBot);
		canvas.drawRect(rect, paint);
		canvas.drawCircle(thumbX, height / 2.0f, isPressed() ? dotSizePressed : dotSize, paint);
	}
}
