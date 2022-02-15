package com.serenegiant.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.Log;

import static org.apache.commons.lang3.math.NumberUtils.min;

public class BitmapUtil {
    public static Bitmap createCircleImage(Bitmap source) {
        int length = source.getWidth() < source.getHeight() ? source.getWidth() : source.getHeight();
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Bitmap target = Bitmap.createBitmap(length, length, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        canvas.drawCircle(length / 2, length / 2, length / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, 0, 0, paint);
        return target;
    }
    /**
     *
     * @param   source  Bitmap source to draw
     * @param   tarWidth  the dest bitmap width
     * @param   tarHeight  Bitmap dest bitmap height
     * @param  radius radius<tarWidth AND tarHeight
     *
     */
    public static Bitmap createCircleImage(Bitmap source,int tarWidth,int tarHeight,int radius) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Bitmap target = Bitmap.createBitmap(tarWidth, tarHeight, Bitmap.Config.ARGB_8888);
        Log.e("createCircleImage","tarWidth:"+tarWidth+"tarHeight:"+tarHeight);
        Canvas canvas = new Canvas(target);
        int min=min(tarWidth/2,tarHeight/2,radius);
        Log.e("createCircleImage","min:"+min);
        int diameter=min*2;
        canvas.drawCircle(tarWidth/2, tarHeight/2, min, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int left,top,right,bottom;
        if(source.getHeight()>diameter){
            top= (source.getHeight()-diameter)/2;
            bottom=source.getHeight()-(source.getHeight()-diameter)/2;
        }else{
            top=0;
            bottom=source.getHeight();
        }
        if(source.getWidth()>diameter){
            left= (source.getWidth()-diameter)/2;
            right=source.getWidth()-(source.getWidth()-diameter)/2;
        }else{
            left=0;
            right=source.getWidth();
        }
        Rect src=new Rect(left,top,right,bottom);
        Rect dst=new Rect(0,0,tarWidth,tarHeight);
        canvas.drawBitmap(source, src, dst, paint);
//        canvas.drawColor(Color.WHITE);
        return target;
    }
}
