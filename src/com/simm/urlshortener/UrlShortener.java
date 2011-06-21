package com.simm.urlshortener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.security.InvalidParameterException;
import java.util.Random;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.util.Log;

public class UrlShortener {
	public static final String EXTRA_LONG_URL = "long_url",
							EXTRA_SHORT_URL = "short_url",
							EXTRA_STATE = "state",
							EXTRA_RESULT_CODE = "result_code",
							TAG = "com.simm.urlshortener",
							ACTION_URL_RECEIVED = "com.simm.urlshortener.URL_RECEIVED",
							URL_TO_GET_SHORT_URL = "https://www.googleapis.com/urlshortener/v1/url",
							JSON_KEY_SHORT_URL = "id",
							JSON_KEY_LONG_URL = "longUrl",
							PREFS_NAME="com.simm.urlshortener.prefs",
							PREFS_KEY_TOKEN = "t",
							PREFS_KEY_ACC_NAME = "an",
							PREFS_KEY_AUTH_STATUS = "as",
							AUTH_TOKEN_TYPE = "ah"
	;
	
	public static final int STATE_NOT_PENDING = 0,
							STATE_PENDING = 1,
							MSG_WHAT_URL_RECEIVED = 3,
							DELAY = 500,
							MSG_RESULT_OK = 4,
							MSG_RESULT_ERROR = 5,
							CONNETCION_TIMEOUT_MSEC = 5000, //wait for connection established
							SOCKET_TIMEOUT_MSEC = 5000, //wait for data
							AUTH_SIGNED_IN = 6,
							AUTH_NOT_SIGNED_IN = 7,
							AUTH_ERROR_WHILE_LAST_ATTEMPT = 8,
							AUTH_SIGNING_IN = 12,
							MSG_WHAT_TOKEN_RECEIVED = 9,
							ACTIVITY_BUILT_IN_AUTH = 10,
							DIALOG_PICK_ACCOUNT = 11
	;
	/** Auth token for test account **/
	public static String token = "OAuth 1/6jgEVfoROPXG-0nhpR8FIdhtEOvQm__HojlYO2gGs9o";

	public static String longUrl, shortUrl;
	
	public static void test(){
		Log.i(TAG, "=== Test authorized request begins ===");
		HttpGet request = new HttpGet("https://www.googleapis.com/urlshortener/v1/url/history");
		//request.addHeader("Authorization", token);
		 //request.addHeader("Authorization", "GoogleLogin auth=" + token);

		//Log.i(TAG, "Request: " + request.getFirstHeader("Authorization").toString());
		HttpClient client = new DefaultHttpClient();
		org.apache.http.HttpResponse responce = null;
		try {
			responce = client.execute(request);
			Log.i(TAG, responce.getStatusLine().toString());
			String jsonString = EntityUtils.toString(responce.getEntity());
			Log.i(TAG, "Authorized responce:");
			Log.i(TAG, jsonString);
		} catch (Exception e) {
			Log.i(TAG, "Error while getting history from Google.");
			e.printStackTrace();
		}

	}

	
	
	public static String getShortUrl(String longUrl) throws JSONException,
															InvalidParameterException, IOException {
		HttpPost request = new HttpPost(URL_TO_GET_SHORT_URL);
		JSONStringer stringer = new JSONStringer();
		String jsonAsString;
		
		//Construct JSON object & Http request
		try { 
			jsonAsString = stringer.object()
					.key(JSON_KEY_LONG_URL)
					.value(longUrl)
					.endObject()
					.toString();
			request.setEntity(new StringEntity(jsonAsString));
			//request.addHeader("Authorization", token);
			Log.i(TAG, "Request-JSON as string: " + jsonAsString);
		} catch (Exception e) {
			JSONException ee = new JSONException("");
			ee.initCause(e);
			throw ee; 
		}
		
		request.addHeader("Content-Type", "application/json");
		HttpParams timeouts = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(timeouts, CONNETCION_TIMEOUT_MSEC);
		HttpConnectionParams.setSoTimeout(timeouts, SOCKET_TIMEOUT_MSEC);
		HttpClient client = new DefaultHttpClient(timeouts);
		
		//Execute request
		HttpResponse responce = null;;
		try {
			responce = client.execute(request);
		} catch (Exception e) {
			IOException ee = new IOException("");
			ee.initCause(e);
			throw ee; 
		}
		
		//Parse response
		if (responce != null && responce.getStatusLine().getStatusCode() != 200){
			String responceJsonString = EntityUtils.toString(responce.getEntity());
			Log.i(TAG, responceJsonString);
			throw new InvalidParameterException();
		}
		
		String shortUrl = null;
		Log.i(TAG, "Responce:");
		Log.i(TAG, responce.getStatusLine().toString());
		try {
			String responceJsonString = EntityUtils.toString(responce.getEntity());
			
			Log.i(TAG, responceJsonString);
			JSONObject jobject = new JSONObject(responceJsonString);
			shortUrl = jobject.getString(JSON_KEY_SHORT_URL);
		} catch (Exception e) {
			InvalidParameterException ee = new InvalidParameterException("");
			ee.initCause(e);
			throw ee; 
		}
		return shortUrl;
	}
	
    public static String convertStreamToString_(InputStream is)    throws IOException {
	/*
	 * To convert the InputStream to String we use the
	 * Reader.read(char[] buffer) method. We iterate until the
	 * Reader return -1 which means there's no more data to
	 * read. We use the StringWriter class to produce the string.
	 */
	if (is != null) {
	    Writer writer = new StringWriter();
	
	    char[] buffer = new char[1024];
	    try {
	        Reader reader = new BufferedReader(
	                new InputStreamReader(is, "UTF-8"));
	        int n;
	        while ((n = reader.read(buffer)) != -1) {
	            writer.write(buffer, 0, n);
	        }
	    } finally {
	        is.close();
	    }
	    return writer.toString();
	} else {        
	    return "";
	}
    }
    

	
}
