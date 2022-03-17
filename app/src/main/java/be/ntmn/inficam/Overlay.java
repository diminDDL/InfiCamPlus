package be.ntmn.inficam;

import static java.lang.Float.NaN;
import static java.lang.Math.abs;
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
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;

import be.ntmn.libinficam.InfiCam;

public class Overlay {
	public static class Data {
		public InfiCam.FrameInfo fi;
		public float[] temp;
		public int[] palette;
		public float rangeMin = NaN, rangeMax = NaN;
		public boolean rotate = false, mirror = false, rotate90 = false; /* Set by Settings. */
		public boolean showMin = false; /* Set by SettingsTherm. */
		public boolean showMax = false;
		public boolean showCenter = false;
		public boolean showPalette = false;
	}

	private final SurfaceMuxer.InputSurface surface;
	private final Paint paint;
	private final Paint paintOutline;
	private final Paint paintTextOutline;
	private final Paint paintPalette;
	private final Drawable lock;
	private int width, height;
	private final Rect vRect = new Rect(), rectTgt = new Rect(); /* Do not alloc each frame! */
	int[] paletteCache_palette;
	Bitmap paletteCache_bitmap;
	int paletteCache_height;

	/* These sizes are in fractions of the total width of the bitmap drawn. */
	private final static float smarker = 0.015f; /* Marker size. */
	private final static  float wmarker = 0.003f; /* How fat the markers are. */
	private final static float toff = 0.03f; /* How far to put the text away from marker. */
	private final static float tclearance = 0.005f; /* How far the text should stay from edges. */
	private final static float textsize = 0.035f;
	private final static float woutline = 0.008f; /* Text outline thickness. */
	private final static float pwidth = 0.038f; /* Palette preview width. */
	private final static float pclearance = 0.016f;

	private final StringBuilder sb = new StringBuilder();

	public Overlay(Context ctx, SurfaceMuxer.InputSurface is) {
		surface = is;
		paint = new Paint();
		paintPalette = new Paint();
		paintPalette.setAntiAlias(false);
		paint.setAntiAlias(true);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paintOutline = new Paint(paint);
		paintOutline.setStyle(Paint.Style.STROKE);
		paintTextOutline = new Paint(paint);
		paintTextOutline.setColor(Color.BLACK);
		paintTextOutline.setStyle(Paint.Style.STROKE);
		lock = AppCompatResources.getDrawable(ctx, R.drawable.ic_baseline_lock_24_2);
	}

	public void setSize(int w, int h) {
		width = w;
		height = h;
		surface.getSurfaceTexture().setDefaultBufferSize(w, h);
		surface.setSize(w, h);
	}

	public void setRect(Rect rect) { /* Set the area of thermal view. */
		int w = rect.width();
		vRect.set(rect);
		paint.setStrokeWidth(wmarker * w);
		paint.setTextSize(textsize * w);
		paintOutline.setStrokeWidth(wmarker * w * 3);
		paintTextOutline.setStrokeWidth(woutline * w);
		paintTextOutline.setTextSize(textsize * w);
	}

	private void drawText(Canvas cvs, StringBuilder sb, float x, float y, boolean la, boolean ta) {
		float theight = (int) -(paint.descent() + paint.ascent());
		paint.setTextAlign(la ? Paint.Align.LEFT : Paint.Align.RIGHT);
		paintTextOutline.setTextAlign(la ? Paint.Align.LEFT : Paint.Align.RIGHT);
		cvs.drawText(sb, 0, sb.length(), x, y + (ta ? theight : 0), paintTextOutline);
		cvs.drawText(sb, 0, sb.length(), x, y + (ta ? theight : 0), paint);
	}

	@SuppressLint("DefaultLocale")
	public void draw(Data d) {
		Canvas cvs = surface.getSurface().lockCanvas(null);
		cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		if (d.showCenter) { // TODO this is off by a pixel and we should check the other points too
			paint.setColor(Color.rgb(255, 255, 0)); // Yellow.
			drawTPoint(cvs, d, d.fi.width / 2, d.fi.height / 2, d.fi.center);
		}

		if (d.showMin) {
			paint.setColor(Color.rgb(0, 127, 255)); // Blue.
			drawTPoint(cvs, d, d.fi.min_x, d.fi.min_y, d.fi.min);
		}

		if (d.showMax) {
			paint.setColor(Color.rgb(255, 64, 64)); // Red.
			drawTPoint(cvs, d, d.fi.max_x, d.fi.max_y, d.fi.max);
		}

		if (d.showPalette) { // TODO maybe we should draw the palette to a bitmap actually
			int clear = (int) (pclearance * vRect.width());
			int theight = (int) -(paint.descent() + paint.ascent());
			int isize = (int) (theight + woutline * vRect.width());
			int iclear = (int) (clear - (woutline * vRect.width()) / 2.0f);
			paint.setColor(Color.WHITE);
			if (width <= vRect.width()) {
				formatTemp(sb, Float.isNaN(d.rangeMax) ? d.fi.max : d.rangeMax);
				drawText(cvs, sb, vRect.right - clear, vRect.top + clear, false, true);
				formatTemp(sb, Float.isNaN(d.rangeMin) ? d.fi.min : d.rangeMin);
				drawText(cvs, sb, vRect.right - clear, vRect.bottom - clear, false, false);
				drawPalette(cvs,
						(int) (vRect.right - clear - pwidth * vRect.width()),
						vRect.top + theight + clear * 2,
						vRect.right - clear,
						vRect.bottom - theight - clear * 2,
						d.palette);
				if (!Float.isNaN(d.rangeMax)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vRect.right - clear - off - isize, vRect.top + iclear,
							vRect.right - clear - off, vRect.top + iclear + isize);
					lock.draw(cvs);
				}
				if (!Float.isNaN(d.rangeMin)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vRect.right - clear - off - isize, vRect.bottom - iclear - isize,
							vRect.right - clear - off, vRect.bottom - iclear);
					lock.draw(cvs);
				}
			} else {
				formatTemp(sb, Float.isNaN(d.rangeMax) ? d.fi.max : d.rangeMax);
				drawText(cvs, sb, vRect.right + clear, vRect.top + clear, true, true);
				formatTemp(sb, Float.isNaN(d.rangeMin) ? d.fi.min : d.rangeMin);
				drawText(cvs, sb, vRect.right + clear, vRect.bottom - clear, true, false);
				drawPalette(cvs,
						vRect.right + clear,
						vRect.top + theight + clear * 2,
						(int) (vRect.right + clear + pwidth * vRect.width()),
						vRect.bottom - theight - clear * 2,
						d.palette);
				if (!Float.isNaN(d.rangeMax)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vRect.right + clear + off, vRect.top + iclear,
							vRect.right + clear + off + isize, vRect.top + iclear + isize);
					lock.draw(cvs);
				}
				if (!Float.isNaN(d.rangeMin)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vRect.right + clear + off, vRect.bottom - iclear - isize,
							vRect.right + clear + off + isize, vRect.bottom - iclear);
					lock.draw(cvs);
				}
			}
		}

		surface.getSurface().unlockCanvasAndPost(cvs);
	}

	private void drawPalette(Canvas cvs, int x1, int y1, int x2, int y2, int[] palette) {
		if (y2 - y1 <= 0)
			return;
		if (paletteCache_palette != palette || paletteCache_height != y2 - y1) {
			int height = y2 - y1;
			paletteCache_bitmap = Bitmap.createBitmap(1, height, Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(paletteCache_bitmap);
			for (int i = 0; i < height; ++i) {
				int col = palette[palette.length - 1 - i * palette.length / height];
				paintPalette.setARGB(255, (col >> 0) & 0xFF, (col >> 8) & 0xFF, (col >> 16) & 0xFF);
				c.drawPoint(0, i, paintPalette);
			}
			paletteCache_palette = palette;
		}
		cvs.drawRect(x1, y1, x2, y2, paintOutline);
		rectTgt.set(x1, y1, x2, y2);
		/* We use the paintPalette for the bitmap to make doubly sure antialias is off, having it
		 *   on causes our 1px line to go transparent.
		 */
		cvs.drawBitmap(paletteCache_bitmap, null, rectTgt, paintPalette);
	}

	private void drawTPoint(Canvas cvs, Data d, int tx, int ty, float temp) {
		if (d.rotate90) {
			int tmp = tx;
			tx = d.fi.height - ty - 1;
			ty = tmp;
		}

		float xm = (tx + 0.5f) * vRect.width() / (d.rotate90 ? d.fi.height : d.fi.width);
		if (d.rotate)
			xm = vRect.width() - xm;
		if (d.mirror)
			xm = vRect.width() - xm;
		xm += vRect.left;

		float ym = (ty + 0.5f) * vRect.height() / (d.rotate90 ? d.fi.width : d.fi.height);
		if (d.rotate)
			ym = vRect.height() - ym;
		ym += vRect.top;

		float smarkerw = smarker * vRect.width();

		cvs.drawLine(xm - smarkerw, ym, xm + smarkerw, ym, paintOutline);
		cvs.drawLine(xm, ym - smarkerw, xm, ym + smarkerw, paintOutline);
		cvs.drawLine(xm - smarkerw, ym, xm + smarkerw, ym, paint);
		cvs.drawLine(xm, ym - smarkerw, xm, ym + smarkerw, paint);

		float offX = toff * vRect.width();
		float offY = -(paint.descent() + paint.ascent()) / 2.0f;
		float tclear = tclearance * vRect.width();
		boolean la = true;
		if (paintTextOutline.measureText(sb, 0, sb.length()) + offX + tclear > vRect.right - xm) {
			offX = -offX;
			la = false;
		}
		offY -= max(ym + offY + paintTextOutline.descent() + tclear - vRect.bottom, 0);
		offY -= min(ym + offY + paintTextOutline.ascent() - tclear - vRect.top, 0);

		formatTemp(sb, temp);
		drawText(cvs, sb, xm + offX, ym + offY, la, false);
	}

	private static void formatTemp(StringBuilder sb, float temp) {
		int t = abs(round(temp * 100.0f));
		sb.setLength(0);
		if (Float.isNaN(temp) || Float.isInfinite(temp)) {
			sb.append("NaN");
			return;
		}
		if (temp < 0)
			sb.append("-");
		sb.append(t / 100);
		sb.append(".");
		sb.append((t / 10) % 10);
		sb.append(t % 10);
		sb.append("°C");
	}
}
