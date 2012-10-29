package com.example.picasaloginanduploading;


import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Images;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


/**
 * Read this first!!
 * 
 * Please fill up the columns in GoogleApiData.java to make this app work!
 * 
 * @author Justin
 *
 */
public class MainActivity extends Activity implements OnUploadListener {

	private static final int LOGIN_REQUEST = 1;
	private static final int PICK_REQUEST = 2;
	
	private static final int MSG_SHOW_UPLOAD_PROGRESS = 100;
	private static final int MSG_WAIT_PICASA_SERVER_RESPONSE = 200;
	private static final int MSG_SHOW_UPLOAD_SUCCESS = 300;
	private static final int MSG_SHOW_UPLOAD_FAIL = 400;
	
	private TextView mTokenView;
	private Button mChoosePhoto;
	private String mToken;
	private String mUserName;
	private String mPassword;
	private Uri mUploadUri;
	private UploadItemThread mCurrentUploadItem;
	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_SHOW_UPLOAD_PROGRESS) {
				if (mTokenView != null) {
					mTokenView.setText("" + msg.arg1);
				}
			} else if (msg.what == MSG_SHOW_UPLOAD_SUCCESS) {
				if (mTokenView != null) {
					mTokenView.setText("Upload Success");
				}
			} else if (msg.what == MSG_SHOW_UPLOAD_FAIL) {
				if (mTokenView != null) {
					mTokenView.setText("Upload Fail");
				}
			} else if (msg.what == MSG_WAIT_PICASA_SERVER_RESPONSE) {
				if (mTokenView != null) {
					mTokenView.setText("Wait for Picasa Server Response");
				}
			}
		}
		
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mTokenView = (TextView) findViewById(R.id.token);
        mChoosePhoto = (Button) findViewById(R.id.button1);
        
        if (mTokenView != null) {
	        mToken = Util.getToken(this);
	        mUserName = Util.getUserName(this);
	        mPassword = Util.getPassword(this);
	        if (!TextUtils.isEmpty(mToken) && !TextUtils.isEmpty(mUserName) && !TextUtils.isEmpty(mPassword)) {
	        	mTokenView.setText(mPassword + "\n" + mToken + "\n" + mUserName);
	        } else {
	        	mTokenView.setText(R.string.no_token);
	        	startLoginActivity();
	        }
        }
        
        if (mChoosePhoto != null) {
        	mChoosePhoto.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if (!TextUtils.isEmpty(mToken) && !TextUtils.isEmpty(mUserName) && !TextUtils.isEmpty(mPassword)) {
						Intent intent = new Intent(Intent.ACTION_PICK);
						intent.setType("image/*");
						startActivityForResult(intent, PICK_REQUEST);
					}
				}
			});
        }
    }

    @Override
    public void onResume() {
    	super.onResume();
    	if (mUploadUri != null) {
    		Cursor c = getContentResolver().query(mUploadUri, new String[] {Images.ImageColumns.DATA, Images.ImageColumns.MIME_TYPE}, null, null, null);
			if (c != null && c.moveToFirst()) {
				String dataPath = c.getString(c.getColumnIndexOrThrow(Images.ImageColumns.DATA));
				String mimeType = c.getString(c.getColumnIndexOrThrow(Images.ImageColumns.MIME_TYPE));
				c.close();
				mUploadUri = null;
				
				mCurrentUploadItem = new UploadItemThread(mToken, mUserName, null, "This is test photo", "This is test summary", mimeType, dataPath, this);
				mCurrentUploadItem.start();
			}
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_login) {
			startLoginActivity();
		} else if (item.getItemId() == R.id.menu_albumlist) {
			changeToAlbumList();
		}
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == LOGIN_REQUEST && data != null) {
			/***** Save this token *****/
			String token = data.getStringExtra("token");
			String username = data.getStringExtra("username");
			String password = data.getStringExtra("password");
			if (Util.saveToken(this, token) && Util.saveUserName(this, username) && Util.savePassword(this, password)) {
				mToken = token;
				mUserName = username;
				mPassword = password;
				mTokenView.setText(mPassword + "\n" + mToken + "\n" + mUserName);
			}
		} else if (requestCode == PICK_REQUEST && data != null) {
			mUploadUri = data.getData();
			Log.v("MainActivity", "uri is " + mUploadUri);
		}
	}
	
	private void startLoginActivity() {
		Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, LOGIN_REQUEST);
	}
	
	private void changeToAlbumList() {
		Intent intent = new Intent(this, AlbumListActivity.class);
        startActivity(intent);
	}

	@Override
	public void onUploading(int progress) {
		Message.obtain(mHandler, MSG_SHOW_UPLOAD_PROGRESS, progress, 0).sendToTarget();
	}

	@Override
	public void onFinish(boolean success) {
		Message.obtain(mHandler, (success) ? MSG_SHOW_UPLOAD_SUCCESS : MSG_SHOW_UPLOAD_FAIL).sendToTarget();
	}

	@Override
	public void onWaitServerResponse() {
		Message.obtain(mHandler, MSG_WAIT_PICASA_SERVER_RESPONSE).sendToTarget();
	}
}
