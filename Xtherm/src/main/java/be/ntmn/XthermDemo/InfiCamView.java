package be.ntmn.XthermDemo;

import android.content.Context;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class InfiCamView extends SurfaceView implements SurfaceHolder.Callback {
    public InfiCamView(Context context) {
        super(context);
    }

    public InfiCamView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InfiCamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public InfiCamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    MediaRecorder recorder;
    SurfaceHolder holder;
    boolean recording = false;
    int counter = 0;
    String defaultFilename = "/sdcard/videocapture_example";

    public void init() {
        recorder = new MediaRecorder();
        //initRecorder();

        holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private void initRecorder() {
        //recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoFrameRate(25);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(640, 480);
        recorder.setVideoEncodingBitRate(10001000);
        recorder.setOutputFile(defaultFilename + counter + ".mp4");
        counter += 1;
        recorder.setMaxDuration(50000); // 50 seconds
        recorder.setMaxFileSize(5000000); // Approximately 5 megabytes
        recorder.setPreviewDisplay(this.getHolder().getSurface());
    }

    private void prepareRecorder() {
        //recorder.setPreviewDisplay(holder.getSurface());

        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            //finish(); // TODO
        } catch (IOException e) {
            e.printStackTrace();
            //finish(); // TODO
        }
    }

    Surface globalSurface;

    Surface getSurf() {
        initRecorder();
        prepareRecorder();
        globalSurface = recorder.getSurface();
        return globalSurface;
    }

    public void startRecord() {
        if (recording) {
            recorder.stop();
            recording = false;

            // Let's initRecorder so we can record again
            initRecorder();
            prepareRecorder();
        } else {
            recording = true;
            recorder.start();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        //recorder.start();
        Log.e("CREATED", "Sorfac!!!!!!!!");
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (globalSurface == null) // TODO this is just so no optimizations could ditch globalSurface
            surfaceCreated(null);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) {
            recorder.stop();
            recording = false;
        }
        recorder.release();
    }
}
