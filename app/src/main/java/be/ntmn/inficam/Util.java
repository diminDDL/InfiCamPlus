package be.ntmn.inficam;

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Util {
	public final static int IMGTYPE_PNG = 0;
	public final static int IMGTYPE_PNG565 = 1;
	public final static int IMGTYPE_JPEG = 2;

	public final static int TEMPUNIT_CELSIUS = 0;
	public final static int TEMPUNIT_FAHRENHEIT = 1;
	public final static int TEMPUNIT_KELVIN = 2;
	public final static int TEMPUNIT_RANKINE = 3;

	private static void writeImage(Context ctx, Bitmap bmp, Bitmap.CompressFormat format,
								  String mimeType, String ext, int quality)
			throws IOException {
		@SuppressLint("SimpleDateFormat")
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String dirname = ctx.getString(R.string.app_name);
		OutputStream out;
		Uri uri;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			String fname = "img_" + timeStamp + ext; /* MediaStore won't overwrite. */
			ContentValues cv = new ContentValues();
			cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
			cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
			cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
			ContentResolver cr = ctx.getContentResolver();
			uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
			out = cr.openOutputStream(uri);
		} else {
			int num = 0;
			String fname = "img_" + timeStamp + "_" + num + ext;
			File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
			File dir = new File(dcim, dirname);
			if (!dir.exists())
				dir.mkdirs();
			File file = new File(dir, fname);
			while (file.exists()) { /* Avoid overwriting existing files. */
				fname = "img_" + timeStamp + "_" + ++num + ext;
				file = new File(fname);
			}
			out = new FileOutputStream(file);
			uri = Uri.fromFile(file);
		}
		bmp.compress(format, quality, out);
		out.flush();
		out.close();
		// TODO also use mediascanner, also do this for videos
		ctx.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
	}

	public static void writeImage(Context ctx, Bitmap bmp, int type, int quality)
			throws IOException {
		switch (type) {
			case IMGTYPE_PNG:
				writeImage(ctx, bmp, Bitmap.CompressFormat.PNG, "image/png", ".png", quality);
				break;
			case IMGTYPE_PNG565: /* Much faster than writePNG() and the output is smaller. */
				Bitmap bmp2 =
						Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(),Bitmap.Config.RGB_565);
				Canvas c = new Canvas(bmp2);
				c.drawBitmap(bmp, 0, 0, null);
				writeImage(ctx, bmp2, Bitmap.CompressFormat.PNG, "image/png", ".png", quality);
				bmp2.recycle();
				break;
			case IMGTYPE_JPEG: /* Fastest and smallest, but lossy. */
				writeImage(ctx, bmp, Bitmap.CompressFormat.JPEG, "image/jpeg", ".jpg", quality);
				break;
		}
	}

	private static class Mscc implements MediaScannerConnection.MediaScannerConnectionClient {
		Context ctx;
		File file;
		MediaScannerConnection conn;

		Mscc(Context ctx, File file) {
			this.ctx = ctx;
			this.file = file;
			conn = new MediaScannerConnection(ctx, this);
			conn.connect();
		}

		@Override
		public void onScanCompleted(String path, Uri uri) {
			try {
				if (uri != null) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					//Intent intent = new Intent("com.android.camera.action.REVIEW");
					intent.setDataAndType(uri, "image/*");
					//intent.setDataAndType(Uri.fromFile(new File(path)), "image/*");
					//intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					//ctx.getContentResolver();

					/*String parentDocUri = DocumentsContract.getTreeDocumentId(uri);
					intent.setDataAndType(DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentDocUri), "image/*");*/

					ctx.startActivity(intent);
				}
			} finally {
				conn.disconnect();
			}
		}

		@Override
		public void onMediaScannerConnected() {
			conn.scanFile(file.getAbsolutePath(), "*/*");
		}
	}

	public static Uri getImageContentUri(Context context, String filePath) {
		Cursor cursor = context.getContentResolver().query(
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				new String[] { MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME },
				MediaStore.Images.Media.BUCKET_DISPLAY_NAME + "='InfiCam'",
				null,
				MediaStore.Images.Media.DATE_TAKEN + " DESC");
		if (cursor != null && cursor.moveToFirst()) {
			int id = cursor.getInt(0);
			cursor.close();
			Log.e("MYLOG", "got cusror one");
			return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
		} else {
			try{
				ContentValues values = new ContentValues();
				values.put(MediaStore.Images.Media.DATA, filePath);
				Log.e("MYLOG", "got value");
				return context.getContentResolver().insert(
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
			} catch (Exception e){
				Log.e("MYLOG", "it's null");
				return null;
			}
		}
	}

	public static void openGallery2(Context context) {
		String bucketId = "";
		final String[] projection = new String[] { MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.BUCKET_ID };
		final Cursor cur = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);
		while (cur != null && cur.moveToNext()) {
			final String bucketName = cur.getString(0);
			Log.e("BUCKETNAME", bucketName);
			if (bucketName.equals("InfiCam")) {
				bucketId = cur.getString(1);
				break;
			}
		}
		Log.e("BUCKETNAME", "got bucketId = " + bucketId);
		Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		if (bucketId.length() > 0) {
			mediaUri = mediaUri.buildUpon().authority("media")
					.appendQueryParameter("bucketId", bucketId).build();
		}
		if (cur != null)
			cur.close();
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(mediaUri, "image/*");
		context.startActivity(intent);
	}

	public static void openGallery(Context ctx) {
		// TODO activity requests permission atm, should it?
		//openGallery2(ctx);
		//File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		File dcim = new File(Environment.getExternalStorageDirectory(), "DCIM");
		String dirname = ctx.getString(R.string.app_name);
		File dir = new File(dcim, dirname);
		// TODO manifest and the provider_paths.xml where modified for this, check later if we still need em
		File[] files = dir.listFiles();
		Uri uri = getImageContentUri(ctx, files[0].getAbsolutePath());
		//Uri uri = FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".provider", files[0]);
		//Intent intent = new Intent("com.android.camera.action.REVIEW"); // TODO try that one first
		Intent intent = new Intent(Intent.ACTION_VIEW);
		//Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setDataAndType(uri, "image/*");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		//intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		//intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//Intent chooser = Intent.createChooser(intent, "Open Gallery");

		List<ResolveInfo> resInfoList = ctx.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		for (ResolveInfo resolveInfo : resInfoList) {
			String packageName = resolveInfo.activityInfo.packageName;
			ctx.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		}
		ctx.startActivity(intent);
		//new Mscc(ctx, files[0]);
		// TODO catch ActivityNotFoundException
	}

	public static String readStringAsset(Context ctx, String filename) throws IOException {
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
		if (tempunit == TEMPUNIT_KELVIN || tempunit == TEMPUNIT_RANKINE)
			temp += 273.15f;
		if (tempunit == TEMPUNIT_FAHRENHEIT || tempunit == TEMPUNIT_RANKINE)
			temp *= 9.0f / 5.0f;
		if (tempunit == TEMPUNIT_FAHRENHEIT)
			temp += 32.0f;
		if (temp < 0)
			sb.append("-");
		temp = abs(temp * 100.0f);
		sb.append((int) temp / 100);
		sb.append(".");
		sb.append((int) ((temp / 10) % 10));
		sb.append((int) (temp % 10));
		if (tempunit == TEMPUNIT_FAHRENHEIT)
			sb.append("°F");
		else if (tempunit == TEMPUNIT_KELVIN)
			sb.append("°K");
		else if (tempunit == TEMPUNIT_RANKINE)
			sb.append("°R");
		else sb.append("°C");
	}

	/* NOT for performance-critical sections! */
	public static String formatTemp(float temp, int tempunit) {
		StringBuilder sb = new StringBuilder();
		formatTemp(sb, temp, tempunit);
		return sb.toString();
	}
}
