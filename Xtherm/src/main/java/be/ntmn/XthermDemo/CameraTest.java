package be.ntmn.XthermDemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.util.Arrays;

public class CameraTest {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "mainactivity";

    ///In order to display the photos vertically
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraManager mCameraManager;//Camera Manager
    private Handler childHandler, mainHandler;
    private String mCameraID;//Camera ID 0 is the back and 1 is the front
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private Surface surface;

    public void initCamera2(Context ctx, Surface surf) {
        surface = surf;
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;//rear camera
        mCameraManager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //Turn on the camera
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Camera creation monitoring
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {//Open the camera
            mCameraDevice = camera;
            //Open preview
            takePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {//Close the camera
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {//An error occurred
            //Toast.makeText(MainActivity.this, "Camera opening failed", Toast.LENGTH_SHORT).show();
            Log.e("FAIL", "camera error");
        }
    };

    /**
     * Start preview
     */
    private void takePreview() {
        try {
            //Create CaptureRequest.Builder needed for preview
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //Use the surface of SurfaceView as the target of CaptureRequest.Builder
            previewRequestBuilder.addTarget(surface);
            //Create CameraCaptureSession, which is responsible for managing and processing preview requests and camera requests
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback()//③
            {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) return;
                    //When the camera is ready, start to display the preview
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        //auto focus
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        //Turn on the flash
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        //Show preview
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    //Toast.makeText(MainActivity.this, "Configuration failed", Toast.LENGTH_SHORT).show();
                    Log.e("FAIL", "fail to create preview");
                    // TODO
                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
