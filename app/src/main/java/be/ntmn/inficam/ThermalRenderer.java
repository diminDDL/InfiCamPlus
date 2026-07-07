package be.ntmn.inficam;

import static java.lang.Math.clamp;
import static java.lang.Math.max;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.Surface;

public class ThermalRenderer {

	private final int[] canvasDrawBuffer; /* Used to draw the palettized frame into our cavas. */
	private final Bitmap frameBitmap;
	private final BitmapShader barberShader;
	private final Matrix shaderMatrix = new Matrix();
	private final Rect dstRect = new Rect();
	private final int width;
	private final int height;
	private final int stripeWidth;
	private final int period;
	private final float speed;
	private float phase = 0f; //animation X offset
	private final Paint barberPaint = new Paint();
	public long lastPaletteNs = 0;
	public long lastLockCanvasNs = 0;
	public long lastUnlockCanvasNs = 0;

	ThermalRenderer(int width, int height){
		this.width = width;
		this.height = height;
		canvasDrawBuffer = new int[width * height];
		frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

		stripeWidth = max(1, max(width,height)/50);
		period = stripeWidth*2;
		speed = stripeWidth /5.0f;
		barberShader = buildBarberTile();
	}



	/*
	build a tile of size (2*stripeWidth,2*stripeWidth) with the barber-pole pattern
	 */
	private BitmapShader buildBarberTile() {
		Bitmap barberTile = Bitmap.createBitmap(period, period, Bitmap.Config.ARGB_8888);
		Canvas tileCanvas = new Canvas(barberTile);
		tileCanvas.drawColor(0xFFFFFFFF); //white background

		/*
		 *draw the red lines as a rectangle from top-left to top-right
		 *repeat it twice
		 *we start with the bottom left corner of the line touching the bottom left corner of the bitmap
		 */
		barberPaint.setColor(0xFFFF0000); //draw in red
		barberPaint.setStyle(Paint.Style.FILL);
		for (int offset = 0; offset <= period * 2; offset += period) {
			// A diagonal band of width STRIPE_WIDTH going from top-left to bottom-right
			Path band = new Path();
			band.moveTo(offset - period, 0);
			band.lineTo(offset - period + stripeWidth, 0);
			band.lineTo(offset + stripeWidth, period);
			band.lineTo(offset, period);
			band.close();
			tileCanvas.drawPath(band, barberPaint);
		}

		return new BitmapShader(barberTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
	}

	private void drawFrame(Canvas canvas, int viewWidth, int viewHeight) {
		phase = (phase + speed) % period;
		shaderMatrix.setTranslate(phase, 0);
		barberShader.setLocalMatrix(shaderMatrix);

		//draw tbe tiles
		barberPaint.setShader(barberShader);
		dstRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
		canvas.drawRect(dstRect, barberPaint);
		barberPaint.setShader(null); //important
	}

	private void applyPaletteMap(
			int[] paletteMap,
			float[] temp,
			float range_min,
			float range_max,
			float sensor_max
	) {
		for (int i = 0; i < width * height; i++) {
			/* clipping the range, or nearly maxing out the sensor
			   (95% => should catch everything without cutting into the range) */
			if(temp[i] > range_max || temp[i] > sensor_max*0.95){
				canvasDrawBuffer[i] = 0; //transparent, barber pattern will be seen through
			} else {
				float pos = clamp((temp[i] - range_min) / (range_max - range_min),0,1);

				int int_pos = (int)((pos*(Palette.paletteSize-1))+0.5f); //fast round()
				canvasDrawBuffer[i] = paletteMap[int_pos];
			}
		}
	}

	void renderTemperatures(Surface surface,
							int[] paletteMap,
							float[] temp,
							float range_min,
							float range_max,
							float sensor_max){
		long startNs = System.nanoTime();
		applyPaletteMap(paletteMap,temp,range_min,range_max,sensor_max);
		lastPaletteNs = System.nanoTime() - startNs;

		startNs = System.nanoTime();
		Canvas canvas = surface.lockCanvas(null);
		lastLockCanvasNs = System.nanoTime() - startNs;
		try {
			drawFrame(canvas,canvas.getWidth(),canvas.getHeight());
			frameBitmap.setPixels(canvasDrawBuffer, 0, width, 0, 0, width, height);
			dstRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
			canvas.drawBitmap(frameBitmap, null, dstRect, null);
		} finally {
			startNs = System.nanoTime();
			surface.unlockCanvasAndPost(canvas);
			lastUnlockCanvasNs = System.nanoTime() - startNs;
		}
	}

}
