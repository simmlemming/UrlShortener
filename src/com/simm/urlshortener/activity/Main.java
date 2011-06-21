package com.simm.urlshortener.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.simm.urlshortener.R;
import com.simm.urlshortener.ServiceShortener;
import com.simm.urlshortener.UrlShortener;

public class Main extends Activity implements Callback{
	private final String TAG = UrlShortener.TAG + "/ActivityMain";
	private Context mContext;
	private int mState = UrlShortener.STATE_NOT_PENDING;
    private ImageButton mBtnShort, mBtnShare;
    private BroadcastReceiver mLocalReceiver = new LocalReceiver();
    private EditText mTextLong, mTextShort;
    private TextView mTxtAuth, mTxtAuthError;
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
				expandUI();
				break;
			case UrlShortener.MSG_RESULT_ERROR:
				handleError(intent.getStringExtra(UrlShortener.EXTRA_SHORT_URL));
				collapseUI();
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
	
	private class LocalOnClickListener implements OnClickListener{
		public void onClick(View v) {
			int status = getAuthStatus();
			if (status == UrlShortener.AUTH_SIGNED_IN)
				signOut();
			else 
				signIn();
		}
	}
	
	private void signIn(){
		setAuthStatus(UrlShortener.AUTH_SIGNING_IN);
		//Get google accounts
        final AccountManager am = AccountManager.get(mContext);
        Account[] accounts;
        accounts = am.getAccountsByType("com.google");
        Log.i(TAG, "Found " + accounts.length + " Google accounts.");
        if (accounts.length == 0) {
        	handleError("You have no Google accounts, this is not handled yet. Sorry.");
        	return;
        	}
        for (int s = 0; s < accounts.length; s++){
        	Log.i(TAG, s + ": " + accounts[s].toString());
        }
        
        if (accounts.length > 1){
        	showDialog(UrlShortener.DIALOG_PICK_ACCOUNT);
        } else {
        	signIn(accounts[0]);
        }
	}
	
	private void singIn(String accName){
    	setAuthAccName(accName);
    	signIn(getAccountByName(accName));
	}
	
	private void signIn(Account account){
    	getTokenAndPutToPrefs(account);
	}
	
	private void getTokenAndPutToPrefs(final Account account) {
        Thread t = new Thread() {
        	
			@Override
			public void run() {
				Log.i(TAG, "Thread for getting auth token started.");
				AccountManager am = AccountManager.get(mContext);
		        AccountManagerFuture<Bundle> future = am.getAuthToken(account, UrlShortener.AUTH_TOKEN_TYPE, true, null, null);
		        Message msg = mHandler.obtainMessage();
		        msg.setData(null); //Just for sure, maybe not needed 
		        try {
					msg.setData(future.getResult());
				} catch (Exception e) {
					//Do not setting data to message - this indicates an error
					e.printStackTrace();
				} finally {
					msg.what = UrlShortener.MSG_WHAT_TOKEN_RECEIVED;
					msg.sendToTarget();
				}
			}

			@Override
			public void destroy() {
				super.destroy();
				Log.i(TAG, "Thread for getting auth token destroyed.");
			}
        };
        
        t.start();
	}

	private void signOut(){
		setAuthStatus(UrlShortener.AUTH_NOT_SIGNED_IN);
		setAuthTokenToPrefs(null);
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
        mBtnShort = (ImageButton) findViewById(R.id.btnShort);
        mBtnShare = (ImageButton) findViewById(R.id.btnShare);
        mTxtAuth = (TextView) findViewById(R.id.txtAuth);
        mTxtAuthError = (TextView) findViewById(R.id.txtAuthError);
        mTxtAuth.setOnClickListener(new LocalOnClickListener());
        
        mBtnShort.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				collapseUI();
				getShortUrl();
			}
		});
        
        mBtnShare.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent i = new Intent(Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_TEXT, mTextShort.getText().toString());
				startActivity(Intent.createChooser(i, "Share via"));
			}
		});

        
    	String shortUrl = savedInstanceState == null ? "" : savedInstanceState.getString(UrlShortener.EXTRA_SHORT_URL); 
    	if (shortUrl != null && shortUrl != ""){
    		expandUI();
    		} else {
    		collapseUI();
    		}
        
        if (savedInstanceState != null){
        	mState = savedInstanceState.getInt(UrlShortener.EXTRA_STATE, -1);
        }

        if (mState == UrlShortener.STATE_PENDING){ //short url is not received by Activity
    		Intent i = new Intent(mContext, ServiceShortener.class);
    	    ServiceConnection sc = new LocalServiceConnection();
    	    
    	    //Try to bind to service. If success (bindSevice will return true) - just
    	    //keep waiting for a broadcast message, if not -
    	    //get short url from persistent storage
    		if (!bindService(i, sc, 0)){
    			Log.i(TAG, "Service ended while activity was dead.");
    			//get url from persistent storage
    			setUiPending(false);
    			mState = UrlShortener.STATE_NOT_PENDING;
    			shortUrlreceived(UrlShortener.shortUrl);
    		}
    		unbindService(sc);
    	}
        
        //If Intent != null than Activity started from ActivityChooser
        //and there is a link to short in extras
        Intent i = getIntent();
        if (i != null && i.getExtras() != null){
        	Log.i(TAG, "Stareted via Intent, MIME=" + i.getType());
        	Log.i(TAG, "Stareted via Intent, categories=" + i.getCategories());
        	Log.i(TAG, i.getExtras().keySet() + "");
        	mTextLong.setText(i.getExtras().getString(Intent.EXTRA_TEXT));
        	//This prevents re-receiving of short URL after screen rotation
        	if (savedInstanceState == null)
        		getShortUrl();
        }
        
    }
    
    private void collapseUI(){
		mTextShort.setVisibility(View.GONE);
		mBtnShare.setVisibility(View.GONE);

    }

    private void expandUI(){
		mTextShort.setVisibility(View.VISIBLE);
		mBtnShare.setVisibility(View.VISIBLE);
    }

	private void getShortUrl() {
		setUiPending(true);
		Intent i = new Intent(mContext, ServiceShortener.class);
		i.putExtra(UrlShortener.EXTRA_LONG_URL, mTextLong.getEditableText().toString());
		mState = UrlShortener.STATE_PENDING;
		startService(i);
	}

	@Override
	public boolean handleMessage(Message msg) {
		Log.i(TAG, "Message handled: " + msg.toString());

		switch (msg.what) {

		case UrlShortener.MSG_WHAT_TOKEN_RECEIVED:
	        Bundle result = msg.getData();
	        if (result == null){ 
	        	Log.i(TAG, "Result is NULL, exiting.");
	        	setAuthStatus(UrlShortener.AUTH_ERROR_WHILE_LAST_ATTEMPT);
	        	handleError("Error while auth");
	        	return true;
	        }
	        
	        Log.i(TAG, "Result`s keys: " + result.keySet().toString());
	        
	        if (result.containsKey(AccountManager.KEY_INTENT)){
	            Intent i = result.getParcelable(AccountManager.KEY_INTENT);
	            Log.i(TAG, "Starting built-in activity.");
	            startActivityForResult(i, UrlShortener.ACTIVITY_BUILT_IN_AUTH);	
	        } else if (result.containsKey(AccountManager.KEY_AUTHTOKEN)){
	            Log.i(TAG, "Token is here.");
	            for (String key : result.keySet()){
	            	Log.i(TAG, key +" - " + result.get(key));
	            }
	            //TODO Bad idea to call every time Prefs.edit() and Editor.commit()
	    		setAuthStatus(UrlShortener.AUTH_SIGNED_IN);
	    		setAuthTokenToPrefs(result.getString(AccountManager.KEY_AUTHTOKEN));
	    		setAuthAccName(result.getString(AccountManager.KEY_ACCOUNT_NAME));
	    		
	        } else {
	        	handleError("Strange result from AccountManager.getAuthToken() - neither Activity to start nor token.");
	    		setAuthStatus(UrlShortener.AUTH_ERROR_WHILE_LAST_ATTEMPT);
	        }
			return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "Built-in activity finished with result code " + resultCode + ".");
        if (data != null && data.getExtras() != null) 
        	Log.i(TAG, "Keys in data from built-in acvtivity: " + data.getExtras().keySet());
        switch (requestCode) {
		case UrlShortener.ACTIVITY_BUILT_IN_AUTH:
			if (resultCode == Activity.RESULT_CANCELED){
				Log.i(TAG, "User has canselled auth in built-in activity, result code: " + resultCode);				
			} else 	if (resultCode == Activity.RESULT_OK) {
				Log.i(TAG, "User has granted auth in built-in activity, result code: " + resultCode);				
				getTokenAndPutToPrefs(getAccountByName(getAuthAccName()));
			} else {
				Log.i(TAG, "Buitl-in auth activity returned with non OK code: " + resultCode);
				handleError("Buitl-in auth activity returned with non OK code: " + resultCode);
				setAuthStatus(UrlShortener.AUTH_ERROR_WHILE_LAST_ATTEMPT);
				return;
			}
			break;
		}
	}
	
	/**
	 * Gets account by it`s name. Not the best implementation.
	 * @param name - Account`s name
	 * @return Found account in AccountManager or null.
	 */
	//TODO Change implementation of getting Account by name
	private Account getAccountByName(String name){
		AccountManager am = AccountManager.get(mContext);
		Account[] accounts = am.getAccountsByType(UrlShortener.AUTH_TOKEN_TYPE);
		Account account = null;
		for (Account a : accounts){
			if (a.name.equals(name))
				account = a;
		}
		return account;
	}
	
	//TODO Put part of dialog creation process to prepareDialog() 
	  @Override
	  protected Dialog onCreateDialog(int id) {
	    switch (id) {
	      case UrlShortener.DIALOG_PICK_ACCOUNT:
	        AlertDialog.Builder builder = new AlertDialog.Builder(this);
	        builder.setTitle("Select a Google account");
	        final AccountManager manager = AccountManager.get(this);
	        final Account[] accounts = manager.getAccountsByType("com.google");
	        final int size = accounts.length;
	        String[] names = new String[size];
	        for (int i = 0; i < size; i++) {
	          names[i] = accounts[i].name;
	        }
	        builder.setItems(names, new DialogInterface.OnClickListener() {
	          public void onClick(DialogInterface dialog, int which) {
	            signIn(accounts[which]);
	          }
	        });
	        return builder.create();
	    }
	    return null;
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
//		restoreState(savedInstanceState);
		mState = savedInstanceState.getInt(UrlShortener.EXTRA_STATE, -1);
		mTextLong.setText(savedInstanceState.getString(UrlShortener.EXTRA_LONG_URL));
		mTextShort.setText(savedInstanceState.getString(UrlShortener.EXTRA_SHORT_URL));
		setAuthStatus(getAuthStatus()); //Labels will be updated
		if (mState == UrlShortener.STATE_PENDING){ //short url is not received by Activity
			setUiPending(true);
		}
		Log.i(TAG, "Activity State restored.");
	}
	
	private void handleError(String message){
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
	
	private void setAuthStatus(int status){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		SharedPreferences.Editor e = prefs.edit();
		e.putInt(UrlShortener.PREFS_KEY_AUTH_STATUS, status);
		e.commit();
		
		//Update label
		switch (status) {
		case UrlShortener.AUTH_SIGNING_IN:
			mTxtAuth.setText(R.string.txt_auth_signing_in);
			mTxtAuthError.setVisibility(View.GONE);
			break;
		case UrlShortener.AUTH_SIGNED_IN:
			String signOut = getAuthAccName() + " (" + getString(R.string.txt_auth_sign_out) + ")";
			SpannableString content = new SpannableString(signOut);
			content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
			mTxtAuth.setText(content);
			mTxtAuthError.setVisibility(View.GONE);
			break;
		case UrlShortener.AUTH_NOT_SIGNED_IN:
			mTxtAuth.setText(R.string.txt_auth_sign_in);
			mTxtAuthError.setVisibility(View.GONE);
			break;
		case UrlShortener.AUTH_ERROR_WHILE_LAST_ATTEMPT:
			mTxtAuth.setText(R.string.txt_auth_sign_in);
			mTxtAuthError.setVisibility(View.VISIBLE);
			break;
		}
	}
	
	private int getAuthStatus(){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		return prefs.getInt(UrlShortener.PREFS_KEY_AUTH_STATUS, -1);
	}

	private void setAuthAccName(String accName){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		SharedPreferences.Editor e = prefs.edit();
		e.putString(UrlShortener.PREFS_KEY_ACC_NAME, accName);
		e.commit();
	}
	
	private String getAuthAccName(){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		return prefs.getString(UrlShortener.PREFS_KEY_ACC_NAME, null);
	}

	private void setAuthTokenToPrefs(String token){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		SharedPreferences.Editor e = prefs.edit();
		if (token == null)
			e.remove(UrlShortener.PREFS_KEY_TOKEN);
		else
			e.putString(UrlShortener.PREFS_KEY_TOKEN, token);
		e.commit();
	}
	
	private String getAuthTokenFromPrefs(){
		SharedPreferences prefs = getSharedPreferences(UrlShortener.PREFS_NAME, 0);
		return prefs.getString(UrlShortener.PREFS_KEY_TOKEN, null);
	}

	
}