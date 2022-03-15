package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class OverlayMuxer implements SurfaceTexture.OnFrameAvailableListener {
	public SurfaceMuxer muxer;
	private SurfaceMuxer.InputSurface overlaySurface;

	public SurfaceMuxer.InputSurface inputSurface;
	public Overlay overlay;
	public InfiCam.FrameInfo lastFi;
	public float[] lastTemp;
	public int[] palette;
	public float rangeMin, rangeMax;
	public final Rect rect = new Rect(0, 0, 1000, 1000); /* Where the image to be overlaid on is. */

	public OverlayMuxer(Context ctx, int w, int h) {
		muxer = new SurfaceMuxer(ctx);
		inputSurface = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST) {
			@Override
			public void getRect(Rect r, int w, int h) {
				r.set(rect);
			}
		};
		inputSurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlaySurface = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST);
		overlaySurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlay = new Overlay(ctx, overlaySurface, w, h);
		muxer.inputSurfaces.add(inputSurface);
		muxer.inputSurfaces.add(overlaySurface);
	}

	public void setSize(int w, int h) {
		inputSurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlaySurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlay.setSize(w, h);
	}

	public void setOutputSurface(Surface surf) {
		SurfaceMuxer.OutputSurface os = new SurfaceMuxer.OutputSurface(muxer, surf, false);
		releaseOutputSurface();
		muxer.outputSurfaces.add(os);
	}

	public void releaseOutputSurface() {
		for (SurfaceMuxer.OutputSurface os : muxer.outputSurfaces)
			os.release();
		muxer.outputSurfaces.clear();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		if (lastFi != null) { // TODO why is it called before lastFi set?
			overlay.draw(lastFi, lastTemp, palette, rangeMin, rangeMax);
			muxer.onFrameAvailable(surfaceTexture);
		}
	}
}
