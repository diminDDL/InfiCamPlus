package be.ntmn.inficam;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
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
	public static void writePNG(Context ctx, Bitmap bitmap) {
		@SuppressLint("SimpleDateFormat")
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String dirname = ctx.getString(R.string.app_name);
		try {
			OutputStream out;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				String fname = "img_" + timeStamp + ".png"; /* MediaStore won't overwrite. */
				ContentValues cv = new ContentValues();
				cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
				cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
				cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + dirname);
				ContentResolver cr = ctx.getContentResolver();
				Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
				out = cr.openOutputStream(uri);
			} else {
				int num = 0;
				String fname = "img_" + timeStamp + "_" + num + ".png";
				File dir = new File(Environment.DIRECTORY_DCIM, dirname);
				File file = new File(dir, fname);
				while (file.exists()) { /* Avoid overwriting existing files. */
					fname = "img_" + timeStamp + "_" + ++num + ".png";
					file = new File(fname);
				}
				out = new FileOutputStream(file);
			}
			bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String readStringAsset(Context ctx, String filename) throws IOException {
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
}
