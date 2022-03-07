package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class Overlay {
	Surface surface;
	Paint paint = new Paint();
	int width, height;

	public Overlay(SurfaceMuxer.InputSurface is, int w, int h) {
		surface = is.getSurface();
		width = w;
		height = h;
		is.getSurfaceTexture().setDefaultBufferSize(width, height);
		paint.setTextSize(45); // TODO font size how big?
	}

	public void draw(InfiCam.FrameInfo fi, float[] temp) {
		Canvas cvs = surface.lockCanvas(null);
		cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		paint.setColor(Color.YELLOW);
		drawTPoint(cvs, fi, fi.width / 2, fi.height / 2, fi.center);

		paint.setColor(Color.RED);
		drawTPoint(cvs, fi, fi.max_x, fi.max_y, fi.max);

		paint.setColor(Color.BLUE);
		drawTPoint(cvs, fi, fi.min_x, fi.min_y, fi.min);

		surface.unlockCanvasAndPost(cvs);
	}

	void drawTPoint(Canvas cvs, InfiCam.FrameInfo fi, int tx, int ty, float temp) {
		paint.setStrokeWidth(5); // TODO what size should the markers have?
		int x = tx * width / fi.width; // TODO maybe we can just set scale for the entire canvas
		int y = ty * height / fi.height;
		cvs.drawLine(x - 20, y, x + 20, y, paint); // TODO size also depends on this
		cvs.drawLine(x, y - 20, x, y + 20, paint);
		// TODO make sure the text is in frame, also the +25 here shouldn't be hardcoded
		cvs.drawText(formatTemp(temp), x + 25, y - (paint.descent() + paint.ascent()) / 2, paint);
	}

	@SuppressLint("DefaultLocale")
	String formatTemp(float temp) {
		return String.format("%.2f°C", temp);
	}
}
