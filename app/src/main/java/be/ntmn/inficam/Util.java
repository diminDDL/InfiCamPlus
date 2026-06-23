package be.ntmn.inficam;

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.core.graphics.ColorUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Util {

    public interface ErrorCallback {
        void onError(String message);
    }
    public static final float ABSOLUTE_ZERO = -273.15f; //C

    public static final int IMGTYPE_PNG = 0;
    public static final int IMGTYPE_PNG565 = 1;
    public static final int IMGTYPE_JPEG = 2;

    public static final int TEMPUNIT_CELSIUS = 0;
    public static final int TEMPUNIT_FAHRENHEIT = 1;
    public static final int TEMPUNIT_KELVIN = 2;
    public static final int TEMPUNIT_RANKINE = 3;

    public static void scanMedia(Context ctx, Uri uri) {
        MediaScannerConnection.scanFile(
            ctx,
            new String[] { uri.getPath() },
            null,
            null
        );
        ctx.sendBroadcast(
            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
        );
    }

    private static void writeImage(
        Context ctx,
        Bitmap bmp,
        Bitmap.CompressFormat format,
        String mimeType,
        String ext,
        int quality
    ) throws IOException {
        @SuppressLint("SimpleDateFormat")
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(
            new Date()
        );
        String dirname = ctx.getString(R.string.app_name);
        OutputStream out;
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String fname =
                "img_" + timeStamp + ext; /* MediaStore won't overwrite. */
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
            cv.put(
                MediaStore.Images.Media.DATE_ADDED,
                System.currentTimeMillis()
            );
            cv.put(
                MediaStore.Images.Media.DATE_TAKEN,
                System.currentTimeMillis()
            );
            ContentResolver cr = ctx.getContentResolver();
            uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if(uri == null){ throw new IOException();}
            out = cr.openOutputStream(uri);
        } else {
            int num = 0;
            String fname = "img_" + timeStamp + "_" + num + ext;
            File dcim = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
            );
            File dir = new File(dcim, dirname);
            if (!dir.exists()){
                if(!dir.mkdirs()){ throw new IOException(); }
            }
            File file = new File(dir, fname);
            while (file.exists()) {
                /* Avoid overwriting existing files. */
                fname = "img_" + timeStamp + "_" + ++num + ext;
                file = new File(fname);
            }
            //noinspection IOStreamConstructor
            out = new FileOutputStream(file);
            uri = Uri.fromFile(file);
        }
        if(out == null){ throw new IOException();}
        bmp.compress(format, quality, out);
        out.flush();
        out.close();
        scanMedia(ctx, uri);
    }

    public static void writeImage(
        Context ctx,
        Bitmap bmp,
        int type,
        int quality
    ) throws IOException {
        switch (type) {
            case IMGTYPE_PNG:
                writeImage(
                    ctx,
                    bmp,
                    Bitmap.CompressFormat.PNG,
                    "image/png",
                    ".png",
                    quality
                );
                break;
            case IMGTYPE_PNG565 /* Much faster than writePNG() and the output is smaller. */:
                Bitmap bmp2 = Bitmap.createBitmap(
                    bmp.getWidth(),
                    bmp.getHeight(),
                    Bitmap.Config.RGB_565
                );
                Canvas c = new Canvas(bmp2);
                c.drawBitmap(bmp, 0, 0, null);
                writeImage(
                    ctx,
                    bmp2,
                    Bitmap.CompressFormat.PNG,
                    "image/png",
                    ".png",
                    quality
                );
                bmp2.recycle();
                break;
            case IMGTYPE_JPEG /* Fastest and smallest, but lossy. */:
                writeImage(
                    ctx,
                    bmp,
                    Bitmap.CompressFormat.JPEG,
                    "image/jpeg",
                    ".jpg",
                    quality
                );
                break;
        }
    }

    /*
    AI generated
     */
    /**
     * Opens the most recently modified image OR video that lives in the app's
     * MediaStore bucket. Runs the MediaStore queries off the UI thread and posts
     * the resulting view intent back to the main thread.
     *
     * Call from the UI thread. Errors are delivered via the supplied callback.
     */
    /* Note that we need permission to read external storage requested first. */
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void openGallery(Context ctx, ErrorCallback onError) {
        // Hold an application context for the background work to avoid leaking an Activity.
        final Context appCtx = ctx.getApplicationContext();
        final Context startCtx = ctx; // used only on the UI thread to startActivity
        final String bucketName = appCtx.getString(R.string.app_name);
        final Handler main = new Handler(Looper.getMainLooper());

        IO_EXECUTOR.execute(() -> {
            MediaItem item = findLatestMedia(appCtx, bucketName);

            main.post(() -> {
                try {
                    launchViewer(startCtx, item);
                } catch (ActivityNotFoundException anfe) {
                    if (onError != null) {
                        onError.onError(appCtx.getString(R.string.err_nogallery));
                    }
                }
            });
        });
    }

    /** Small holder so we keep the URI and its real MIME type together. */
    private static final class MediaItem {
        final Uri uri;
        final String mimeType; // "image/*" or "video/*"
        MediaItem(Uri uri, String mimeType) {
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }

    private static MediaItem findLatestMedia(Context ctx, String bucketName) {
        final String selection = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME + "=?";
        final String[] selectionArgs = { bucketName };

        Uri bestUri = null;
        String bestType = null;
        long bestDate = Long.MIN_VALUE;

        // --- Latest image ---
        final String[] imageProjection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_MODIFIED,
        };
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                selection,
                selectionArgs,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                long date = c.getLong(1);
                bestUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                bestType = "image/*";
                bestDate = date;
            }
        }

        // --- Latest video (wins only if strictly newer) ---
        final String[] videoProjection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_MODIFIED,
        };
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                selection,
                selectionArgs,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                long date = c.getLong(1);
                if (date > bestDate) {
                    bestUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    bestType = "video/*";
                    bestDate = date;
                }
            }
        }

        return bestUri == null ? null : new MediaItem(bestUri, bestType);
    }

    private static void launchViewer(Context ctx, MediaItem item)
            throws ActivityNotFoundException {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (item != null) {
            // Correct MIME type per item (fixes the video-shown-as-image bug)
            // and grant the target app read access to the content URI.
            intent.setDataAndType(item.uri, item.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            // Nothing authored by this app yet: open the device gallery instead of
            // sending a data-less ACTION_VIEW (which generally won't resolve).
            // ACTION_PICK on the images collection reliably opens a gallery/picker.
            intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }

        // If this is ever called from a non-Activity Context, a new task is required.
        if (!(ctx instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        ctx.startActivity(intent); // may throw ActivityNotFoundException
    }

    /*
    end of AI generated
     */

    public static String readStringAsset(Context ctx, String filename)
        throws IOException {
        InputStream input = ctx.getAssets().open(filename);
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        int len;
        while ((len = input.read(buffer)) >= 0) {
            bas.write(buffer, 0, len);
        }
        input.close();
        bas.close();
        return bas.toString();
    }

    public static void formatTemp(StringBuilder sb, float temp, int tempunit) {
        sb.setLength(0);
        if (Float.isNaN(temp) || Float.isInfinite(temp)) {
            sb.append("NaN");
            return;
        }
        if (tempunit == TEMPUNIT_KELVIN || tempunit == TEMPUNIT_RANKINE) temp +=
            273.15f;
        if (
            tempunit == TEMPUNIT_FAHRENHEIT || tempunit == TEMPUNIT_RANKINE
        ) temp *= 9.0f / 5.0f;
        if (tempunit == TEMPUNIT_FAHRENHEIT) temp += 32.0f;
        if (temp < 0) sb.append("-");
        temp = abs(temp * 100.0f);
        sb.append((int) temp / 100);
        sb.append(".");
        sb.append((int) ((temp / 10) % 10));
        sb.append((int) (temp % 10));
        if (tempunit == TEMPUNIT_FAHRENHEIT) sb.append("°F");
        else if (tempunit == TEMPUNIT_KELVIN) sb.append("K");
        // Fun Fact: Kelvin does not use degrees
        else if (tempunit == TEMPUNIT_RANKINE) sb.append("°R");
        // Funner Fact: Rankine does use degrees
        else sb.append("°C");
    }

    /* NOT for performance-critical sections! */
    public static String formatTemp(float temp, int tempunit) {
        StringBuilder sb = new StringBuilder();
        formatTemp(sb, temp, tempunit);
        return sb.toString();
    }

    public static int blendColor3(int left, int mid, int right, float pos) {
        if (pos < 0.5) return ColorUtils.blendARGB(left, mid, pos * 2);
        return ColorUtils.blendARGB(mid, right, pos * 2 - 1);
    }
}
