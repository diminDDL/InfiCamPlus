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

public class BatteryLevel extends View {
	private final static int batColor = Color.WHITE;
	private final static int textColor = Color.BLACK;
	private final static int emptyColor = Color.rgb(150, 150, 150);
	private final static int fullColor = Color.rgb(20, 200, 20);

	private final Paint paint = new Paint();
	private int scale = 1, level = 0;

	public BatteryLevel(Context context) { super(context); }
	public BatteryLevel(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
	public BatteryLevel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
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
		canvas.translate(0, h / 2.0f);
		canvas.scale(w / 24.0f, w / 24.0f);
		paint.setColor(batColor);
		canvas.drawRoundRect(2, -5, 20, 5, 1, 1, paint);
		canvas.drawRect(19, -2, 22, 2, paint);
		paint.setColor(emptyColor);
		canvas.drawRect(4, -3, 18, 3, paint);
		paint.setColor(fullColor);
		float lvl = (float) level / scale;
		canvas.drawRect(4, -3, 4 + 14 * lvl, 3, paint);
		paint.setColor(textColor);
		paint.setTextSize(5.5f);
		paint.setFakeBoldText(true);
		paint.setTypeface(Typeface.DEFAULT_BOLD);
		paint.setTextAlign(Paint.Align.CENTER);
		canvas.drawText((level * 100 / scale) + "%", 4 + 7, 2, paint);
		canvas.restore();
	}
}
