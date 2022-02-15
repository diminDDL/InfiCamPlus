package com.serenegiant.utils;
import android.content.Context;
import android.media.AudioRecord;

public class PermissionCompatUtils {
    public static int audioFormat = 2;
    public static int audioSource = 1;
    public static int bufferSizeInBytes = 0;
    public static int channelConfig = 12;
    public static int sampleRateInHz = 44100;

    public static boolean isHasAudioRecordPermission(Context paramContext)
    {
        bufferSizeInBytes = 0;
        bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        try
        {
            audioRecord.startRecording();
        }
        catch (IllegalStateException localIllegalStateException)
        {
            localIllegalStateException.printStackTrace();
        }
        if (audioRecord.getRecordingState() != 3)
            return false;
        audioRecord.stop();
        audioRecord.release();
        return true;
    }
}
