package com.front.camera;

import com.front.camera.utils.WriteToFile;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

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
		if (v == btnTest) {
			wrf.takePicture();
		}
	}
}
