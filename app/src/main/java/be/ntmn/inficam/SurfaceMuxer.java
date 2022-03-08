package be.ntmn.inficam;

import android.graphics.Bitmap;
import android.graphics.Matrix;
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
 * To use do the following:
 * - Create an instance of the SurfaceMuxer class.
 * - To get an input surface create a SurfaceMuxer.InputSurface instance, this is bound to the
 *     SurfaceMuxer instance you give the constructor.
 * - Call getSurface() and/or getSurfaceTexture on that to get the actual input Surface and/or
 *     SurfaceTexture texture to draw to.
 * - Call setOnFrameAvailableListener(surfaceMuxer) on the SurfaceTexture from the InputSurface(s)
 *     you want to synchronize the output with.
 * - Create an instance of SurfaceMuxer.OutputSurface for surfaces you want to output to and call
 *     setSize() on them to set the dimensions.
 * - Add the InputSurface and OutputSurface instances to the inputSurfaces and outputSurfaces of
 *     the SurfaceMuxer instance, and you're of to the races.
 * - Call init() on the SurfaceMuxer instance, most likely you'll want to do this in onResume()
 *     because at onPause()  (or onStop()?) the EGL context can get destroyed, and in onPause()
 *     you should call deinit() to release any resources attached to the EGL context in question.
 *     Also the InputSurface instances have an init() and deinit() function that should be called
 *     at the same times, but if they are in the inputSurfaces array, this happens automatically.
 *     Also OutputSurface has init() and deinit() but these will normally survive the context being
 *     destroyed, yet init() should be called on them if they were created at a time no context
 *     existed (from a SurfaceHolder callback this can happen). Again SurfaceMuxer.init() will call
 *     this for you if the OutputSurface is in the outputSurfaces array.
 *
 * The order of operations of the above list isn't of particular importance as long as it's
 *   actually possible, but only use this class from a single thread since the EGL context is
 *   bound to a single thread.
 *
 * The surface given to the OutputSurface constructor only released in the OutputSurface.release()
 *   function. You should call the release() for instances of InputSurface and OutputSurface when
 *   they're no longer used to free the EGL stuff they use but if they're in the
 *   inputSurfaces/outputSurfaces arrays, that happens automatically when calling
 *   SurfaceMuxer.release(), which also should be called when the SurfaceMuxer instance is no
 *   longer in use.
 */
public class SurfaceMuxer implements SurfaceTexture.OnFrameAvailableListener {
	static final int EGL_RECORDABLE_ANDROID = 0x3142;
	EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
	EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
	EGLConfig eglConfig;
	FloatBuffer pVertex;
	FloatBuffer pTexCoord;
	int hProgram;
	public ArrayList<InputSurface> inputSurfaces = new ArrayList<>();
	public ArrayList<OutputSurface> outputSurfaces = new ArrayList<>();

	final String vss = "attribute vec2 vPosition;\n" +
			"attribute vec2 vTexCoord;\n" +
			"varying vec2 texCoord;\n" +
			"void main() {\n" +
			"  texCoord = vTexCoord;\n" +
			"  gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
			"}";

	final String fss = "#extension GL_OES_EGL_image_external : require\n" +
			"precision mediump float;\n" +
			"uniform samplerExternalOES sTexture;\n" +
			"varying vec2 texCoord;\n" +
			"void main() {\n" +
			"  gl_FragColor = texture2D(sTexture,texCoord);\n" +
			"}";

	public static class InputSurface {
		SurfaceMuxer surfaceMuxer;
		SurfaceTexture surfaceTexture;
		Surface surface;
		int[] textures = new int[1];
		boolean smooth, initialized = false;

		public InputSurface(SurfaceMuxer muxer, boolean smooth) {
			surfaceMuxer = muxer;
			this.smooth = smooth;
			init();
		}

		public void setSmooth(boolean smooth) {
			this.smooth = smooth;
			init();
		}

		public int getTexture() { return textures[0]; }
		public SurfaceTexture getSurfaceTexture() { return surfaceTexture; }
		public Surface getSurface() { return surface; }

		public void init() {
			int filter = smooth ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
			if (surfaceMuxer == null || surfaceMuxer.eglDisplay == EGL14.EGL_NO_DISPLAY)
				return;
			deinit();
			EGL14.eglMakeCurrent(surfaceMuxer.eglDisplay, EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_SURFACE, surfaceMuxer.eglContext);
			surfaceMuxer.checkEglError("eglMakeCurrent");
			GLES20.glGenTextures(1, textures, 0);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
					filter);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
					filter);
			if (surfaceTexture == null) {
				surfaceTexture = new SurfaceTexture(textures[0]);
				surface = new Surface(surfaceTexture);
			} else surfaceTexture.attachToGLContext(textures[0]);
			/* After attaching to a new context we must call updateTexImage() or the
			 *   OnFrameAvailableListener may stop being called for some reason.
			 */
			getSurfaceTexture().updateTexImage();
			initialized = true;
		}

		public void deinit() {
			if (initialized && surfaceMuxer != null &&
					surfaceMuxer.eglDisplay != EGL14.EGL_NO_DISPLAY) {
				EGL14.eglMakeCurrent(surfaceMuxer.eglDisplay, EGL14.EGL_NO_SURFACE,
						EGL14.EGL_NO_SURFACE, surfaceMuxer.eglContext);
				surfaceMuxer.checkEglError("eglMakeCurrent");
				surfaceTexture.detachFromGLContext();
				GLES20.glDeleteTextures(1, textures, 0);
			}
			initialized = false;
		}

		public void release() {
			if (surfaceMuxer != null && surfaceMuxer.eglDisplay != EGL14.EGL_NO_DISPLAY)
				deinit();
			surfaceMuxer = null;
			if (surface != null)
				surface.release();
			surface = null;
			if (surfaceTexture != null)
				surfaceTexture.release();
			surfaceTexture = null;
		}
	}

	public static class OutputSurface {
		SurfaceMuxer surfaceMuxer;
		Surface surface;
		boolean surfaceOwned;
		EGLSurface eglSurface = EGL14.EGL_NO_SURFACE; /* EGLSurface is not bound to context. */
		int width = 1, height = 1;

		public OutputSurface(SurfaceMuxer muxer, Surface surf, boolean release) {
			surfaceMuxer = muxer;
			surface = surf;
			surfaceOwned = release;
			init();
		}

		public void init() {
			if (surfaceMuxer == null || surfaceMuxer.eglDisplay == EGL14.EGL_NO_DISPLAY ||
					eglSurface != EGL14.EGL_NO_SURFACE)
				return;
			int[] attr = { EGL14.EGL_NONE };
			eglSurface = EGL14.eglCreateWindowSurface(surfaceMuxer.eglDisplay,
					surfaceMuxer.eglConfig, surface, attr, 0);
			surfaceMuxer.checkEglError("eglCreateWindowSurface");
		}

		public void deinit() {
			if (surfaceMuxer != null && surfaceMuxer.eglDisplay != EGL14.EGL_NO_DISPLAY &&
					eglSurface != EGL14.EGL_NO_SURFACE) {
				EGL14.eglDestroySurface(surfaceMuxer.eglDisplay, eglSurface);
				eglSurface = EGL14.EGL_NO_SURFACE;
			}
		}

		public void setSize(int w, int h) {
			width = w;
			height = h;
		}

		public void makeCurrent() {
			EGL14.eglMakeCurrent(surfaceMuxer.eglDisplay, eglSurface, eglSurface,
					surfaceMuxer.eglContext);
			surfaceMuxer.checkEglError("eglMakeCurrent");
		}

		public void swapBuffers() {
			EGL14.eglSwapBuffers(surfaceMuxer.eglDisplay, eglSurface);
			surfaceMuxer.checkEglError("eglSwapBuffers");
		}

		public void setPresentationTime(long nsecs) {
			EGLExt.eglPresentationTimeANDROID(surfaceMuxer.eglDisplay, eglSurface, nsecs);
			surfaceMuxer.checkEglError("eglPresentationTimeANDROID");
		}

		public void release() {
			deinit();
			if (surfaceOwned)
				surface.release();
			surfaceMuxer = null;
			surface = null;
		}
	}

	public SurfaceMuxer () {
		init();
	}

	void render(int w, int h) {
		GLES20.glViewport(0, 0, w, h);

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
			/*if (inputSurfaces.indexOf(is) == 1) // TODO this is lame
					GLES20.glViewport(0, 0, 640, 480);*/

			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, is.getTexture());
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}
		GLES20.glFlush();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		if (eglContext == EGL14.EGL_NO_CONTEXT)
			return;
		for (InputSurface is : inputSurfaces)
			is.getSurfaceTexture().updateTexImage();
		for (OutputSurface os : outputSurfaces) {
			os.makeCurrent();
			render(os.width, os.height);
			os.setPresentationTime(surfaceTexture.getTimestamp());
			os.swapBuffers();
		}
	}

	Bitmap getBitmap(int w, int h) {
		Bitmap ret = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
		int[] attr = new int[]{
				EGL14.EGL_WIDTH, w,
				EGL14.EGL_HEIGHT, h,
				EGL14.EGL_NONE
		};
		EGLSurface surface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attr, 0);
		checkEglError("eglCreatePbufferSurface");
		EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext);
		render(w, h);
		GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
		EGL14.eglDestroySurface(eglDisplay, surface);
		ret.copyPixelsFromBuffer(buf);
		Matrix matrix = new Matrix(); /* We have to flip y because OpenGL is weird. */
		matrix.postScale(1, -1, w / 2.0f, h / 2.0f);
		return Bitmap.createBitmap(ret, 0, 0, w, h, matrix, false);
	}

	void checkEglError(String msg) {
		int error;
		if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS)
			throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
	}

	public void init() { /* Initialize EGL context. */
		if (eglContext != EGL14.EGL_NO_CONTEXT)
			deinit();
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
		checkEglError("eglChooseConfig");
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

		/* Initialize any InputSurfaces we have. */
		for (InputSurface is : inputSurfaces)
			is.init();

		/* Initialize output surfaces, these do not perish with the EGL context but need init if
		 *   they were created at a time no context existed.
		 */
		for (OutputSurface os : outputSurfaces)
			os.init();
	}

	public void deinit() {
		for (InputSurface is : inputSurfaces)
			is.deinit();
		if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
			EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_CONTEXT);
			/* Destroying the context will also delete textures, program, etc. */
			EGL14.eglDestroyContext(eglDisplay, eglContext);
			EGL14.eglReleaseThread();
			EGL14.eglTerminate(eglDisplay);
		}
		eglDisplay = EGL14.EGL_NO_DISPLAY;
		eglContext = EGL14.EGL_NO_CONTEXT;
	}

	public void release() { // TODO don't forget to call
		/* Destroying the context will also release the EGL surfaces, but not Android's Surfaces. */
		for (OutputSurface ts : outputSurfaces) {
			ts.release();
			outputSurfaces.remove(ts);
		}
		for (InputSurface is : inputSurfaces) {
			is.release();
			inputSurfaces.remove(is);
		}
		deinit();
	}
}
