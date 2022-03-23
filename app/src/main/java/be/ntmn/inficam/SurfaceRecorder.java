package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SurfaceRecorder implements Runnable {
	private static final String MUX_MIME_TYPE = "video/mp4";
	private static final String MUX_EXT = ".mp4";
	private static final String VID_MIME_TYPE = "video/avc"; /* H.264 */
	private static final int FRAME_RATE = 25;
	private static final int IFRAME_INTERVAL = 10; /* In seconds. */
	private static final float BITRATE = 1; /* In bits per pixel. */
	private static final int DEQUEUE_TIMEOUT = 5000; /* In microseconds. */

	private static final String SND_MIME_TYPE = "audio/mp4a-latm";
	private static final int SND_SAMPLERATE = 44100;
	private static final boolean SND_STEREO = false;
	private static final int SND_BITRATE = 128000;

	private Context ctx;
	private Surface inputSurface;
	private MediaCodec videoEncoder;
	private MediaCodec audioEncoder;
	private AudioRecord audioRecord;
	private int audioBufferSize;
	private MediaMuxer muxer;
	private int videoTrack, audioTrack;
	private MediaCodec.BufferInfo bufferInfo;
	private volatile boolean endSignal; /* Volatile is important because threading. */
	private boolean muxerStarted;
	private Thread thread;
	private Uri fileUri;
	private ParcelFileDescriptor fileDescriptor;

	/* If enabling audio, request audio permission first! */
	@SuppressLint("MissingPermission")
	private Surface _start(Context ctx, int w, int h, boolean sound) throws IOException {
		this.ctx = ctx;
		/* Deal with actually getting a file and opening the muxer. */
		@SuppressLint("SimpleDateFormat")
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String dirname = ctx.getString(R.string.app_name);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			String fname = "vid_" + timeStamp + MUX_EXT; /* MediaStore won't overwrite. */
			ContentValues cv = new ContentValues();
			cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
			cv.put(MediaStore.MediaColumns.MIME_TYPE, MUX_MIME_TYPE);
			cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
			cv.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
			cv.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
			ContentResolver cr = ctx.getContentResolver();
			fileUri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
			fileDescriptor = cr.openFileDescriptor(fileUri, "rw");
			muxer = new MediaMuxer(fileDescriptor.getFileDescriptor(),
					MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		} else {
			int num = 0;
			String fname = "vid_" + timeStamp + "_" + num + MUX_EXT;
			File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
			File dir = new File(dcim, dirname);
			if (!dir.exists())
				dir.mkdirs();
			File file = new File(dir, fname);
			while (file.exists()) { /* Avoid overwriting existing files. */
				fname = "vid_" + timeStamp + "_" + ++num + MUX_EXT;
				file = new File(fname);
			}
			muxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			fileUri = Uri.fromFile(file);
		}
		muxerStarted = false; /* We start it later, when the codecs report they're configured. */

		/* Prepare the format etc. */
		bufferInfo = new MediaCodec.BufferInfo();
		MediaFormat format = MediaFormat.createVideoFormat(VID_MIME_TYPE, w, h);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (BITRATE * w * h * FRAME_RATE));
		format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		videoEncoder = MediaCodec.createEncoderByType(VID_MIME_TYPE);
		videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		inputSurface = videoEncoder.createInputSurface();
		videoTrack = -1;
		videoEncoder.start();

		if (sound) {
			/* MediaCodec defaults to 16bit PCM input, changing it seems to only be possible if we
			 *   would change minimum API level.
			 */
			int achannels =
					SND_STEREO ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
			audioBufferSize = AudioRecord.getMinBufferSize(SND_SAMPLERATE, achannels,
					AudioFormat.ENCODING_PCM_16BIT);
			MediaFormat aFormat = MediaFormat.createAudioFormat(SND_MIME_TYPE, SND_SAMPLERATE,
					SND_STEREO ? 2 : 1);
			aFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize);
			format.setInteger(
					MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			aFormat.setInteger(MediaFormat.KEY_BIT_RATE, SND_BITRATE);
			audioEncoder = MediaCodec.createEncoderByType(SND_MIME_TYPE);
			audioEncoder.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SND_SAMPLERATE,
					achannels, AudioFormat.ENCODING_PCM_16BIT, audioBufferSize);
			audioTrack = -1;
			audioEncoder.start();
			audioRecord.startRecording();
		} else {
			audioEncoder = null;
			audioRecord = null;
		}

		endSignal = false;
		thread = new Thread(this);
		thread.start();
		return inputSurface;
	}

	/* Wrapper so we don't need to indent everything so far to call stop in case of exceptions. */
	public Surface start(Context ctx, int w, int h, boolean sound) throws IOException {
		stop(); /* Just restart if started to prevent disasters. */
		try {
			return _start(ctx, w, h, sound);
		} catch (Exception e) {
			stop();
			throw e;
		}
	}

	/* Safe to call when stopped. */
	public void stop() {
		try {
			if (thread != null) {
				endSignal = true;
				thread.join();
				thread = null;
			}
		} catch (InterruptedException e) {
			e.printStackTrace(); /* This should never happen. */
		}
		if (videoEncoder != null) {
			videoEncoder.stop();
			videoEncoder.release();
		}
		videoEncoder = null;
		if (audioRecord != null) {
			audioRecord.stop();
			audioRecord.release();
		}
		audioRecord = null;
		if (audioEncoder != null) {
			audioEncoder.stop();
			audioEncoder.release();
		}
		audioEncoder = null;
		if (muxerStarted)
			muxer.stop();
		muxerStarted = false;
		if (muxer != null)
			muxer.release();
		muxer = null;
		if (inputSurface != null)
			inputSurface.release();
		inputSurface = null;
		audioTrack = -1;
		videoTrack = -1;
		if (fileDescriptor != null) {
			try {
				fileDescriptor.close();
			} catch (Exception e) { /* Empty. */ }
		}
		fileDescriptor = null;
		if (fileUri != null)
			Util.scanMedia(ctx, fileUri);
		fileUri = null;
	}

	@Override
	public void run() {
		boolean stop = false, vidDone = false, sndDone = false;
		/* This has to be a separate thread because if the encoder runs out of buffers then
		 *   swapBuffers() on the surface will block.
		 */
		while (!vidDone || (!sndDone && audioEncoder != null)) {
			int encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT);
			if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				/* Should happen exactly once, before output buffer given. */
				MediaFormat format = videoEncoder.getOutputFormat();
				videoTrack = muxer.addTrack(format);
				if (audioTrack >= 0 || audioEncoder == null) {
					muxer.start();
					muxerStarted = true;
				}
			} else if (encoderStatus >= 0 && !vidDone) {
				if (!muxerStarted)
					continue;
				ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
				if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
					bufferInfo.size = 0;
				if (bufferInfo.size != 0)
					muxer.writeSampleData(videoTrack, encodedData, bufferInfo);
				videoEncoder.releaseOutputBuffer(encoderStatus, false);
				if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
					vidDone = true;
			} else if (stop) { /* Most likely MediaCodec.INFO_TRY_AGAIN_LATER. */
				vidDone = true;
			}

			if (audioEncoder != null) {
				/* Shovel audio from the recorder to the encoder. */
				int index = audioEncoder.dequeueInputBuffer(DEQUEUE_TIMEOUT);
				if (index >= 0) { /* Won't be >= 0 after BUFFER_FLAG_END_OF_STREAM. */
					ByteBuffer buffer = audioEncoder.getInputBuffer(index);
					buffer.clear();
					int len = audioRecord.read(buffer, audioBufferSize);
					if (len > 0)
						audioEncoder.queueInputBuffer(index, 0, len, System.nanoTime() / 1000,
								stop ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
				}

				/* Shovel audio from the encoder to the muxer. */
				encoderStatus = audioEncoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT);
				if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					/* Should happen exactly once, before output buffer given. */
					MediaFormat format = audioEncoder.getOutputFormat();
					audioTrack = muxer.addTrack(format);
					if (videoTrack >= 0) {
						muxer.start();
						muxerStarted = true;
					}
				} else if (encoderStatus >= 0 && !sndDone) {
					if (!muxerStarted)
						continue;
					ByteBuffer encodedData = audioEncoder.getOutputBuffer(encoderStatus);
					if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
						bufferInfo.size = 0;
					if (bufferInfo.size != 0)
						muxer.writeSampleData(audioTrack, encodedData, bufferInfo);
					audioEncoder.releaseOutputBuffer(encoderStatus, false);
					if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
						sndDone = true;
				} else if (stop) { /* Most likely MediaCodec.INFO_TRY_AGAIN_LATER. */
					sndDone = true;
				}
			}

			if (endSignal) {
				endSignal = false;
				stop = true;
				videoEncoder.signalEndOfInputStream(); /* Only allowed after getInputSurface(). */
			}
		}
	}

	public boolean isRecording() {
		return thread != null;
	}
}
