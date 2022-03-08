package be.ntmn.inficam;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class Overlay {
	Surface surface;
	Paint paint;
	Paint paintOutline;
	Paint paintTextOutline;
	int width, height;

	float smarker = 20; /* Marker size. */
	float toff = 25; /* How far to put the text away from marker. */
	float tclearance = 5; /* How far the text should stay away from screen edges. */

	public Overlay(SurfaceMuxer.InputSurface is, int w, int h) {
		surface = is.getSurface();
		width = w;
		height = h;
		is.getSurfaceTexture().setDefaultBufferSize(width, height);
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(3); // TODO what size should the markers have?
		paint.setTextSize(45); // TODO font size how big?
		paint.setStrokeCap(Paint.Cap.ROUND);
		paintOutline = new Paint(paint);
		paintOutline.setStrokeWidth(6);
		paintTextOutline = new Paint(paint);
		paintTextOutline.setStrokeWidth(4);
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
		int x = tx * width / fi.width; // TODO maybe we can just set scale for the entire canvas
		int y = ty * height / fi.height;
		cvs.drawLine(x - smarker, y, x + smarker, y, paintOutline);
		cvs.drawLine(x, y - smarker, x, y + smarker, paintOutline);
		cvs.drawLine(x - smarker, y, x + smarker, y, paint);
		cvs.drawLine(x, y - smarker, x, y + smarker, paint);

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
