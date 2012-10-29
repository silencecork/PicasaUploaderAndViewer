package com.example.picasaloginanduploading;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.text.TextUtils;

/**
 * Login flow is: 
 * 1. Create another thread caller worker thread in createWorker() which called in onCreate(), this is responsible for network login process.
 * 2. User input his account and password.
 * 3. Get the username and password and send to worker with message MSG_LOGIN and do the process in worker thread.
 * 4. after login, it will send message MSG_LOGIN_SUCCESS or MSG_LOGIN_ERROR to main thread handler 
 * 5. If message is MSG_SUCCESS, LoginActivity will send back username, password and token back to original activity with setResult(RESULT_OK, intent);
 * 
 * NOTICE! If you want to know much deeper about login flow in Picasa Server, you can check function doClientLogin()
 * 
 * @author Justin Liu
 *
 */
public class LoginActivity extends Activity {
	private static final String TAG = "LoginActivity";
	
	private static final int MSG_LOGIN = 0;
	private static final int MSG_LOGIN_ERROR = 1;
	private static final int MSG_LOGIN_SUCCESS = 2;
	
	private static final int DLG_WAIT = 3;
	
	private EditText mUserName;
	private EditText mPassword;
	private Button mLoginButton;
	
	protected HandlerThread mWorker;
	protected Handler mWorkerHandler;
	
	private ProgressDialog mProgress;

	private Handler mHandler = new Handler() {

		@SuppressWarnings("deprecation")
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_LOGIN_ERROR) {
				Toast.makeText(LoginActivity.this, getString(R.string.error_service_not_available), Toast.LENGTH_LONG).show();
				dismissDialog(DLG_WAIT);
			} else if (msg.what == MSG_LOGIN_SUCCESS) {
				if (msg.obj != null) {
					String[] loginInfo = (String[]) msg.obj;
					String token = loginInfo[0];
					String username = loginInfo[1];
					String password = loginInfo[2];
					Log.d(TAG, "token " + token + ", user name " + username + ", password " + password);
					Intent intent = new Intent();
					intent.putExtra("token", token);
					intent.putExtra("username", username);
					intent.putExtra("password", password);
					setResult(RESULT_OK, intent);
					finish();
				}
				dismissDialog(DLG_WAIT);
			}
		}
		
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        createWorker();
        
        setContentView(R.layout.activity_login);
        
        mUserName = (EditText) findViewById(R.id.input_username);
        mPassword = (EditText) findViewById(R.id.input_password);
        mLoginButton = (Button) findViewById(R.id.btn_ok);
        
        if (mLoginButton != null) {
        	mLoginButton.setOnClickListener(new View.OnClickListener() {
				
				@SuppressWarnings("deprecation")
				@Override
				public void onClick(View v) {
					String username = (mUserName != null) ? mUserName.getText().toString() : null;
					String password = (mPassword != null) ? mPassword.getText().toString() : null;
					
					if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
						showDialog(DLG_WAIT);
						mWorkerHandler.sendEmptyMessage(MSG_LOGIN);
					}
				}
			});
        }
        
    }
    
    protected void onDestroy() {
    	super.onDestroy();
    	
    	if (mWorker != null) {
    		mWorker.quit();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_login, menu);
        return true;
    }
    
    @Override
	@Deprecated
	protected Dialog onCreateDialog(int id) {
    	if (id == DLG_WAIT) {
    		mProgress = new ProgressDialog(this);
    		mProgress.setMessage(getString(R.string.msg_load_wait));
    		return mProgress;
    	}
		return super.onCreateDialog(id);
	}

	private void createWorker() {
		if (mWorker == null || !mWorker.isAlive()) {
			mWorker = new HandlerThread("worker");
			mWorker.start();
			mWorkerHandler = new Handler(mWorker.getLooper()) {
	
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == MSG_LOGIN) {
						String username = (mUserName != null) ? mUserName.getText().toString() : null;
						String password = (mPassword != null) ? mPassword.getText().toString() : null;
						Log.i(TAG, "login username " + username + ", password " + password);
						if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
							doClientLogin(username, password);
						}
					}
				}
				
			};
		} 
	}
	
	
	/* Following functions and variables are about login process of Picasa */
	private static final String PARAM_EMAIL = "Email";
	private static final String PARAM_PASSWD = "Passwd";
	private static final String PARAM_SOURCE = "source";
	private static final String PARAM_SERVICE = "service";
	private static final String PICASA_SERVICE = "lh2";
	private static final int ERR_PARSE_ERROR = 1001; 
	private static final int ERR_NO_RESPONSE_CONTENT = 1002;
	private static final int ERR_RESPONSE_CODE_INVALID = 1003;
	private static final int ERR_INVALID_USERNAME_PASSWORD = 1004;
	private static final int ERR_NO_TOKEN = 1005;
	private static final String AUTH_BASE_URL = "https://www.google.com/accounts/ClientLogin";
	private DefaultHttpClient mHttpClient;
    
    private void doClientLogin(String username, String password) {
    	if (username.contains("@gmail.") || username.contains("@googlemail.")) {
			username = username.substring(0, username.indexOf('@'));
		}
		Log.d(TAG, "login username " + username + ", password " + password);
		ArrayList <NameValuePair> nvps = new ArrayList <NameValuePair>();
		nvps.add(new BasicNameValuePair(PARAM_EMAIL, username));
		nvps.add(new BasicNameValuePair(PARAM_PASSWD, password));
		nvps.add(new BasicNameValuePair(PARAM_SERVICE, PICASA_SERVICE));
		nvps.add(new BasicNameValuePair(PARAM_SOURCE, "Migration"));
		HttpEntity body = null;
		try {
			body = new UrlEncodedFormEntity(nvps, HTTP.DEFAULT_CONTENT_CHARSET);
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "create auth entity fail", e);
		}
		
		HttpResponse response = null;
		try {
			response = httpPost(AUTH_BASE_URL, null, body);
		} catch (IOException e) {
			
		}
		if (response == null) {
			Log.e(TAG, "there is no response in auth");
			Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_NO_RESPONSE_CONTENT, 0).sendToTarget();
			return;
		}
		
		int status = response.getStatusLine().getStatusCode();
		
		if (status != HttpStatus.SC_OK) {
			Log.e(TAG, "response status code is " + status);
			if (status == HttpStatus.SC_FORBIDDEN) {
				Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_INVALID_USERNAME_PASSWORD, 0).sendToTarget();
			} else {
				Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_RESPONSE_CODE_INVALID, 0).sendToTarget();
			}
			return;
		}
		
		InputStream in = null;
		try {
			in = response.getEntity().getContent();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (in == null) {
			Log.e(TAG, "there is no input stream in auth process");
			Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_NO_RESPONSE_CONTENT, 0).sendToTarget();
			return;
		}
		
		String token = getAuth(in);
		if (token == null) {
			Log.e(TAG, "Can't get token in response stream");
			Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_PARSE_ERROR, 0).sendToTarget();
			return;
		}
		
		if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
			Message.obtain(mHandler, MSG_LOGIN_SUCCESS, new String[] {token, username, password}).sendToTarget();
		} else {
			Message.obtain(mHandler, MSG_LOGIN_ERROR, ERR_NO_TOKEN, 0).sendToTarget();
		}
    }
    
    private String getAuth(InputStream is) {
		if (is == null)
			return null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line = null;
		String strAuth = null;
		try {
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("Auth=")) {
					Log.i(TAG, line);
					strAuth = line.substring(5);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return strAuth;
	}
    
    public HttpResponse httpPost(String url, Header[] headers, HttpEntity body) throws IOException {
		Log.i(TAG, "POST " + url);
		HttpPost post = new HttpPost(url);
		handleHeader(post, headers);
		if (body != null) {
			post.setEntity(body);
		}
		return callMethod(post);
	}
    
    private void handleHeader(HttpUriRequest request, Header[] headers) {
		if (headers != null && headers.length > 0) {
	 		for (Header header : headers) {
	 			request.addHeader(header);
			}
		}
	}
    
    private void initHttpClient() {
    	if (mHttpClient != null) return;
    	
    	HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setStaleCheckingEnabled(params, false);
		HttpConnectionParams.setConnectionTimeout(params, 20000);
		HttpConnectionParams.setSoTimeout(params, 20000);
		HttpClientParams.setRedirecting(params, true);
		HttpProtocolParams.setUserAgent(params, "Migration GData-Java/2.0; gzip");
    	
    	SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		
		final ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, schemeRegistry);
		mHttpClient = new DefaultHttpClient(manager, params);
    }
    
	private synchronized HttpResponse callMethod(HttpUriRequest request)
			throws IOException {
		initHttpClient();
		HttpResponse httpResponse = null;
		try {
			Log.v(TAG, "HttpClient execute");
			httpResponse = mHttpClient.execute(request);
		} catch (IOException e) {
			Log.w(TAG, "Request failed: " + request.getURI());
			throw e;
		}

		if (httpResponse == null) {
			throw new IOException("something wrong when internet access");
		}

		return httpResponse;
	}
}
