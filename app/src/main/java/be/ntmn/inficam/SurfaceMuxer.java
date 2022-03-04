package be.ntmn.inficam;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/* SurfaceMuxer
 *
 * Our native code can't write directly to the Surface that we can get from a MediaCodec or
 *   MediaRecorder for whatever reason (the documentation states: "The Surface must be rendered
 *   with a hardware-accelerated API, such as OpenGL ES. Surface.lockCanvas(android.graphics.Rect)
 *   may fail or produce unexpected results."). To solve this we use this class that'll take one or
 *   more input surfaces and draw them to one or more output surfaces using EGL.
 *
 * Note that to tell the thing when frames are available you need to tell the SurfaceTexture you
 *   want to synchronize to setOnFrameAvailableListener(surfaceMuxer).
 *
 * Be aware this uses an EGL context and everything is bound to the thread it's created on.
 */
public class SurfaceMuxer implements SurfaceTexture.OnFrameAvailableListener {
    static final int EGL_RECORDABLE_ANDROID = 0x3142;
    EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    EGLConfig eglConfig;
    FloatBuffer pVertex;
    FloatBuffer pTexCoord;
    int hProgram;
    ArrayList<InputSurface> inputSurfaces = new ArrayList<>();
    ArrayList<OutputSurface> outputSurfaces = new ArrayList<>();

    final String vss =  "attribute vec2 vPosition;\n" +
                        "attribute vec2 vTexCoord;\n" +
                        "varying vec2 texCoord;\n" +
                        "void main() {\n" +
                        "  texCoord = vTexCoord;\n" +
                        "  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
                        "}";

    final String fss =  "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "uniform samplerExternalOES sTexture;\n" +
                        "varying vec2 texCoord;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                        "}";

    class InputSurface {
        SurfaceTexture surfaceTexture;
        int[] textures = new int[1];

        InputSurface() {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST); // TODO let user optionally use GL_LINEAR
            surfaceTexture = new SurfaceTexture(textures[0]);
        }

        public int getTexture() {
            return textures[0];
        }

        public SurfaceTexture getSurfaceTexture() {
            return surfaceTexture;
        }

        public void release() {
            surfaceTexture.release();
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);
                GLES20.glDeleteTextures(1, textures, 0);
            }
        }
    }

    class OutputSurface {
        Surface surface;
        EGLSurface eglSurface;

        public OutputSurface(Surface surf) {
            int[] attr = { EGL14.EGL_NONE };
            surface = surf;
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surf, attr, 0);
            checkEglError("eglCreateWindowSurface");
        }

        public void makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
            checkEglError("eglMakeCurrent");
        }

        public void swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
            checkEglError("eglSwapBuffers");
        }

        public void setPresentationTime(long nsecs) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs);
            checkEglError("eglPresentationTimeANDROID");
        }

        public void release() { // TODO Don't forget to call when done.
            if (eglDisplay != EGL14.EGL_NO_DISPLAY)
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
            surface.release(); // TODO remember this releases the surface too, but should it?
            surface = null;
        }
    }

    public SurfaceMuxer() {
        init();
    }

    public SurfaceTexture createInputSurfaceTexture() {
        InputSurface is = new InputSurface();
        inputSurfaces.add(is);
        return is.getSurfaceTexture();
    }

    public void addOutputSurface(Surface s) {
        outputSurfaces.add(new OutputSurface(s));
    }

    public void removeOutputSurface(Surface s) {
        for (OutputSurface os : outputSurfaces) {
            if (os.surface == s) {
                outputSurfaces.remove(os);
                break;
            }
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        for (InputSurface is : inputSurfaces) // TODO should we do it for all of them?
            is.getSurfaceTexture().updateTexImage(); // TODO check whether we should indeed run this every time
        for (OutputSurface ts : outputSurfaces) {
            ts.makeCurrent();
            GLES20.glViewport(0, 0, 1280, 960); // TODO maybe move to onsurfacechanged? or maybe we store size info there and then... idfk

            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(hProgram);

            int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
            int tch = GLES20.glGetAttribLocation(hProgram, "vTexCoord");
            int th = GLES20.glGetUniformLocation(hProgram, "sTexture");

            GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 2, pVertex);
            GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
            GLES20.glEnableVertexAttribArray(ph);
            GLES20.glEnableVertexAttribArray(tch);
            GLES20.glUniform1i(th, 0); /* Tells the shader what texture to use. */

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            //GLES20.glBlendColor(1, 1, 1, 0.1f);
            for (InputSurface is : inputSurfaces) {
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                /*if (inputSurfaces.indexOf(is) == 1)
                    GLES20.glBlendFunc(GLES20.GL_CONSTANT_ALPHA, GLES20.GL_ONE_MINUS_CONSTANT_ALPHA);*/
                if (inputSurfaces.indexOf(is) == 1) // TODO this is lame
                    GLES20.glViewport(0, 0, 640, 480);

                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, is.getTexture());
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
            GLES20.glFlush();

            ts.setPresentationTime(surfaceTexture.getTimestamp());
            ts.swapBuffers();
        }
    }

    void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS)
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
    }

    void init() { /* Initialize EGL context. */
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY)
            throw new RuntimeException("unable to get EGL14 display");
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
            throw new RuntimeException("unable to initialize EGL14");

        /* Get an EGL configuration. */
        int[] cfga = {
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, EGL14.EGL_TRUE, /* We need this to be able to record. */
            EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, cfga, 0, configs, 0, configs.length, numConfigs, 0);
        checkEglError("eglCreateContext");
        eglConfig = configs[0];

        /* Create an EGL context. */
        int[] ctxa = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxa, 0);
        checkEglError("eglCreateContext");

        /* Now we make the context current so creating our shaders etc binds to this context. */
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);
        //Log.i("GLEXT", "Gl extensions: " + GLES20.glGetString(GLES10.GL_EXTENSIONS));

        /* Enable alpha blending. */
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        /* Initialize the vertexes and textures. */
        float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
        float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
        pVertex = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put(vtmp);
        pVertex.position(0);
        pTexCoord = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put(ttmp);
        pTexCoord.position(0);

        /* Create the shaders. */
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0)
            throw new RuntimeException(GLES20.glGetShaderInfoLog(vshader));

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0)
            throw new RuntimeException(GLES20.glGetShaderInfoLog(vshader));

        /* Create the program. */
        hProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(hProgram, vshader);
        GLES20.glAttachShader(hProgram, fshader);
        GLES20.glLinkProgram(hProgram);
        GLES20.glDeleteShader(vshader); /* They will still live until the program dies. */
        GLES20.glDeleteShader(fshader);
    }

    public void release() { // TODO don't forget to call
        /* Destroying the context will also release the EGL surfaces, but not Android's Surfaces. */
        for (OutputSurface ts : outputSurfaces)
            ts.release();
        for (InputSurface is : inputSurfaces)
            is.release();

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(eglDisplay, eglContext); /* Will also delete textures, program, etc. */
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
    }
}
