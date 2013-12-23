package com.renteng.tourrecord.camera;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AutoFocusCallback implements Camera.AutoFocusCallback {
	
	private static final String TAG = AutoFocusCallback.class.getSimpleName();
	
	private static final long AUTOFOCUS_INTERVAL_MS = 1500L;
	
	private Handler autoFocusHandler;
	private int autoFocusMessage;
	
	void setHandler(Handler autoFocusHandler, int autoFocusMessage){
		this.autoFocusHandler = autoFocusHandler;
		this.autoFocusMessage = autoFocusMessage;
	}
	
	public void onAutoFocus(boolean sucess, Camera camera){
		if(autoFocusHandler != null){
			Message msg = autoFocusHandler.obtainMessage(autoFocusMessage, sucess);
			autoFocusHandler.sendMessageDelayed(msg, AUTOFOCUS_INTERVAL_MS);
		}else{
			Log.d(TAG, "Got auto-focus callback, but no handler for it");
		}
	}

}
