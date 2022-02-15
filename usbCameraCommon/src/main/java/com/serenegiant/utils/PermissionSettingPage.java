package com.serenegiant.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.util.List;

public class PermissionSettingPage
{
    private static final String MARK = Build.MANUFACTURER.toLowerCase();

    private static Intent google(Context paramContext)
    {
        Intent localIntent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
        localIntent.setData(Uri.fromParts("package", paramContext.getPackageName(), null));
        return localIntent;
    }

    private static boolean hasIntent(Context paramContext, Intent paramIntent)
    {
        return (paramContext.getPackageManager().queryIntentActivities(paramIntent,PackageManager.MATCH_DEFAULT_ONLY /*65536*/).size() > 0);
    }

    private static Intent huawei(Context paramContext)
    {
        Intent localIntent = new Intent();
        localIntent.putExtra("packageName", paramContext.getPackageName());
        localIntent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity"));
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"));
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.notificationmanager.ui.NotificationManagmentActivity"));
        return localIntent;
    }

    private static Intent meizu(Context paramContext)
    {
        Intent localIntent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
        localIntent.putExtra("packageName", paramContext.getPackageName());
        localIntent.setComponent(new ComponentName("com.meizu.safe", "com.meizu.safe.security.AppSecActivity"));
        return localIntent;
    }

    private static Intent oppo(Context paramContext)
    {
        Intent localIntent = new Intent();
        localIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK/*268435456*/);
        localIntent.putExtra("packageName", "com.phone.recording");
        localIntent.setComponent(new ComponentName("com.color.safecenter", "com.color.safecenter.permission.PermissionManagerActivity"));
        return localIntent;
    }

    public static void start(Context paramContext, boolean paramBoolean)
    {
        Intent localIntent;
        if (MARK.contains("huawei"))
            localIntent = huawei(paramContext);
        else if (MARK.contains("xiaomi"))
            localIntent = xiaomi(paramContext);
        else if (MARK.contains("oppo"))
            localIntent = oppo(paramContext);
        else if (MARK.contains("vivo"))
            localIntent = vivo(paramContext);
        else if (MARK.contains("meizu"))
            localIntent = meizu(paramContext);
        else
            localIntent = google(paramContext);
        if (paramBoolean)
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try
        {
            paramContext.startActivity(localIntent);
            return;
        }
        catch (Exception localException)
        {
        }
    }

    private static Intent vivo(Context paramContext)
    {
        Intent localIntent = new Intent();
        localIntent.setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.FloatWindowManager");
        localIntent.putExtra("packagename", paramContext.getPackageName());
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.SoftPermissionDetailActivity"));
        return localIntent;
    }

    private static Intent xiaomi(Context paramContext)
    {
        Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        localIntent.putExtra("extra_pkgname", paramContext.getPackageName());
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setPackage("com.miui.securitycenter");
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
        if (hasIntent(paramContext, localIntent))
            return localIntent;
        localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
        return localIntent;
    }
}