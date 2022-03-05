package be.ntmn.libinficam;

import android.view.Surface;

public class InfiCam {
	/* We start with a bit of fluff to make JNI work. */
	final long instance;

	static {
		System.loadLibrary("usb1.0");
		System.loadLibrary("uvc");
		System.loadLibrary("InfiCam");
	}

	native static long nativeNew();
	native static void nativeDelete(long ptr);

	public InfiCam() {
		if ((instance = nativeNew()) == 0)
			throw new OutOfMemoryError();
	}

	public void release() {
		nativeDelete(instance);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			release();
		} finally {
			super.finalize();
		}
	}

	/* The actual class starts here. */
	public interface FrameCallback {
		void onFrame(FrameInfo fi, float[] temp);
	}

	/* C++ fills this one and passes it to callback, do not rename or modify without also looking
	 *   at the C++ side.
	 */
	public static class FrameInfo {
		public float min, max, avg, center;
		public int min_x, min_y, max_x, max_y;

		public float correction, temp_reflected, temp_air, humidity, emissivity, distance;
	}

	public static final int paletteLen = 0x4000;
	FrameCallback userFrameCallback = null;

	/* Called by the C++ code, do not rename. */
	void frameCallback(FrameInfo fi, float[] temp) {
		if (userFrameCallback != null)
			userFrameCallback.onFrame(fi, temp);
	}

	native int nativeConnect(int fd);
	public void connect(int fd) throws Exception {
		if (nativeConnect(fd) != 0)
			throw new Exception("Failed to connect to camera.");
	}
	public native void disconnect();

	public native int getWidth();
	public native int getHeight();

	native int nativeStartStream();
	public void startStream() throws Exception {
		if (nativeStartStream() != 0)
			throw new Exception("Failed to start stream.");
	}
	public native void stopStream();

	native int nativeSetSurface(Surface surface);
	public void setSurface(Surface surface) throws Exception {
		if (nativeSetSurface(surface) != 0)
			throw new Exception("Failed to set surface.");
	}

	public void setFrameCallback(FrameCallback fcb) {
		userFrameCallback = fcb;
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

	native int nativeSetPalette(int[] palette); /* Length must be paletteLen. */
	public void setPalette(int[] palette) {
		if (nativeSetPalette(palette) != 0)
			throw new IllegalArgumentException();
	}
}
