package be.ntmn.inficam;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

public class MainActivity extends FullscreenActivity {
    SurfaceMuxer surfaceMuxer = new SurfaceMuxer();
    USBConnector usbConnector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView sv = findViewById(R.id.cameraView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                surfaceMuxer.addOutputSurface(surfaceHolder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                // TODO
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                // TODO
            }
        });
        SurfaceTexture ist2 = surfaceMuxer.createInputSurfaceTexture();
        SurfaceTexture ist = surfaceMuxer.createInputSurfaceTexture();
        ist.setDefaultBufferSize(1280, 960);
        askPermission(Manifest.permission.CAMERA, isGranted -> {
            if (isGranted) {
                CameraTest ct = new CameraTest();
                ct.initCamera2(this, new Surface(ist));
                usbConnector.tryConnect(); /* Connecting to a UVC device needs camera permission. */
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show();
            }
        });
        //ist.setOnFrameAvailableListener(surfaceMuxer); // TODO set the right one
/*
        Bitmap bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setColor(Color.TRANSPARENT);
        c.drawRect(new Rect(0, 0, 640, 480), p);
        Paint p2 = new Paint();
        p2.setColor(Color.RED);
        c.drawLine(0, 0, 640, 480, p2);

        SurfaceTexture st = sm.createInputSurfaceTexture();
        st.setDefaultBufferSize(640, 480);
        //st.setOnFrameAvailableListener(et2);
        Surface s = new Surface(st);
        Canvas cvs = s.lockCanvas(null);
        //cvs.drawBitmap(bmp, 0, 0, null);
        cvs.drawLine(0, 0, 640, 480, p2);
        s.unlockCanvasAndPost(cvs);*/

        usbConnector = new USBConnector(this) {
            @Override
            public boolean deviceFilter(UsbDevice dev) {
                return true;
            }

            @Override
            public void onConnect(UsbDevice dev, UsbDeviceConnection conn) {
                InfiCam cam = new InfiCam();
                try {
                    cam.connect(conn.getFileDescriptor());
                    cam.startStream(new Surface(ist2));
                    ist2.setOnFrameAvailableListener(surfaceMuxer);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            cam.calibrate();
                        }
                    }, 500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Log.e("TEST", "receiver registered");
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    void askPermission(String perm, ActivityResultCallback<Boolean> result) {
        ActivityResultLauncher<String> launcher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), result);
        launcher.launch(perm);
    }
}
