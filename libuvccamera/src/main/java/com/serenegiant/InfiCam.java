package com.serenegiant;

public class InfiCam {
    static final int paletteLen = 0x4000;

    native static int connect(int fd);
    native static void disconnect();

    //native static int stream_start(frame_callback_t *cb, void *user_ptr);
    native static void nativeStopStream();

    /* Set range, valid values are 120 and 400 (see InfiFrame class).
     * Changes take effect after update/update_table().
     */
    native static void setRange(int range);

    /* Distance multiplier, 3.0 for 6.8mm lens, 1.0 for 13mm lens.
     * Changes only take effect after update_table().
     */
    native static void setDistanceMultiplier(float dm);

    /* Setting parameters, only works while streaming.
     * Changes only take effect after update_table().
     */
    native static void setCorrection(float corr);
    native static void setTempReflected(float t_ref);
    native static void setTempAir(float t_air);
    native static void setHumidity(float humi);
    native static void setEmissivity(float emi);
    native static void setDistance(float dist);
    native static void setParams(float corr, float t_ref, float t_air, float humi, float emi,
                                  float dist);
    /* Store user memory to camera so values remain when reconnecting. */
    native static void storeParams();

    native static void updateTable();
    native static void calibrate();

    native static int nativeSetPalette(int[] palette); /* Length must be palette_len. */
    static void setPalette(int[] palette) {
        if (nativeSetPalette(palette) != 0)
            throw new IllegalArgumentException();
    }
}
