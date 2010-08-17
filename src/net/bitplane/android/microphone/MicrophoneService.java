package net.bitplane.android.microphone;

import java.nio.ByteBuffer;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class MicrophoneService extends Service implements OnSharedPreferenceChangeListener {
	
	private static final String APP_TAG = "Microphone";
	private static final int mSampleRate = 44100;
	private static final int mFormat     = AudioFormat.ENCODING_PCM_16BIT;
	
	private AudioTrack     mAudioOutput;
	private AudioRecord    mAudioInput;
	private int            mInBufferSize;
	private int            mOutBufferSize;
	SharedPreferences      mSharedPreferences;
	private static boolean mActive = false;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
    @Override
    public void onCreate() {
    	
    	Log.d(APP_TAG, "Creating mic service"); 
    	
    	// create input and output streams
        mInBufferSize  = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, mFormat);
        mOutBufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, mFormat);
        mAudioInput = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, mFormat, mInBufferSize);
        mAudioOutput = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, mFormat, mOutBufferSize, AudioTrack.MODE_STREAM);
    	
    	// listen for preference changes
    	mSharedPreferences = getSharedPreferences(APP_TAG, MODE_PRIVATE);
    	mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    	mActive = mSharedPreferences.getBoolean("active", false);
    	
    	if (mActive)
    		record();
    }
    
    @Override
    public void onDestroy() {
    	Log.d(APP_TAG, "Stopping mic service"); 
    	
    	// close the service
    	SharedPreferences.Editor e = mSharedPreferences.edit();
    	e.putBoolean("active", false);
    	e.commit();
    	
    	// disable the listener
    	mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    	
    	mAudioInput.release();
    	mAudioOutput.release();
    }
    
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// intercept the preference change.
		
		if (!key.equals("active"))
			return;
		
		boolean bActive = sharedPreferences.getBoolean("active", false);
		
		Log.d(APP_TAG, "Mic state changing (from " + mActive + " to " + bActive + ")"); 
		
		if (bActive != mActive) {
		
			mActive = bActive;
			
			if (mActive)
				record();
		}
	}
	
	public void record() {
		Thread t = new Thread() {
			public void run() {
				
				Log.d(APP_TAG, "Recording...");

				int d = mAudioOutput.getState();
				
				if ( mAudioOutput.getState() != AudioTrack.STATE_INITIALIZED || mAudioInput.getState() != AudioTrack.STATE_INITIALIZED) {
					Log.d(APP_TAG, "Can't start. Race condition?");
					return;
				}
				
				mAudioOutput.play();
				mAudioInput.startRecording();
			
		        ByteBuffer bytes = ByteBuffer.allocateDirect(mInBufferSize);
		        int o = 0;
			        
		        while(mActive) {
		        	o = mAudioInput.read(bytes, mInBufferSize);
		        	byte b[] = new byte[o];
		        	bytes.get(b);
		        	bytes.rewind();
		        	mAudioOutput.write(b, 0, o);
		        }
		        
		        Log.d(APP_TAG, "Finished recording");
		        
		        mAudioOutput.stop();
		        mAudioInput.stop();			
			}
		};
		
		t.start();
		
	}
}
