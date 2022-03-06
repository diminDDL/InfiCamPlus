package be.ntmn.inficam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class MessageView extends androidx.appcompat.widget.AppCompatTextView {
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

	void _showMessage(boolean preserve) {
		setVisibility(View.VISIBLE);
		removeCallbacks(_hideMessage);
		if (!preserve)
			postDelayed(_hideMessage, 2000);
	}

	public void showMessage(int res, boolean preserve) {
		setText(res);
		_showMessage(preserve);
	}

	public void showMessage(String str, boolean preserve) {
		setText(str);
		_showMessage(preserve);
	}
}
