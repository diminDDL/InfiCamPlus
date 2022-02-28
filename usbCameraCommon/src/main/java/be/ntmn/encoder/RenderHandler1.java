package be.ntmn.encoder;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import be.ntmn.EGLBase;
import be.ntmn.widget.GLDrawer2D1;

/**
 * Draw shared texture on specific whole Surface using OpenGL|ES
 * this will deprecate soon because I don't use this now
 */
@Deprecated
public final class RenderHandler1 extends Handler {
    //	private static final boolean DEBUG = false;	// FIXME set false on release
    private static final String TAG = "RenderHandler1";

    private static final int MSG_RENDER_SET_GLCONTEXT = 1;
    private static final int MSG_RENDER_DRAW = 2;
    private static final int MSG_CHECK_VALID = 3;
    private static final int MSG_RENDER_QUIT = 9;
    private bitmapMsg mBitmapMsg;
    private int mTexId[] = {-1,-1,-1};
    private final RenderThread mThread;

    public static RenderHandler1 createHandler() {
//		if (DEBUG) Log.v(TAG, "createHandler:");
        return createHandler("RenderThread");
    }

    public static final RenderHandler1 createHandler(final String name) {
//		if (DEBUG) Log.v(TAG, "createHandler:name=" + name);
        final RenderThread thread = new RenderThread(name);
        thread.start();
        return thread.getHandler();
    }

    public final void setEglContext(final EGLBase.IContext sharedContext,
                                    final int[] tex_id, final Object surface, final boolean isRecordable) {
//		if (DEBUG) Log.i(TAG, "RenderHandler:setEglContext:");
        if (!(surface instanceof Surface)
                && !(surface instanceof SurfaceTexture)
                && !(surface instanceof SurfaceHolder))
            throw new RuntimeException("unsupported window type:" + surface);
        mTexId = tex_id;
        sendMessage(obtainMessage(MSG_RENDER_SET_GLCONTEXT,
                isRecordable ? 1 : 0, 0, new RenderHandler1.ContextParams(sharedContext, surface)));
    }

    public final void draw() {
        sendMessage(obtainMessage(MSG_RENDER_DRAW, mTexId[0], 0, null));
    }

    public final void draw(final int tex_id) {
        sendMessage(obtainMessage(MSG_RENDER_DRAW, tex_id, 0, null));
    }

    public final void draw(final float[] tex_matrix, Bitmap bitmap) {
        if(mBitmapMsg==null){
            mBitmapMsg=new bitmapMsg();
        }else{
            mBitmapMsg.tex_matrix=tex_matrix;
            mBitmapMsg.bitmap=bitmap;
        }
        sendMessage(obtainMessage(MSG_RENDER_DRAW, mTexId[0], mTexId[2], mBitmapMsg));//录像时draw
    }

    public final void draw(final int tex_id, final float[] tex_matrix) {
        sendMessage(obtainMessage(MSG_RENDER_DRAW, tex_id, 0, tex_matrix));
    }

    public boolean isValid() {
        synchronized (mThread.mSync) {
            sendEmptyMessage(MSG_CHECK_VALID);
            try {
                mThread.mSync.wait();
            } catch (final InterruptedException e) {
            }
            return mThread.mSurface != null && mThread.mSurface.isValid();
        }
    }

    public final void release() {
//		if (DEBUG) Log.i(TAG, "release:");
        removeMessages(MSG_RENDER_SET_GLCONTEXT);
        removeMessages(MSG_RENDER_DRAW);
        sendEmptyMessage(MSG_RENDER_QUIT);
    }

    @Override
    public final void handleMessage(final Message msg) {
        switch (msg.what) {
            case MSG_RENDER_SET_GLCONTEXT:
                final RenderHandler1.ContextParams params = (RenderHandler1.ContextParams)msg.obj;
                mThread.handleSetEglContext(params.sharedContext, params.surface, msg.arg1 != 0);
                break;
            case MSG_RENDER_DRAW:
                mThread.handleDraw(msg.arg1, msg.arg2,(bitmapMsg)msg.obj);
                break;
            case MSG_CHECK_VALID:
                synchronized (mThread.mSync) {
                    mThread.mSync.notify();
                }
                break;
            case MSG_RENDER_QUIT:
                Looper.myLooper().quit();
                break;
            default:
                super.handleMessage(msg);
        }
    }

    //********************************************************************************
//********************************************************************************
    private RenderHandler1(final RenderHandler1.RenderThread thread) {
//		if (DEBUG) Log.i(TAG, "RenderHandler:");
        mBitmapMsg=new bitmapMsg();
        mThread = thread;
    }

    private static final class ContextParams {
        final EGLBase.IContext sharedContext;
        final Object surface;
        public ContextParams(final EGLBase.IContext sharedContext, final Object surface) {
            this.sharedContext = sharedContext;
            this.surface = surface;
        }
    }

    /**
     * Thread to execute render methods
     * You can also use HandlerThread insted of this and create Handler from its Looper.
     */
    private static final class RenderThread extends Thread {
        private static final String TAG_THREAD = "RenderThread";
        private final Object mSync = new Object();
        private RenderHandler1 mHandler;
        private EGLBase mEgl;
        private EGLBase.IEglSurface mTargetSurface;
        private Surface mSurface;
        private GLDrawer2D1 mDrawer;

        public RenderThread(final String name) {
            super(name);
        }

        public final RenderHandler1 getHandler() {
            synchronized (mSync) {
                // create rendering thread
                try {
                    mSync.wait();
                } catch (final InterruptedException e) {
                }
            }
            return mHandler;
        }

        /**
         * Set shared context and Surface
         * @param shardContext
         * @param surface
         */
        public final void handleSetEglContext(final EGLBase.IContext shardContext,
                                              final Object surface, final boolean isRecordable) {
//    		if (DEBUG) Log.i(TAG_THREAD, "setEglContext:");
            release();
            synchronized (mSync) {
                mSurface = surface instanceof Surface ? (Surface)surface
                        : (surface instanceof SurfaceTexture
                        ? new Surface((SurfaceTexture)surface) : null);
            }
            mEgl = EGLBase.createFrom(3, shardContext, false, 0, isRecordable);
            try {
                mTargetSurface = mEgl.createFromSurface(surface);
                mDrawer = new GLDrawer2D1(isRecordable);
            } catch (final Exception e) {
                Log.w(TAG, e);
                if (mTargetSurface != null) {
                    mTargetSurface.release();
                    mTargetSurface = null;
                }
                if (mDrawer != null) {
                    mDrawer.release();
                    mDrawer = null;
                }
            }
        }

        /**
         * drawing
         * @param tex_id
         * @param ibitmapMsg
         */
        public void handleDraw(final int tex_id,final int tex_id2, bitmapMsg ibitmapMsg) {//录像的draw
//    		if (DEBUG) Log.i(TAG_THREAD, "draw");
            if (tex_id >= 0 && mTargetSurface != null) {
                 int[]tex_ids={tex_id,tex_id2};
                mTargetSurface.makeCurrent();
                mDrawer.draw(tex_ids, ibitmapMsg.tex_matrix, 0,ibitmapMsg.bitmap);
                mTargetSurface.swap();
            }
        }

        @Override
        public final void run() {
//			if (DEBUG) Log.v(TAG_THREAD, "started");
            Looper.prepare();
            synchronized (mSync) {
                mHandler = new RenderHandler1(this);
                mSync.notify();
            }
            Looper.loop();
//			if (DEBUG) Log.v(TAG_THREAD, "finishing");
            release();
            synchronized (mSync) {
                mHandler = null;
            }
//			if (DEBUG) Log.v(TAG_THREAD, "finished");
        }

        private final void release() {
//    		if (DEBUG) Log.v(TAG_THREAD, "release:");
            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            synchronized (mSync) {
                mSurface = null;
            }
            if (mTargetSurface != null) {
                clear();
                mTargetSurface.release();
                mTargetSurface = null;
            }
            if (mEgl != null) {
                mEgl.release();
                mEgl = null;
            }
        }

        /**
         * Fill black on specific Surface
         */
        private final void clear() {
//    		if (DEBUG) Log.v(TAG_THREAD, "clear:");
            mTargetSurface.makeCurrent();
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mTargetSurface.swap();
        }
    }
    class bitmapMsg{
        float[] tex_matrix;
        Bitmap bitmap;
    }

}
