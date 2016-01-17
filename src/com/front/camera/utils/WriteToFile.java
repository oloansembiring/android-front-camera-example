package com.front.camera.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.Toast;

public class WriteToFile {

	private boolean onProccesing=false;
	private Context mContext;
	private WindowManager mWindowManager;
	private final String CAPTURED_DIR = "/front_camera_examples/captured_files";

	private String fileName;

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
	private static final String FRAGMENT_DIALOG = "dialog";

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	/**
	 * Camera state: Showing camera preview.
	 */
	private static final int STATE_PREVIEW = 0;

	/**
	 * Camera state: Waiting for the focus to be locked.
	 */
	private static final int STATE_WAITING_LOCK = 1;

	/**
	 * Camera state: Waiting for the exposure to be precapture state.
	 */
	private static final int STATE_WAITING_PRECAPTURE = 2;

	/**
	 * Camera state: Waiting for the exposure state to be something other than
	 * precapture.
	 */
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;

	/**
	 * Camera state: Picture was taken.
	 */
	private static final int STATE_PICTURE_TAKEN = 4;

	private CameraDevice mCameraDevice;
	private ImageReader mImageReader;
	private String mCameraId;
	private int mState = STATE_PREVIEW;
	private CameraCaptureSession mCaptureSession;

	/**
	 * An additional thread for running tasks that shouldn't block the UI.
	 */
	private HandlerThread mBackgroundThread;
	private Handler mBackgroundHandler;

	/**
	 * A {@link Semaphore} to prevent the app from exiting before closing the
	 * camera.
	 */
	private Semaphore mCameraOpenCloseLock = new Semaphore(1);

	public WriteToFile(Context mContext) {
		super();
		this.mContext = mContext;
		mWindowManager = (WindowManager) mContext
				.getSystemService(Context.WINDOW_SERVICE);
	}

	public void ensureDirectoriIsExist() {
		File extDir;
		extDir = new File(Environment.getExternalStorageDirectory()
				+ CAPTURED_DIR);
		Ngelog.v("getStorage: " + Environment.getExternalStorageDirectory());
		if (!extDir.exists())
			extDir.mkdirs();
	}

	public File getCapturedFileDir() {
		File exportedFile;
		File extDir = new File(Environment.getExternalStorageDirectory()
				+ CAPTURED_DIR);
		long timeMilis = System.currentTimeMillis();
		fileName = "_" + timeMilis + ".jpg";
		exportedFile = new File(extDir, fileName);
		return exportedFile;
	}

	// Camera
	public void takePicture() {
		if (onProccesing)
			return;
		if (!onProccesing)
			onProccesing = true;
		setupCamera();
		openCamera();
	}

	private void setupCamera() {
		CameraManager manager = (CameraManager) mContext
				.getSystemService(Context.CAMERA_SERVICE);

		try {
			for (String cameraId : manager.getCameraIdList()) {
				CameraCharacteristics characteristics = manager
						.getCameraCharacteristics(cameraId);

				if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}

				StreamConfigurationMap map = characteristics
						.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				if (map == null) {
					continue;
				}

				Size largest = Collections.max(
						Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
						new CompareSizesByArea());

				mCameraId = cameraId;
				mImageReader = ImageReader.newInstance(largest.getWidth(),
						largest.getHeight(), ImageFormat.JPEG, 2);
				mImageReader.setOnImageAvailableListener(
						mOnImageAvailableListener, mBackgroundHandler);

			}
		} catch (CameraAccessException | NullPointerException e) {
			e.printStackTrace();
			Ngelog.v("Exception: " + e.getLocalizedMessage());
		}

	}

	private void openCamera() {
		CameraManager manager = (CameraManager) mContext
				.getSystemService(Context.CAMERA_SERVICE);
		try {
			if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException(
						"Time out waiting to lock camera opening.");
			}
			manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
			Ngelog.v("Exception: " + e.getLocalizedMessage());
		} catch (InterruptedException e) {
			Ngelog.v("Exception: " + e.getLocalizedMessage());
			throw new RuntimeException(
					"Interrupted while trying to lock camera opening.", e);
		}
	}

	private void closeCamera() {
		try {
			mCameraOpenCloseLock.acquire();
			if (null != mCaptureSession) {
				mCaptureSession.close();
				mCaptureSession = null;
			}
			if (null != mCameraDevice) {
				mCameraDevice.close();
				mCameraDevice = null;
			}
			if (null != mImageReader) {
				mImageReader.close();
				mImageReader = null;
			}
		} catch (InterruptedException e) {
			Ngelog.v("Exception: " + e.getLocalizedMessage());
			throw new RuntimeException(
					"Interrupted while trying to lock camera closing.", e);
		} finally {
			mCameraOpenCloseLock.release();
		}
	}

	private void createCaptureSession() {
		List<Surface> outputSurfaces = new LinkedList<>();
		outputSurfaces.add(mImageReader.getSurface());

		try {

			mCameraDevice.createCaptureSession(outputSurfaces,
					new CameraCaptureSession.StateCallback() {
						@Override
						public void onConfigured(CameraCaptureSession session) {
							mCaptureSession = session;
							createCaptureRequest();
						}

						@Override
						public void onConfigureFailed(
								CameraCaptureSession session) {
						}
					}, null);

		} catch (CameraAccessException e) {
			Ngelog.v("Exception: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	private void createCaptureRequest() {
		try {

			CaptureRequest.Builder requestBuilder = mCameraDevice
					.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			requestBuilder.addTarget(mImageReader.getSurface());

			// Focus
			requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

			// Orientation
			int rotation = mWindowManager.getDefaultDisplay().getRotation();
			Ngelog.v("rotation: " + rotation);
			requestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
					ORIENTATIONS.get(rotation));

			CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(
						@NonNull CameraCaptureSession session,
						@NonNull CaptureRequest request,
						@NonNull TotalCaptureResult result) {
				}
			};

			mCaptureSession.capture(requestBuilder.build(), CaptureCallback,
					null);

		} catch (CameraAccessException e) {
			Ngelog.v("Exception: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	/**
	 * {@link CameraDevice.StateCallback} is called when {@link CameraDevice}
	 * changes its state.
	 */
	private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(@NonNull CameraDevice cameraDevice) {
			// This method is called when the camera is opened. We start camera
			// preview here.
			mCameraOpenCloseLock.release();
			mCameraDevice = cameraDevice;
			createCaptureSession();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(@NonNull CameraDevice cameraDevice, int error) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

	};

	/**
	 * This a callback object for the {@link ImageReader}. "onImageAvailable"
	 * will be called when a still image is ready to be saved.
	 */
	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

		@Override
		public void onImageAvailable(ImageReader reader) {

			mBackgroundHandler.post(new ImageSaver(mImageReader
					.acquireLatestImage(), getCapturedFileDir()));
		}

	};

	/**
	 * Saves a JPEG {@link Image} into the specified {@link File}.
	 */
	private class ImageSaver implements Runnable {

		/**
		 * The JPEG image
		 */
		private final Image mImage;
		/**
		 * The file we save the image into.
		 */
		private final File mFile;

		public ImageSaver(Image image, File file) {
			mImage = image;
			mFile = file;
		}

		@Override
		public void run() {
			ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(mFile);
				output.write(bytes);
			} catch (IOException e) {
				Ngelog.v("Exception: " + e.getLocalizedMessage());
				e.printStackTrace();
			} finally {
				Toast.makeText(mContext, "FINISH", Toast.LENGTH_SHORT).show();
				closeCamera();
				if (null != output) {
					try {
						onProccesing = false;
						output.close();
					} catch (IOException e) {
						Ngelog.v("Exception: " + e.getLocalizedMessage());
						e.printStackTrace();
					}
				}
			}
		}

	}

	/**
	 * Starts a background thread and its {@link Handler}.
	 */
	public void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("CameraBackground");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	/**
	 * Stops the background thread and its {@link Handler}.
	 */
	public void stopBackgroundThread() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			Ngelog.v("Exception: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	static class CompareSizesByArea implements Comparator<Size> {

		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight()
					- (long) rhs.getWidth() * rhs.getHeight());
		}

	}

}
