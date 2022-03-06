package be.ntmn.inficam;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;

public abstract class USBMonitor extends BroadcastReceiver {
	static final String ACTION_USB_PERMISSION = "be.ntmn.inficam.USB_PERMISSION";

	Context ctx;
	boolean registered = false;
	UsbManager manager;

	public void start(Context ctx) { /* Recommended use is in onStart(). */
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

	public void stop() { /* Call this in onStop()! */
		try {
			registered = false;
			ctx.unregisterReceiver(this); /* Prevent resurrection of dead Activities. */
		} catch (Exception e) {
			/* We don't care, probably wasn't registered yet. */
		}
	}

	public void scan() { /* To connect with devices already connected on start, call this. */
		HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
		for (UsbDevice dev : deviceList.values()) {
			boolean res = onDeviceFound(dev);
			Log.e("DEV", "name = " + dev.getProductName() + " take = " + res);
			if (!res)
				continue;
			if (!manager.hasPermission(dev)) {
				Intent intent = new Intent(ACTION_USB_PERMISSION);
				PendingIntent pending = PendingIntent.getBroadcast(ctx, 0, intent,
						PendingIntent.FLAG_MUTABLE);
				manager.requestPermission(dev, pending);
			} else onPermissionGranted(dev);
		}
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
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
					onPermissionGranted(dev);
				else onPermissionDenied(dev);
				break;
		}
	}

	public UsbDeviceConnection connect(UsbDevice dev) {
		return manager.openDevice(dev);
	}

	public abstract boolean onDeviceFound(UsbDevice dev);
	public abstract void onPermissionGranted(UsbDevice dev);
	public abstract void onPermissionDenied(UsbDevice dev);
	public abstract void onDisconnect(UsbDevice dev);
}
