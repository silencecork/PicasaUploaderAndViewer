package com.example.picasaloginanduploading;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picasa.AlbumEntry;
import com.example.picasa.PhotoEntry;
import com.example.picasa.PicasaApi;
import com.example.picasaloginanduploading.AlbumListActivity.AlbumAdapter.DownloadThread;

public class PhotoListActivity extends Activity {

	private static final int MSG_LOAD_PHOTO = 0;
	private static final int MSG_LOAD_PHOTO_SUCCESS = 1;
	private static final int MSG_LOAD_PHOTO_FAIL = 2;
	
	private static final int DLG_WAIT = 3;
	
	private PicasaApi mApi;
	private ListView mListView;
	private WebView mViewer;
	private PhotoAdapter mAdapter;
	private String mToken;
	private String mUsername;
	private String mAlbumId;
	
	protected HandlerThread mWorker;
	protected Handler mWorkerHandler;
	
	private ProgressDialog mProgress;
	
	private Handler mHandler = new Handler() {
		@SuppressWarnings("deprecation")
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_LOAD_PHOTO_SUCCESS) {
				if (msg.obj != null) {
					PhotoEntry[] entries = (PhotoEntry[]) msg.obj;
					mAdapter = new PhotoAdapter(PhotoListActivity.this, entries);
					if (mListView != null) {
						mListView.setAdapter(mAdapter);
						mListView.setOnItemClickListener(mAdapter);
					}
				}
				dismissDialog(DLG_WAIT);
			} else if (msg.what == MSG_LOAD_PHOTO_FAIL) {
				Toast.makeText(PhotoListActivity.this, getString(R.string.error_service_not_available), Toast.LENGTH_LONG).show();
				dismissDialog(DLG_WAIT);
			}
		}
	};

    @SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        createWorker();
        
        loadUserAndToken();
        
        mAlbumId = getIntent().getStringExtra("albumid");
        
        if (TextUtils.isEmpty(mUsername) || TextUtils.isEmpty(mToken) || TextUtils.isEmpty(mAlbumId)) {
	        AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setMessage("Please Login first");
	    	builder.setPositiveButton(R.string.btn_ok, null);
	    	builder.show();
	    	return;
	    }
        
        mApi = new PicasaApi();
        
        setContentView(R.layout.activity_photo_list);
        
        mListView = (ListView) findViewById(R.id.photo_list);
        mViewer = (WebView) findViewById(R.id.viewer);
        if (mViewer != null) {
        	mViewer.getSettings().setBuiltInZoomControls(true);
        }
        
        showDialog(DLG_WAIT);
        mWorkerHandler.sendEmptyMessage(MSG_LOAD_PHOTO);
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (mWorker != null) {
    		mWorker.quit();
    	}
    	if (mAdapter != null) {
    		mAdapter.stop();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_album_list, menu);
        return true;
    }
    
    @SuppressWarnings("deprecation")
	@Override
    public Dialog onCreateDialog(int id) {
    	if (id == DLG_WAIT) {
    		mProgress = new ProgressDialog(this);
    		mProgress.setMessage(getString(R.string.msg_load_wait));
    		return mProgress;
    	}
		return super.onCreateDialog(id);
    }
    
    
    @Override
	public void onBackPressed() {
    	if (mViewer != null && mViewer.getVisibility() == View.VISIBLE) {
    		mViewer.setVisibility(View.GONE);
    		return;
    	}
		super.onBackPressed();
	}

	private void loadUserAndToken() {
    	mToken = Util.getToken(this);
        mUsername = Util.getUserName(this);
    }

    private void createWorker() {
		if (mWorker == null || !mWorker.isAlive()) {
			mWorker = new HandlerThread("worker");
			mWorker.start();
			mWorkerHandler = new Handler(mWorker.getLooper()) {
	
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == MSG_LOAD_PHOTO) {
						if (mApi != null && !TextUtils.isEmpty(mUsername) && !TextUtils.isEmpty(mToken) && !TextUtils.isEmpty(mAlbumId)) {
							PhotoEntry[] entry = mApi.getPhotos(mUsername, mAlbumId, mToken);
							if (entry != null && entry.length > 0) {
								Message.obtain(mHandler, MSG_LOAD_PHOTO_SUCCESS, entry).sendToTarget();
							} else {
								mHandler.sendEmptyMessage(MSG_LOAD_PHOTO_FAIL);
							}
						}
					}
				}
				
			};
		} 
	}
    
    public class PhotoAdapter extends BaseAdapter implements OnItemClickListener {
    	
    	private static final int MSG_UPDATE = 0;
    	private PhotoEntry[] mEntries;
    	private LayoutInflater mInflater;
    	private SimpleDateFormat mFormat;
    	private SparseArray<View> mViewCache;
    	private SparseArray<Bitmap> mBitmapCache;
    	private DownloadThread mDownloadThread;
    	
    	private Handler mHandler = new Handler() {

    		@Override
    		public void handleMessage(Message msg) {
    			if (msg.what == MSG_UPDATE) {
    				int pos = msg.arg1;
    				Bitmap b = (Bitmap) msg.obj;
    				if (mAdapter != null) {
    					mAdapter.update(pos, b);
    				}
    			} 
    		}
    		
    	};
    	
    	public PhotoAdapter(Context context, PhotoEntry[] entry) {
    		mEntries = entry;
    		mInflater = LayoutInflater.from(context);
    		mFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    		mViewCache = new SparseArray<View>();
    		mBitmapCache = new SparseArray<Bitmap>();
    		mDownloadThread = new DownloadThread();
    		mDownloadThread.start();
    	}

    	public void update(int pos, Bitmap b) {
    		View view;
    		synchronized (mViewCache) {
    			view = mViewCache.get(pos, null);
			}
    		if (view != null) {
    			ImageView imageView = (ImageView) view.findViewById(R.id.icon);
    			imageView.setImageBitmap(b);
    			imageView.invalidate();
    		}
    	}
    	
    	public void stop() {
    		if (mDownloadThread != null) {
    			mDownloadThread.stopThread();
    		}
    		if (mViewCache != null) {
    			mViewCache.clear();
    		}
    		if (mBitmapCache != null) {
    			for (int i = 0; i < mBitmapCache.size(); i++) {
    				Bitmap b = mBitmapCache.get(i);
    				if (b != null) {
    					b.recycle();
    				}
				}
    		}
    	}
    	
		@Override
		public int getCount() {
			return (mEntries != null) ? mEntries.length : 0;
		}

		@Override
		public Object getItem(int pos) {
			return (mEntries != null) ? mEntries[pos] : null;
		}

		@Override
		public long getItemId(int pos) {
			return pos;
		}

		private View getViewFromCache(int position) {
			synchronized (mViewCache) {
				return mViewCache.get(position);
			}
		}
		
		private void putViewToCache(int position, View v) {
			synchronized (mViewCache) {
				mViewCache.put(position, v);
			}
		}
		
		private Bitmap getBitmapFromCache(int position) {
			synchronized (mBitmapCache) {
				return mBitmapCache.get(position);
			}
		}
		
		private void putBitmapToCache(int position, Bitmap b) {
			synchronized (mBitmapCache) {
				mBitmapCache.put(position, b);
			}
		}
		
		@Override
		public View getView(int pos, View v, ViewGroup parent) {

			v = getViewFromCache(pos);
			if (v == null) {
				v = mInflater.inflate(R.layout.photo_list_item, null);
				putViewToCache(pos, v);
			}
			
			PhotoEntry ent = mEntries[pos];
			
			if (ent != null) {
			
				TextView title = (TextView) v.findViewById(R.id.title);
				TextView date = (TextView) v.findViewById(R.id.date);
				
				if (title != null) {
					title.setText(ent.mTitle);
				}
				if (date != null) {
					date.setText(mFormat.format(new Date(ent.mDateModified)));
				}
				
				ImageView image = (ImageView) v.findViewById(R.id.icon);
				if (image != null) {
					Bitmap b = getBitmapFromCache(pos);
					if (b != null && !b.isRecycled()) {
						image.setImageBitmap(b);
					}
				}
			}
			return v;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
			PhotoEntry entry = mEntries[pos];
			String url = entry.mThumbUrl;
			if (!TextUtils.isEmpty(url)) {
				if (mViewer == null) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					startActivity(intent);
				} else {
					mViewer.setVisibility(View.VISIBLE);
					mViewer.loadUrl(url);
				}
			}
		}
		
		class DownloadThread extends Thread {
			
			private boolean mDone;
			
			public DownloadThread() {
				super("download_thread");
			}
			
			public void stopThread() {
				mDone = true;
			}
			
			public void run() {
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
				int index = 0;
				int length = mEntries.length;
				System.out.println("entry length " + length);
				while (!mDone && index < length) {
					System.out.println("index " + index);
					PhotoEntry entry = mEntries[index];
					String path = Environment.getExternalStorageDirectory().getPath() + File.separator + "my_download" 
							+ File.separator + "photo" + File.separator + entry.mAlbumId + File.separator + entry.mPhotoId;
					if (new File(path).exists()) {
						putBitmapToCacheAndPostBack(index, path);
					} else {
						if (download(entry.mMiniUrl, path)) {
							putBitmapToCacheAndPostBack(index, path);
						}
					}
					index++;
				}
			}
			
			
			private void putBitmapToCacheAndPostBack(int pos, String path) {
				Bitmap b = BitmapFactory.decodeFile(path);
				putBitmapToCache(pos, b);
				Message.obtain(mHandler, MSG_UPDATE, pos, 0, b).sendToTarget();
			}
			
			private boolean download(String downloadURL, String path) {
				boolean success = false;
				if (downloadURL == null || path == null)
					return success;
				InputStream in = null;
				FileOutputStream out = null;
				HttpURLConnection conn = null;
				try {
					File file = new File(path);
					if (file.exists()) {
						Log.v(getClass().getName(), "file " + path + " already exists");
						success = true;
						return success;
					}
					File parent = file.getParentFile();
					if (!parent.exists()) {
						Log.w(getClass().getName(), "download parent dir doesn't exist " + parent.toString());
						parent.mkdirs();
					}
					URL url = new URL(downloadURL);
					conn = (HttpURLConnection) url.openConnection();
					conn.setConnectTimeout(10000);
					conn.connect();
					@SuppressWarnings("unused")
					int contentLength = conn.getContentLength();
					in = conn.getInputStream();
					out = new FileOutputStream(file);
					
					byte[] buffer = new byte[1024];
					int len;
//					long total = 0;
					while ((len = in.read(buffer)) > 0) {
						out.write(buffer, 0, len);
//						total += len;
					}
					return true;
				} catch (MalformedURLException e) {
					Log.e(getClass().getName(), "parse URL occur error", e);
				} catch (SocketTimeoutException e) {
					Log.e(getClass().getName(), "Connection Timeout error", e);
				} catch (IOException e) {
					Log.e(getClass().getName(), "IO error", e);
				} finally {
					closeStream(in);
					closeStream(out);
					if (conn != null) {
						conn.disconnect();
					}
				}
				return false;
			}
			
			public void closeStream(Closeable out) {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
	    }
    	
    }
    
}
