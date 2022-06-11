package be.ntmn.inficam;

import android.content.Context;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/* SurfaceMuxer
 *
 * Our native code can't write directly to the Surface that we can get from a MediaCodec or
 *   MediaRecorder for whatever reason (the documentation states: "The Surface must be rendered
 *   with a hardware-accelerated API, such as OpenGL ES. Surface.lockCanvas(android.graphics.Rect)
 *   may fail or produce unexpected results."). To solve this we use this class that'll facilitate
 *   drawing to and from Surfaces using EGL.
 *
 * To use do the following:
 * - Create an instance of the SurfaceMuxer class.
 * - To get an input surface create a SurfaceMuxer.InputSurface instance, this is bound to the
 *     SurfaceMuxer instance you give the constructor.
 * - Call setSize() on the InputSurface.
 * - Use .surface and/or .surfaceTexture on that to get the actual input Surface and/or
 *     SurfaceTexture texture to draw to.
 * - Create an instance of SurfaceMuxer.OutputSurface for surfaces you want to output to and call
 *     setSize() on them to set the dimensions.
 * - ThroughSurface is like InputSurface and OutputSurface in one, it can draw to itself.
 * - Call init() on the SurfaceMuxer instance, most likely you'll want to do this in onResume()
 *     because at onPause()  (or onStop()?) the EGL context can get destroyed, and in onPause()
 *     you should call deinit() to release any resources attached to the EGL context in question.
 * - Use .draw() on Input- and/or ThroughSurface(s) to draw to Output- and/or ThroughSurfaces.
 * - When done drawing to an Output- or ThroughSurface call .swapBuffers() on it to flip the
 *     buffer and make the drawn framebuffer go through, or instead getBitmap() to get the bitmap
 *     if null was passed as the Surface to create an OutputSurface.
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
public class SurfaceMuxer {
	private static class DrawMode {
		private final String file;
		private String source;
		private int program;
		private DrawMode(String file) { this.file = file; }
	}

	public final static int DM_NEAREST = 0;
	public final static int DM_LINEAR = 1;
	public final static int DM_CUBIC = 2;
	public final static int DM_CMROM = 3;
	public final static int DM_FADAPTIVE = 4;
	public final static int DM_SHARPEN = 5;
	public final static int DM_EDGE = 6;
	private final DrawMode[] drawModes = {
			new DrawMode("fnearest.glsl"),
			new DrawMode("flinear.glsl"),
			new DrawMode("fcubic.glsl"),
			new DrawMode("fcmrom.glsl"),
			new DrawMode("fadaptive.glsl"),
			new DrawMode("fsharpen.glsl"),
			new DrawMode("fedge.glsl")
	};

	private final ArrayList<Object> surfaces = new ArrayList<>();
	private static final int EGL_RECORDABLE_ANDROID = 0x3142;
	private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
	private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
	private EGLConfig eglConfig;
	private final String vss;
	private final FloatBuffer pTexCoord =
			ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	private final FloatBuffer pVertex =
			ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

	public static class InputSurface {
		private SurfaceMuxer muxer;
		public SurfaceTexture surfaceTexture;
		public Surface surface;
		private final int[] textures = new int[1];
		private boolean initialized = false;
		public int width = 1, height = 1;
		public boolean rotate = false, mirror = false, rotate90 = false;
		public float scale_x = 1.0f, scale_y = 1.0f;
		public float translate_x = 0.0f, translate_y = 0.0f;
		public float sharpening = 0.0f;

		public InputSurface(SurfaceMuxer muxer) {
			this.muxer = muxer;
			muxer.surfaces.add(this);
			init();
		}

		public void setSize(int w, int h) {
			width = w;
			height = h;
			surfaceTexture.setDefaultBufferSize(w, h);
		}

		private void init() {
			if (muxer == null || muxer.eglDisplay == EGL14.EGL_NO_DISPLAY)
				return;
			deinit();
			EGL14.eglMakeCurrent(muxer.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
					muxer.eglContext);
			muxer.checkEglError("eglMakeCurrent");
			GLES20.glGenTextures(1, textures, 0);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
					GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
					GLES20.GL_LINEAR);
			if (surfaceTexture == null) {
				surfaceTexture = new SurfaceTexture(textures[0]);
				surface = new Surface(surfaceTexture);
			} else surfaceTexture.attachToGLContext(textures[0]);
			/* After attaching to a new context we must call updateTexImage() or the
			 *   OnFrameAvailableListener may stop being called for some reason.
			 */
			surfaceTexture.updateTexImage();
			initialized = true;
		}

		private void deinit() {
			if (initialized && muxer != null && muxer.eglDisplay != EGL14.EGL_NO_DISPLAY) {
				EGL14.eglMakeCurrent(muxer.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
						muxer.eglContext);
				muxer.checkEglError("eglMakeCurrent");
				surfaceTexture.detachFromGLContext();
				GLES20.glDeleteTextures(1, textures, 0);
			}
			initialized = false;
		}

		public void release() {
			deinit();
			if (muxer != null)
				muxer.surfaces.remove(this);
			muxer = null;
			if (surface != null)
				surface.release();
			surface = null;
			if (surfaceTexture != null)
				surfaceTexture.release();
			surfaceTexture = null;
		}

		public void draw(OutputSurface os, int drawMode, int x, int y, int w, int h) {
			if (muxer.eglContext == EGL14.EGL_NO_CONTEXT)
				return;
			os.makeCurrent(); /* We need the context to be current before updateTexImage(). */
			surfaceTexture.updateTexImage(); /* Call might be redundant, probably don't care. */
			GLES20.glViewport(x, y, w, h);
			int program = muxer.drawModes[drawMode].program;
			GLES20.glUseProgram(program);
			int ph = GLES20.glGetAttribLocation(program, "vPosition");
			GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 2,
					muxer.pVertex);
			GLES20.glEnableVertexAttribArray(ph);
			int tch = GLES20.glGetAttribLocation(program, "vTexCoord");
			GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2,
					muxer.pTexCoord);
			GLES20.glEnableVertexAttribArray(tch);
			int th = GLES20.glGetUniformLocation(program, "sTexture");
			GLES20.glUniform1i(th, 0); /* Tells the shader what texture to use. */
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			int sc = GLES20.glGetUniformLocation(program, "scale");
			float sx = scale_x * (mirror ? -1.0f : 1.0f) * (rotate ? -1.0f : 1.0f);
			float sy = scale_y * (rotate ? -1.0f : 1.0f);
			GLES20.glUniform2f(sc, sx, sy);
			int tr = GLES20.glGetUniformLocation(program, "translate");
			GLES20.glUniform2f(tr, translate_x, translate_y);
			int r9 = GLES20.glGetUniformLocation(program, "rot90");
			GLES20.glUniform1i(r9, rotate90 ? 1 : 0);
			int isc = GLES20.glGetUniformLocation(program, "texSize");
			GLES20.glUniform2f(isc, width, height);
			int sh = GLES20.glGetUniformLocation(program, "sharpening");
			GLES20.glUniform1f(sh, sharpening); /* It's okay if the shader doesn't have this. */

			GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		public void draw(OutputSurface os, int drawMode) {
			draw(os, drawMode, 0, 0, os.width, os.height);
		}

		public void draw(ThroughSurface ts, int drawMode) {
			draw(ts.outputSurface, drawMode);
		}

		public void draw(ThroughSurface ts, int drawMode, int x, int y, int w, int h) {
			draw(ts.outputSurface, drawMode, x, y, w, h);
		}
	}

	public static class OutputSurface {
		private SurfaceMuxer muxer;
		private Surface surface;
		private boolean surfaceOwned;
		private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
		public int width, height;

		public OutputSurface(SurfaceMuxer muxer, Surface surf, boolean release, int w, int h) {
			init(muxer, surf, release, w, h);
		}

		public OutputSurface(SurfaceMuxer muxer, Surface surf, boolean release) {
			init(muxer, surf, release, 1, 1);
		}

		public OutputSurface(SurfaceMuxer muxer, Surface surf, int w, int h) {
			init(muxer, surf, false, w, h);
		}

		public OutputSurface(SurfaceMuxer muxer, Surface surf) { init(muxer, surf, false, 1, 1); }

		/* Give a null surface for buffer surface to get bitmaps. */
		private void init(SurfaceMuxer muxer, Surface surf, boolean release, int w, int h) {
			this.muxer = muxer;
			surface = surf;
			surfaceOwned = release;
			muxer.surfaces.add(this);
			width = w;
			height = h;
			init();
		}

		private void init() {
			deinit();
			if (muxer == null || muxer.eglDisplay == EGL14.EGL_NO_DISPLAY)
				return;
			int[] attr = { EGL14.EGL_NONE };
			if (surface != null) {
				eglSurface = EGL14.eglCreateWindowSurface(muxer.eglDisplay, muxer.eglConfig,
						surface, attr, 0);
				muxer.checkEglError("eglCreateWindowSurface");
			} else {
				attr = new int[] {
						EGL14.EGL_WIDTH, width,
						EGL14.EGL_HEIGHT, height,
						EGL14.EGL_NONE
				};
				eglSurface = EGL14.eglCreatePbufferSurface(muxer.eglDisplay, muxer.eglConfig, attr,
						0);
				muxer.checkEglError("eglCreatePbufferSurface");
			}
		}

		private void deinit() {
			if (muxer != null && muxer.eglDisplay != EGL14.EGL_NO_DISPLAY &&
					eglSurface != EGL14.EGL_NO_SURFACE) {
				EGL14.eglDestroySurface(muxer.eglDisplay, eglSurface);
				eglSurface = EGL14.EGL_NO_SURFACE;
				/* setDefaultBufferSize() requires destroying surface and making it non-current. */
				EGL14.eglMakeCurrent(muxer.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
						muxer.eglContext);
			}
		}

		public void setSize(int w, int h) {
			width = w;
			height = h;
			deinit(); /* In case setDefaultBufferSize() happened, this is important. */
			init();
		}

		public void makeCurrent() {
			if (muxer.eglContext == EGL14.EGL_NO_CONTEXT)
				return;
			EGL14.eglMakeCurrent(muxer.eglDisplay, eglSurface, eglSurface, muxer.eglContext);
			muxer.checkEglError("eglMakeCurrent");
		}

		public void swapBuffers() {
			if (muxer.eglContext == EGL14.EGL_NO_CONTEXT)
				return;
			EGL14.eglSwapBuffers(muxer.eglDisplay, eglSurface);
			muxer.checkEglError("eglSwapBuffers");
		}

		public void setPresentationTime(long nsecs) {
			if (muxer.eglContext == EGL14.EGL_NO_CONTEXT)
				return;
			EGLExt.eglPresentationTimeANDROID(muxer.eglDisplay, eglSurface, nsecs);
			muxer.checkEglError("eglPresentationTimeANDROID");
		}

		public void release() {
			deinit();
			if (muxer != null)
				muxer.surfaces.remove(this);
			if (surface != null) {
				if (surfaceOwned)
					surface.release();
				muxer = null;
				surface = null;
			}
		}

		public void clear(float r, float g, float b, float a) {
			if (muxer.eglContext == EGL14.EGL_NO_CONTEXT)
				return;
			makeCurrent();
			GLES20.glClearColor(r, g, b, a);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		}

		public Bitmap getBitmap() {
			Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
			makeCurrent();
			GLES20.glFlush();
			GLES20.glFinish();
			GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
			bmp.copyPixelsFromBuffer(buf);
			Matrix matrix = new Matrix();
			matrix.setScale(1, -1);
			Bitmap ret = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, false);
			bmp.recycle();
			return ret;
		}
	}

	public static class ThroughSurface extends InputSurface {
		private final OutputSurface outputSurface;

		public ThroughSurface(SurfaceMuxer muxer) {
			super(muxer);
			outputSurface = new OutputSurface(muxer, surface, false);
		}

		@Override
		public void setSize(int w, int h) {
			super.setSize(w, h);
			outputSurface.setSize(w, h);
		}

		@Override
		public void release() {
			super.release();
			outputSurface.release();
		}

		public void swapBuffers() { outputSurface.swapBuffers(); }
	}

	public SurfaceMuxer(Context ctx) {
		try {
			vss = Util.readStringAsset(ctx, "vshader.glsl");
			for (DrawMode dm : drawModes)
				dm.source = Util.readStringAsset(ctx, dm.file);
		} catch (IOException e) {
			/* Crash to inform the user I done did a stupid. */
			throw new RuntimeException(e);
		}

		/* Initialize vertex and texture coords. */
		float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
		pTexCoord.put(ttmp);
		pTexCoord.rewind();
		float[] vtmp = { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
		pVertex.put(vtmp);
		pVertex.rewind();

		/* Try initializing the context already. */
		init();
	}

	private void checkEglError(String msg) {
		int error;
		if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS)
			throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
	}

	private int loadShader(int type, String source) {
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, source);
		GLES20.glCompileShader(shader);
		int[] compiled = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0)
			throw new RuntimeException(GLES20.glGetShaderInfoLog(shader));
		return shader;
	}

	public void init() { /* Initialize EGL context. */
		if (eglContext != EGL14.EGL_NO_CONTEXT)
			deinit();
		eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		if (eglDisplay == EGL14.EGL_NO_DISPLAY)
			throw new RuntimeException("Unable to get EGL14 display.");
		int[] version = new int[2];
		if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
			throw new RuntimeException("Unable to initialize EGL14.");

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

		/* Compile the shaders. */
		int vshader = loadShader(GLES20.GL_VERTEX_SHADER, vss);
		for (DrawMode dm : drawModes) {
			int fshader = loadShader(GLES20.GL_FRAGMENT_SHADER, dm.source);
			dm.program = GLES20.glCreateProgram();
			GLES20.glAttachShader(dm.program, vshader);
			GLES20.glAttachShader(dm.program, fshader);
			GLES20.glLinkProgram(dm.program);
			GLES20.glDeleteShader(fshader); /* Just decreases refcount. */
		}
		GLES20.glDeleteShader(vshader); /* Shader will still live until the programs die. */

		/* Initialize any surfaces we have. */
		for (Object o : surfaces) {
			if (o instanceof InputSurface)
				((InputSurface) o).init();
			/* OutputSurfaces do not perish with the EGL context but need init if they were created
			 *   at a time no context existed.
			 */
			if (o instanceof OutputSurface)
				((OutputSurface) o).init();
		}
	}

	public void deinit() {
		for (Object o : surfaces) {
			if (o instanceof InputSurface)
				((InputSurface) o).deinit();
			/* OutputSurfaces don't need deinit, they'll live until next init(). */
		}
		if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
			EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_CONTEXT);
			/* Destroying the context will also delete textures, program, etc. */
			EGL14.eglDestroyContext(eglDisplay, eglContext);
			EGL14.eglReleaseThread();
			//EGL14.eglTerminate(eglDisplay); // TODO causes samsung crash, is it needed?
			// TODO what about releaseThread()?
		}
		eglDisplay = EGL14.EGL_NO_DISPLAY;
		eglContext = EGL14.EGL_NO_CONTEXT;
	}

	public void release() {
		while (surfaces.size() > 0) {
			Object o = surfaces.get(0);
			if (o instanceof InputSurface)
				((InputSurface) o).release();
			if (o instanceof OutputSurface)
				((OutputSurface) o).release();
			surfaces.remove(o);
		}
		deinit();
	}
}
