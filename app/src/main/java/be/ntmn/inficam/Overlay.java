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
	Surface surface;
	SurfaceTexture surfaceTexture;
	Paint paint;
	Paint paintOutline;
	Paint paintTextOutline;
	int width, height;
	boolean rotate = false, mirror = false; /* Set by Settings. */
	boolean showMin = false; /* Set by SettingsTherm. */
	boolean showMax = false;
	boolean showCenter = false;

	/* These sizes are in fractions of the total width of the bitmap drawn. */
	float smarker = 0.015f; /* Marker size. */
	float wmarker = 0.003f; /* How fat the markers are. */
	float toff = 0.03f; /* How far to put the text away from marker. */
	float tclearance = 0.005f; /* How far the text should stay away from screen edges. */
	float textsize = 0.038f;
	float woutline = 0.008f; /* Text outline thickness. */

	public Overlay(SurfaceMuxer.InputSurface is, int w, int h) {
		surface = is.getSurface();
		surfaceTexture = is.getSurfaceTexture();
		paint = new Paint();
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

	public void draw(InfiCam.FrameInfo fi, float[] temp) {
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

		surface.unlockCanvasAndPost(cvs);
	}

	void drawTPoint(Canvas cvs, InfiCam.FrameInfo fi, int tx, int ty, float temp) {
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
}
