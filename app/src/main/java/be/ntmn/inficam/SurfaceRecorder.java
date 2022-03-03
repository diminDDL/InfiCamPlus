package be.ntmn.inficam;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

// TODO remember to try MediaRecorder.AudioSource.CAMCORDER first, then default/mic, it's allegedly better
public class SurfaceRecorder extends Thread {
    static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();
    static final String MIME_TYPE = "video/avc"; /* H.264 */
    static final int FRAME_RATE = 25;
    static final int IFRAME_INTERVAL = 10; /* In seconds. */
    static final float BITRATE = 1; /* In bits per pixel. */

    Surface inputSurface; // TODO release?
    MediaCodec videoEncoder;
    MediaMuxer muxer;
    int videoTrack;
    boolean muxerStarted;
    MediaCodec.BufferInfo bufferInfo;
    boolean endOfStream = false;

    public SurfaceRecorder(int width, int height) throws IOException {
        super();
        bufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (BITRATE * width * height * FRAME_RATE));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoEncoder.createInputSurface();

        String outputPath = new File(OUTPUT_DIR, "test.mp4").toString(); // TODO

        /* The muxer starts when we get the actual video format later. */
        muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        videoTrack = -1;
        muxerStarted = false;
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    void release() {
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (muxer != null) {
            muxer.stop();
            muxer.release();
            muxer = null;
        }
    }

    public void startRecording() {
        videoEncoder.start(); // TODO maybe make this only happen on stream start
        start();
    }

    public void stopRecording() {
        synchronized (this) {
            endOfStream = true;
        }
    }

    @Override
    public void run() {
        /* This has to be a separate thread because if the encoder runs out of buffers then
         *   swapBuffers() on the surface will block.
         */
        final int TIMEOUT_USEC = 10000;
/*        if (endOfStream)
            videoEncoder.signalEndOfInputStream();*/
/*
    protected void signalEndOfInputStream() {
		if (DEBUG) Log.d(TAG, "sending EOS to encoder");
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
//		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode((byte[])null, 0, getPTSUs());
	}

	TODO note getPTSUs is presentation time there
 */

        while (true) {
            boolean eos;
            synchronized (this) {
                eos = endOfStream;
            }

            if (eos)
                videoEncoder.signalEndOfInputStream();

            int encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                /*if (eos)
                    break;*/
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                /* Should happen exactly once, before output buffer given. */
                if (muxerStarted)
                    throw new RuntimeException("format changed twice");
                MediaFormat format = videoEncoder.getOutputFormat();
                videoTrack = muxer.addTrack(format);
                muxer.start();
                muxerStarted = true;
            } else if (encoderStatus >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
                    bufferInfo.size = 0;
                ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
                if (bufferInfo.size != 0) {
                    if (!muxerStarted)
                        throw new RuntimeException("muxer hasn't started");
                    muxer.writeSampleData(videoTrack, encodedData, bufferInfo);
                }
                videoEncoder.releaseOutputBuffer(encoderStatus, false);
            }

            if (eos)
                break;
        }
    }
}
