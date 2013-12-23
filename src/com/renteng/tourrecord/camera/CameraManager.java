package com.renteng.tourrecord.camera;

import java.io.IOException;
import com.renteng.tourrecord.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;

/*CameraManager类用于管理相机的相关事务
 *使用单例模式
 * */
public final class CameraManager {

	private static final String TAG = CameraManager.class.getSimpleName(); /* 本类类名 */

	private static final int MIN_FRAME_WIDTH = 240;
	private static final int MIN_FRAME_HEIGHT = 240;
	private static final int MAX_FRAME_WIDTH = 480;
	private static final int MAX_FRAME_HEIGHT = 360;

	private static CameraManager cameraManager; // 实例

	static final int SDK_INT;
	static {
		int sdkInt;
		try {
			sdkInt = Integer.parseInt(Build.VERSION.SDK);
		} catch (NumberFormatException nfe) {
			sdkInt = 10000;
		}
		SDK_INT = sdkInt;
	}

	private final Context context;
	private final CameraConfigurationManager configManager;
	private Camera camera; /* 相机实例 */
	private Rect qrCodeRect;
	private Rect qrCodeRectInPreview;

	private boolean initialized;
	private boolean previewing;
	private boolean reverseImage;
	private final boolean useOneShotPreviewCallback;

	private final PreviewCallback previewCallback;
	private final AutoFocusCallback autoFocusCallback;

	/* 初始化实例 */
	public static void init(Context context) {
		if (cameraManager == null) {
			cameraManager = new CameraManager(context);
		}
	}

	/* 获取实例 */
	public static CameraManager get() {
		return cameraManager;
	}

	/* 构造函数 */
	private CameraManager(Context context) {
		this.context = context;
		this.configManager = new CameraConfigurationManager(context);

		useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > 3;

		previewCallback = new PreviewCallback(configManager,
				useOneShotPreviewCallback);
		autoFocusCallback = new AutoFocusCallback();
	}

	public void openDriver(SurfaceHolder holder)throws IOException {
		if (camera == null) {
			camera = Camera.open();
			if (camera == null) {
				throw new IOException();
			}
		}
		camera.setPreviewDisplay(holder);
		if (!initialized) {
			initialized = true;
			configManager.initFromCameraParameters(camera);
		}
		configManager.setDesiredCameraParameters(camera);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		reverseImage = prefs.getBoolean("preferences_reverse_image", false);
		if (prefs.getBoolean("preferences_front_light", false)) {
			FlashlightManager.enableFlashlight();
		}
		startPreview();
	}

	public void closeDriver() {
		if (camera != null) {
			FlashlightManager.disableFlashlight();
			stopPreview();
			camera.release();
			camera = null;

			qrCodeRect = null;
			qrCodeRectInPreview = null;
		}
	}

	public void startPreview() {
		if (camera != null && !previewing) {
			camera.startPreview();
			previewing = true;
		}
	}

	public void stopPreview() {
		if (camera != null && previewing) {
			if (!useOneShotPreviewCallback) {
				camera.setPreviewCallback(null);
			}
			camera.stopPreview();
			previewCallback.setHandler(null, 0);
			autoFocusCallback.setHandler(null, 0);
			previewing = false;
		}
	}

	public void requestPreviewFrame(Handler handler, int message) {
		if (camera != null && previewing) {
			previewCallback.setHandler(handler, message);
			if (useOneShotPreviewCallback) {
				camera.setOneShotPreviewCallback(previewCallback);
			} else {
				camera.setPreviewCallback(previewCallback);
			}
		}
	}

	public void requestAutoFocus(Handler handler, int message) {
		if (camera != null && previewing) {
			autoFocusCallback.setHandler(handler, message);
			camera.autoFocus(autoFocusCallback);
		}
	}

	public Rect getQRFramingRect() {
		if (qrCodeRect == null) {
			if (camera == null) {
				return null;
			}

			Point screenResolution = configManager.getScreenResolution();
			int width = screenResolution.x / 4;
			if (width < MIN_FRAME_WIDTH) {
				width = MIN_FRAME_WIDTH;
			} else if (width > MAX_FRAME_WIDTH) {
				width = MAX_FRAME_WIDTH;
			}

			int height = screenResolution.y / 3;
			if (height < MIN_FRAME_HEIGHT) {
				height = MIN_FRAME_HEIGHT;
			} else if (height > MAX_FRAME_HEIGHT) {
				height = MAX_FRAME_HEIGHT;
			}

			int minlength;
			if (width < height) {
				minlength = width;
			} else {
				minlength = height;
			}

			int leftOffset = screenResolution.x - (int) (1.2 * minlength);
			int topOffset = screenResolution.y - (int) (1.2 * minlength);

			qrCodeRect = new Rect(leftOffset, topOffset,
					leftOffset + minlength, topOffset + minlength);
			Log.d(TAG, "Calculated framing rect: " + qrCodeRect);
		}
		return qrCodeRect;
	}

	public Rect getqrCodeRectInPreview() {
		if (qrCodeRectInPreview == null) {
			Rect rect = new Rect(getQRFramingRect());
			Point cameraResolution = configManager.getCameraResolution();
			Point screenResolution = configManager.getScreenResolution();

			rect.left = rect.left * cameraResolution.x / screenResolution.x;
			rect.right = rect.right * cameraResolution.x / screenResolution.x;
			rect.top = rect.top * cameraResolution.y / screenResolution.y;
			rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;

			qrCodeRectInPreview = rect;
		}
		return qrCodeRectInPreview;
	}

	public void setManualFramingRect(int width, int height) {
		Point screenResolution = configManager.getScreenResolution();
		if (width > screenResolution.x) {
			width = screenResolution.x;
		}
		if (height > screenResolution.y) {
			height = screenResolution.y;
		}
		int leftOffset = (screenResolution.x - width) / 2;
		int topOffset = (screenResolution.y - height) / 2;
		qrCodeRect = new Rect(leftOffset, topOffset, leftOffset + width,
				topOffset + height);
		Log.d(TAG, "Calculated manual framing rect: " + qrCodeRect);
		qrCodeRectInPreview = null;
	}

	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
			int width, int height) {
		Rect rect = getqrCodeRectInPreview();
		int previewFormat = configManager.getPreviewFormat();
		String previewFormatString = configManager.getPreviewFormatString();

		switch (previewFormat) {
		// This is the standard Android format which all devices are REQUIRED to
		// support.
		// In theory, it's the only one we should ever care about.
		case PixelFormat.YCbCr_420_SP:
			// This format has never been seen in the wild, but is compatible as
			// we only care
			// about the Y channel, so allow it.
		case PixelFormat.YCbCr_422_SP:
			return new PlanarYUVLuminanceSource(data, width, height, rect.left,
					rect.top, rect.width(), rect.height(), reverseImage);
		default:
			// The Samsung Moment incorrectly uses this variant instead of the
			// 'sp' version.
			// Fortunately, it too has all the Y data up front, so we can read
			// it.
			if ("yuv420p".equals(previewFormatString)) {
				return new PlanarYUVLuminanceSource(data, width, height,
						rect.left, rect.top, rect.width(), rect.height(),
						reverseImage);
			}
		}
		throw new IllegalArgumentException("Unsupported picture format: "
				+ previewFormat + '/' + previewFormatString);
	}
}
