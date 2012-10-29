package com.example.picasaloginanduploading;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picasa.PhotoEntry;
import com.example.picasa.PicasaApi;

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

    	private PhotoEntry[] mEntries;
    	private LayoutInflater mInflater;
    	private SimpleDateFormat mFormat;
    	
    	public PhotoAdapter(Context context, PhotoEntry[] entry) {
    		mEntries = entry;
    		mInflater = LayoutInflater.from(context);
    		mFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
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

		@Override
		public View getView(int pos, View v, ViewGroup parent) {
			if (v == null) {
				v = mInflater.inflate(R.layout.photo_list_item, null);
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
    	
    }
    
}
