package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;

public class CameraView extends SurfaceView {
	private Context context;
	ScaleGestureDetector scaleDetector;

	public CameraView(Context context) {
		super(context);
		init(context);
	}

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context ctx) {
		context = ctx;
	}

	@SuppressLint("ClickableViewAccessibility") /* Don't worry about it. */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (scaleDetector != null) {
			scaleDetector.onTouchEvent(event);
			if (scaleDetector.isInProgress()) {
				setPressed(false);
				return true;
			}
		}
		return super.onTouchEvent(event);
	}

	@Override
	public boolean performClick() {
		return super.performClick();
	}

	public void setScaleListener(ScaleGestureDetector.OnScaleGestureListener listener) {
		scaleDetector = null;
		if (listener != null)
			scaleDetector = new ScaleGestureDetector(context, listener);
	}
}
