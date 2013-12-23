package com.renteng.tourrecord.camera;

import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public final class CameraConfigurationManager {
	
	private static final String TAG = CameraConfigurationManager.class.getSimpleName();
	private static final int TEN_DESIRED_ZOOM = 27; //不知道啥意思
	private static final Pattern COMMA_PATTERN = Pattern.compile(",");//正则表达式，不知道匹配啥东西
	
	private final Context context;             //运行上下文
	private Point screenResolution;      //屏幕分辨率
	private Point cameraResolution;      //相机分辨率
	private int previewFormat;           //预览格式，具体取值，不清楚
	private String previewFormatString;  //预览格式字符串，具体取值，不清楚

	CameraConfigurationManager(Context context){
		this.context = context;
	}
	
	//从相机参数中获取初始参数 
	void initFromCameraParameters(Camera camera){
		Camera.Parameters parameter = camera.getParameters();
		previewFormat = parameter.getPreviewFormat();       //获取支持预览的格式
		previewFormatString = parameter.get("preview-format");
		Log.d(TAG, "Default preview format: " + previewFormat + '/' + previewFormatString);
		WindowManager manager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		screenResolution = new Point(display.getWidth(), display.getHeight());
		Log.d(TAG, "Screen resolution: " + screenResolution);
		cameraResolution = getCameraResolution(parameter, screenResolution);
		Log.d(TAG, "Camera resolution: " + cameraResolution);
	}
	
	void setDesiredCameraParameters(Camera camera){
		Camera.Parameters p = camera.getParameters();
		p.setPreviewSize(cameraResolution.x, cameraResolution.y);
		setFlash(p);
		setZoom(p);
		camera.setParameters(p);
	}
	

	
	//获取相机分辨率
	private static Point getCameraResolution(Camera.Parameters parameter, Point screenResolution){
		String previewSizeValueString = parameter.get("preview-size-values");
		
		if(previewSizeValueString == null){
			previewSizeValueString = parameter.get("preview-size-value");
		}
		
		Point cameraResolution = null;
		
		if(previewSizeValueString != null){
			Log.d(TAG, "preview-size-values parameter: "
					+ previewSizeValueString);
			cameraResolution = findBestPreviewSizeValue(previewSizeValueString,
					screenResolution);
		}
		
		/*将屏幕分辨率取8的倍数*/
		if(cameraResolution == null){
			cameraResolution = new Point((screenResolution.x >> 3) << 3, 
					(screenResolution.y >> 3) << 3);
		}
		
		return cameraResolution;
	}
	
	private static Point findBestPreviewSizeValue(
			CharSequence previewSizeValueString, Point screenResolution){
		int bestX = 0;
		int bestY = 0;
		int diff = Integer.MAX_VALUE;
		for(String previewSize : COMMA_PATTERN.split(previewSizeValueString)){
			
			previewSize = previewSize.trim();
			int dimPosition = previewSize.indexOf('x');
			if(dimPosition < 0){
				Log.w(TAG, "Bak preview-size: " + previewSize);
				continue;
			}
			
			int newX;
			int newY;
			
			try{
				newX = Integer.parseInt(previewSize.substring(0, dimPosition));
				newY = Integer.parseInt(previewSize.substring(dimPosition + 1));
			}catch (NumberFormatException nfe){
				Log.w(TAG, "Bad preview-size: " + previewSize);
				continue;
			}
			
			int newDiff = Math.abs(newX - screenResolution.x)
					+ Math.abs(newY - screenResolution.y);
			if(newDiff == 0){
				bestX = newX;
				bestY = newY;
				break;
			}else if(newDiff < diff){
				bestX = newX;
				bestY = newY;
				diff = newDiff;
			}
		}
		
		if(bestX > 0 && bestY > 0){
			return new Point(bestX, bestY);
		}
		
		return null;
	}
	
	private static int findBestMotZoomValue(CharSequence stringValues,
			int tenDesiredZoom){
		int tenBestValue = 0;
		for(String stringValue : COMMA_PATTERN.split(stringValues)){
			stringValue = stringValue.trim();
			double value;
			
			try{
				value = Double.parseDouble(stringValue);
			}catch(NumberFormatException nfe){
				return tenDesiredZoom;
			}
			
			int tenValue = (int)(10.0 * value);
			if(Math.abs(tenDesiredZoom - value) < Math.abs(tenDesiredZoom - tenBestValue)){
				tenBestValue = tenValue;
			}			
		}
		return tenBestValue;
	}
	
	private void setFlash(Camera.Parameters parameters){
		if(Build.MODEL.contains("Behold II") && CameraManager.SDK_INT == 3){
			parameters.set("flash-value", 1);
		}else{
			parameters.set("flash-value", 2);
		}
		
		parameters.set("flash-value", "off");
	}
	
	private void setZoom(Camera.Parameters parameters){
		String zoomSupportedString = parameters.get("zoom-supported");
		if(zoomSupportedString != null
				&& !Boolean.parseBoolean(zoomSupportedString)){
			return;
		}
		
		int tenDesiredZoom = TEN_DESIRED_ZOOM;
		
		String maxZoomString = parameters.get("max-zoom");
		if(maxZoomString != null){
			try{
				int tenMaxZoom = (int) (10.0 * Double.parseDouble(maxZoomString));
				if(tenDesiredZoom > tenMaxZoom){
					tenDesiredZoom = tenMaxZoom;
				}
			}catch(NumberFormatException nfe){
				Log.w(TAG, "Bad max-zoom: " + maxZoomString);
			}
		}
		
		String takingPictureZoomMaxString = parameters.get("taking-picture-zoom-max");
		if(takingPictureZoomMaxString != null){
			try{
				int tenMaxZoom = Integer.parseInt(takingPictureZoomMaxString);
				if(tenDesiredZoom > tenMaxZoom){
					tenDesiredZoom = tenMaxZoom;
				}
			}catch(NumberFormatException nfe){
					Log.w(TAG, "Bad taking-picture-zoom-max: "
							+ takingPictureZoomMaxString);
			}
		}
		
		String motZoomValuesString = parameters.get("mot-zoom-values");
		if(motZoomValuesString != null){
			tenDesiredZoom = findBestMotZoomValue(motZoomValuesString,
					tenDesiredZoom);
		}
		

		String motZoomStepString = parameters.get("mot-zoom-step");
		if (motZoomStepString != null) {
			try {
				double motZoomStep = Double.parseDouble(motZoomStepString
						.trim());
				int tenZoomStep = (int) (10.0 * motZoomStep);
				if (tenZoomStep > 1) {
					tenDesiredZoom -= tenDesiredZoom % tenZoomStep;
				}
			} catch (NumberFormatException nfe) {
				// continue
			}
		}
		
		if (maxZoomString != null || motZoomValuesString != null) {
			parameters.set("zoom", String.valueOf(tenDesiredZoom / 10.0));
		}
		
		if (takingPictureZoomMaxString != null) {
			parameters.set("taking-picture-zoom", tenDesiredZoom);
		}
	}
	
	Point getCameraResolution(){
		return cameraResolution;
	}
	
	Point getScreenResolution(){
		return screenResolution;
	}
	
	int getPreviewFormat(){
		return previewFormat;
	}
	
	String getPreviewFormatString(){
		return previewFormatString;
	}
}
