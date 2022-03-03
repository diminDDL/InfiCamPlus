package be.ntmn.inficam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class MainActivity extends FullscreenActivity {
    static final int CAMERA_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceMuxer sm = new SurfaceMuxer();
        SurfaceView sv = findViewById(R.id.cameraView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                sm.addOutputSurface(surfaceHolder.getSurface());
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
        ist = sm.createInputSurfaceTexture();
        ist.setDefaultBufferSize(640, 480);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.CAMERA }, CAMERA_REQUEST_CODE);
        // TODO onrequestpermissionresults
        ist.setOnFrameAvailableListener(sm); // TODO set the right one
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

    SurfaceTexture ist;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        CameraTest ct = new CameraTest();
        ct.initCamera2(this, new Surface(ist));
    }
}
