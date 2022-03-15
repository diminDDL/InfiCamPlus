package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class OverlayMuxer implements SurfaceTexture.OnFrameAvailableListener {
	public SurfaceMuxer muxer; // TODO release
	private SurfaceMuxer.OutputSurface inputOs;
	private SurfaceMuxer.InputSurface inputIs;
	private SurfaceMuxer.InputSurface overlaySurface;
	private int width, height;

	public SurfaceMuxer.InputSurface inputSurface;
	public Overlay overlay;
	public InfiCam.FrameInfo lastFi;
	public float[] lastTemp;
	public int[] palette;
	public float rangeMin, rangeMax;
	public final Rect rect = new Rect(0, 0, 1, 1); /* Where the image to be overlaid on is. */
	// TODO set rect and set it for the overlay itself too

	public OverlayMuxer(Context ctx, int w, int h) {
		muxer = new SurfaceMuxer(ctx);
		inputSurface = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST) {
			/*@Override
			public void getRect(Rect r, int w, int h) { r.set(rect); }*/
		};
		inputSurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		inputSurface.getSurfaceTexture().setOnFrameAvailableListener(this);
		overlaySurface = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST);
		overlaySurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlay = new Overlay(ctx, overlaySurface, w, h);
		muxer.inputSurfaces.add(inputSurface);
		muxer.inputSurfaces.add(overlaySurface);
		width = w;
		height = h;
		overlay.setRect(0, 0, 1000, 1000);
	}

	public void setSize(int w, int h) {
		inputSurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlaySurface.getSurfaceTexture().setDefaultBufferSize(w, h);
		overlay.setSize(w, h);
		for (SurfaceMuxer.OutputSurface os : muxer.outputSurfaces)
			os.setSize(w, h);
		if (inputOs != null)
			inputOs.setSize(w, h);
		width = w;
		height = h;
	}

	public void attachInput(SurfaceMuxer im) {
		inputOs = new SurfaceMuxer.OutputSurface(im, inputSurface.getSurface(), false);
		inputOs.setSize(width, height);
		im.outputSurfaces.add(inputOs);
	}

	public void setOutputSurface(Surface surf) {
		SurfaceMuxer.OutputSurface os = new SurfaceMuxer.OutputSurface(muxer, surf, false);
		releaseOutputSurface();
		muxer.outputSurfaces.add(os);
		os.setSize(width, height);
	}

	public void releaseOutputSurface() {
		while (muxer.outputSurfaces.size() > 0)
			muxer.outputSurfaces.get(0).release();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		overlay.draw(lastFi, lastTemp, palette, rangeMin, rangeMax);
		muxer.onFrameAvailable(surfaceTexture);
	}

	public void release() {
		muxer.release();
	}
}
