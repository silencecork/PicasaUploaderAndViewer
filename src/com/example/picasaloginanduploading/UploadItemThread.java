package com.example.picasaloginanduploading;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

interface OnUploadListener {
	public void onUploading(int progress);
	public void onWaitServerResponse();
	public void onFinish(boolean success);
}

public class UploadItemThread extends Thread {
	
	private static final String TAG = "UploadItemThread";
	private OnUploadListener mListener;
	private String mToken;
	private String mTitle;
	private String mSummary;
	private String mMimeType;
	private String mUserId;
	private String mAlbumId;
	private String mDataPath;
	
	/**
	 * You can not upload original photo to server, it may cause out of memory error.
	 * This variable is to make uploading photo smaller.
	 */
	private static final int PHOTO_RESIZE_SIDE_LENGTH = 1280;
	
	/**
	 * The constructor of UploadItemThread
	 * 
	 * @param c Activity instance
	 * @param token The access token of your account, can not null
	 * @param secret The access secret of your account, can not null
	 * @param userId The user id of your account, can not null
	 * @param albumId The id of album you want your photo uploading to, can be null
	 * @param title The photo title
	 * @param summary summary of photo 
	 * @param mimetype photo mime type, e.g. image/jpg, image/png
	 * @param path the photo path in the phone
	 * @param l You can get upload status through this listener
	 */
	UploadItemThread(String token, String username, String albumId, String title, String summary, String mimetype, String path, OnUploadListener l) {
		super("UploadItemThread");
		mToken = token;
		mUserId = username;
		mAlbumId = albumId;
		mTitle = title;
		mSummary = summary;
		mMimeType = mimetype;
		mDataPath = path;
		mListener = l;
	}
	
	@Override
	public void run() {
		String resizedPath = resizeBitmap(mDataPath, PHOTO_RESIZE_SIDE_LENGTH);
		uploadToPicasa(resizedPath);
	}
	
	/**
	 * Please do not take deep into this source code, this source code makes photo as multi-part uploading mechanism
	 */
	private void uploadToPicasa(String resizedPath) {
		Log.d(TAG, "upload to picasa " + resizedPath);
		DataOutputStream out = null;
		HttpURLConnection conn = null;
		try {
			File f = new File(resizedPath);
			if (!f.exists()) {
				Log.e(TAG, "file " + resizedPath + " does not exists");
				return;
			}
			
			FileInputStream in = new FileInputStream(f);
			
			String boundary = "----=_Part_0_625715618.1281115354330";
			Uri uri = Uri.parse("http://picasaweb.google.com/data/feed/api/user/");
			Uri.Builder b = uri.buildUpon();
			b.appendEncodedPath(mUserId);
			if (mAlbumId != null) {
				b.appendEncodedPath("albumid");
				b.appendEncodedPath(mAlbumId);
			}
			String url = b.build().toString();
			URL u = new URL(url);
			conn = (HttpURLConnection) u.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "multipart/related;boundary=\""+boundary+"\"");
			conn.setRequestProperty("Content-Length", String.valueOf(f.length()));
			conn.setRequestProperty("Authorization", "GoogleLogin auth=" + mToken);
			conn.setRequestProperty("User-Agent", "PicasaTestClient");
			conn.setRequestProperty("Accept-Encoding", "gzip");
			conn.setRequestProperty("GData-Version", "2.0");
			conn.setRequestProperty("Slug", f.getName());
			conn.setRequestProperty("Cache-Control", "no-cache");
			conn.setRequestProperty("Pragma", "no-cache");
			conn.setRequestProperty("Host", "picasaweb.google.com");
			conn.setRequestProperty("Accept", "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2");
			conn.setRequestProperty("Connection", "keep-alive");
			conn.setRequestProperty("Transfer-Encoding", "chunked");
			conn.setUseCaches(false);
			conn.setInstanceFollowRedirects(true);
			conn.setDoOutput(true);
			conn.setChunkedStreamingMode(0);
			
			OutputStream data = conn.getOutputStream();
			if (mMimeType == null) {
				mMimeType = "image/jpeg";
			}
			out = new DataOutputStream(data);
			out.write(("--"+ boundary).getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(("Content-Type: application/atom+xml; charset=UTF-8").getBytes("UTF-8"));
			out.write(("\r\n\r\n").getBytes("UTF-8"));
			generateXmlForUpload(out, mTitle, mSummary, mMimeType);
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(("--"+ boundary).getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(("Content-Type: "+ mMimeType).getBytes("UTF-8"));
			out.write(("\r\n\r\n").getBytes("UTF-8"));
			generatePhotoBinary(out, in);
			out.write(("\r\n").getBytes("UTF-8"));
			out.write(("--"+ boundary + "--").getBytes("UTF-8"));
			out.write(("\r\n").getBytes("UTF-8"));
			
			conn.connect();
			out.flush();
			
			if (mListener != null) {
				mListener.onWaitServerResponse();
			}
			
			int status = conn.getResponseCode();
			Log.v(TAG, "response code: " + status);
			if (status != HttpURLConnection.HTTP_CREATED) {
				Log.v(TAG, "response: " + conn.getResponseMessage());
			}
			Log.v(TAG, "response: " + conn.getResponseMessage());
			if (mListener != null) {
				mListener.onFinish(status == HttpURLConnection.HTTP_CREATED);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "upload file occur exception", e);
		} finally {
			Util.closeSilently(out);
			if (conn != null)
				conn.disconnect();
		}
	}
	
	public static void generateXmlForUpload(DataOutputStream out, String title, String summary, String type) throws IOException {
		StringBuilder builder = new StringBuilder();
		
		builder.append("<?xml version='1.0' encoding='UTF-8'?><entry xmlns='http://www.w3.org/2005/Atom' xmlns:exif='http://schemas.google.com/photos/exif/2007' xmlns:geo='http://www.w3.org/2003/01/geo/wgs84_pos#' xmlns:gml='http://www.opengis.net/gml' xmlns:georss='http://www.georss.org/georss' xmlns:photo='http://www.pheed.com/pheed/' xmlns:media='http://search.yahoo.com/mrss/' xmlns:gphoto='http://schemas.google.com/photos/2007'><category scheme='http://schemas.google.com/g/2005#kind' term='http://schemas.google.com/photos/2007#photo'/>");
		if (title != null) {
			builder.append("<atom:title xmlns:atom='http://www.w3.org/2005/Atom'>"+title+"</atom:title>");
		}
		if (summary != null)
			builder.append("<atom:summary xmlns:atom='http://www.w3.org/2005/Atom'>"+summary+"</atom:summary>");
		// TODO Get correct latitude longitude
//		if (!(latlng == null || latlng[0] == 255f || latlng[0] == 0 || latlng[1] == 255f || latlng[1] == 0)) {
//			builder.append("<georss:point xmlns:georss='http://www.georss.org/georss'>" + latlng[0] + " " + latlng[1] + "</georss:point>");
//		}
		builder.append("<atom:content xmlns:atom='http://www.w3.org/2005/Atom' type='"+type+"'/><gphoto:client>PicasaTestClient</gphoto:client></entry>");
		out.write(builder.toString().getBytes("UTF-8"));
	}
	
	private String resizeBitmap(String dataPath, int side) {
		if (dataPath == null)
			return null;
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(dataPath, opts);
		opts.inJustDecodeBounds = false;
		boolean isJPEG = "image/jpeg".equals(opts.outMimeType);
		Bitmap reSizedBitmap = BitmapUtils.getKeepRatioBitmap(side, dataPath, opts);
		
		int rotation = 0;
		if (isJPEG) {
			try {
				ExifInterface exif = new ExifInterface(dataPath);
				String ori = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
				if ("3".equals(ori)) {
					rotation = 180;
				} else if ("6".equals(ori)) {
					rotation = 90;
				} else if ("8".equals(ori)) {
					rotation = 270;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (rotation != 0) {
			Bitmap tmp = BitmapUtils.rotate(reSizedBitmap, rotation);
			if (tmp != null) {
				reSizedBitmap.recycle();
				reSizedBitmap = tmp;
			}
		}
		
		if (reSizedBitmap == null) {
			return null;
		}
		Log.d(TAG, "upload file size " + reSizedBitmap.getWidth() + ", " + reSizedBitmap.getHeight());
		File dir = Environment.getExternalStorageDirectory();
		String extension = "jpg";
		if ("image/png".equals(opts.outMimeType)) {
			extension = "png";
		} else if ("image/gif".equals(opts.outMimeType)) {
			extension = "gif";
		}
		String tempPath = dir.toString() + "/upload." + extension;
		File tmpFile = new File(tempPath);
		
		if (tmpFile.exists()) {
			tmpFile.delete();
		}
		
		if (!BitmapUtils.saveBitmapToFile(tempPath, reSizedBitmap, 100)) {
			return null;
		}
		
		if (isJPEG) {
			saveDataToExif(dataPath, tempPath, rotation);
		}
		
		return tempPath;
	}
	
	private void saveDataToExif(String dataPath, String resizedPath, int rotation) {
		try {
			ExifInterface exif1 = new ExifInterface(dataPath);
			ExifInterface exif2 = new ExifInterface(resizedPath);
			String data = exif1.getAttribute(ExifInterface.TAG_ORIENTATION);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_ORIENTATION, (rotation != 0) ? "1" : data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_APERTURE);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_APERTURE, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_DATETIME);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_DATETIME, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_DATETIME, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_FLASH);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_FLASH, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_ISO);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_ISO, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_MAKE);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_MAKE, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_MODEL);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_MODEL, data);
			}
			data = exif1.getAttribute(ExifInterface.TAG_WHITE_BALANCE);
			if (!TextUtils.isEmpty(data)) {
				exif2.setAttribute(ExifInterface.TAG_WHITE_BALANCE, data);
			}
			exif2.saveAttributes();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void generatePhotoBinary(OutputStream out, InputStream in)
			throws IOException {
		BufferedOutputStream bos = new BufferedOutputStream(out);
		BufferedInputStream bis = new BufferedInputStream(in);
		int totalSize = bis.available();
		Log.v(TAG, "upload file available " + bis.available());
		try {
			byte[] buf = new byte[2048]; // Transfer in 2k chunks
			int bytesRead = 0;
			float len = 0;
			while ((bytesRead = bis.read(buf, 0, buf.length)) >= 0) {
				bos.write(buf, 0, bytesRead);
				len += bytesRead;
				len += bytesRead;
				int progress = (int)((len / totalSize) * 100);
				if (mListener != null) {
					mListener.onUploading(progress);
				}
			}
			bos.flush();
		} finally {
			bis.close();
		}
	}
	
}
