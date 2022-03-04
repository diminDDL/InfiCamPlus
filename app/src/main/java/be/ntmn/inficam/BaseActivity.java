package be.ntmn.inficam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import java.util.ArrayList;

/* This is where I've hidden the cruft to make the app go into immersive mode and for requesting
 *   permissions.
 */
public class BaseActivity extends AppCompatActivity {
    ArrayList<PermissionCallback> permissionCallbacks = new ArrayList<>();
    final static long hideDelay = 2000;
    Handler handler = new Handler();

    interface PermissionCallback {
        void onPermission(boolean granted);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View dv = getWindow().getDecorView();
        dv.setOnSystemUiVisibilityChangeListener(i -> {
            if ((i & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0)
                return;
            handler.postDelayed(this::hideUI, hideDelay);
        });
        hideUI();
    }

    void hideUI() {
        View dv = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        dv.setSystemUiVisibility(uiOptions);
    }

    public void askPermission(String perm, PermissionCallback callback) {
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            callback.onPermission(true);
        } else {
            permissionCallbacks.add(callback);
            requestPermissions(new String[]{perm}, permissionCallbacks.size());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            PermissionCallback cb = permissionCallbacks.remove(requestCode - 1);
            cb.onPermission(grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } catch (Exception e) {
            e.printStackTrace(); /* Sometimes we get two calls, idk why... */
        }
    }
}
