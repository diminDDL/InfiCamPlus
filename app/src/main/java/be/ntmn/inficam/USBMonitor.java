package be.ntmn.inficam;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.HashMap;

public abstract class USBMonitor extends BroadcastReceiver {
	private static final String ACTION_USB_PERMISSION = "be.ntmn.inficam.USB_PERMISSION";

	public interface ConnectCallback {
		void onConnected(UsbDevice dev, UsbDeviceConnection conn);
		void onPermissionDenied(UsbDevice dev);
		void onFailed(UsbDevice dev);
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

	public void stop() { /* Call this in onDestroy()/onStop(), matching start() call. */
		try {
			registered = false;
			ctx.unregisterReceiver(this); /* Prevent resurrection of dead Activities. */
		} catch (Exception e) {
			/* We don't care, probably wasn't registered yet. */
		}
	}

	public void scan() { /* To discover devices already connected on start, call this. */
		if (manager == null)
			return;
		HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		for (UsbDevice dev : deviceList.values())
			onDeviceFound(dev);
	}

	private void _connect(UsbDevice dev, ConnectCallback cb) {
		UsbDeviceConnection conn = manager.openDevice(dev);
		if (conn != null)
			cb.onConnected(dev, conn);
		else cb.onFailed(dev);
	}

	public void connect(UsbDevice dev, ConnectCallback cb) {
		if (!manager.hasPermission(dev)) {
			Intent intent = new Intent(ACTION_USB_PERMISSION);
			int flags = PendingIntent.FLAG_ONE_SHOT;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
				flags |= PendingIntent.FLAG_MUTABLE;
			PendingIntent pending = PendingIntent.getBroadcast(ctx, 0, intent, flags);
			callbacks.put(dev, cb);
			manager.requestPermission(dev, pending);
		} else _connect(dev, cb);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
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
					_connect(dev, cb);
				} else cb.onPermissionDenied(dev);
				break;
		}
	}

	public abstract void onDeviceFound(UsbDevice dev);
	public abstract void onDisconnect(UsbDevice dev);
}
