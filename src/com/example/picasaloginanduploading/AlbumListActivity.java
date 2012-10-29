package com.example.picasaloginanduploading;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.picasa.AlbumEntry;
import com.example.picasa.PicasaApi;

public class AlbumListActivity extends Activity {
	
	private static final int MSG_LOAD_ALBUMS = 0;
	private static final int MSG_LOAD_ALBUMS_SUCCESS = 1;
	private static final int MSG_LOAD_ALBUMS_FAIL = 2;
	
	private static final int DLG_WAIT = 3;
	
	private PicasaApi mApi;
	private ListView mListView;
	private AlbumAdapter mAdapter;
	private String mToken;
	private String mUsername;
	
	protected HandlerThread mWorker;
	protected Handler mWorkerHandler;
	
	private ProgressDialog mProgress;
	
	private Handler mHandler = new Handler() {
		@SuppressWarnings("deprecation")
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_LOAD_ALBUMS_SUCCESS) {
				if (msg.obj != null) {
					AlbumEntry[] entries = (AlbumEntry[]) msg.obj;
					mAdapter = new AlbumAdapter(AlbumListActivity.this, entries);
					if (mListView != null) {
						mListView.setAdapter(mAdapter);
						mListView.setOnItemClickListener(mAdapter);
					}
				}
				dismissDialog(DLG_WAIT);
			} else if (msg.what == MSG_LOAD_ALBUMS_FAIL) {
				Toast.makeText(AlbumListActivity.this, getString(R.string.error_service_not_available), Toast.LENGTH_LONG).show();
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
        
        if (TextUtils.isEmpty(mUsername) || TextUtils.isEmpty(mToken)) {
	        AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setMessage("Please Login first");
	    	builder.setPositiveButton(R.string.btn_ok, null);
	    	builder.show();
	    	return;
	    }
        
        mApi = new PicasaApi();
        
        setContentView(R.layout.activity_album_list);
        
        mListView = (ListView) findViewById(R.id.album_list);
        
        showDialog(DLG_WAIT);
        mWorkerHandler.sendEmptyMessage(MSG_LOAD_ALBUMS);
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
					if (msg.what == MSG_LOAD_ALBUMS) {
						if (mApi != null && !TextUtils.isEmpty(mUsername) && !TextUtils.isEmpty(mToken)) {
							AlbumEntry[] entry = mApi.getAlbums(mUsername, mToken);
							if (entry != null && entry.length > 0) {
								Message.obtain(mHandler, MSG_LOAD_ALBUMS_SUCCESS, entry).sendToTarget();
							} else {
								mHandler.sendEmptyMessage(MSG_LOAD_ALBUMS_FAIL);
							}
						}
					}
				}
				
			};
		} 
	}
    
    public class AlbumAdapter extends BaseAdapter implements OnItemClickListener {

    	private AlbumEntry[] mEntries;
    	private LayoutInflater mInflater;
    	private Context mContext;
    	
    	public AlbumAdapter(Context context, AlbumEntry[] entry) {
    		mContext = context;
    		mEntries = entry;
    		mInflater = LayoutInflater.from(context);
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
				v = mInflater.inflate(R.layout.album_list_item, null);
			}
			
			AlbumEntry ent = mEntries[pos];
			
			if (ent != null) {
			
				TextView title = (TextView) v.findViewById(R.id.title);
				TextView summary = (TextView) v.findViewById(R.id.summary);
				TextView count = (TextView) v.findViewById(R.id.count);
				
				if (title != null) {
					title.setText(ent.mTitle);
				}
				if (summary != null) {
					summary.setText(ent.mSummary);
				}
				if (count != null) {
					count.setText(String.valueOf(ent.mNumPhotos));
				}
			}
			return v;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
			AlbumEntry clickEntry = mEntries[pos];
			String albumId = clickEntry.mAlbumId;
			Intent intent = new Intent(mContext, PhotoListActivity.class);
			intent.putExtra("albumid", albumId);
			startActivity(intent);
		}
    	
    }
}
