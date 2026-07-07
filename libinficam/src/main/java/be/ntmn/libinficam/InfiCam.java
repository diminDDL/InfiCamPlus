package be.ntmn.libinficam;

public class InfiCam {

	/* We start with a bit of fluff to make JNI work. */
	private final long instance;
	private FrameCallback userFrameCallback = null;
	private SettingsCallback userSettingsCallback = null;

	static {
		System.loadLibrary("usb1.0");
		System.loadLibrary("uvc");
		System.loadLibrary("InfiCam");
	}

	private native static long nativeNew(InfiCam self);
	private native static void nativeDelete(long ptr);

	public InfiCam() {
		if ((instance = nativeNew(this)) == 0) { throw new OutOfMemoryError(); }
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

	public interface SettingsCallback { void onSettings(CamSettings cs); }

	/* C++ fills these and passes them to callbacks, do not rename or modify without also looking
	 *   at the C++ side.
	 */
	public static class CamSettings {

		public int range; //ID of the range
		public float max_temp_clipping; //maximum temperature the sensor can see

		public float correction, temp_reflected, temp_air, humidity, emissivity;
		public short distance;
	}
	public static class FrameInfo {
		public int width, height;
		@SuppressWarnings({"unused","FieldMayBeFinal"})
		public//from C++
		CamSettings settings = new CamSettings();
	}

	/* These are what get passed to the frameCallback, so that we don't have to allocate a new one
	 *   for every frame. The way we make sure they won't get overwritten is that frameCallback()
	 *   only runs again if the last frameCallback() has finished.
	 */
	@SuppressWarnings({"unused","FieldMayBeFinal"}) //from C++
	private FrameInfo framcb_frameInfo = new FrameInfo();
	@SuppressWarnings({"unused","FieldMayBeFinal"}) //from C++
	private float[] framcb_temp = new float[0];
	/*
	 * Like the above, but for the settings callback
	 */
	@SuppressWarnings({"unused","FieldMayBeFinal"}) //from C++
	private CamSettings setcb_camSettings = new CamSettings();

	/* Called by the C++ code, do not rename. */
	private void frameCallback(FrameInfo fi, float[] temp) {
		synchronized (this) {
			if (userFrameCallback != null) userFrameCallback.onFrame(fi, temp);
		}
	}

	/* Called by the C++ code, do not rename. */
	private void settingsCallback(CamSettings cs) {
		synchronized (this) {
			if (userSettingsCallback != null) userSettingsCallback.onSettings(cs);
		}
	}

	private native int nativeConnect(int fd);

	public void connect(int fd) {
		if (nativeConnect(fd) != 0) { throw new RuntimeException( "Failed to connect to camera." ); }
	}

	public native void disconnect();

	public native int getWidth();

	public native int getHeight();

	private native int nativeStartStream();

	public void startStream() {
		if (nativeStartStream() != 0) { throw new RuntimeException("Failed to start stream."); }
	}

	public native void stopStream();

	/* Note that the frame callback is called from a separate thread. */
	public void setFrameCallback(FrameCallback fcb) {
		synchronized (this) {
			userFrameCallback = fcb;
		}
	}

	/* Note that the frame callback is called from a separate thread. */
	public void setSettingsCallback(SettingsCallback scb) {
		synchronized (this) {
			userSettingsCallback = scb;
		}
	}

	//set the range identifier corresponding to the desired range
	public native void setRange(int range);

	//Get the ranges, in the {{-20,120},{120,400}} format.
	public native float[][] getRanges();

	public native void setCorrection(float corr);

	public native float getCorrection();

	public native void setTempReflected(float t_ref);

	public native float getTempReflected();

	public native void setTempAir(float t_air);

	public native float getTempAir();

	public native void setHumidity(float humi);

	public native float getHumidity();

	public native void setEmissivity(float emi);

	public native float getEmissivity();

	public native void setDistance(short dist);

	public native short getDistance();

	public native void storeParams(); /* Store user memory to camera so values remain when reconnecting. */

	public native void lockShutter();

	public native void unlockShutter();

	public native void calibrate();
	public native void calibrateBlocking();
	public native boolean isCalibrating();
	public native boolean setCalibrationSuppressed(boolean suppress);
	public native void setSmartCalibrationEnabled(boolean enabled);

	public native void setAutoShutterSettings(boolean enable, int interval_min, int interval_max);
}
