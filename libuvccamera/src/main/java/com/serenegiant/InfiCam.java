package com.serenegiant;

import android.view.Surface;

public class InfiCam {
    static boolean isLoaded = false;
    static {
        if (!isLoaded) {
            System.loadLibrary("usb1.0");
            System.loadLibrary("uvc");
            System.loadLibrary("UVCCamera");
            isLoaded = true;
        }
    }

    public static int width = 256; // TODO
    public static int height = 192;
    public static final int paletteLen = 0x4000;

    public native static int connect(int fd);
    public native static void disconnect();

    public native static int startStream(Surface surface); // TODO throw exceptions
    public native static void stopStream();

    /* Set range, valid values are 120 and 400 (see InfiFrame class).
     * Changes take effect after update/update_table().
     */
    public native static void setRange(int range);

    /* Distance multiplier, 3.0 for 6.8mm lens, 1.0 for 13mm lens.
     * Changes only take effect after update_table().
     */
    public native static void setDistanceMultiplier(float dm);

    /* Setting parameters, only works while streaming.
     * Changes only take effect after update_table().
     */
    public native static void setCorrection(float corr);
    public native static void setTempReflected(float t_ref);
    public native static void setTempAir(float t_air);
    public native static void setHumidity(float humi);
    public native static void setEmissivity(float emi);
    public native static void setDistance(float dist);
    public native static void setParams(float corr, float t_ref, float t_air, float humi, float emi,
                                  float dist);
    /* Store user memory to camera so values remain when reconnecting. */
    public native static void storeParams();

    public native static void updateTable();
    public native static void calibrate();

    native static int nativeSetPalette(int[] palette); /* Length must be palette_len. */
    public static void setPalette(int[] palette) {
        if (nativeSetPalette(palette) != 0)
            throw new IllegalArgumentException();
    }
}
