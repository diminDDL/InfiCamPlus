package be.ntmn.inficam;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.view.Surface;

import androidx.appcompat.content.res.AppCompatResources;

import be.ntmn.libinficam.InfiCam;

public class Overlay {
	private final Surface surface;
	private final SurfaceTexture surfaceTexture;
	private final Paint paint;
	private final Paint paintOutline;
	private final Paint paintTextOutline;
	private final Paint paintPalette;
	private final Drawable lock;

	private int width, height;
	private boolean rotate = false, mirror = false; /* Set by Settings. */
	private boolean showMin = false; /* Set by SettingsTherm. */
	private boolean showMax = false;
	private boolean showCenter = false;
	private boolean showPalette = false;

	/* These sizes are in fractions of the total width of the bitmap drawn. */
	private final static float smarker = 0.015f; /* Marker size. */
	private final static  float wmarker = 0.003f; /* How fat the markers are. */
	private final static float toff = 0.03f; /* How far to put the text away from marker. */
	private final static float tclearance = 0.005f; /* How far the text should stay from edges. */
	private final static float textsize = 0.038f;
	private final static float woutline = 0.008f; /* Text outline thickness. */
	private final static float pwidth = 0.038f; /* Palette preview width. */
	private final static float pclearance = 0.016f;

	private final StringBuilder sb = new StringBuilder();

	private static class PaletteCache {
		Bitmap bitmap;
		int[] palette;
		Rect rectSrc = new Rect(), rectTgt = new Rect(); /* Don't allocate each frame eh :). */
	}

	private final PaletteCache paletteCache = new PaletteCache();

	public Overlay(Context ctx, SurfaceMuxer.InputSurface is, int w, int h) {
		surface = is.getSurface();
		surfaceTexture = is.getSurfaceTexture();
		paint = new Paint();
		paintPalette = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paintOutline = new Paint(paint);
		paintOutline.setStyle(Paint.Style.STROKE);
		paintTextOutline = new Paint(paint);
		paintTextOutline.setColor(Color.BLACK);
		paintTextOutline.setStyle(Paint.Style.STROKE);
		setSize(w, h);
		lock = AppCompatResources.getDrawable(ctx, R.drawable.ic_baseline_lock_24_2);
	}

	public void setSize(int w, int h) {
		width = w;
		height = h;
		surfaceTexture.setDefaultBufferSize(w, h);
		paint.setStrokeWidth(wmarker * w);
		paint.setTextSize(textsize * w);
		paintOutline.setStrokeWidth(wmarker * w * 3);
		paintTextOutline.setStrokeWidth(woutline * w);
		paintTextOutline.setTextSize(textsize * w);
	}

	@SuppressLint("DefaultLocale")
	public void draw(InfiCam.FrameInfo fi, float[] temp, int[] palette, float rmin, float rmax) {
		Canvas cvs = surface.lockCanvas(null);

		cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		if (showCenter) {
			paint.setColor(Color.rgb(255, 255, 0)); // Yellow.
			drawTPoint(cvs, fi, fi.width / 2, fi.height / 2, fi.center);
		}

		if (showMin) {
			paint.setColor(Color.rgb(0, 127, 255)); // Blue.
			drawTPoint(cvs, fi, fi.min_x, fi.min_y, fi.min);
		}

		if (showMax) {
			paint.setColor(Color.rgb(255, 64, 64)); // Red.
			drawTPoint(cvs, fi, fi.max_x, fi.max_y, fi.max);
		}

		if (showPalette) { // TODO maybe we should draw the palette to a bitmap actually
			int clear = (int) (pclearance * width);
			int theight = (int) -(paint.descent() + paint.ascent());
			int isize = (int) (theight + woutline * width);
			int iclear = (int) (clear - (woutline * width) / 2.0f);
			paintTextOutline.setTextAlign(Paint.Align.RIGHT);
			paint.setTextAlign(Paint.Align.RIGHT);
			paint.setColor(Color.WHITE);
			formatTemp(sb, Float.isNaN(rmax) ? fi.max : rmax);
			if (!Float.isNaN(rmax)) {
				int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
				lock.setBounds(width - clear - off - isize, iclear,
						width - clear - off, iclear + isize);
				lock.draw(cvs);
			}
			if (!Float.isNaN(rmin)) {
				int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
				lock.setBounds(width - clear - off - isize, height - iclear - isize,
						width - clear - off, height - iclear);
				lock.draw(cvs);
			}
			cvs.drawText(sb, 0, sb.length(), width - clear, theight + clear, paintTextOutline);
			cvs.drawText(sb, 0, sb.length(), width - clear, theight + clear, paint);
			formatTemp(sb, Float.isNaN(rmin) ? fi.min : rmin);
			cvs.drawText(sb, 0, sb.length(), width - clear, height - clear, paintTextOutline);
			cvs.drawText(sb, 0, sb.length(), width - clear, height - clear, paint);
			drawPalette(cvs, (int) (width - clear - pwidth * width), (int) (theight + clear * 2),
					(int) (width - clear), (int) (height - theight - clear * 2), palette);
		}

		surface.unlockCanvasAndPost(cvs);
	}

	private void drawPalette(Canvas cvs, int x1, int y1, int x2, int y2, int[] palette) {
		if (y2 - y1 <= 0)
			return;
		if (paletteCache.palette != palette || paletteCache.rectSrc.bottom != y2 - y1) {
			int height = y2 - y1;
			paletteCache.bitmap = Bitmap.createBitmap(1, height, Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(paletteCache.bitmap);
			for (int i = 0; i < height; ++i) {
				int col = palette[palette.length - 1 - i * palette.length / height];
				paintPalette.setARGB(255, (col >> 0) & 0xFF, (col >> 8) & 0xFF, (col >> 16) & 0xFF);
				c.drawPoint(0, i, paintPalette);
			}
			paletteCache.palette = palette;
			paletteCache.rectSrc.set(0, 0, 1, height);
		}
		cvs.drawRect(x1, y1, x2, y2, paintOutline);
		paletteCache.rectTgt.set(x1, y1, x2, y2);
		cvs.drawBitmap(paletteCache.bitmap, paletteCache.rectSrc, paletteCache.rectTgt, paint);
	}

	private void drawTPoint(Canvas cvs, InfiCam.FrameInfo fi, int tx, int ty, float temp) {
		float x = (tx + 0.5f) * width / fi.width; // TODO maybe we can just set scale for the entire canvas
		float y = (ty + 0.5f) * height / fi.height;

		if (rotate) {
			x = width - x;
			y = height - y;
		}
		if (mirror) {
			x = width - x;
		}

		float smarkerw = smarker * width;
		cvs.drawLine(x - smarkerw, y, x + smarkerw, y, paintOutline);
		cvs.drawLine(x, y - smarkerw, x, y + smarkerw, paintOutline);
		cvs.drawLine(x - smarkerw, y, x + smarkerw, y, paint);
		cvs.drawLine(x, y - smarkerw, x, y + smarkerw, paint);

		float offX = toff * width;
		float offY = -(paint.descent() + paint.ascent()) / 2.0f;
		formatTemp(sb, temp);
		if (paintTextOutline.measureText(sb, 0, sb.length()) + offX + tclearance * width <
				width - x) {
			paint.setTextAlign(Paint.Align.LEFT);
			paintTextOutline.setTextAlign(Paint.Align.LEFT);
		} else {
			offX = -offX;
			paint.setTextAlign(Paint.Align.RIGHT);
			paintTextOutline.setTextAlign(Paint.Align.RIGHT);
		}
		offY -= max(y + offY + paintTextOutline.descent() + tclearance * width - height, 0);
		offY -= min(y + offY + paintTextOutline.ascent() - tclearance * width, 0);
		cvs.drawText(sb, 0, sb.length(), x + offX, y + offY, paintTextOutline);
		cvs.drawText(sb, 0, sb.length(), x + offX, y + offY, paint);
	}

	public void setRotate(boolean rotate) { this.rotate = rotate; }
	public void setMirror(boolean mirror) { this.mirror = mirror; }
	public void setShowCenter(boolean showCenter) { this.showCenter = showCenter; }
	public void setShowMax(boolean showMax) { this.showMax = showMax; }
	public void setShowMin(boolean showMin) { this.showMin = showMin; }
	public void setShowPalette(boolean showPalette) { this.showPalette = showPalette; }

	private static void formatTemp(StringBuilder sb, float temp) {
		int t = round(temp * 100.0f);
		sb.setLength(0);
		sb.append(t / 100);
		sb.append(".");
		sb.append((t / 10) % 10);
		sb.append(t % 10);
		sb.append("°C");
	}
}
