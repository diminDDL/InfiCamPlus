package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import be.ntmn.libinficam.InfiCam;

public class OverlayMuxer implements SurfaceTexture.OnFrameAvailableListener {
	private final SurfaceMuxer muxer;
	private SurfaceMuxer.OutputSurface inputOs;
	private final SurfaceMuxer.InputSurface inputIs;
	private final SurfaceMuxer.InputSurface overlaySurface;
	private int width, height;
	private final Rect rect = new Rect(0, 0, 1, 1); /* Where the image to be overlaid on is. */
	private final Overlay.Data data;
	private final Overlay overlay;

	public OverlayMuxer(Context ctx, Overlay.Data d) {
		muxer = new SurfaceMuxer(ctx);
		inputIs = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST);
		inputIs.getSurfaceTexture().setOnFrameAvailableListener(this);
		overlaySurface = new SurfaceMuxer.InputSurface(muxer, SurfaceMuxer.IMODE_NEAREST);
		overlay = new Overlay(ctx, overlaySurface);
		muxer.inputSurfaces.add(inputIs);
		muxer.inputSurfaces.add(overlaySurface);
		data = d;
	}

	public void init() { muxer.init(); }
	public void deinit() { muxer.deinit(); }

	public void setSize(int w, int h) {
		inputIs.getSurfaceTexture().setDefaultBufferSize(w, h);
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
		if (inputOs != null)
			inputOs.release();
		inputOs = null;
		if (im != null) {
			inputOs = new SurfaceMuxer.OutputSurface(im, inputIs.getSurface(), false);
			inputOs.setSize(width, height);
			im.outputSurfaces.add(inputOs);
		}
	}

	public void setOutputSurface(Surface surf) {
		while (muxer.outputSurfaces.size() > 0)
			muxer.outputSurfaces.get(0).release();
		if (surf != null) {
			SurfaceMuxer.OutputSurface os = new SurfaceMuxer.OutputSurface(muxer, surf, false);
			muxer.outputSurfaces.add(os);
			os.setSize(width, height);
		}
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		overlay.draw(data);
		muxer.onFrameAvailable(surfaceTexture);
	}

	public void setRect(Rect rect) {
		this.rect.set(rect);
		overlay.setRect(rect);
	}

	public void release() {
		muxer.release();
	}
}
