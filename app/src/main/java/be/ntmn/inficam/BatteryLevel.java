package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class BatteryLevel extends androidx.appcompat.widget.AppCompatTextView {
	private class BatteryDrawable extends Drawable {
		Drawable battery;
		int scale = 1, level = 0;

		BatteryDrawable(Drawable battery) {
			this.battery = battery;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			battery.setBounds(0, 0, BatteryLevel.this.getWidth(), BatteryLevel.this.getHeight());
			//battery.setLevel(50 * 10000 / 100);
			battery.draw(canvas);
		}

		@Override
		public void setAlpha(int i) { /* Ignored. */ }

		@Override
		public void setColorFilter(@Nullable ColorFilter colorFilter) { /* Ignored. */ }

		@Override
		public int getOpacity() {
			return PixelFormat.OPAQUE;
		}

		public void setLevel(int scale, int level) {
			this.scale = scale;
			this.level = level;
			invalidateSelf();
		}
	}

	private Context ctx;
	private BatteryDrawable drawable;

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
		this.ctx = ctx;
		Drawable battery = ContextCompat.getDrawable(ctx, R.drawable.ic_baseline_battery);
		drawable = new BatteryDrawable(battery);
		setBackgroundDrawable(drawable);
	}

	public void setLevel(int scale, int level) {
		drawable.setLevel(scale, level);
		setText(ctx.getString(R.string.batlevel, level * 100 / scale));
	}
}
