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

	/* These sizes are in fractions of the total width of the bitmap drawn. */
	float smarker = 0.015f; /* Marker size. */
	float wmarker = 0.003f; /* How fat the markers are. */
	float toff = 0.03f; /* How far to put the text away from marker. */
	float tclearance = 0.005f; /* How far the text should stay away from screen edges. */
	float textsize = 0.038f;
	float woutline = 0.008f; /* Text outline thickness. */

	public Overlay(SurfaceMuxer.InputSurface is, int w, int h) {
		smarker *= w;
		toff *= w;
		tclearance *= w;
		textsize *= w;
		wmarker *= w;
		woutline *= w;
		surface = is.getSurface();
		surfaceTexture = is.getSurfaceTexture();
		width = w;
		height = h;
		is.getSurfaceTexture().setDefaultBufferSize(width, height);
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(wmarker); // TODO what size should the markers have?
		paint.setTextSize(textsize);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paintOutline = new Paint(paint);
		paintOutline.setStrokeWidth(wmarker * 3);
		paintTextOutline = new Paint(paint);
		paintTextOutline.setStrokeWidth(woutline);
		paintTextOutline.setColor(Color.BLACK);
		paintTextOutline.setStyle(Paint.Style.STROKE);
	}

	public void draw(InfiCam.FrameInfo fi, float[] temp) {
		Canvas cvs = surface.lockCanvas(null);
		cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		paint.setColor(Color.rgb(255, 255, 0)); // Yellow.
		drawTPoint(cvs, fi, fi.width / 2, fi.height / 2, fi.center);

		paint.setColor(Color.rgb(255, 64, 64)); // Red.
		drawTPoint(cvs, fi, fi.max_x, fi.max_y, fi.max);

		paint.setColor(Color.rgb(0, 127, 255)); // Blue.
		drawTPoint(cvs, fi, fi.min_x, fi.min_y, fi.min);

		surface.unlockCanvasAndPost(cvs);
	}

	void drawTPoint(Canvas cvs, InfiCam.FrameInfo fi, int tx, int ty, float temp) {
		float x = (tx + 0.5f) * width / fi.width; // TODO maybe we can just set scale for the entire canvas
		float y = (ty + 0.5f) * height / fi.height;
		cvs.drawLine(x - smarker, y, x + smarker, y, paintOutline);
		cvs.drawLine(x, y - smarker, x, y + smarker, paintOutline);
		cvs.drawLine(x - smarker, y, x + smarker, y, paint);
		cvs.drawLine(x, y - smarker, x, y + smarker, paint);

		@SuppressLint("DefaultLocale")
		String text = String.format("%.2f°C", temp);
		float offX = toff;
		float offY = -(paint.descent() + paint.ascent()) / 2.0f;
		if (paintTextOutline.measureText(text) + offX + tclearance < width - x) {
			paint.setTextAlign(Paint.Align.LEFT);
			paintTextOutline.setTextAlign(Paint.Align.LEFT);
		} else {
			offX = -offX;
			paint.setTextAlign(Paint.Align.RIGHT);
			paintTextOutline.setTextAlign(Paint.Align.RIGHT);
		}
		offY -= max(y + offY + paintTextOutline.descent() + tclearance - height, 0);
		offY -= min(y + offY + paintTextOutline.ascent() - tclearance, 0);
		cvs.drawText(text, x + offX, y + offY, paintTextOutline);
		cvs.drawText(text, x + offX, y + offY, paint);
	}
}
