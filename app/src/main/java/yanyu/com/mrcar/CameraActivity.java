package yanyu.com.mrcar;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CameraActivity  extends Activity {
    private TextView tv;
    @Nullable
    private Camera mCamera;
    private Camera.CameraInfo mCameraInfo;
    private int mCameraId = 0;
    @Nullable
    private Camera.CameraInfo mFrontCameraInfo = null;
    private int mFrontCameraId = -1;
    @Nullable
    private Camera.CameraInfo mBackCameraInfo = null;
    private int mBackCameraId = -1;
    private boolean mlibLoaded=false;
    private SurfaceHolder mPreviewSurface;

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final String TAG = "MRCar";
    private static final String sdcarddir = "/sdcard/mrcar";
    final int PREVIEW_WIDTH=640;
    final int PREVIEW_HEIGHT=480;
    private static final int PREVIEW_FORMAT = ImageFormat.NV21;
    private Thread thread;
    private boolean killed = false;
    private byte nv21[];
    private boolean isNV21ready = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        initCameraInfo();
        tv=findViewById(R.id.rstLic);
        SurfaceView cameraPreview = findViewById(R.id.surface_view);
        cameraPreview.getHolder().addCallback(new PreviewSurfaceCallback());
        nv21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
    }
    private void setPreviewSurface(@Nullable SurfaceHolder previewSurface) {
        Camera camera = mCamera;
        if (camera != null && previewSurface != null) {
            try {
                camera.setPreviewDisplay(previewSurface);
                Log.d(TAG, "setPreviewSurface() called");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private class PreviewSurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mPreviewSurface = holder;
            setPreviewSize(width,height);
            setPreviewSurface(holder);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stopPreview();
        }
    }
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    new Thread() {
                        @Override
                        public void run() {
                            initFile();
                            System.loadLibrary("mrcar");
                            if(!mlibLoaded){
                                MRCar.init(sdcarddir);
                                mlibLoaded = true;
                            }
                        }
                    }.start();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    private boolean isRequiredPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (!isRequiredPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS_CODE);
        }
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        openCamera(0);
        killed = false;
        final byte[] tmp = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
        thread = new Thread(){
            @Override
            public void run() {
                super.run();
                while (!killed) {
                    if(!isNV21ready)
                        continue;
                    synchronized (nv21) {
                        System.arraycopy(nv21, 0, tmp, 0, nv21.length);
                        isNV21ready = false;
                    }
                    if(mlibLoaded)
                    {
                        final String str=MRCar.plateNV21(tmp,PREVIEW_HEIGHT,PREVIEW_WIDTH);
                        Log.i(TAG,str);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(str.length()>=6)
                                    tv.setText(str);
                            }
                        });
                    }
                }
            }
        };
        thread.start();

    }
    private void closeCamera() {
        Camera camera = mCamera;
        mCamera = null;
        if (camera != null) {
            camera.release();
            mCameraId = -1;
            mCameraInfo = null;
        }
    }
    protected void onPause() {
        super.onPause();
        closeCamera();
    }
    private void initCameraInfo() {
        int numberOfCameras = Camera.getNumberOfCameras();// 获取摄像头个数
        for (int cameraId = 0; cameraId < numberOfCameras; cameraId++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // 后置摄像头信息
                mBackCameraId = cameraId;
                mBackCameraInfo = cameraInfo;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                // 前置摄像头信息
                mFrontCameraId = cameraId;
                mFrontCameraInfo = cameraInfo;
            }
        }
    }
    private boolean isPreviewFormatSupported(Camera.Parameters parameters, int format) {
        List<Integer> supportedPreviewFormats = parameters.getSupportedPreviewFormats();
        return supportedPreviewFormats != null && supportedPreviewFormats.contains(format);
    }
    private void setPreviewSize(int shortSide, int longSide) {
        Camera camera = mCamera;
        if (camera != null && shortSide != 0 && longSide != 0) {
            float aspectRatio = (float) longSide / shortSide;
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            if(supportedPreviewSizes.size()>1&&supportedPreviewSizes.get(0).width < supportedPreviewSizes.get(1).width)
                Collections.reverse(supportedPreviewSizes);
//            for (Camera.Size previewSize : supportedPreviewSizes) {
//                if ((float) previewSize.width / previewSize.height == aspectRatio && previewSize.height <= shortSide && previewSize.width <= longSide) {
//                    parameters.setPreviewSize(previewSize.width, previewSize.height);
//                    Log.d(TAG, "setPreviewSize() called with: width = " + previewSize.width + "; height = " + previewSize.height);
//                    if (isPreviewFormatSupported(parameters, PREVIEW_FORMAT)) {
//                        parameters.setPreviewFormat(PREVIEW_FORMAT);
//                        int frameWidth = previewSize.width;
//                        int frameHeight = previewSize.height;
//                        int previewFormat = parameters.getPreviewFormat();
//                        PixelFormat pixelFormat = new PixelFormat();
//                        PixelFormat.getPixelFormatInfo(previewFormat, pixelFormat);
//                        int bufferSize = (frameWidth * frameHeight * pixelFormat.bitsPerPixel) / 8;
//                        camera.addCallbackBuffer(new byte[bufferSize]);
//                        camera.addCallbackBuffer(new byte[bufferSize]);
//                        camera.addCallbackBuffer(new byte[bufferSize]);
//                        Log.d(TAG, "Add three callback buffers with size: " + bufferSize);
//                    }
//
//                    camera.setParameters(parameters);
//                    break;
//                }
//            }
            int frameWidth = PREVIEW_WIDTH;
            int frameHeight = PREVIEW_HEIGHT;
            int previewFormat = parameters.getPreviewFormat();
            PixelFormat pixelFormat = new PixelFormat();
            PixelFormat.getPixelFormatInfo(previewFormat, pixelFormat);
            int bufferSize = (frameWidth * frameHeight * pixelFormat.bitsPerPixel) / 8;
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.addCallbackBuffer(new byte[bufferSize]);
            Log.d(TAG, "Add three callback buffers with size: " + bufferSize);
        }
    }
    private class PreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            synchronized (nv21) {
                System.arraycopy(data, 0, nv21, 0, data.length);
                isNV21ready = true;
            }
            camera.addCallbackBuffer(data);
        }
    }
    private void startPreview() {
        Camera camera = mCamera;
        SurfaceHolder previewSurface = mPreviewSurface;
        if (camera != null && previewSurface != null) {
            camera.setPreviewCallbackWithBuffer(new PreviewCallback());
            camera.startPreview();
            Log.d(TAG, "startPreview() called");
        }
    }
    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            Log.d(TAG, "stopPreview() called");
        }
    }
    private void openCamera(int cameraId) {
        if (mCamera != null) {
            throw new RuntimeException("You must close previous camera before open a new one.");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mCamera = Camera.open(cameraId);
            mCameraId = cameraId;
            mCameraInfo = cameraId == mFrontCameraId ? mFrontCameraInfo : mBackCameraInfo;
            Log.d(TAG, "Camera[" + cameraId + "] has been opened.");
            assert mCamera != null;
            mCamera.setDisplayOrientation(getCameraDisplayOrientation(mCameraInfo));
        }
    }
    private int getCameraDisplayOrientation(Camera.CameraInfo cameraInfo) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return result;
    }
    private void initFile(){
        MRAssetUtil.CopyAssets(this,"mrcar",sdcarddir);
    }
}
