package com.simm.urlshortener.activity;

import java.io.IOException;
import java.security.spec.MGF1ParameterSpec;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.simm.urlshortener.R;
import com.simm.urlshortener.ServiceShortener;
import com.simm.urlshortener.UrlShortener;

public class Main extends Activity implements Callback{
	private final String TAG = UrlShortener.TAG + "/ActivityMain";
	private Context mContext;
	private int mState = UrlShortener.STATE_NOT_PENDING;
    private Button mBtnShort;
    private BroadcastReceiver mLocalReceiver = new LocalReceiver();
    private EditText mTextLong, mTextShort;
    private final Handler mHandler = new Handler(this);
    
    private class LocalReceiver extends BroadcastReceiver{
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "onReceive()");
			setUiPending(false);
			mState = UrlShortener.STATE_NOT_PENDING;
			int resultCode = intent.getIntExtra(UrlShortener.EXTRA_RESULT_CODE, -1);
			switch (resultCode) {
			case UrlShortener.MSG_RESULT_OK:
				shortUrlreceived(intent.getStringExtra(UrlShortener.EXTRA_SHORT_URL));
				break;
			case UrlShortener.MSG_RESULT_ERROR:
				handleError(intent.getStringExtra(UrlShortener.EXTRA_SHORT_URL));
				break;
			default:
				Log.w(TAG, "Unknown result code.");
				break;
			}
			
		}
    }
    
	private class LocalServiceConnection implements ServiceConnection{

		@Override
		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			Log.i(TAG, "Service connected.");
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			Log.i(TAG, "Service disconnected.");
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
        setContentView(R.layout.main);
        registerReceiver(mLocalReceiver, new IntentFilter(UrlShortener.ACTION_URL_RECEIVED));

		mContext = this;
        mTextLong = (EditText) findViewById(R.id.txtLong);
        mTextShort = (EditText) findViewById(R.id.txtShort);
        mBtnShort = (Button) findViewById(R.id.btnShort);

        mBtnShort.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				setUiPending(true);
				Intent i = new Intent(mContext, ServiceShortener.class);
				i.putExtra(UrlShortener.EXTRA_LONG_URL, mTextLong.getEditableText().toString());
				mState = UrlShortener.STATE_PENDING;
				startService(i);
			}
		});
        
        if (savedInstanceState != null)
        	mState = savedInstanceState.getInt(UrlShortener.EXTRA_STATE, -1);

        if (mState == UrlShortener.STATE_PENDING){ //short url is not received by Activity
    		Intent i = new Intent(mContext, ServiceShortener.class);
    	    ServiceConnection sc = new LocalServiceConnection();
    	    
    	    //Try to bind to service. If success (bindSevice will return true) - just
    	    //keep waiting for a broadcast message, if not -
    	    //get short url from persistent storege
    		if (!bindService(i, sc, 0)){
    			Log.i(TAG, "Service ended while activity was dead.");
    			//get url from persistent storage
    			setUiPending(false);
    			mState = UrlShortener.STATE_NOT_PENDING;
    			shortUrlreceived(UrlShortener.shortUrl);
    		}
    		unbindService(sc);
    	}
        
        //Test AccountManager
        Log.i(TAG, "===== Test AccountManager begins =====");
        final AccountManager am = AccountManager.get(mContext);
        Account[] accounts;
        accounts = am.getAccountsByType(null);
        Log.i(TAG, "Found " + accounts.length + " accounts.");
        if (accounts.length == 0) {return;}
        for (int s = 0; s < accounts.length; s++){
        	Log.i(TAG, s + ": " + accounts[s].toString());
        }
        final Account googleAccount = am.getAccountsByType("com.google")[0];
        
        Thread t = new Thread() {
			@Override
			public void run() {
				Log.i(TAG, "Thread started.");
		        AccountManagerFuture<Bundle> future = am.getAuthToken(googleAccount, "ah", true, null, null);
		        Message msg = mHandler.obtainMessage();
		        try {
					msg.setData(future.getResult());
					msg.sendToTarget();
				} catch (Exception e) {
					Log.i(TAG, "Error while getting auth token.");
					e.printStackTrace();
				}
		        
			}
        };
        //t.start();
        
        //UrlShortener.test();
        //finish();
    }
    
	@Override
	public boolean handleMessage(Message msg) {
		Log.i(TAG, "Message handled: " + msg.toString());
        Bundle result = msg.getData();
        if (result == null){ 
        	Log.i(TAG, "Result is NULL, exiting.");
        	return true;
        }
        Log.i(TAG, "Result`s keys: " + result.keySet().toString());
        
        if (result.containsKey(AccountManager.KEY_INTENT)){
            Intent i = result.getParcelable(AccountManager.KEY_INTENT);
            Log.i(TAG, "Starting built-in activity.");
            startActivityForResult(i, 100);	
        } else {
            Log.i(TAG, "No need to start built-in activity.");
            for (String key : result.keySet()){
            	Log.i(TAG, key +" - " + result.get(key));
            }
            
        }
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "Built-in activity finished with result code " + resultCode + ".");
        Log.i(TAG, "Keys in data from built-in acvtivity: " + data.getExtras().keySet());
	}
	
    
    private void shortUrlreceived(String url){
		mTextShort.setText(url);
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(UrlShortener.EXTRA_LONG_URL, mTextLong.getEditableText().toString());
		outState.putString(UrlShortener.EXTRA_SHORT_URL, mTextShort.getEditableText().toString());
		outState.putInt(UrlShortener.EXTRA_STATE, mState);
	}
	
	private void restoreState(Bundle savedInstanceState){
		mState = savedInstanceState.getInt(UrlShortener.EXTRA_STATE, -1);
		mTextLong.setText(savedInstanceState.getString(UrlShortener.EXTRA_LONG_URL));
		mTextShort.setText(savedInstanceState.getString(UrlShortener.EXTRA_SHORT_URL));
		Log.i(TAG, "Activity State restored.");
	}


	private void setUiPending(boolean pending){
		if (pending){
			mBtnShort.setEnabled(false);
		} else {
			mBtnShort.setEnabled(true);
		}
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy()");
		unregisterReceiver(mLocalReceiver);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		restoreState(savedInstanceState);
		if (mState == UrlShortener.STATE_PENDING){ //short url is not received by Activity
			setUiPending(true);
		}
	}
	
	private void handleError(String message){
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

}