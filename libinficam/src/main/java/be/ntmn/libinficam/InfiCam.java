package be.ntmn.libinficam;

import android.view.Surface;

public class InfiCam {
	/* We start with a bit of fluff to make JNI work. */
	private final long instance;
	private FrameCallback userFrameCallback = null;

	static {
		System.loadLibrary("usb1.0");
		System.loadLibrary("uvc");
		System.loadLibrary("InfiCam");
	}

	private native static long nativeNew(InfiCam self);
	private native static void nativeDelete(long ptr);

	public InfiCam() {
		if ((instance = nativeNew(this)) == 0)
			throw new OutOfMemoryError();
	}

	public void release() { nativeDelete(instance); }

	@Override
	protected void finalize() throws Throwable {
		try {
			release();
		} finally {
			super.finalize();
		}
	}

	/* The actual class starts here. */
	public interface FrameCallback { void onFrame(FrameInfo fi, float[] temp); }

	/* C++ fills this one and passes it to callback, do not rename or modify without also looking
	 *   at the C++ side.
	 */
	public static class FrameInfo {
		public float min, max, avg, center;
		public int min_x, min_y, max_x, max_y;
		public int width, height;

		public float correction, temp_reflected, temp_air, humidity, emissivity, distance;
	}

	public static final int paletteLen = 0x4000;

	/* These are what get passed to the frameCallback, so that we don't have to allocate a new one
	 *   for every frame. The way we make sure they won't get overwritten is that frameCallback()
	 *   only runs again if the last frameCallback() has finished.
	 */
	private FrameInfo frameInfo = new FrameInfo();
	private float[] temp;

	/* Called by the C++ code, do not rename. */
	private void frameCallback(FrameInfo fi, float[] temp) {
		synchronized (this) {
			if (userFrameCallback != null)
				userFrameCallback.onFrame(fi, temp);
		}
	}

	private native int nativeConnect(int fd);
	/* Make sure surface is either valid, not set or null before calling connect. */
	public void connect(int fd) {
		if (nativeConnect(fd) != 0)
			throw new RuntimeException("Failed to connect to camera.");
	}
	public native void disconnect();

	public native int getWidth();
	public native int getHeight();

	/* Be aware that when streaming is started, the output surface has to flip buffers or following
	 *   calls to setSurface() or else the frame callback will get stuck. Also do NOT block the CB
	 *   during startStream/stopStream/connect/disconnect!
	 */
	private native int nativeStartStream();
	public void startStream() {
		if (nativeStartStream() != 0)
			throw new RuntimeException("Failed to start stream.");
	}
	public native void stopStream();

	private native int nativeSetSurface(Surface surface);
	public void setSurface(Surface surface) {
		if (nativeSetSurface(surface) != 0)
			throw new RuntimeException("Failed to set surface.");
	}

	/* Note that the frame callback is called from a separate thread. */
	public void setFrameCallback(FrameCallback fcb) {
		synchronized (this) {
			userFrameCallback = fcb;
		}
	}

	/* Set range, valid values are 120 and 400 (see InfiFrame class).
	 * Changes take effect after update/update_table().
	 */
	public native void setRange(int range);

	/* Distance multiplier, 3.0 for 6.8mm lens, 1.0 for 13mm lens.
	 * Changes only take effect after update_table().
	 */
	public native void setDistanceMultiplier(float dm);

	/* Setting parameters, only works while streaming.
	 * Changes only take effect after update_table().
	 */
	public native void setCorrection(float corr);
	public native void setTempReflected(float t_ref);
	public native void setTempAir(float t_air);
	public native void setHumidity(float humi);
	public native void setEmissivity(float emi);
	public native void setDistance(float dist);
	public native void setParams(float corr, float t_ref, float t_air, float humi, float emi,
								 float dist);
	/* Store user memory to camera so values remain when reconnecting. */
	public native void storeParams();

	public native void updateTable();
	public native void calibrate();
	public native void closeShutter();
	public native void setRawSensor(boolean raw);
    public native void setP2Pro(boolean p2Pro);

	private native int nativeSetPalette(int[] palette); /* Length must be paletteLen. */
	public void setPalette(int[] palette) {
		if (nativeSetPalette(palette) != 0)
			throw new IllegalArgumentException();
	}

	/* Applies the set palette to the surface given with setSurface(). */
	public native void applyPalette(float min, float max);
}
