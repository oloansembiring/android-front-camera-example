package com.front.camera;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.front.camera.utils.Ngelog;
import com.front.camera.utils.WriteToFile;

public class TestActivity extends Activity implements OnClickListener {

	private Button btnTest;
	private Context mContext;
	private String mFileDirectory;
	private WriteToFile wrf;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_layout);
		mContext = TestActivity.this;
		btnTest = (Button) findViewById(R.id.btn_test);
		btnTest.setOnClickListener(this);

		wrf = new WriteToFile(mContext);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		wrf.startBackgroundThread();
		wrf.ensureDirectoriIsExist();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		wrf.stopBackgroundThread();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		Ngelog.v("Start: " + System.currentTimeMillis());
		if (v == btnTest) {
			wrf.takePicture();
		}
	}

}
