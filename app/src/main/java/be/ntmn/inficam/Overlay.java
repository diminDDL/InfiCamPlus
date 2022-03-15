package be.ntmn.inficam;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.lang.Math.signum;

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
	private final SurfaceMuxer.InputSurface surface;
	private final Paint paint;
	private final Paint paintOutline;
	private final Paint paintTextOutline;
	private final Paint paintPalette;
	private final Drawable lock;

	private int width, height;
	private final Rect vSize = new Rect();
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
		surface = is;
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
		surface.getSurfaceTexture().setDefaultBufferSize(w, h);
		surface.setSize(w, h);
	}

	public void setRect(int x1, int y1, int x2, int y2) { /* Set the area of thermal view. */
		int w = x2 - x1;
		vSize.set(x1, y1, x2, y2);
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
	public void draw(InfiCam.FrameInfo fi, float[] temp, int[] palette, float rmin, float rmax) {
		Canvas cvs = surface.getSurface().lockCanvas(null);

		cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		if (showCenter) { // TODO this is off by a pixel and we should check the other points too
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
			int clear = (int) (pclearance * vSize.width());
			int theight = (int) -(paint.descent() + paint.ascent());
			int isize = (int) (theight + woutline * vSize.width());
			int iclear = (int) (clear - (woutline * vSize.width()) / 2.0f);
			paint.setColor(Color.WHITE);
			if (width <= vSize.width()) {
				formatTemp(sb, Float.isNaN(rmax) ? fi.max : rmax);
				drawText(cvs, sb, vSize.right - clear, vSize.top + clear, false, true);
				formatTemp(sb, Float.isNaN(rmin) ? fi.min : rmin);
				drawText(cvs, sb, vSize.right - clear, vSize.bottom - clear, false, false);
				drawPalette(cvs,
						(int) (vSize.right - clear - pwidth * vSize.width()),
						vSize.top + theight + clear * 2,
						vSize.right - clear,
						vSize.bottom - theight - clear * 2,
						palette);
				if (!Float.isNaN(rmax)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vSize.right - clear - off - isize, vSize.top + iclear,
							vSize.right - clear - off, vSize.top + iclear + isize);
					lock.draw(cvs);
				}
				if (!Float.isNaN(rmin)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vSize.right - clear - off - isize, vSize.bottom - iclear - isize,
							vSize.right - clear - off, vSize.bottom - iclear);
					lock.draw(cvs);
				}
			} else {
				formatTemp(sb, Float.isNaN(rmax) ? fi.max : rmax);
				drawText(cvs, sb, vSize.right + clear, vSize.top + clear, true, true);
				formatTemp(sb, Float.isNaN(rmin) ? fi.min : rmin);
				drawText(cvs, sb, vSize.right + clear, vSize.bottom - clear, true, false);
				drawPalette(cvs,
						vSize.right + clear,
						vSize.top + theight + clear * 2,
						(int) (vSize.right + clear + pwidth * vSize.width()),
						vSize.bottom - theight - clear * 2,
						palette);
				if (!Float.isNaN(rmax)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vSize.right + clear + off, vSize.top + iclear,
							vSize.right + clear + off + isize, vSize.top + iclear + isize);
					lock.draw(cvs);
				}
				if (!Float.isNaN(rmin)) {
					int off = (int) paintTextOutline.measureText(sb, 0, sb.length());
					lock.setBounds(vSize.right + clear + off, vSize.bottom - iclear - isize,
							vSize.right + clear + off + isize, vSize.bottom - iclear);
					lock.draw(cvs);
				}
			}
		}

		surface.getSurface().unlockCanvasAndPost(cvs);
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

	private float tpx(float p, InfiCam.FrameInfo fi) {
		p = (p + 0.5f) * vSize.width() / fi.width;
		if (rotate)
			p = vSize.width() - p;
		if (mirror)
			p = vSize.width() - p;
		return vSize.left + p;
	}

	private float tpy(float p, InfiCam.FrameInfo fi) {
		p = (p + 0.5f) * vSize.height() / fi.height;
		if (rotate)
			p = vSize.height() - p;
		return vSize.top + p;
	}

	private void drawTPoint(Canvas cvs, InfiCam.FrameInfo fi, int tx, int ty, float temp) {
		float xm = tpx(tx, fi);
		float ym = tpy(ty, fi);
		float smarkerw = smarker * vSize.width();

		cvs.drawLine(xm - smarkerw, ym, xm + smarkerw, ym, paintOutline);
		cvs.drawLine(xm, ym - smarkerw, xm, ym + smarkerw, paintOutline);
		cvs.drawLine(xm - smarkerw, ym, xm + smarkerw, ym, paint);
		cvs.drawLine(xm, ym - smarkerw, xm, ym + smarkerw, paint);

		float offX = toff * vSize.width();
		float offY = -(paint.descent() + paint.ascent()) / 2.0f;
		float tclear = tclearance * vSize.width();
		boolean la = true;
		if (paintTextOutline.measureText(sb, 0, sb.length()) + offX + tclear > vSize.right - xm) {
			offX = -offX;
			la = false;
		}
		offY -= max(ym + offY + paintTextOutline.descent() + tclear - vSize.bottom, 0);
		offY -= min(ym + offY + paintTextOutline.ascent() - tclear - vSize.top, 0);

		formatTemp(sb, temp);
		drawText(cvs, sb, xm + offX, ym + offY, la, false);
	}

	public void setRotate(boolean rotate) { this.rotate = rotate; }
	public void setMirror(boolean mirror) { this.mirror = mirror; }
	public void setShowCenter(boolean showCenter) { this.showCenter = showCenter; }
	public void setShowMax(boolean showMax) { this.showMax = showMax; }
	public void setShowMin(boolean showMin) { this.showMin = showMin; }
	public void setShowPalette(boolean showPalette) { this.showPalette = showPalette; }

	private static void formatTemp(StringBuilder sb, float temp) {
		int t = abs(round(temp * 100.0f));
		sb.setLength(0);
		if (Float.isNaN(temp)) {
			sb.append("Low");
			return;
		} else if (Float.isInfinite(temp)) {
			sb.append("High");
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
