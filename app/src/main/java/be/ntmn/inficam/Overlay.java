package be.ntmn.inficam;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class Overlay {
	private final Surface surface;
	private final SurfaceTexture surfaceTexture;
	private final Paint paint;
	private final Paint paintOutline;
	private final Paint paintTextOutline;
	private final Paint paintPalette;
	private int width, height;
	private boolean rotate = false, mirror = false; /* Set by Settings. */
	private boolean showMin = false; /* Set by SettingsTherm. */
	private boolean showMax = false;
	private boolean showCenter = false;

	/* These sizes are in fractions of the total width of the bitmap drawn. */
	private final float smarker = 0.015f; /* Marker size. */
	private final float wmarker = 0.003f; /* How fat the markers are. */
	private final float toff = 0.03f; /* How far to put the text away from marker. */
	private final float tclearance = 0.005f; /* How far the text should stay away from edges. */
	private final float textsize = 0.038f;
	private final float woutline = 0.008f; /* Text outline thickness. */

	public Overlay(SurfaceMuxer.InputSurface is, int w, int h) {
		surface = is.getSurface();
		surfaceTexture = is.getSurfaceTexture();
		paint = new Paint();
		paintPalette = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paintOutline = new Paint(paint);
		paintTextOutline = new Paint(paint);
		paintTextOutline.setColor(Color.BLACK);
		paintTextOutline.setStyle(Paint.Style.STROKE);
		setSize(w, h);
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

	public void draw(InfiCam.FrameInfo fi, float[] temp, int[] palette) {
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

		/*cvs.drawRect(cvs.getWidth() - 64, 36, cvs.getWidth() - 16, cvs.getHeight() - 36, paintOutline);
		drawPalette(cvs, cvs.getWidth() - 60, 40, 40, cvs.getHeight() - 80, palette);*/
		surface.unlockCanvasAndPost(cvs);
	}

	private void drawPalette(Canvas cvs, int x, int y, int w, int h, int[] palette) {
		for (int i = 0; i < h; ++i) {
			int col = palette[palette.length - 1 - i * palette.length / h];
			paintPalette.setARGB(255, (col >> 0) & 0xFF, (col >> 8) & 0xFF, (col >> 16) & 0xFF);
			cvs.drawLine(x, y + i, x + w, y + i, paintPalette);
		}
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

		@SuppressLint("DefaultLocale")
		String text = String.format("%.2f°C", temp);
		float offX = toff * width;
		float offY = -(paint.descent() + paint.ascent()) / 2.0f;
		if (paintTextOutline.measureText(text) + offX + tclearance * width < width - x) {
			paint.setTextAlign(Paint.Align.LEFT);
			paintTextOutline.setTextAlign(Paint.Align.LEFT);
		} else {
			offX = -offX;
			paint.setTextAlign(Paint.Align.RIGHT);
			paintTextOutline.setTextAlign(Paint.Align.RIGHT);
		}
		offY -= max(y + offY + paintTextOutline.descent() + tclearance * width - height, 0);
		offY -= min(y + offY + paintTextOutline.ascent() - tclearance * width, 0);
		cvs.drawText(text, x + offX, y + offY, paintTextOutline);
		cvs.drawText(text, x + offX, y + offY, paint);
	}

	public void setRotate(boolean rotate) { this.rotate = rotate; }
	public void setMirror(boolean mirror) { this.mirror = mirror; }
	public void setShowCenter(boolean showCenter) { this.showCenter = showCenter; }
	public void setShowMax(boolean showMax) { this.showMax = showMax; }
	public void setShowMin(boolean showMin) { this.showMin = showMin; }
}
