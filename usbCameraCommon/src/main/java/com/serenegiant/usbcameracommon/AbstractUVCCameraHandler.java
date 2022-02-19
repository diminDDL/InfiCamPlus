/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameracommon;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;


import com.serenegiant.MyApp;
import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaSurfaceEncoder;
import com.serenegiant.encoder.MediaVideoBufferEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.usb.ITemperatureCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

abstract class AbstractUVCCameraHandler extends Handler {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbsUVCCameraHandler";

	public interface CameraCallback {
		public void onOpen();
		public void onClose();
		public void onStartPreview();
		public void onStopPreview();
		public void onStartRecording();
		public void onStopRecording();
		public void onError(final Exception e);
	}

	private static final int MSG_OPEN = 0;
	private static final int MSG_CLOSE = 1;
	private static final int MSG_PREVIEW_START = 2;
	private static final int MSG_PREVIEW_STOP = 3;
	private static final int MSG_CAPTURE_STILL = 4;
	private static final int MSG_CAPTURE_START = 5;
	private static final int MSG_CAPTURE_STOP = 6;
	private static final int MSG_MEDIA_UPDATE = 7;
	private static final int MSG_RELEASE = 9;
	private static final int MSG_TEMPERATURE_START=10;
	private static final int MSG_TEMPERATURE_STOP=11;
	private static final int MSG_ON_RECEIVE_TEMPERATURE=12;
	private static final int MSG_CHANGE_PALETTE=13;
	private static final int MSG_SET_TEMPRANGE=14;
	private static final int MSG_SET_SHUTTERFIX=28;
	private static final int MSG_OPEN_SYS_CAMERA=16;
	private static final int MSG_CLOSE_SYS_CAMERA=17;
    private static final int MSG_SET_HIGHTHROW=18;
    private static final int MSG_SET_LOWTHROW=19;
    private static final int MSG_SET_HIGHPLAT=20;
    private static final int MSG_SET_LOWPLAT=21;
    private static final int MSG_SET_ORGSUBGSHIGH=22;
    private static final int MSG_SET_ORGSUBGSLOW=23;
    private static final int MSG_SET_SIGMAD=24;
    private static final int MSG_SET_SIGMAR=25;
	private static final int MSG_RELAYOUT=26;
    private static final int MSG_WATERMARK_ONOFF=27;
	private final WeakReference<AbstractUVCCameraHandler.CameraThread> mWeakThread;
	private volatile boolean mReleased;

	protected AbstractUVCCameraHandler(final CameraThread thread) {
		mWeakThread = new WeakReference<CameraThread>(thread);
	}

	public int getWidth() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getWidth() : 0;
	}

	public int getHeight() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getHeight() : 0;
	}

	public boolean isOpened() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isCameraOpened();
	}
	public byte [] getTemperaturePara(int len) {
		final CameraThread thread = mWeakThread.get();
		if((thread != null)&&(thread.mUVCCamera)!=null) {
			return thread.mUVCCamera.getByteArrayTemperaturePara(len);
		}
		else{
			byte[] para=new byte[len];
			return para;
		}
	}

    public int getHighThrow() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getHighThrow();
        }
        return 0;
    }
    public int getLowThrow() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getLowThrow();
        }
        return 0;
    }
    public int getHighPlat() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getHighPlat();
        }
        return 0;
    }
    public int getLowPlat() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getLowPlat();
        }
        return 0;
    }
    public int getOrgSubGsHigh() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getOrgSubGsHigh();
        }
        return 0;
    }
    public int getOrgSubGsLow() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getOrgSubGsLow();
        }
        return 0;
    }
    public float getSigmaD() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getSigmaD();
        }
        return 0;
    }
    public float getSigmaR() {
        final CameraThread thread = mWeakThread.get();
        if((thread != null)&&(thread.mUVCCamera)!=null) {
            //return thread.mUVCCamera.getSigmaR();
        }
        return 0;
    }


	public boolean isPreviewing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isPreviewing();
	}

	public boolean isRecording() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isRecording();
	}

	public boolean isTemperaturing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isTemperaturing();
	}

	public boolean isEqual(final UsbDevice device) {
		final CameraThread thread = mWeakThread.get();
		return (thread != null) && thread.isEqual(device);
	}

	protected boolean isCameraThread() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && (thread.getId() == Thread.currentThread().getId());
	}

	protected boolean isReleased() {
		final CameraThread thread = mWeakThread.get();
		return mReleased || (thread == null);
	}

	protected void checkReleased() {
		if (isReleased()) {
			throw new IllegalStateException("already released");
		}
	}

	public void open(final USBMonitor.UsbControlBlock ctrlBlock) {
		checkReleased();
		sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
	}

	public void close() {
		if (DEBUG) Log.v(TAG, "close:");
		if (isOpened()) {
			//stopPreview();
			sendEmptyMessage(MSG_CLOSE);
		}
		if (DEBUG) Log.v(TAG, "close:finished");
	}

	public void resize(final int width, final int height) {
		checkReleased();
		throw new UnsupportedOperationException("does not support now");
	}

	protected void startPreview(final Object surface) {
		checkReleased();
	//	if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
	//		throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture");
	//	}
		sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
	}

	public void stopPreview() {
		if (DEBUG) Log.v(TAG, "stopPreview:");
		removeMessages(MSG_PREVIEW_START);
		stopRecording();
		if (isPreviewing()) {
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			synchronized (thread.mSync) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				if (!isCameraThread()) {
					// wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
					// while preview is still running.
					// therefore this method will take a time to execute
					try {
						thread.mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
			}
		}
		if (DEBUG) Log.v(TAG, "stopPreview:finished");
	}

	protected void captureStill() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_STILL);
	}

	protected void captureStill(final String path) {
		checkReleased();
		sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
	}

	public void startRecording() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_START);
	}


	public void stopRecording() {
		sendEmptyMessage(MSG_CAPTURE_STOP);
	}
	public void startTemperaturing() {
		checkReleased();
		sendEmptyMessage(MSG_TEMPERATURE_START);
	}
	public void setTempRange(int range){
		Message message = Message.obtain();
		message.what = MSG_SET_TEMPRANGE;
		message.arg1 = range;
		sendMessage(message);
	}

	public void setShutterFix(float mShutterFix){
		Message message = Message.obtain();
		message.what = MSG_SET_SHUTTERFIX;
		message.obj = mShutterFix;
		sendMessage(message);
	}

	public void relayout(int rotate){
		Message message = Message.obtain();
		message.what = MSG_RELAYOUT;
		message.arg1 = rotate;
		sendMessage(message);
	}
    public void watermarkOnOff(int isWatermaker){
        Message message = Message.obtain();
        message.what = MSG_WATERMARK_ONOFF;
        message.arg1 = isWatermaker;
        sendMessage(message);
    }
    public void setHighThrow(int inputHighThrow){
        Message message = Message.obtain();
        message.what = MSG_SET_HIGHTHROW;
        message.arg1 = inputHighThrow;
        sendMessage(message);
    }
    public void setLowThrow(int inputLowThrow){
        Message message = Message.obtain();
        message.what = MSG_SET_LOWTHROW;
        message.arg1 = inputLowThrow;
        sendMessage(message);
    }
    public void setHighPlat(int inputHighPlat){
        Message message = Message.obtain();
        message.what = MSG_SET_HIGHPLAT;
        message.arg1 = inputHighPlat;
        sendMessage(message);
    }
    public void setLowPlat(int inputLowPlat){
        Message message = Message.obtain();
        message.what = MSG_SET_LOWPLAT;
        message.arg1 = inputLowPlat;
        sendMessage(message);
    }
    public void setSigmaD(int inputSigmaD){
        Message message = Message.obtain();
        message.what = MSG_SET_SIGMAD;
        message.arg1 = inputSigmaD;
        sendMessage(message);
    }
    public void setSigmaR(int inputSigmaR){
        Message message = Message.obtain();
        message.what = MSG_SET_SIGMAR;
        message.arg1 = inputSigmaR;
        sendMessage(message);
    }
    public void setOrgSubGsHigh(int inputOrgSubGsHigh){
        Message message = Message.obtain();
        message.what = MSG_SET_ORGSUBGSHIGH;
        message.arg1 = inputOrgSubGsHigh;
        sendMessage(message);
    }
    public void setOrgSubGsLow(int inputOrgSubGsLow){
        Message message = Message.obtain();
        message.what = MSG_SET_ORGSUBGSLOW;
        message.arg1 = inputOrgSubGsLow;
        sendMessage(message);
    }
	public void stopTemperaturing() {
		sendEmptyMessage(MSG_TEMPERATURE_STOP);
	}
	public  void changePalette(int typeOfPalette){
		Message message = Message.obtain();
		message.what = MSG_CHANGE_PALETTE;
		message.arg1 = typeOfPalette;
		sendMessage(message);

	}
	public void openSystemCamera(){
        sendEmptyMessage(MSG_OPEN_SYS_CAMERA);
	}
	public void closeSystemCamera(){
		sendEmptyMessage(MSG_CLOSE_SYS_CAMERA);
	}
	public void release() {
		mReleased = true;
		close();
		sendEmptyMessage(MSG_RELEASE);
	}

	public void addCallback(final CameraCallback callback) {
		checkReleased();
		if (!mReleased && (callback != null)) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.add(callback);
			}
		}
	}

	public void removeCallback(final CameraCallback callback) {
		if (callback != null) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.remove(callback);
			}
		}
	}

	protected void updateMedia(final String path) {
		sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
	}

	public int getValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	public int setValue(final int flag, final int value) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera =(thread != null ? thread.mUVCCamera : null);
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.setBrightness(value);
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.setContrast(value);
				return camera.getContrast();
			}else if (flag==UVCCamera.CTRL_ZOOM_ABS)
			{
				camera.setZoom(value);
				return 1;
			}
		}
		return 100;
	}

	public void whenShutRefresh() {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera =(thread != null ? thread.mUVCCamera : null);
		if (camera != null) {
			camera.whenShutRefresh();
		}
	}

	public void whenChangeTempPara() {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera =(thread != null ? thread.mUVCCamera : null);
		if (camera != null) {
			camera.whenChangeTempPara();
		}
	}

	public int resetValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.resetBrightness();
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.resetContrast();
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	@Override
	public void handleMessage(final Message msg) {
		final CameraThread thread = mWeakThread.get();
		if (thread == null) return;
		switch (msg.what) {
			case MSG_OPEN:
				thread.handleOpen((USBMonitor.UsbControlBlock)msg.obj);
				break;
			case MSG_CLOSE:
				thread.handleClose();
				break;
			case MSG_PREVIEW_START:
				thread.handleStartPreview(msg.obj);
				break;
			case MSG_PREVIEW_STOP:
				thread.handleStopPreview();
				break;
			case MSG_CAPTURE_STILL:
				thread.handleCaptureStill((String)msg.obj);
				break;
			case MSG_CAPTURE_START:
				thread.handleStartRecording();
				break;
			case MSG_CAPTURE_STOP:
				thread.handleStopRecording();
				break;
			case MSG_TEMPERATURE_START:
				thread.handleStartTemperaturing();
				break;
			case MSG_SET_TEMPRANGE:
				int range=msg.arg1;
				thread.handleSetTempRange(range);
				break;
			case MSG_SET_SHUTTERFIX:
				float mShutterFix= (float) msg.obj;
				thread.handleSetShutterFix(mShutterFix);
				break;
            case MSG_RELAYOUT:
                int rotate=msg.arg1;
                thread.handleRelayout(rotate);
                break;
            case MSG_WATERMARK_ONOFF:
                boolean isWatermaker;
                isWatermaker=(msg.arg1>0);
                Log.e(TAG, "handleMessage isWatermaker: "+isWatermaker );
                thread.handleWatermarkOnOff(isWatermaker);
                break;
            case MSG_SET_HIGHTHROW:
                int inputHighThrow=msg.arg1;
                thread.handleSetHighThrow(inputHighThrow);
                break;
            case MSG_SET_LOWTHROW:
                int inputLowThrow=msg.arg1;
                thread.handleSetLowThrow(inputLowThrow);
                break;
            case MSG_SET_HIGHPLAT:
                int inputHighPlat=msg.arg1;
                thread.handleSetHighPlat(inputHighPlat);
                break;
            case MSG_SET_LOWPLAT:
                int inputLowPlat=msg.arg1;
                thread.handleSetLowPlat(inputLowPlat);
                break;
            case MSG_SET_ORGSUBGSHIGH:
                int inputOrgSubGsHigh=msg.arg1;
                thread.handleSetOrgSubGsHigh(inputOrgSubGsHigh);
                break;
            case MSG_SET_ORGSUBGSLOW:
                int inputOrgSubGsLow=msg.arg1;
                thread.handleSetOrgSubGsLow(inputOrgSubGsLow);
                break;
            case MSG_SET_SIGMAD:
                int sigmaD=msg.arg1;
                float inputSigmaD=sigmaD/10.0f;
                thread.handleSetSigmaD(inputSigmaD);
                break;
            case MSG_SET_SIGMAR:
                int sigmaR=msg.arg1;
                float inputSigmaR=sigmaR/10.0f;
                thread.handleSetSigmaR(inputSigmaR);
                break;

			case MSG_TEMPERATURE_STOP:
				thread.handleStopTemperaturing();
				break;
			case MSG_MEDIA_UPDATE:
				thread.handleUpdateMedia((String)msg.obj);
				break;
			case MSG_RELEASE:
				thread.handleRelease();
				break;
			case MSG_CHANGE_PALETTE:
				int typeOfPalette=msg.arg1;
				thread.handleChangePalette(typeOfPalette);
				break;
            case MSG_OPEN_SYS_CAMERA:
                thread.handleOpenSysCamera();
                break;
			case MSG_CLOSE_SYS_CAMERA:
				thread.handleCloseSysCamera();
				break;
			default:
				throw new RuntimeException("unsupported message:what=" + msg.what);
		}
	}

	static final class CameraThread extends Thread {
		private static final String TAG_THREAD = "CameraThread";
		private final Object mSync = new Object();
		private final Class<? extends AbstractUVCCameraHandler> mHandlerClass;
		private final WeakReference<Activity> mWeakParent;
		private final WeakReference<UVCCameraTextureView> mWeakCameraView;
		private final int mEncoderType;
		private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();
		private int mWidth, mHeight, mPreviewMode;
		private float mBandwidthFactor;
		private boolean mIsPreviewing;
		private boolean mIsTemperaturing;
        private boolean mIsCapturing;
		private boolean mIsRecording;
		public  ITemperatureCallback CameraThreadTemperatureCallback;
		/**
		 * shutter sound
		 */
		private SoundPool mSoundPool;
		private int mSoundId;
		private AbstractUVCCameraHandler mHandler;
		/**
		 * for accessing UVC camera
		 */
		private UVCCamera mUVCCamera;
		/**
		 * muxer for audio/video recording
		 */
		private MediaMuxerWrapper mMuxer;
		private MediaVideoBufferEncoder mVideoEncoder;
		/**
		 *
		 * @param clazz Class extends AbstractUVCCameraHandler
		 * @param parent parent Activity
		 * @param cameraView for still capturing
		 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
		 * @param width
		 * @param height
		 * @param format either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
		 * @param bandwidthFactor
		 */
		CameraThread(final Class<? extends AbstractUVCCameraHandler> clazz,
					 final Activity parent, final UVCCameraTextureView cameraView,
					 final int encoderType, final int width, final int height, final int format,
					 final float bandwidthFactor,ITemperatureCallback temperatureCallback) {

			super("CameraThread");
			mHandlerClass = clazz;
			mEncoderType = encoderType;
			//mEncoderType=2;
			mWidth = width;//探测器的面阵
			mHeight = height;
			System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl");
			System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");
			System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl");
			CameraThreadTemperatureCallback=temperatureCallback;
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<UVCCameraTextureView>(cameraView);
			loadShutterSound(parent);
		}
		private  float[] temperatureData=new float[640*512+10];
		private byte[] ByteTemperatureData=new byte[(640*512+10)*4];
		private short[] ShortTemperatureData=new short[640*512+10];
		private Handler mMySubHandler;
		@Override
		protected void finalize() throws Throwable {
			Log.i(TAG, "CameraThread#finalize");
			super.finalize();
		}

		public AbstractUVCCameraHandler getHandler() {
			if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
			synchronized (mSync) {
				if (mHandler == null)
					try {
						mSync.wait();
					} catch (final InterruptedException e) {
					}
			}
			return mHandler;
		}

		public int getWidth() {
			synchronized (mSync) {
				return mWidth;
			}
		}

		public int getHeight() {
			synchronized (mSync) {
				return mHeight;
			}
		}

		public boolean isCameraOpened() {
			synchronized (mSync) {
				return mUVCCamera != null;
			}
		}

		public boolean isTemperaturing() {
			synchronized (mSync) {
				return mUVCCamera != null && mIsTemperaturing;
			}
		}
		public boolean isPreviewing() {
			synchronized (mSync) {
				return mUVCCamera != null && mIsPreviewing;
			}
		}

		public boolean isRecording() {
			synchronized (mSync) {
				return (mUVCCamera != null) && (mMuxer != null);
			}
		}

		public boolean isEqual(final UsbDevice device) {
			return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
		}

		public void handleOpen(final USBMonitor.UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
		//	handleClose();
			try {
				final UVCCamera camera;
				camera = new UVCCamera();

				camera.open(ctrlBlock);
				synchronized (mSync) {
					mUVCCamera = camera;
				}
				callOnOpen();
			} catch (final Exception e) {
				callOnError(e);
			}
			String mSupportedSize = mUVCCamera.getSupportedSize();
			int find_str_postion= mSupportedSize.indexOf("384x292");
			if(find_str_postion>=0){
				mWidth=384;
				mHeight=292;
                Log.e(TAG, "handleOpen: 384 DEVICE " );
			}
			find_str_postion= mSupportedSize.indexOf("240x184");
            if(find_str_postion>=0){
				mWidth=240;
				mHeight=184;
                Log.e(TAG, "handleOpen: 240 DEVICE " );
			}
            find_str_postion= mSupportedSize.indexOf("256x196");
            if(find_str_postion>=0){
                mWidth=256;
                mHeight=196;
                Log.e(TAG, "handleOpen: 256 DEVICE " );
            }
			find_str_postion= mSupportedSize.indexOf("640x516");
			if(find_str_postion>=0){
				mWidth=640;
				mHeight=516;
				Log.e(TAG, "handleOpen: 640 DEVICE " );
			}
			if (DEBUG) Log.i(TAG, "supportedSize:" + (mUVCCamera != null ? mUVCCamera.getSupportedSize() : null));
		}

		public void handleClose() {
			//if (DEBUG)
			    Log.e(TAG_THREAD, "handleClose:");
			//handleStopTemperaturing();
			//handleStopRecording();
            if(mIsRecording){
                mIsRecording=false;
                handleStopRecording();
            }
            if(mIsTemperaturing){
                mIsTemperaturing=false;
                handleStopTemperaturing();
            }
			final UVCCamera camera;
			synchronized (mSync) {
				camera = mUVCCamera;
				mUVCCamera = null;
			}
			if (camera != null) {
//				camera.stopPreview();
				mIsPreviewing=false;
				camera.destroy();
				callOnClose();
			}
		}

		public void handleStartPreview(final Object surface) {
            Log.e(TAG, "handleStartPreview:mUVCCamera"+mUVCCamera+" mIsPreviewing:"+mIsPreviewing);
			if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
			if ((mUVCCamera == null) || mIsPreviewing) return;
			Log.e(TAG, "handleStartPreview2 ");
			try {
				mUVCCamera.setPreviewSize(mWidth, mHeight, 1, 26, mPreviewMode, mBandwidthFactor);
				Log.e(TAG, "handleStartPreview3 mWidth: "+mWidth+"mHeight:"+mHeight);
			} catch (final IllegalArgumentException e) {
				try {
					// fallback to YUV mode
					mUVCCamera.setPreviewSize(mWidth, mHeight, 1,26, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
					Log.e(TAG, "handleStartPreview4");
				} catch (final IllegalArgumentException e1) {
					callOnError(e1);
					return;
				}
			}
			if (surface instanceof SurfaceHolder) {
				Log.e(TAG, "SurfaceHolder:" );
				mUVCCamera.setPreviewDisplay((SurfaceHolder)surface);
			}else if (surface instanceof Surface) {
				Log.e(TAG, "Surface:" );
				mUVCCamera.setPreviewDisplay((Surface)surface);
			} else if(surface instanceof SurfaceTexture){
				Log.e(TAG, "SurfaceTexture:" );
				mUVCCamera.setPreviewTexture((SurfaceTexture)surface);
			}
            Log.e(TAG, "handleStartPreview: startPreview1" );
			mUVCCamera.startPreview();
			Log.e(TAG, "handleStartPreview: startPreview2" );

			/*===========================================================================
			 * if need rgba callback
			 *set this setFrameCallback(...) function
			 *==========================================================================*/
//			mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV);
//            mIsCapturing=true;
//			mUVCCamera.startCapture();

			/*===========================================================================
			 * if need Temperature callback
			 *set this setTemperatureCallback(...) function
			 *==========================================================================*/
			mWeakCameraView.get().setSuportWH(mWidth,mHeight);
			ITemperatureCallback mTempCb= mWeakCameraView.get().getTemperatureCallback();
			mUVCCamera.setTemperatureCallback(mTempCb);
			mWeakCameraView.get().setTemperatureCbing(false);
			Boolean isT3 = MyApp.deviceName.contains("DL")|| MyApp.deviceName.contains("DV");
			if(isT3){
				mWeakCameraView.get().setRotation(180);
			}
			synchronized (mSync) {
				mIsPreviewing = true;
			}
			callOnStartPreview();
		}

		public void handleStopPreview() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
			if (mIsPreviewing) {
				if (mUVCCamera != null) {

					mUVCCamera.stopPreview();
				}
				synchronized (mSync) {
					mIsPreviewing = false;
					mSync.notifyAll();
				}
				callOnStopPreview();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
		}

		public void handleCaptureStill(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
			final Activity parent = mWeakParent.get();
			if (parent == null) return;
			mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
			try {
				final Bitmap bitmap = mWeakCameraView.get().captureStillImage();
				if(mIsTemperaturing) {
					temperatureData = mWeakCameraView.get().GetTemperatureData();
					for(int j=10;j<(mWidth*(mHeight-4)+10);j++){
						ShortTemperatureData[j]=(short)(temperatureData[j]*10+2731);
					}
                    ShortTemperatureData[0]=(short)mWidth;
                    ShortTemperatureData[1]=(short)(mHeight-4);
					for (int i = 0; i < (mWidth*(mHeight-4)+10); i++) {
						short curshort= ShortTemperatureData[i];
                        ByteTemperatureData[2*i]=(byte)  ( (curshort>>0)& 0b1111_1111);
                        ByteTemperatureData[2*i+1]=(byte)  ( (curshort>>8)& 0b1111_1111);
					}

				}
				// get buffered output stream for saving a captured still image as a file on external storage.
				// the file name is came from current time.
				// You should use extension name as same as CompressFormat when calling Bitmap#compress.
				final File outputFile = TextUtils.isEmpty(path)
						? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
						: new File(path);
				final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
				try {
					try {
						bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
						if(mIsTemperaturing) {
							os.write(ByteTemperatureData,0,mWidth*(mHeight-4)*2+20);//添加温度数据
						}
						os.flush();

						mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));


					} catch (final IOException e) {
					}
				} finally {
					os.close();
				}
			//	if(mIsTemperaturing) {
			//		String NewPath = outputFile.getPath();
			//		PngUtil.wirteByteArrayToPng(NewPath, ByteTemperatureData,NewPath );
			//	}
			} catch (final Exception e) {
				callOnError(e);
			}
		}

		public void handleStartRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
			try {
				if ((mUVCCamera == null) || (mMuxer != null)) return;
				final MediaMuxerWrapper muxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
				MediaVideoBufferEncoder videoEncoder = null;
				switch (mEncoderType) {
					case 1:	// for video capturing using MediaVideoEncoder
						//new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
						new MediaVideoEncoder(muxer, mWeakCameraView.get().getWidth(), mWeakCameraView.get().getHeight(), mMediaEncoderListener);
						break;
					case 2:	// for video capturing using MediaVideoBufferEncoder
						videoEncoder = new MediaVideoBufferEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
						//videoEncoder = new MediaVideoBufferEncoder(muxer, 384, 288, mMediaEncoderListener);
						break;
					// case 0:	// for video capturing using MediaSurfaceEncoder
					default:
						new MediaSurfaceEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
						break;
				}
				if (true) {
					 //for audio capturing
					new MediaAudioEncoder(muxer, mMediaEncoderListener);
				}
				muxer.prepare();
				muxer.startRecording();
				if (videoEncoder != null) {
					Log.e(TAG, "setFrameCallback ");
					//mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV);
				}
				synchronized (mSync) {
					mMuxer = muxer;
					mVideoEncoder = videoEncoder;
				}
				callOnStartRecording();
			} catch (final IOException e) {
				callOnError(e);
				Log.e(TAG, "startCapture:", e);
			}
		}



		public  void handleStartTemperaturing() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartTemperaturing:");

			if ((mUVCCamera == null) || mIsTemperaturing) return;
			mIsTemperaturing=true;
			mUVCCamera.startTemp();
			mWeakCameraView.get().setTemperatureCbing(true);
		}
        public  void handleRelayout(int rotate){
            if (mUVCCamera == null)  return;
            mWeakCameraView.get().relayout(rotate);
        }
		public  void handleWatermarkOnOff(boolean isWatermaker){
            Log.e(TAG, "handleWatermarkOnOff isWatermaker: "+isWatermaker);
			mWeakCameraView.get().watermarkOnOff(isWatermaker);
		}
		public void handleOpenSysCamera(){
			mWeakCameraView.get().openSysCamera();
        }
		public void handleCloseSysCamera(){
			mWeakCameraView.get().closeSysCamera();
		}

		public void handleStopTemperaturing() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopTemperaturing:");
			if ((mUVCCamera == null) ){
				return;
			}
			mIsTemperaturing=false;
			mWeakCameraView.get().setTemperatureCbing(false);
			mUVCCamera.stopTemp();
		}

		public void handleStopRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
			final MediaMuxerWrapper muxer;
			synchronized (mSync) {
				muxer = mMuxer;
				mMuxer = null;
				mVideoEncoder = null;
				//if (mUVCCamera != null) {
				//	mUVCCamera.stopCapture();
				//}
			}
			try {
				mWeakCameraView.get().setVideoEncoder(null);
			} catch (final Exception e) {
				// ignore
			}
			if (muxer != null) {
				muxer.stopRecording();
				//mUVCCamera.setFrameCallback(null, 0);
				// you should not wait here
				callOnStopRecording();
			}
		}




		public void handleUpdateMedia(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
			final Activity parent = mWeakParent.get();
			final boolean released = (mHandler == null) || mHandler.mReleased;
			if (parent != null && parent.getApplicationContext() != null) {
				try {
					if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
					MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, ScanCompletedListener);
				} catch (final Exception e) {
					Log.e(TAG, "handleUpdateMedia:", e);
				}
				if (released || parent.isDestroyed()) {
					handleRelease();
				}
				/*	if(mIsTemperaturing) {
						String NewPath = "storage/emulated/0/DCIM/XthermDemo/out.png";
						try {
							PngUtil.wirteByteArrayToPng(path, ByteTemperatureData, path);
							try {
								MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
							} catch (final Exception e) {
							}
						} catch (final Exception e) {
					Log.e(TAG, "handleUpdateMedia wirteByteArrayToPng:", e);
				}
					}*/
			} else {
				Log.w(TAG, "MainActivity already destroyed");
				// give up to add this movie to MediaStore now.
				// Seeing this movie on Gallery app etc. will take a lot of time.
				handleRelease();
			}
		}

		MediaScannerConnection.OnScanCompletedListener ScanCompletedListener =
				new MediaScannerConnection.OnScanCompletedListener() {
					@Override
					public void onScanCompleted(String path, Uri uri) {
					/*	final Activity parent = mWeakParent.get();
						final boolean released = (mHandler == null) || mHandler.mReleased;
						if (parent != null && parent.getApplicationContext() != null) {
							if (released || parent.isDestroyed()) {
								handleRelease();
							}
							if(mIsTemperaturing) {
								String[] SplitArray=path.split("\\.");
								String NewPath = SplitArray[0]+"IR.png";
								try {
									PngUtil.wirteByteArrayToPng(path, ByteTemperatureData, NewPath);
										try {
											MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ NewPath }, null, null);
										} catch (final Exception e) {
										}
								} catch (final Exception e) {
									Log.e(TAG, "handleUpdateMedia wirteByteArrayToPng:", e);
								}
								File OldPhoto=new File(path);
								if(OldPhoto.isFile() && OldPhoto.exists()) {
									Boolean succeedDelete = OldPhoto.delete();
									if(succeedDelete){
										try {
											MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
										} catch (final Exception e) {
										}
									}

								}
							}
						} else {
							Log.w(TAG, "MainActivity already destroyed");
							// give up to add this movie to MediaStore now.
							// Seeing this movie on Gallery app etc. will take a lot of time.
							handleRelease();
						}*/
					}
				};

		public void handleRelease() {
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
			handleClose();
			mCallbacks.clear();
			if (!mIsRecording) {
				mHandler.mReleased = true;
				Looper.myLooper().quit();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
		}

		private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
			@Override
			public void onPrepared(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
                Log.e(TAG, "onPrepared: mIsRecording:"+mIsRecording);
				mIsRecording = true;
				if (encoder instanceof MediaVideoEncoder)
					try {
						mWeakCameraView.get().setVideoEncoder((MediaVideoEncoder)encoder);
					} catch (final Exception e) {
						Log.e(TAG, "onPrepared:", e);
					}
				/*if (encoder instanceof MediaSurfaceEncoder)
					try {
						mWeakCameraView.get().setVideoEncoder((MediaSurfaceEncoder)encoder);
						mUVCCamera.startCapture(((MediaSurfaceEncoder)encoder).getInputSurface());
					} catch (final Exception e) {
						Log.e(TAG, "onPrepared:", e);
					}*/
			}

			@Override
			public void onStopped(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
				if ((encoder instanceof MediaVideoEncoder)
						|| (encoder instanceof MediaSurfaceEncoder))
					try {
						mIsRecording = false;
						final Activity parent = mWeakParent.get();
						mWeakCameraView.get().setVideoEncoder(null);
						final String path = encoder.getOutputPath();
						if (!TextUtils.isEmpty(path)) {
							mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
						} else {
							final boolean released = (mHandler == null) || mHandler.mReleased;
							if (released || parent == null || parent.isDestroyed()) {
								handleRelease();
							}
						}
					} catch (final Exception e) {
						Log.e(TAG, "onPrepared:", e);
					}
			}
		};

		/**
		 * prepare and load shutter sound for still image capturing
		 */
		@SuppressWarnings("deprecation")
		private void loadShutterSound(final Context context) {
			// get system stream type using reflection
			int streamType;
			try {
				final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
				final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
				streamType = sseField.getInt(null);
			} catch (final Exception e) {
				streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
			}
			if (mSoundPool != null) {
				try {
					mSoundPool.release();
				} catch (final Exception e) {
				}
				mSoundPool = null;
			}
			// load shutter sound from resource
			mSoundPool = new SoundPool(2, streamType, 0);
			mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
		}

		@Override
		public void run() {
			Looper.prepare();
			AbstractUVCCameraHandler handler = null;
			try {
				final Constructor<? extends AbstractUVCCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
				handler = constructor.newInstance(this);
			} catch (final NoSuchMethodException e) {
				Log.w(TAG, e);
			} catch (final IllegalAccessException e) {
				Log.w(TAG, e);
			} catch (final InstantiationException e) {
				Log.w(TAG, e);
			} catch (final InvocationTargetException e) {
				Log.w(TAG, e);
			}
			if (handler != null) {
				synchronized (mSync) {
					mHandler = handler;
					mSync.notifyAll();
				}
				Looper.loop();
				if (mSoundPool != null) {
					mSoundPool.release();
					mSoundPool = null;
				}
				if (mHandler != null) {
					mHandler.mReleased = true;
				}
			}
			mCallbacks.clear();
			synchronized (mSync) {
				mHandler = null;
				mSync.notifyAll();
			}
		}

		private void callOnOpen() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onOpen();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnClose() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onClose();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStartRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnStopRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		private void callOnError(final Exception e) {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onError(e);
				} catch (final Exception e1) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		public void handleChangePalette(int typeOfPalette) {
			if ((mUVCCamera == null) ){
				return;
			}
			mUVCCamera.changePalette(typeOfPalette);
		}
		public void handleSetTempRange(int range) {
			if ((mUVCCamera == null) ){
				return;
			}
			mUVCCamera.setTempRange(range);
		}
		public void handleSetShutterFix(float mShutterFix) {
			if ((mUVCCamera == null) ){
				return;
			}
			mUVCCamera.setShutterFix(mShutterFix);
		}
		public void handleSetHighThrow(int inputHighThrow) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setHighThrow(inputHighThrow);
		}
		public void handleSetLowThrow(int inputLowThrow) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setLowThrow(inputLowThrow);
		}
		public void handleSetLowPlat(int inputLowPlat) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setLowPlat(inputLowPlat);
		}
		public void handleSetHighPlat(int inputHighPlat) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setHighPlat(inputHighPlat);
		}
		public void handleSetOrgSubGsHigh(int inputOrgSubGsHigh) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setOrgSubGsHigh(inputOrgSubGsHigh);
		}
		public void handleSetOrgSubGsLow(int inputOrgSubGsLow) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setOrgSubGsLow(inputOrgSubGsLow);
		}
		public void handleSetSigmaD(float inputSigmaD) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setSigmaD(inputSigmaD);
		}
		public void handleSetSigmaR(float inputSigmaR) {
			if ((mUVCCamera == null) ){
				return;
			}
			//mUVCCamera.setSigmaR(inputSigmaR);
		}
	}
}
