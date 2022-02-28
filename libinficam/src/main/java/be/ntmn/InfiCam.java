package be.ntmn;

import android.util.Log;
import android.view.Surface;

public class InfiCam {
    /* We start with a bit of fluff to make JNI work. */
    static boolean isLoaded = false;
    public final long instance;

    static {
        if (!isLoaded) {
            System.loadLibrary("usb1.0");
            System.loadLibrary("uvc");
            System.loadLibrary("InfiCam");
            isLoaded = true;
        }
    }

    native static long nativeNew();
    native static void nativeDelete(long ptr);

    public InfiCam() {
        if ((instance = nativeNew()) == 0)
            throw new OutOfMemoryError();
    }

    @Override
    protected void finalize() {
        nativeDelete(instance);
    }

    /* The actual class starts here. */
    public interface TemperatureCallback {
        void onReceiveTemperature(FrameInfo fi, float[] temp);
    }

    public static class FrameInfo {
        float min, max, avg, center;
        int min_x, min_y, max_x, max_y;
    }

    public static final int paletteLen = 0x4000;
    TemperatureCallback tcb = new TemperatureCallback() {
        @Override
        public void onReceiveTemperature(FrameInfo fi, float[] temp) {
            Log.w("FRAMECB", "ct: " + fi.center + " " + fi.min + " " + fi.max);
        }
    };

    /* Called by the C++ code, do not rename. */
    void frameCallback(FrameInfo fi, float[] temp) {
        if (tcb != null)
            tcb.onReceiveTemperature(fi, temp);
    }

    native int nativeConnect(int fd);
    public void connect(int fd) throws Exception {
        if (nativeConnect(fd) != 0)
            throw new Exception("Failed to connect to camera.");
    }
    public native void disconnect();

    public native int getWidth();
    public native int getHeight();

    public native int nativeStartStream(Surface surface);
    public void startStream(Surface surface) throws Exception {
        if (nativeStartStream(surface) != 0)
            throw new Exception("Failed to start stream.");
    }
    public native void stopStream();

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
