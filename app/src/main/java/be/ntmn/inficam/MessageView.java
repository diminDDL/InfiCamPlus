package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class MessageView extends androidx.appcompat.widget.AppCompatTextView {
	final static long TIME_LONG = 2000;
	final static long TIME_SHORT = 500;
	final Runnable _hideMessage = () -> setVisibility(View.INVISIBLE);

	public MessageView(Context context) {
		super(context);
	}

	public MessageView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public MessageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	void _showMessage(boolean preserve, long time) {
		setVisibility(View.VISIBLE);
		removeCallbacks(_hideMessage);
		if (!preserve)
			postDelayed(_hideMessage, time);
	}

	public void setMessage(int res) {
		setText(res);
		_showMessage(false, TIME_LONG);
	}

	public void setMessage(String str) {
		setText(str);
		_showMessage(false, TIME_LONG);
	}

	public void showMessage(int res) {
		setText(res);
		_showMessage(false, TIME_LONG);
	}

	public void showMessage(String str) {
		setText(str);
		_showMessage(false, TIME_LONG);
	}

	public void shortMessage(int res) {
		setText(res);
		_showMessage(false, TIME_SHORT);
	}

	public void shortMessage(String str) {
		setText(str);
		_showMessage(false, TIME_SHORT);
	}
}
