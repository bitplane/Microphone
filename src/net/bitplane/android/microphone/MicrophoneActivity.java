package net.bitplane.android.microphone;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageButton;

public class MicrophoneActivity extends Activity implements OnSharedPreferenceChangeListener, OnClickListener {
	
	private static final String APP_TAG         = "Microphone";
	private static final int    ABOUT_DIALOG_ID = 0;
	
	SharedPreferences mSharedPreferences;
	boolean           mActive = false;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(APP_TAG, "Opening mic activity");
        
        // listen for preference changes
    	mSharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE);
    	mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    	
    	// listen for preference changes
    	mSharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE);
    	mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        
    	mActive = mSharedPreferences.getBoolean("active", false);
    	if (mActive)
    		startService(new Intent(this, MicrophoneService.class));
    	
    	setContentView(R.layout.main);
    	
    	ImageButton b = (ImageButton)findViewById(R.id.RecordButton);
    	b.setOnClickListener(this);
    	b.setImageBitmap(BitmapFactory.decodeResource(getResources(), mActive ? R.drawable.red : R.drawable.mic));
    	
        int lastVersion = mSharedPreferences.getInt("lastVersion", 0);
        int thisVersion = -1;
        try {
        	thisVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
        	e.printStackTrace();
        }
        
        if (lastVersion != thisVersion) {
        	SharedPreferences.Editor e = mSharedPreferences.edit();
        	e.putInt("lastVersion", thisVersion);
        	e.commit();
        	showDialog(ABOUT_DIALOG_ID);
        }
    	
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	
    	Log.d(APP_TAG, "Closing mic activity");
    	
    	mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.about:
        	showDialog(ABOUT_DIALOG_ID);
        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override 
    public Dialog onCreateDialog(int id) {
    	Dialog dialog = null;
    	switch (id) {
    	case ABOUT_DIALOG_ID:
    		Builder b = new AlertDialog.Builder(this);
    		b.setTitle(getString(R.string.about));
    		
    		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
    		View aboutView = inflater.inflate(R.layout.about, (ViewGroup)findViewById(R.id.AboutWebView));
    		
    		b.setView(aboutView);
    		
    		String data = "";
    		
    		InputStream in = getApplicationContext().getResources().openRawResource(R.raw.about);
    		try {
	    		int ch;
	    		StringBuffer buf = new StringBuffer();
	    		while( ( ch = in.read() ) != -1 ){
	    			buf.append( (char)ch );
	    		}
	    		data = buf.toString();
    		}
    		catch (IOException e) {
    			// this is fucking silly. do something nicer than this shit method
    		}
    		
    		WebView wv = (WebView)aboutView.findViewById(R.id.AboutWebView);
    		wv.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
    		
    		dialog = b.create();
    		
    		break;
    	}
    	return dialog;
    }
    
	public void onClick(View v) {
		if (v.getId() == R.id.RecordButton) {
			SharedPreferences.Editor e = mSharedPreferences.edit();
			e.putBoolean("active", !mActive);
			e.commit();
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// intercept the preference change.
		
		if (key.equals("active")) {
			boolean bActive = sharedPreferences.getBoolean("active", false);

			if (bActive != mActive) {
				if (bActive) {
					startService(new Intent(this, MicrophoneService.class));
				}
				else {
					stopService(new Intent(this, MicrophoneService.class));
				}
				mActive = bActive;
				runOnUiThread(	new Runnable() {
									public void run() {
										ImageButton b = (ImageButton)findViewById(R.id.RecordButton);
										b.setImageBitmap(BitmapFactory.decodeResource(getResources(), mActive ? R.drawable.red : R.drawable.mic));						
									}
								});
			}
		}
		
	}
    
}