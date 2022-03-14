package be.ntmn.inficam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
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
 *   may fail or produce unexpected results."). To solve this we use this class that'll take one or
 *   more input surfaces and draw them to one or more output surfaces using EGL.
 *
 * To use do the following:
 * - Create an instance of the SurfaceMuxer class.
 * - To get an input surface create a SurfaceMuxer.InputSurface instance, this is bound to the
 *     SurfaceMuxer instance you give the constructor.
 * - If you are going to use cubic interpolation or edge detection, also call setSize() on the
 *     InputSurface.
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
	public final static int IMODE_NEAREST = 0;
	public final static int IMODE_LINEAR = 1;
	public final static int IMODE_BICUBIC = 2;
	public final static int IMODE_EDGE = 3; /* Not really an interpolation mode -_o_-. */

	public final static int SMODE_FIT = 0;

	public final ArrayList<InputSurface> inputSurfaces = new ArrayList<>();
	public final ArrayList<OutputSurface> outputSurfaces = new ArrayList<>();
	private final ArrayList<Object> allSurfaces = new ArrayList<>();

	private static final int EGL_RECORDABLE_ANDROID = 0x3142;
	private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
	private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
	private EGLConfig eglConfig;
	private final FloatBuffer[][] pVertex = new FloatBuffer[2][2];
	private FloatBuffer pTexCoord;
	private int hProgram, hProgram_cubic, hProgram_edge;
	private final String vss, fss, fss_cubic, fss_edge;
	private final Rect outRect = new Rect();

	public static class InputSurface {
		private SurfaceMuxer surfaceMuxer;
		private SurfaceTexture surfaceTexture;
		private Surface surface;
		private final int[] textures = new int[1];
		private boolean initialized = false;
		private int imode, width = 1, height = 1;
		private boolean rotate = false, mirror = false;
		private int smode;

		public InputSurface(SurfaceMuxer muxer, int imode, int smode) {
			surfaceMuxer = muxer;
			this.imode = imode;
			muxer.allSurfaces.add(this);
			init();
		}

		public void setIMode(int imode) {
			this.imode = imode;
			init();
		}

		public void setSMode(int smode) {
			this.smode = smode;
			init();
		}

		public void setSize(int w, int h) {
			width = w;
			height = h;
		}

		public void getRect(Rect r, int w, int h) { /* Git rekt lol. */
			if (smode == SMODE_FIT) {
				int sw = w, sh = h;
				if (height * w / width > h)
					sw = width * h / height;
				else sh = height * w / width;
				r.set(w / 2 - sw / 2, h / 2 - sh / 2, sw, sh);
				r.right += r.left;
				r.bottom += r.top;
			} else {
				// TODO
			}
		}

		public void getRect(Rect r, OutputSurface os) { getRect(r, os.width, os.height); }

		public void setRotate(boolean rotate) { this.rotate = rotate; }
		public void setMirror(boolean mirror) { this.mirror = mirror; }

		public int getTexture() { return textures[0]; }
		public SurfaceTexture getSurfaceTexture() { return surfaceTexture; }
		public Surface getSurface() { return surface; }

		private void init() {
			int filter = (imode != IMODE_NEAREST) ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
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

		private void deinit() {
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
			deinit();
			if (surfaceMuxer != null) {
				surfaceMuxer.inputSurfaces.remove(this);
				surfaceMuxer.allSurfaces.remove(this);
			}
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
		private SurfaceMuxer surfaceMuxer;
		private Surface surface;
		private final boolean surfaceOwned;
		private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE; /* Not dependent on context. */
		private int width = 1, height = 1;

		public OutputSurface(SurfaceMuxer muxer, Surface surf, boolean release) {
			surfaceMuxer = muxer;
			surface = surf;
			surfaceOwned = release;
			muxer.allSurfaces.add(this);
			init();
		}

		private void init() {
			if (surfaceMuxer == null || surfaceMuxer.eglDisplay == EGL14.EGL_NO_DISPLAY ||
					eglSurface != EGL14.EGL_NO_SURFACE)
				return;
			int[] attr = { EGL14.EGL_NONE };
			eglSurface = EGL14.eglCreateWindowSurface(surfaceMuxer.eglDisplay,
					surfaceMuxer.eglConfig, surface, attr, 0);
			surfaceMuxer.checkEglError("eglCreateWindowSurface");
		}

		private void deinit() {
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
			if (surfaceMuxer != null) {
				surfaceMuxer.outputSurfaces.remove(this);
				surfaceMuxer.allSurfaces.remove(this);
			}
			if (surfaceOwned)
				surface.release();
			surfaceMuxer = null;
			surface = null;
		}
	}

	public SurfaceMuxer(Context ctx) {
		try {
			vss = Util.readStringAsset(ctx, "vshader.glsl");
			fss = Util.readStringAsset(ctx, "fshader.glsl");
			fss_cubic = Util.readStringAsset(ctx, "fcubic.glsl");
			fss_edge = Util.readStringAsset(ctx, "fedge.glsl");
		} catch (IOException e) {
			/* Crash to inform the user I done did a stupid. */
			throw new RuntimeException(e);
		}
		init();
	}

	private void render(int w, int h) {
		GLES20.glClearColor(0, 0, 0, 1);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		/* We use an oldschool loop because for (... : ...) causes an allocation to happen. */
		for (int i = 0; i < inputSurfaces.size(); ++i) {
			InputSurface is = inputSurfaces.get(i);

			is.getRect(outRect, w, h);
			GLES20.glViewport(outRect.left, outRect.top, outRect.width(), outRect.height());

			int program = hProgram;
			if (is.imode == IMODE_BICUBIC)
				program = hProgram_cubic;
			if (is.imode == IMODE_EDGE)
				program = hProgram_edge;
			GLES20.glUseProgram(program);
			if (program == hProgram_cubic || program == hProgram_edge) {
				int isc = GLES20.glGetUniformLocation(program, "texSize");
				GLES20.glUniform2f(isc, is.width, is.height);
			}
			int ph = GLES20.glGetAttribLocation(program, "vPosition");
			GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 2,
					pVertex[is.rotate ? 1 : 0][is.mirror ? 1 : 0]);
			GLES20.glEnableVertexAttribArray(ph);
			int tch = GLES20.glGetAttribLocation(program, "vTexCoord");
			GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
			GLES20.glEnableVertexAttribArray(tch);
			int th = GLES20.glGetUniformLocation(program, "sTexture");
			GLES20.glUniform1i(th, 0); /* Tells the shader what texture to use. */
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			//GLES20.glBlendColor(1, 1, 1, 0.1f);

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
		/* We use oldschool for loops because for (... : ...) causes an allocation to happen. */
		for (int i = 0; i < inputSurfaces.size(); ++i) {
			InputSurface is = inputSurfaces.get(i);
			is.getSurfaceTexture().updateTexImage();
		}
		for (int i = 0; i < outputSurfaces.size(); ++i) {
			OutputSurface os = outputSurfaces.get(i);
			os.makeCurrent();
			render(os.width, os.height);
			os.setPresentationTime(surfaceTexture.getTimestamp());
			os.swapBuffers();
		}
	}

	public Bitmap getBitmap(int w, int h) {
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

	private void checkEglError(String msg) {
		int error;
		if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS)
			throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
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

		/* Initialize the vertexes and textures. */
		float[] ttmp = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
		pVertex[0][0] =
				ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex[0][1] =
				ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex[1][0] =
				ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex[1][1] =
				ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

		/* Normal.*/
		float[] vtmp = new float[] { 1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f };
		pVertex[0][0].put(vtmp);
		pVertex[0][0].position(0);

		/* Mirrored. */
		vtmp = new float[] { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
		pVertex[0][1].put(vtmp);
		pVertex[0][1].position(0);

		/* Upside down. */
		vtmp = new float[] { -1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f };
		pVertex[1][0].put(vtmp);
		pVertex[1][0].position(0);

		/* Upside down and mirrored. */
		vtmp = new float[] { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
		pVertex[1][1].put(vtmp);
		pVertex[1][1].position(0);

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
			throw new RuntimeException(GLES20.glGetShaderInfoLog(fshader));

		int fshaderc = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fshaderc, fss_cubic);
		GLES20.glCompileShader(fshaderc);
		GLES20.glGetShaderiv(fshaderc, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0)
			throw new RuntimeException(GLES20.glGetShaderInfoLog(fshaderc));

		int fshadere = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
		GLES20.glShaderSource(fshadere, fss_edge);
		GLES20.glCompileShader(fshadere);
		GLES20.glGetShaderiv(fshadere, GLES20.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == 0)
			throw new RuntimeException(GLES20.glGetShaderInfoLog(fshadere));

		/* Create the program. */
		hProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(hProgram, vshader);
		GLES20.glAttachShader(hProgram, fshader);
		GLES20.glLinkProgram(hProgram);
		hProgram_cubic = GLES20.glCreateProgram();
		GLES20.glAttachShader(hProgram_cubic, vshader);
		GLES20.glAttachShader(hProgram_cubic, fshaderc);
		GLES20.glLinkProgram(hProgram_cubic);
		hProgram_edge = GLES20.glCreateProgram();
		GLES20.glAttachShader(hProgram_edge, vshader);
		GLES20.glAttachShader(hProgram_edge, fshadere);
		GLES20.glLinkProgram(hProgram_edge);
		GLES20.glDeleteShader(vshader); /* They will still live until the program dies. */
		GLES20.glDeleteShader(fshader);
		GLES20.glDeleteShader(fshaderc);
		GLES20.glDeleteShader(fshadere);

		/* Initialize any surfaces we have. */
		for (Object o : allSurfaces) {
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
		for (Object o : allSurfaces) {
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
			EGL14.eglTerminate(eglDisplay);
		}
		eglDisplay = EGL14.EGL_NO_DISPLAY;
		eglContext = EGL14.EGL_NO_CONTEXT;
	}

	public void release() {
		for (Object o : allSurfaces) {
			if (o instanceof InputSurface) {
				inputSurfaces.remove(o);
				((InputSurface) o).release();
			}
			if (o instanceof OutputSurface) {
				outputSurfaces.remove(o);
				((OutputSurface) o).release();
			}
			allSurfaces.remove(o);
		}
		deinit();
	}
}
