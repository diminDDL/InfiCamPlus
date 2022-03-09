package be.ntmn.inficam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;

/* This is where I've hidden the cruft to make the app go into immersive mode and for requesting
 *   permissions.
 */
public class BaseActivity extends AppCompatActivity {
	public final Handler handler = new Handler();
	ArrayList<PermissionCallback> permissionCallbacks = new ArrayList<>();
	boolean fullscreen = false; /* If you want to change the default, look at Settings class. */
	final static long hideDelay = 2500;

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
			deferHide();
		});
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // TODO setting
	}

	@Override
	protected void onResume() {
		super.onResume();
		deferHide();
	}

	@Override
	protected void onPause() {
		/* To make sure the navigation doesn't suddenly hide on resume. */
		handler.removeCallbacks(hideUI);
		super.onPause();
	}

	void deferHide() {
		if (fullscreen) {
			handler.removeCallbacks(hideUI);
			handler.postDelayed(hideUI, hideDelay);
		}
	}

	final Runnable hideUI = () -> {
		View dv = getWindow().getDecorView();
		/* Flags to go properly fullscreen. */
		int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE
				//| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				//| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				//| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN;
		dv.setSystemUiVisibility(uiOptions);
	};

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent ev) {
		deferHide();
		return super.dispatchGenericMotionEvent(ev);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		deferHide();
		return super.dispatchTouchEvent(ev);
	}

	public void askPermission(String perm, PermissionCallback callback) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
				callback.onPermission(true);
			} else {
				permissionCallbacks.add(callback);
				requestPermissions(new String[]{perm}, permissionCallbacks.size());
			}
		} else callback.onPermission(true);
	}

	public boolean checkPermission(String perm) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
			return true;
		return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
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

	/* Called by settings, has to be called before fullscreen starts working. */
	public void setFullscreen(boolean value) {
		View dv = getWindow().getDecorView();
		if (value) {
			/*int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;*/
			int uiOptions = 0;
			dv.setSystemUiVisibility(uiOptions);
			fullscreen = true;
			deferHide();
		} else {
			fullscreen = false;
			handler.removeCallbacks(hideUI);
			dv.setSystemUiVisibility(0);
		}
	}
}
