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

public abstract class USBConnector extends BroadcastReceiver {
    static final String ACTION_USB_PERMISSION = "be.ntmn.inficam.USB_PERMISSION";

    Context ctx;
    UsbManager manager;

    public USBConnector(Context context) {
        super();
        ctx = context;
        manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        ctx.registerReceiver(this, filter);
    }

    public void tryConnect() { /* To connect with devices already connected on start, call this. */
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        for (UsbDevice dev : deviceList.values()) {
            if (!deviceFilter(dev))
                continue;
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            @SuppressLint("UnspecifiedImmutableFlag")
            PendingIntent pending = PendingIntent.getBroadcast(ctx, 0, intent, 0);
            manager.requestPermission(dev, pending);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                tryConnect();
                break;
            case ACTION_USB_PERMISSION:
                UsbDevice dev = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    UsbDeviceConnection conn = manager.openDevice(dev);
                    onConnect(dev, conn);
                } else {
                    onPermissionDenied(dev);
                }
                break;
        }
    }

    public abstract boolean deviceFilter(UsbDevice dev);
    public abstract void onConnect(UsbDevice dev, UsbDeviceConnection conn);
    public abstract void onPermissionDenied(UsbDevice dev);
}
