package com.example.picasaloginanduploading;

import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

public class BitmapUtils {
	
	private static final String TAG = "BitmapUtils";
	
	public static Bitmap getKeepRatioBitmap(int longSide, String path, BitmapFactory.Options opts)  {
		try {
			if (opts == null) {
				opts = new BitmapFactory.Options();
			}
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(path, opts);
			opts.inJustDecodeBounds = false;
			int width = opts.outWidth;
			int height = opts.outHeight;
			if (width == -1 || height == -1) {
				Log.e(TAG, "photo " + path + " widht " + width + ", height " + height);
				return null;
			}
			int finalWidth, finalHeight;
			if (width > height) {
				float ratio = (float)width / (float)height;
				finalWidth = longSide;
				finalHeight = (int) (longSide / ratio);
			} else {
				float ratio = (float)height / (float)width;
				finalWidth = (int) (longSide / ratio);
				finalHeight = longSide;
			}
			int baseSide = (width > height) ? width : height;
			float scale = baseSide / longSide;
			if (scale <= 1.F) 
				scale = 1.f;
			opts.inSampleSize = (int) scale;
			Bitmap b = BitmapFactory.decodeFile(path, opts);
			if (b == null)
				return null;
			Log.v(TAG, "decode temp resampled bitmap w " + b.getWidth() + ", h " + b.getHeight());
			Log.v(TAG, "desire width " + finalWidth + ", h " + finalHeight);
			Bitmap retBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(retBitmap);
//			nativeDrawBitmap(canvas, b, null, new Rect(0, 0, finalWidth, finalHeight));
			Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
			p.setDither(true);
			canvas.drawBitmap(b, null, new Rect(0, 0, finalWidth, finalHeight), p);
			if (b != null) {
				b.recycle();
			}
			return retBitmap;
			
		} finally {
			if (opts != null) {
				opts.inSampleSize = 1;
			}
		}
	}
	
	public static Bitmap rotate(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees,
                    (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            } catch (Exception e) {
            	
            }
        }
        return b;
    }
	
	public static boolean saveBitmapToFile(String fullPath, Bitmap b, int quality) {
		if (b == null) {
			Log.e("BitmapCompresser", "no bitmap to be saved");
			return false;
		}
		java.io.File file = new java.io.File(fullPath);
		java.io.FileOutputStream fos = null;
		try {
			fos = new java.io.FileOutputStream(file);
			b.compress(CompressFormat.JPEG, quality, fos);
		} catch (Exception ex) {
			 Log.e("BitmapCompresser", "compress error", ex);
			 return false;
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {

				}
				fos = null;
			}
		}
		return true;
	}
}
