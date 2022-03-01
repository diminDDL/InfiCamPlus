package be.ntmn.XthermDemo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;
/* TODO
Most 3D applications, such as games or simulations, are continuously animated. But some 3D
applications are more reactive: they wait passively until the user does something, and then react
to it. For those types of applications, the default GLSurfaceView behavior of continuously
redrawing the screen is a waste of time. If you are developing a reactive application, you can
 call GLSurfaceView.setRenderMode(RENDERMODE_WHEN_DIRTY), which turns off the continuous animation.
 Then you call GLSurfaceView.requestRender() whenever you want to re-render.
 */
// LOT of info from here https://www.maninara.com/2012/09/render-camera-preview-using-opengl-es.html
// this also looks interesting https://github.com/googlecreativelab/shadercam/blob/master/shadercam/src/main/java/com/androidexperiments/shadercam/gl/CameraRenderer.java
//   also https://github.com/googlecreativelab/shadercam/blob/master/shadercam/src/main/java/com/androidexperiments/shadercam/gl/WindowSurface.java

public class InfiCamView extends GLSurfaceView {
    public InfiCamView(Context context) {
        super(context);
        init();
    }

    public InfiCamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    InfiRenderer rend;

    void init() {
        rend = new InfiRenderer(this);
        setEGLContextClientVersion ( 2 );
        setRenderer(rend);
        setRenderMode ( GLSurfaceView.RENDERMODE_WHEN_DIRTY );
    }

    public Surface getSurf() {
        return rend.getSurf();
    }
}

class InfiRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private final String vss =
            "attribute vec2 vPosition;\n" +
                    "attribute vec2 vTexCoord;\n" +
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  texCoord = vTexCoord;\n" +
                    "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
                    "}";

    private final String fss =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                    "}";

    private int[] hTex;
    private FloatBuffer pVertex;
    private FloatBuffer pTexCoord;
    private int hProgram;

    private SurfaceTexture mSTexture = null;

    private boolean mUpdateST = false;

    private InfiCamView mView;

    MediaRecorder getRecorder() {
        MediaRecorder ret = new MediaRecorder();

        //set the sources
        /**
         * {@link MediaRecorder.AudioSource.CAMCORDER} is nice because on some fancier
         * phones microphones will be aligned towards whatever camera is being used, giving us better
         * directional audio. And if it doesn't have that, it will fallback to the default Microphone.
         */
        ret.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        /**
         * Using {@link MediaRecorder.VideoSource.SURFACE} creates a {@link Surface}
         * for us to use behind the scenes. We then pass this service to our {@link ExampleRenderer}
         * later on for creation of our EGL contexts to render to.
         *
         * {@link MediaRecorder.VideoSource.SURFACE} is also the default for rendering
         * out Camera2 api data without any shader manipulation at all.
         */
        ret.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        //set output
        ret.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        /**
         * This would eventually be worth making a parameter at each call to {@link #setupMediaRecorder()}
         * so that you can pass in a timestamp or unique file name each time to setup up.
         */
        ret.setOutputFile("/sdcard/videocapture_example/test.mp4");

        /**
         * Media Recorder can be finicky with certain video sizes, so lets make sure we pass it
         * something 'normal' - ie 720p or 1080p. this will create a surface of the same size,
         * which will be used by our renderer for drawing once recording is enabled
         */
        ret.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        ret.setVideoEncodingBitRate(10000000);
        ret.setVideoSize(640, 480);
        ret.setVideoFrameRate(30);

        //setup audio
        ret.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        ret.setAudioEncodingBitRate(44800);

        /**
         * we can determine the rotation and orientation of our screen here for dynamic usage
         * but since we know our app will be portrait only, setting the media recorder to
         * 720x1280 rather than 1280x720 and letting orientation be 0 will keep everything looking normal
         */
        /*int rotation = ((Activity)mContext).getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation); Log.d(TAG, "orientation: " + orientation);
        ret.setOrientationHint(0);*/

        try {
            /**
             * There are what seems like an infinite number of ways to fuck up the previous steps,
             * so prepare() will throw an exception if you fail, and hope that stackoverflow can help.
             */
            ret.prepare();
        } catch (IOException e) {
            //Toast.makeText(mContext, "MediaRecorder failed on prepare()", Toast.LENGTH_LONG).show();
            Log.e("ERRORERROR", "MediaRecorder failed on prepare() " + e.getMessage());
        }
        return ret;
    }

    MediaRecorder rec = getRecorder();

    InfiRenderer(InfiCamView view) {
        mView = view;
        float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
        float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
        pVertex = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put(vtmp);
        pVertex.position(0);
        pTexCoord = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put(ttmp);
        pTexCoord.position(0);
    }

    public void close()
    {
        mUpdateST = false;
        mSTexture.release();
        /*mCamera.stopPreview();
        mCamera.release();
        mCamera = null;*/
        deleteTex();
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        Log.i("mr", "Gl extensions: " + extensions);
        //Assert.assertTrue(extensions.contains("OES_EGL_image_external"));

        initTex();
        mSTexture = new SurfaceTexture ( hTex[0] );
        mSTexture.setOnFrameAvailableListener(this);
        //GLES20.glClearColor ( 0.0f, 0.0f, 0.0f, 1.0f );
        hProgram = loadShader ( vss, fss );
    }

    public Surface getSurf() {
        rec.start();

        return new Surface(mSTexture);
    }

    public void onDrawFrame ( GL10 unused ) {
        GLES20.glClear( GLES20.GL_COLOR_BUFFER_BIT );

        synchronized(this) {
            if ( mUpdateST ) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        GLES20.glUseProgram(hProgram);

        int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
        int tch = GLES20.glGetAttribLocation ( hProgram, "vTexCoord" );
        int th = GLES20.glGetUniformLocation ( hProgram, "sTexture" );

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glUniform1i(th, 0);

        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4*2, pVertex);
        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4*2, pTexCoord );
        GLES20.glEnableVertexAttribArray(ph);
        GLES20.glEnableVertexAttribArray(tch);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFlush();
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        //GLES20.glViewport( 0, 0, 256 * 5, 192 * 5 );
        GLES20.glViewport(0, 0, width, height);
    }

    private void initTex() {
        hTex = new int[1];
        GLES20.glGenTextures ( 1, hTex, 0 );
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST); // TODO let user optionally use GL_LINEAR
    }

    private void deleteTex() {
        GLES20.glDeleteTextures ( 1, hTex, 0 );
    }

    public synchronized void onFrameAvailable(SurfaceTexture st) {
        mUpdateST = true;
        mView.requestRender();
    }

    private static int loadShader(String vss, String fss) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vshader");
            Log.v("Shader", "Could not compile vshader:" + GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fshader");
            Log.v("Shader", "Could not compile fshader:" + GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);

        return program;
    }
}
