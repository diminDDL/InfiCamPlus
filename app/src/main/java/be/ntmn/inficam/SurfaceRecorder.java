package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

// TODO remember to try MediaRecorder.AudioSource.CAMCORDER first, then default/mic, it's allegedly better
public class SurfaceRecorder extends Thread {
	static final String MIME_TYPE = "video/avc"; /* H.264 */
	static final int FRAME_RATE = 25;
	static final int IFRAME_INTERVAL = 10; /* In seconds. */
	static final float BITRATE = 1; /* In bits per pixel. */
	static final int DEQUEUE_TIMEOUT = 10000; /* In microseconds. */

	Surface inputSurface;
	MediaCodec videoEncoder;
	MediaMuxer muxer;
	int videoTrack;
	boolean muxerStarted;
	MediaCodec.BufferInfo bufferInfo;
	volatile boolean endOfStream = false;

	public SurfaceRecorder(Context ctx, int width, int height) throws IOException {
		super();

		/* Prepare the format etc. */
		bufferInfo = new MediaCodec.BufferInfo();
		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (BITRATE * width * height * FRAME_RATE));
		format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		inputSurface = videoEncoder.createInputSurface();
		videoTrack = -1;
		muxerStarted = false; /* The muxer starts when we get the actual video format later. */

		/* Deal with actually getting a file and opening the muxer. */
		@SuppressLint("SimpleDateFormat")
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String dirname = ctx.getString(R.string.app_name);
		OutputStream out;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			String fname = "vid_" + timeStamp + ".mp4"; /* MediaStore won't overwrite. */
			ContentValues cv = new ContentValues();
			cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
			cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
			cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
			ContentResolver cr = ctx.getContentResolver();
			Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
			ParcelFileDescriptor fd = cr.openFileDescriptor(uri, "rw");
			muxer = new MediaMuxer(fd.getFileDescriptor(),
					MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		} else {
			int num = 0;
			String fname = "vid_" + timeStamp + "_" + num + ".mp4";
			File dir = new File(Environment.DIRECTORY_DCIM, dirname);
			File file = new File(dir, fname);
			while (file.exists()) { /* Avoid overwriting existing files. */
				fname = "vid_" + timeStamp + "_" + ++num + ".mp4";
				file = new File(fname);
			}
			muxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		}
	}

	public Surface getInputSurface() {
		return inputSurface;
	}

	public void startRecording() {
		videoEncoder.start(); // TODO maybe make this only happen on stream start
		start();
	}

	public void stopRecording() {
		synchronized (this) {
			endOfStream = true;
		}
		try {
			join();
		} catch (InterruptedException e) {
			e.printStackTrace(); /* This should never happen. */
		}
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
		if (inputSurface != null) {
			inputSurface.release();
			inputSurface = null;
		}
	}

	@Override
	public void run() {
		/* This has to be a separate thread because if the encoder runs out of buffers then
		 *   swapBuffers() on the surface will block.
		 */
		while (true) {
			int encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT);
			if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				/* Should happen exactly once, before output buffer given. */
				if (muxerStarted)
					throw new RuntimeException("Format changed twice.");
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
						throw new RuntimeException("Muxer hasn't started.");
					muxer.writeSampleData(videoTrack, encodedData, bufferInfo);
				}
				videoEncoder.releaseOutputBuffer(encoderStatus, false);
			} /* Most likely MediaCodec.INFO_TRY_AGAIN_LATER. */
			if (endOfStream) {
				/* Note to self, I read that a more generic way to signal the stream end is:
				 *   encode((byte[]) null, 0, presentationTime);
				 * The usefulness is that it works both on audio and video streams.
				 */
				videoEncoder.signalEndOfInputStream();
				break;
			}
		}
	}
}
