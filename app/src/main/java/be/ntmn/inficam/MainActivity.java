package be.ntmn.inficam;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MainActivity extends BaseActivity {
    SurfaceMuxer surfaceMuxer = new SurfaceMuxer();
    SurfaceView cameraView;
    InfiCam infiCam = new InfiCam();
    boolean isConnected = false; /* Whether a device is connected. */
    UsbDevice device = null;
    SurfaceMuxer.InputSurface inputSurface; /* InfiCam class writes to this. */
    SurfaceMuxer.InputSurface overlaySurface; /* This is where we will draw annotations. */
    SurfaceMuxer.InputSurface videoSurface; /* To draw video from the normal camera if enabled. */

    USBConnector usbConnector = new USBConnector(this) {
        @Override
        public boolean deviceFilter(UsbDevice dev) {
            /* Because onpause gets called on dialogs, we may arrive here more than expected. */
            if (device == null) {
                device = dev;
                return true;
            }
            return false;
        }

        @Override
        public void onConnect(UsbDevice dev, UsbDeviceConnection conn) {
            infiCam = new InfiCam();
            try {
                infiCam.connect(conn.getFileDescriptor());
                infiCam.startStream(inputSurface.getSurface());
                handler.postDelayed(() -> infiCam.calibrate(), 1000);
                isConnected = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPermissionDenied(UsbDevice dev) {
            Toast.makeText(ctx, R.string.permdenied_usb, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.cameraView);
        inputSurface = new SurfaceMuxer.InputSurface(surfaceMuxer, true);
        surfaceMuxer.inputSurfaces.add(inputSurface);
        inputSurface.getSurfaceTexture().setOnFrameAvailableListener(surfaceMuxer);
        SurfaceHolder sh = cameraView.getHolder();
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
                surfaceMuxer.removeOutputSurface(surfaceHolder.getSurface());
            }
        });

        // TODO very temporary
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                infiCam.calibrate();
            }
        });

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*askPermission(Manifest.permission.CAMERA, granted -> {
            if (granted) {
                SurfaceTexture ist = surfaceMuxer.createInputSurfaceTexture();
                ist.setDefaultBufferSize(1280, 960);
                CameraTest ct = new CameraTest();
                ct.initCamera2(this, new Surface(ist));
                //ist.setOnFrameAvailableListener(surfaceMuxer); // TODO set the right one
            } else {
                Toast.makeText(this, R.string.permdenied_cam, Toast.LENGTH_LONG).show();
            }
        });*/
        askPermission(Manifest.permission.CAMERA, granted -> {
            if (granted) {
                usbConnector.start(); /* Connecting to a UVC device needs camera permission. */
            } else {
                Toast.makeText(this, R.string.permdenied_cam, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isConnected = false;
        infiCam.stopStream();
        infiCam.disconnect();
        device = null;
    }
}
