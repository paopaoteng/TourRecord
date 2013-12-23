package com.renteng.tourrecord;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.renteng.tourrecord.camera.CameraManager;

public final class CaptureTrainTicket extends Activity implements
		SurfaceHolder.Callback {

	private boolean hasSurface;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_capture_train_ticket);
		CameraManager.init(getApplication());
		hasSurface = false;
		// TODO Auto-generated method stub
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview);
		SurfaceHolder holder = surfaceView.getHolder();

		if (hasSurface) {
			initCamera(holder);
		} else {
			holder.addCallback(this);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		CameraManager.get().closeDriver();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		CameraManager.get().closeDriver();
	}

	private void initCamera(SurfaceHolder holder) {
		try {
			CameraManager.get().openDriver(holder);
		} catch (IOException ioe) {
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		hasSurface = false;
	}

}
