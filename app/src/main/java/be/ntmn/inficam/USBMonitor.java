package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import java.util.HashMap;

public abstract class USBMonitor extends BroadcastReceiver {
	private static final String ACTION_USB_PERMISSION = "be.ntmn.inficam.USB_PERMISSION";

	public interface ConnectCallback {
		void onConnected(UsbDevice dev, UsbDeviceConnection conn);
		void onPermissionDenied(UsbDevice dev);
	}

	private Context ctx;
	private boolean registered = false;
	private UsbManager manager;
	private final HashMap<UsbDevice, ConnectCallback> callbacks = new HashMap<>();

	public void start(Context ctx) { /* Recommended use is in onCreate()/onStart(). */
		this.ctx = ctx;
		if (!registered) {
			manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
			IntentFilter filter = new IntentFilter();
			filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
			filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			filter.addAction(ACTION_USB_PERMISSION);
			ctx.registerReceiver(this, filter);
			registered = true;
		}
	}

	public void stop() { /* Call this in onDestroy()/onStop(), matching start() call! */
		try {
			registered = false;
			ctx.unregisterReceiver(this); /* Prevent resurrection of dead Activities. */
		} catch (Exception e) {
			/* We don't care, probably wasn't registered yet. */
		}
	}

	public void scan() { /* To connect with devices already connected on start, call this. */
		/* We have to account for the fact manager could be null because we can't start() until
		 *   camera permission is obtained (when looking for UVC devices) and if the dialog has to
		 *   be show, start() will be delayed until after onResume() and onPause(). An onResume()
		 *   will luckily follow any dialog dismissal, so if we call scan() in onResume() it will
		 *   be called again anyway.
		 */
		if (manager == null)
			return;
		HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		for (UsbDevice dev : deviceList.values())
			onDeviceFound(dev);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		UsbDevice dev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
		switch (intent.getAction()) {
			case UsbManager.ACTION_USB_DEVICE_ATTACHED:
				scan();
				break;
			case UsbManager.ACTION_USB_DEVICE_DETACHED:
				onDisconnect(dev);
				break;
			case ACTION_USB_PERMISSION:
				ConnectCallback cb = callbacks.remove(dev);
				if (cb == null)
					return;
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					UsbDeviceConnection conn = manager.openDevice(dev);
					cb.onConnected(dev, conn);
				} else cb.onPermissionDenied(dev);
				break;
		}
	}

	public void connect(UsbDevice dev, ConnectCallback cb) {
		if (!manager.hasPermission(dev)) {
			Intent intent = new Intent(ACTION_USB_PERMISSION);
			@SuppressLint("UnspecifiedImmutableFlag")
			PendingIntent pending = PendingIntent.getBroadcast(ctx, 0, intent, 0);
			callbacks.put(dev, cb);
			manager.requestPermission(dev, pending);
		} else {
			UsbDeviceConnection conn = manager.openDevice(dev);
			cb.onConnected(dev, conn);
		}
	}

	public abstract void onDeviceFound(UsbDevice dev);
	public abstract void onDisconnect(UsbDevice dev);
}
