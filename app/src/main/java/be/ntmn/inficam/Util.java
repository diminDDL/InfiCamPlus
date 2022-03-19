package be.ntmn.inficam;

import static java.lang.Math.round;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {
	public final static int IMGTYPE_PNG = 0;
	public final static int IMGTYPE_PNG565 = 1;
	public final static int IMGTYPE_JPEG = 2;

	public final static int TEMPUNIT_CELCIUS = 0;
	public final static int TEMPUNIT_FAHRENHEIT = 1;
	public final static int TEMPUNIT_KELVIN = 2;

	private static void writeImage(Context ctx, Bitmap bmp, Bitmap.CompressFormat format,
								  String mimeType, String ext, int quality) {
		@SuppressLint("SimpleDateFormat")
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String dirname = ctx.getString(R.string.app_name);
		try {
			OutputStream out;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				String fname = "img_" + timeStamp + ext; /* MediaStore won't overwrite. */
				ContentValues cv = new ContentValues();
				cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
				cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
				cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
				ContentResolver cr = ctx.getContentResolver();
				Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
				out = cr.openOutputStream(uri);
			} else {
				int num = 0;
				String fname = "img_" + timeStamp + "_" + num + ext;
				File dir = new File(Environment.DIRECTORY_DCIM, dirname);
				File file = new File(dir, fname);
				while (file.exists()) { /* Avoid overwriting existing files. */
					fname = "img_" + timeStamp + "_" + ++num + ext;
					file = new File(fname);
				}
				out = new FileOutputStream(file);
			}
			bmp.compress(format, quality, out);
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void writeImage(Context ctx, Bitmap bmp, int type, int quality) {
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
		if (tempunit == TEMPUNIT_FAHRENHEIT)
			temp = temp * 9 / 5 + 32;
		if (tempunit == TEMPUNIT_KELVIN)
			temp += 273.15;
		if (temp < 0)
			sb.append("-");
		sb.append((int) temp);
		sb.append(".");
		sb.append((int) ((temp * 10) % 10));
		sb.append(round(temp * 100 % 10));
		if (tempunit == TEMPUNIT_FAHRENHEIT)
			sb.append("°F");
		if (tempunit == TEMPUNIT_KELVIN)
			sb.append("°K");
		else sb.append("°C");
	}

	/* NOT for performance-critical sections! */
	public static String formatTemp(float temp, int tempunit) {
		StringBuilder sb = new StringBuilder();
		formatTemp(sb, temp, tempunit);
		return sb.toString();
	}
}
