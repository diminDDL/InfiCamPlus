package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BatteryLevel extends View {
	private final static int batColor = Color.WHITE;
	private final static int textColor = Color.WHITE;
	private final static int fillBackColor = Color.GRAY;
	private final static int emptyColor = Color.rgb(220, 20, 20);
	private final static int midColor = Color.rgb(220, 150, 0);
	private final static int fullColor = Color.rgb(20, 220, 20);

	private final Paint paint = new Paint();
	private final Path chargePath = new Path();
	private int scale = 1, level = 0;
	private boolean charging = false;

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
		paint.setAntiAlias(true);
		paint.setTypeface(Typeface.DEFAULT_BOLD);
		paint.setTextAlign(Paint.Align.CENTER);
		paint.setStyle(Paint.Style.FILL);
		chargePath.moveTo(40, -10);
		chargePath.lineTo(95, -10);
		chargePath.lineTo(95, -30);
		chargePath.lineTo(170, 10);
		chargePath.lineTo(115, 10);
		chargePath.lineTo(115, 30);
		chargePath.close();
	}

	public void setLevel(int scale, int level, boolean charging) {
		this.scale = scale;
		this.level = level;
		this.charging = charging;
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
		float batY = -40;
		float lvl = (float) level / scale;
		canvas.save();
		canvas.translate(0, h / 2.0f);
		canvas.scale(w / 240.0f, w / 240.0f);
		paint.setColor(batColor);
		canvas.drawRoundRect(20, -50 + batY, 200, 50 + batY, 10, 10, paint);
		canvas.drawRect(190, -20 + batY, 220, 20 + batY, paint);
		paint.setColor(fillBackColor);
		canvas.drawRect(40, -30 + batY, 180, 30 + batY, paint);
		paint.setColor(Util.blendColor3(emptyColor, midColor, fullColor, lvl));
		canvas.drawRect(40, -30 + batY, 40 + 140 * lvl, 30 + batY, paint);
		paint.setColor(textColor);
		paint.setTextSize(90);
		canvas.drawText((level * 100 / scale) + "%", 120, 90, paint);
		if (charging) {
			paint.setColor(batColor);
			canvas.save();
			canvas.translate(5f, batY);
			canvas.drawPath(chargePath, paint);
			canvas.restore();
		}
		canvas.restore();
	}
}
