package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class MessageView extends androidx.appcompat.widget.AppCompatTextView {
	private final static long TIME_LONG = 2000;
	private final static long TIME_SHORT = 500;
	private String persistentMessage;

	private final Runnable _hideMessage = () -> {
		if (persistentMessage != null)
			setText(persistentMessage);
		else setVisibility(View.INVISIBLE);
	};

	public MessageView(Context context) {
		super(context);
		init();
	}

	public MessageView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public MessageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		if (getVisibility() == VISIBLE)
			persistentMessage = getText().toString();
	}

	private void _showMessage(boolean preserve, long time) {
		setVisibility(View.VISIBLE);
		removeCallbacks(_hideMessage);
		if (!preserve)
			postDelayed(_hideMessage, time);
	}

	public void setMessage(int res) {
		setMessage(getContext().getString(res));
	}

	public void setMessage(String str) {
		persistentMessage = str;
		setText(str);
		_showMessage(true, 0);
	}

	public void clearMessage() {
		persistentMessage = null;
		removeCallbacks(_hideMessage);
		_hideMessage.run();
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
