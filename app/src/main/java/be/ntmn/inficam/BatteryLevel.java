package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

public class BatteryLevel extends View {
	private final static int batColor = Color.WHITE;
	private final static int textColor = Color.WHITE;
	private final static int fillBackColor = Color.rgb(150, 150, 150);
	private final static int emptyColor = Color.RED;
	private final static int fullColor = Color.GREEN;

	private final Paint paint = new Paint();
	private int scale = 1, level = 0;

	public BatteryLevel(Context context) {
		super(context);
		init(context);
	}

	public BatteryLevel(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public BatteryLevel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context ctx) {
		paint.setTypeface(Typeface.DEFAULT_BOLD);
		paint.setTextAlign(Paint.Align.CENTER);
	}

	public void setLevel(int scale, int level) {
		this.scale = scale;
		this.level = level;
		invalidate();
	}

	/* I tried hard to do this the "proper" way with XML drawables. I've concluded they are
	 *   complete and utter typhus infected garbage and we're far better off doing this.
	 */
	@Override
	public void draw(@NonNull Canvas canvas) {
		super.draw(canvas);
		int w = getWidth();
		int h = getHeight();
		paint.setAntiAlias(true);
		canvas.save();
		float batY = -5;
		canvas.translate(0, h / 2.0f);
		canvas.scale(w / 24.0f, w / 24.0f);
		paint.setColor(batColor);
		canvas.drawRoundRect(2, -5 + batY, 20, 5 + batY, 1, 1, paint);
		canvas.drawRect(19, -2 + batY, 22, 2 + batY, paint);
		paint.setColor(fillBackColor);
		canvas.drawRect(4, -3 + batY, 18, 3 + batY, paint);
		float lvl = (float) level / scale;
		paint.setColor(ColorUtils.blendARGB(emptyColor, fullColor, lvl));
		canvas.drawRect(4, -3 + batY, 4 + 14 * lvl, 3 + batY, paint);
		paint.setColor(textColor);
		paint.setTextSize(9.0f);
		canvas.drawText((level * 100 / scale) + "%", 12, 9, paint);

		canvas.restore();
	}
}
