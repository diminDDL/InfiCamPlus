package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.Collections;

public abstract class NormalCamera {
	private Context ctx;
	private CameraDevice dev;
	private CaptureRequest.Builder req;
	private CameraCaptureSession session;
	private Surface surface;
	boolean started = false;

	CameraDevice.StateCallback devCallbacks = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(@NonNull CameraDevice cameraDevice) {
			dev = cameraDevice;
			startPreview();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice cameraDevice) {
			stop();
			onStopped();
		}

		@Override
		public void onError(@NonNull CameraDevice cameraDevice, int i) {
			stop();
			onStartFailed(ctx.getString(R.string.err_failopencamera));
		}
	};

	CameraCaptureSession.StateCallback capCallbacks = new CameraCaptureSession.StateCallback() {
		@Override
		public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
			session = cameraCaptureSession;
			req.set(CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			req.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON);
			CaptureRequest previewRequest = req.build();
			try {
				session.setRepeatingRequest(previewRequest, null, null);
			} catch (Exception e) {
				stop();
				onStartFailed(e.getMessage());
			}
			onStarted();
		}

		@Override
		public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
			stop();
			onStartFailed(ctx.getString(R.string.err_failconfigcamera));
		}
	};

	@SuppressLint("MissingPermission")
	public void start(Context ctx, Surface surf) {
		if (started)
			stop();
		started = true;
		this.ctx = ctx;
		surface = surf;
		try {
			CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
			String cameraId = manager.getCameraIdList()[0];
			manager.openCamera(cameraId, devCallbacks, null);
		} catch (Exception e) {
			stop();
			onStartFailed(e.getMessage());
		}
	}

	public void stop() {
		if (session != null) {
			try {
				session.stopRepeating();
			} catch (Exception e) {
				e.printStackTrace(); /* Not the end of the world. */
			}
		}
		session = null;
		if (dev != null)
			dev.close();
		dev = null;
		started = false;
		onStopped();
	}

	void startPreview() {
		try {
			req = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			req.addTarget(surface);
			dev.createCaptureSession(Collections.singletonList(surface), capCallbacks, null);
		} catch (Exception e) {
			stop();
			onStartFailed(e.getMessage());
		}
	}

	public abstract void onStarted();
	public abstract void onStopped();
	public abstract void onStartFailed(String message);
}
