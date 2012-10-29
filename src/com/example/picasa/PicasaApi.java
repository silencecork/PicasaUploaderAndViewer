package com.example.picasa;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.picasaloginanduploading.Util;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * PicasaApi implemented base on Picasa Specification, 
 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_protocol?hl=zh-TW
 * I don't use the library that provided by Google, because the size of library is too large
 * and update very slowly.
 * 
 * @author Justin Liu
 *
 */

public class PicasaApi {
	
	private static final String TAG = "PicasaApi";
	
	private static final String FEED_BASE = "http://picasaweb.google.com/data/feed/api/";
	private static final Uri FEED_URI = Uri.parse(FEED_BASE);
	
	private static final String PATH_USER = "user";
	private static final String PATH_ALBUM = "albumid";
	private static final String PARAM_IMG_MAX = "imgmax";
	private static final String PARAM_THUMB_SIZE = "thumbsize";
	private static final String PARAM_KIND = "kind";
	private static final String PARAM_VISIBLE = "visibility";
	private static final String PARAM_ALT = "alt";
	private static final String PARAM_FIELDS = "fields";
	
	private static final String HEADER_KEY_VERSION = "GData-Version";
	private static final String HEADER_KEY_ENCODE = "Accept-Encoding";
	private static final String HEADER_KEY_AUTH = "Authorization";
	@SuppressWarnings("unused")
	private static final String HEADER_KEY_IF_NONE_MATCH = "If-None-Match";
	@SuppressWarnings("unused")
	private static final String HEADER_KEY_IF_MATCH = "If-Match";
	@SuppressWarnings("unused")
	private static final String HEADER_KEY_ETAG = "ETag";
	
	private static final String HEADER_VALUE_VERSION = "2.0";
	private static final String HEADER_VALUE_ENCODE = "gzip";
	private static final String HEADER_VALUE_AUTH = "GoogleLogin auth=";
	
	
	/**
	 * This function will let you get albums in Picasa for specific users
	 * The result will get a album list. The album information that you can get please refer to AlbumEntry.java
	 * You can customize the returned information in the function (called partial response), 
	 * you can check source code in this function.
	 * 
	 * BTW, you can refer this url: 
	 * https://developers.google.com/gdata/docs/2.0/reference?hl=zh-TW#PartialResponse 
	 * get more information about returned value customization
	 * 
	 * 
	 * 
	 * @param uid user id, usually the user's email account (not include @gmail.com)
	 * @param token the token that get from login request
	 * @return AlbumEntry list
	 */
	public AlbumEntry[] getAlbums(String uid, String token) {
		Uri.Builder builder = FEED_URI.buildUpon();
		builder.appendEncodedPath(PATH_USER);
		builder.appendEncodedPath(uid);
		builder.appendQueryParameter(PARAM_IMG_MAX, PhotoSize.CROP_104.size());
		builder.appendQueryParameter(PARAM_THUMB_SIZE, PhotoSize.CROP_104.size());
		builder.appendQueryParameter(PARAM_KIND, "album");
		builder.appendQueryParameter(PARAM_VISIBLE, "visible");
		builder.appendQueryParameter(PARAM_ALT, "json");
		
		/**
		 *  customized the partial response
		 */
		builder.appendQueryParameter(PARAM_FIELDS, "entry(title,summary,published,updated,gphoto:id,gphoto:user,gphoto:numphotos,gphoto:bytesUsed,gphoto:timestamp,media:group(media:thumbnail))");
		Uri u = builder.build();
		Log.v(TAG, "album request uri " + u);
		Operation operation = new Operation();
		operation.token = token;
		
		try {
			getContent(uid, u, operation);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		
		if (operation.statusCode != HttpURLConnection.HTTP_OK) {
			return null;
		}
		
		String content = operation.content;
		
        JSONObject obj;
		try {
			obj = new JSONObject(content);
		} catch (JSONException e) {
			Log.e(TAG, "parse JSONObject occur error", e);
    		return null;
		}
		try {
			JSONObject feed = obj.getJSONObject("feed");
	        if (feed == null) {
				return null;
	        }
	        
			JSONArray entries = feed.getJSONArray("entry");
			int count;
			if (entries == null || (count = entries.length()) <= 0) {
				Log.e(TAG, "there is no entry");
				return null;
			}
			
			ArrayList<AlbumEntry> list = new ArrayList<AlbumEntry>(count);
			
			for (int i = 0; i < count; i++) {
				JSONObject entry = entries.getJSONObject(i);
				String id = entry.getJSONObject("gphoto$id").getString("$t");
				String user = entry.getJSONObject("gphoto$user").getString("$t");
				String title = entry.getJSONObject("title").getString("$t");
				@SuppressWarnings("unused")
				long timestamp = Long.parseLong(entry.getJSONObject("gphoto$timestamp").getString("$t"));

				String summary = entry.getJSONObject("summary").getString("$t");
				long dataPublished = parseAtomTimestamp(entry.getJSONObject("published").getString("$t"));
				long dataUpdated = parseAtomTimestamp(entry.getJSONObject("updated").getString("$t"));
				int numPhotos = Integer.parseInt(entry.getJSONObject("gphoto$numphotos").getString("$t"));

				JSONArray thumbAry = entry.getJSONObject("media$group").getJSONArray("media$thumbnail");
				int thumbType = thumbAry.length();
				String[] thumbUrls = new String[thumbType];
				for (int j = 0; j < thumbType; j++) {
					thumbUrls[j] = thumbAry.getJSONObject(j).getString("url");
				}
				
				AlbumEntry album = new AlbumEntry();
				album.mUserId = user;
				album.mAlbumId = id;
				album.mTitle = title;
				album.mSummary = summary;
				album.mNumPhotos = numPhotos;
				album.mDateModified = dataUpdated;
				album.mDatePublished = dataPublished;
				album.mThumbUrl = thumbUrls[0];
				
				list.add(album);
			}
			
			AlbumEntry[] ret = new AlbumEntry[list.size()];
			list.toArray(ret);
			
			return ret;
		} catch (Exception e) {
			Log.e(TAG, "parse JSONObject occur error", e);
    		return null;
		} 
	}
	
	/**
	 * This function let you get photos information of specific album
	 * You can check the PhotoEntry.java to know the detail information that you can get via this function
	 * This function also use partial response:
	 * https://developers.google.com/gdata/docs/2.0/reference?hl=zh-TW#PartialResponse 
	 * You can customize this part accroding above URL
	 * 
	 * @param uid user user id, usually the user's email account (not include @gmail.com)
	 * @param aid id of album that you can get via getAlbums() in this class
	 * @param token the token that get from login request
	 * @return PhotoEntry list
	 */
	public PhotoEntry[] getPhotos(String uid, String aid, String token) {
		Uri.Builder builder = FEED_URI.buildUpon();
		builder.appendEncodedPath(PATH_USER);
		builder.appendEncodedPath(uid);
		builder.appendEncodedPath(PATH_ALBUM);
		builder.appendEncodedPath(aid);
		builder.appendQueryParameter(PARAM_IMG_MAX, PhotoSize.UNCROP_800.size());
		builder.appendQueryParameter(PARAM_THUMB_SIZE, PhotoSize.UNCROP_128.size() + "," + PhotoSize.UNCROP_512.size() + "," + PhotoSize.UNCROP_800.size());
		builder.appendQueryParameter(PARAM_KIND, "photo");
		builder.appendQueryParameter(PARAM_VISIBLE, "visible");
		builder.appendQueryParameter(PARAM_ALT, "json");
		
		/**
		 *  customized the partial response
		 */
		builder.appendQueryParameter(PARAM_FIELDS, "entry(title,summary,content,gphoto:albumid,gphoto:id,gphoto:width,gphoto:height,gphoto:size,gphoto:commentingEnabled,gphoto:commentCount,gphoto:timestamp,published,updated,media:group(media:content,media:thumbnail),exif:tags(exif:make,exif:model,exif:time),georss:where)");
		Uri u = builder.build();
		Log.v(TAG, "photos request uri " + u);
		Operation operation = new Operation();
		operation.token = token;
		try {
			getContent(uid, u, operation);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		
		if (operation.statusCode != HttpURLConnection.HTTP_OK) {
			return null;
		}
		
		String content = operation.content;
		
		JSONObject obj;
		try {
			obj = new JSONObject(content);
		} catch (JSONException e) {
			Log.e(TAG, "parse JSONObject occur error", e);
    		return null;
		}
		try {
			JSONObject feed = obj.getJSONObject("feed");
	        if (feed == null) {
				return null;
	        }
	        
			JSONArray entries = feed.getJSONArray("entry");
			int count;
			if (entries == null || (count = entries.length()) <= 0) {
				Log.e(TAG, "there is no entry");
				return null;
			}
			
			ArrayList<PhotoEntry> list = new ArrayList<PhotoEntry>();
	    	
        	for (int i = 0; i < count; i++) {
        		JSONObject entry = entries.getJSONObject(i);
        		String userId = uid;
        		String albumId = entry.getJSONObject("gphoto$albumid").getString("$t");
        		long dateModified =  Long.parseLong(entry.getJSONObject("gphoto$timestamp").getString("$t"));
        		long datePublished = parseAtomTimestamp(entry.getJSONObject("published").getString("$t"));
        		String title = entry.getJSONObject("title").getString("$t");
        		String photoId = entry.getJSONObject("gphoto$id").getString("$t");
        		
        		JSONObject exif = entry.getJSONObject("exif$tags");
        		
        		String cameraMaker = null;
        		String cameraModel = null;
        		long dateTaken = 0L;
        		
        		if (exif != null) {
        			if (exif.has("exif$make")) {
	        			JSONObject make = exif.getJSONObject("exif$make");
	        			if (make != null) {
	        				cameraMaker = make.getString("$t");
	        			}
        			}
        			if (exif.has("exif$model")) {
	        			JSONObject model = exif.getJSONObject("exif$model");
	        			if (model != null) {
	        				cameraModel = model.getString("$t");
	        			}
        			}
        			if (exif.has("exif$time")) {
	        			JSONObject time = exif.getJSONObject("exif$time");
	        			if (time != null) {
	        				dateTaken = Long.parseLong(time.getString("$t"));
	        			}
        			}
        		}
        		JSONObject commentObj = entry.getJSONObject("gphoto$commentingEnabled");
        		int commentEnabled = (commentObj != null) ? (commentObj.getString("$t").equals("true") ? 1 : 0) : 1; 
        		int commentCount = Integer.parseInt(entry.getJSONObject("gphoto$commentCount").getString("$t"));
        		int width = Integer.parseInt(entry.getJSONObject("gphoto$width").getString("$t"));
        		int height = Integer.parseInt(entry.getJSONObject("gphoto$height").getString("$t"));
        		int size = Integer.parseInt(entry.getJSONObject("gphoto$size").getString("$t"));
        		String mimeType = entry.getJSONObject("content").getString("type");
        		
        		JSONObject media = entry.getJSONObject("media$group");
				String largeUrl = media.getJSONArray("media$content").getJSONObject(0).getString("url");
				
				JSONArray thumbUrls = media.getJSONArray("media$thumbnail");
				String miniUrl = thumbUrls.getJSONObject(0).getString("url");
				String thumbUrl = thumbUrls.getJSONObject(1).getString("url");
				double lat = 0d;
				double lng = 0d;
				if (entry.has("georss$where")) {
					JSONObject geo = entry.getJSONObject("georss$where");
					if (geo != null) {
						String pos = geo.getJSONObject("gml$Point").getJSONObject("gml$pos").getString("$t");
						int spaceIndex = pos.indexOf(' ');
						lat = Double.parseDouble(pos.substring(0, spaceIndex));
						lng = Double.parseDouble(pos.substring(spaceIndex + 1));
					}
				}
				
				PhotoEntry photo = new PhotoEntry();
				photo.mAlbumId = albumId;
				photo.mPhotoId = photoId;
				photo.mUserId = userId;
				photo.mTitle = title;
				photo.mDatePublished = datePublished;
				photo.mDateModified = dateModified;
				photo.mDateTaken = dateTaken;
				photo.mWidth = width;
				photo.mHeight = height;
				photo.mSize = size;
				photo.mMimeType = mimeType;
				photo.mMake = cameraMaker;
				photo.mModel = cameraModel;
				photo.mCommentEnabled = commentEnabled;
				photo.mCommentCount = commentCount;
				photo.mLargeUrl = largeUrl;
				photo.mThumbUrl = thumbUrl;
				photo.mMiniUrl = miniUrl;
				photo.mLatitude = lat;
				photo.mLongitude = lng;
				list.add(photo);
        	}
        	
        	PhotoEntry [] ret = new PhotoEntry[list.size()];
        	list.toArray(ret);
        	
        	return ret;
        	
		} catch (Exception e) {
			Log.e(TAG, "parse JSONObject occur error", e);
    		return null;
    	} 
	}
	
	private void getContent(String uid, Uri u, Operation operation) throws IOException {
		String content = null;
		
		HashMap<String, String> basicHeaders = new HashMap<String, String>();
		basicHeaders.put(HEADER_KEY_VERSION, HEADER_VALUE_VERSION);
		basicHeaders.put(HEADER_KEY_ENCODE, HEADER_VALUE_ENCODE);
		String authToken = operation.token;
		if (authToken != null)
			basicHeaders.put(HEADER_KEY_AUTH, HEADER_VALUE_AUTH + authToken);
		
		HttpURLConnection conn = httpGetJavaNet(u.toString(), basicHeaders);
		InputStream in = null;
		try {
			int code = conn.getResponseCode();
			operation.statusCode = code;
			operation.errMsg = conn.getResponseMessage();
			Log.v(TAG, "response msg " + operation.errMsg);
			in = conn.getInputStream();
			content = Util.streamToString(in);
			operation.content = content;
		} finally {
			Util.closeSilently(in);
			if (conn != null)
				conn.disconnect();
		}
	}
	
	public HttpURLConnection httpGetJavaNet(String url, HashMap<String, String> headers) throws IOException {
		HttpURLConnection conn = getConnection(url, headers, false);
		if (conn == null)
			return null;
		conn.connect();
		return conn;
	}
	
	private HttpURLConnection getConnection(String strUrl, HashMap<String, String> headers, boolean post) throws IOException {
		URL url = new URL(strUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod((post) ? "POST" : "GET");
		if (headers != null) {
			Iterator<Entry<String, String>> setItr = headers.entrySet().iterator();
			if (setItr != null) {
				while (setItr.hasNext()) {
					Entry<String, String> ent = setItr.next();
					conn.addRequestProperty(ent.getKey(), ent.getValue());
				}
			}
		}
		conn.setConnectTimeout(20000);
		return conn;
}
	
	private long parseAtomTimestamp(String timestamp) {
		if (TextUtils.isEmpty(timestamp)) {
			Log.e(TAG, "timestamp is empty");
			return 0L;
		}
		Date time = null;
		try {
			time = Util.parseRFC3339Date(timestamp);
		} catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return (time != null) ? time.getTime() : 0;
    }
	
	public static final int[] UNCROP_THUMB_SIZES = {94, 110, 128, 200, 220, 288, 320, 400, 512, 576, 640, 720, 800, 912, 1024, 1152, 1280, 1440, 1600};
	public static enum PhotoSize {
		CROP_32("32c", 32),
		CROP_48("48c", 48),
		CROP_64("64c", 64),
		CROP_72("72c", 72),
		CROP_104("104c", 104),
		CROP_144("144c", 144),
		CROP_150("150c", 150),
		CROP_160("160c", 160),
		
		UNCROP_94("94u", 94),
		UNCROP_110("110u", 110),
		UNCROP_128("128u", 128),
		UNCROP_200("200u", 200),
		UNCROP_220("220u", 220),
		UNCROP_288("288u", 288),
		UNCROP_320("320u", 320),
		UNCROP_400("400u", 400),
		UNCROP_512("512u", 512),
		UNCROP_576("576u", 576),
		UNCROP_640("640u", 640),
		UNCROP_720("720u", 720),
		UNCROP_800("800u", 800),
		UNCROP_912("912u", 912),
		UNCROP_1024("1024u", 1024);
		
		private final int mLongSide;
		private final String mSize;
		private PhotoSize(String size, int longSide) {
			mSize = size;
            mLongSide = longSide;
        }
		
		public int longSide() {
			return mLongSide;
		}
		
		public String size() {
			return mSize;
		}
		
	};
	
	private class Operation {
		public int statusCode;
		public String content;
		public String errMsg;
		public String token;
		
		public void reset() {
			statusCode = -1;
			content = null;
			errMsg = null;
			token = null;
		}
	}
}
