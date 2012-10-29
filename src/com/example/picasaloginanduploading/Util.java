package com.example.picasaloginanduploading;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

public class Util {

	public static String streamToString(InputStream in) throws IOException {
		if (in == null) {
			return null;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			byte[] buffer = new byte[1024];
			int len;
			int size = 0;
			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
				size += len;
			}
			if (size <= 0) {
				return null;
			}
			return Util.removeUnusePart(out.toString());
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	private static String removeUnusePart(String stream)
			throws StringIndexOutOfBoundsException {
		if (TextUtils.isEmpty(stream))
			return null;
		if (stream.startsWith("["))
			return stream;
		int splitIdx = stream.indexOf("{");
		if (splitIdx == -1) {
			return null;
		}
		if (splitIdx == 0)
			return stream;
		return stream.substring(splitIdx, stream.length() - 1);
	}

	public static Date parseRFC3339Date(String datestring)
			throws ParseException, IndexOutOfBoundsException {
		Date d = new Date();

		// if there is no time zone, we don't need to do any special parsing.
		if (datestring.endsWith("Z")) {
			try {
				SimpleDateFormat s = new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm:ss'Z'");// spec for RFC3339
				d = s.parse(datestring);
			} catch (java.text.ParseException pe) {// try again with optional
													// decimals
				SimpleDateFormat s = new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");// spec for RFC3339
															// (with fractional
															// seconds)
				s.setLenient(true);
				d = s.parse(datestring);
			}
			return d;
		}

		// step one, split off the timezone.
		String firstpart = datestring.substring(0, datestring.lastIndexOf('-'));
		String secondpart = datestring.substring(datestring.lastIndexOf('-'));

		// step two, remove the colon from the timezone offset
		secondpart = secondpart.substring(0, secondpart.indexOf(':'))
				+ secondpart.substring(secondpart.indexOf(':') + 1);
		datestring = firstpart + secondpart;
		SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");// spec
																			// for
																			// RFC3339
		try {
			d = s.parse(datestring);
		} catch (java.text.ParseException pe) {// try again with optional
												// decimals
			s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");// spec
																		// for
																		// RFC3339
																		// (with
																		// fractional
																		// seconds)
			s.setLenient(true);
			d = s.parse(datestring);
		}
		return d;
	}

	public static void closeSilently(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void clearCookies(Context context) {
		// Edge case: an illegal state exception is thrown if an instance of
		// CookieSyncManager has not be created. CookieSyncManager is normally
		// created by a WebKit view, but this might happen if you start the
		// app, restore saved state, and click logout before running a UI
		// dialog in a WebView -- in which case the app crashes
		@SuppressWarnings("unused")
		CookieSyncManager cookieSyncMngr = CookieSyncManager
				.createInstance(context);
		CookieManager cookieManager = CookieManager.getInstance();
		cookieManager.removeAllCookie();
	}

	public static String getFileExtension(String filePath, boolean hasDot) {
		int extensionIdx = filePath.lastIndexOf('.');
		String extension = null;
		if (extensionIdx > 0) {
			extensionIdx = (!hasDot) ? extensionIdx + 1 : extensionIdx;
			extension = filePath.substring(extensionIdx, filePath.length());
		}
		return extension;
	}

	public static boolean isNetworkAvailable(Context context) {
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager == null) {
			Log.e("Network", "no ConnectivityManager");
		} else {
			NetworkInfo info = manager.getActiveNetworkInfo();
			if (info != null) {
				if (info.getState() == NetworkInfo.State.CONNECTED) {
					if (info.getType() == ConnectivityManager.TYPE_WIFI
							|| info.getType() == ConnectivityManager.TYPE_WIMAX
							|| info.getType() == ConnectivityManager.TYPE_MOBILE
							&& info.isConnected()) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public static boolean saveToken(Context c, String token) {
		if (TextUtils.isEmpty(token)) {
			return false;
		}
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		Editor editor = prefs.edit();
		editor.putString("valid_token", token);
		return editor.commit();
	}
    
	public static String getToken(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		return prefs.getString("valid_token", null);
	}
	
	public static boolean saveUserName(Context c, String secret) {
		if (TextUtils.isEmpty(secret)) {
			return false;
		}
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		Editor editor = prefs.edit();
		editor.putString("username", secret);
		return editor.commit();
	}
    
	public static String getUserName(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		return prefs.getString("username", null);
	}
	
	public static boolean savePassword(Context c, String userId) {
		if (TextUtils.isEmpty(userId)) {
			return false;
		}
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		Editor editor = prefs.edit();
		editor.putString("password", userId);
		return editor.commit();
	}
    
	public static String getPassword(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		return prefs.getString("password", null);
	}
}