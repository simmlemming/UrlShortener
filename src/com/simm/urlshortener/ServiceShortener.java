package com.simm.urlshortener;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.security.InvalidParameterException;
import java.util.Random;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class ServiceShortener extends Service implements Callback {
	private final String TAG = UrlShortener.TAG + "/ServiceShortener";
	private final LocalBinder mBinder = new LocalBinder();
	private Handler mLocalHandler; 
	
	public class LocalBinder extends Binder{
		public ServiceShortener getService(){
			return ServiceShortener.this;
		}
		
	}
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "Start retreiving short url.");

		final String longUrl = intent.getStringExtra(UrlShortener.EXTRA_LONG_URL);
		Thread t = new Thread(){
			@Override
			public void run() {
				//get url
//				try {
//					Thread.sleep(UrlShortener.DELAY);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
				Message msg = mLocalHandler.obtainMessage(UrlShortener.MSG_WHAT_URL_RECEIVED);
					String shortUrl;
					try {
						shortUrl = UrlShortener.getShortUrl(longUrl);
						//put url to persistent storage
						UrlShortener.shortUrl = shortUrl;

						//send short url to handler
						msg.arg1 = UrlShortener.MSG_RESULT_OK;
						msg.obj = shortUrl;
						
					} catch (SocketTimeoutException e) {
						e.printStackTrace();
						msg.arg1 = UrlShortener.MSG_RESULT_ERROR;
						msg.obj = "Connection timeout";
					} catch (InvalidParameterException e) {
						msg.arg1 = UrlShortener.MSG_RESULT_ERROR;
						msg.obj = "Bad request";
						e.printStackTrace();
					} catch (JSONException e) {
						msg.arg1 = UrlShortener.MSG_RESULT_ERROR;
						msg.obj = "Internal error while building JSON and Http request";
						e.printStackTrace();
					} catch (IOException e) {
						msg.arg1 = UrlShortener.MSG_RESULT_ERROR;
						msg.obj = "Error while executing query";
						e.printStackTrace();
					} catch (Exception e){
						msg.arg1 = UrlShortener.MSG_RESULT_ERROR;
						msg.obj = "Unknown error";
						e.printStackTrace();						
					}

				msg.sendToTarget();	
			}
		};
		t.start();
		return Service.START_NOT_STICKY;
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "Service created.");
		mLocalHandler = new Handler(this);
	}
	
	
	@Override
	public boolean handleMessage(Message message) {
		if (message.what == UrlShortener.MSG_WHAT_URL_RECEIVED){
			Log.i(TAG, "Message handled.");
			Intent i = new Intent(UrlShortener.ACTION_URL_RECEIVED);
			i.putExtra(UrlShortener.EXTRA_RESULT_CODE, message.arg1);
			i.putExtra(UrlShortener.EXTRA_SHORT_URL, (String)message.obj);
			sendBroadcast(i);
			stopSelf();
			return true;
		}
		return false;
	}


	@Override
	public void onDestroy() {
		Log.i(TAG, "Service destroyed.");
	}
}
