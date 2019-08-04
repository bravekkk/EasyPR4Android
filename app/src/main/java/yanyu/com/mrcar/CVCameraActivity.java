package yanyu.com.mrcar;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class CVCameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final String TAG = "MRCar";
    private static final String sdcarddir = "/sdcard/mrcar";
    private int mCameraId = 0;
    private int numberOfCameras = 1;
    private boolean mlibLoaded=false;

    private Thread thread;
    private boolean killed = false;
    private boolean isMatready=false;
    private Mat mImg2Recog;

    private Mat mRgba;
    private MRCameraView mOpenCvCameraView;
    private OnRecognizedCallBack regcallback=new OnRecognizedCallBack() {
        @Override
        public void onRecognized(final String str) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast toast=Toast.makeText(CVCameraActivity.this,str,Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }
    };
    public interface OnRecognizedCallBack{
        public void onRecognized(String str);
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
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    private boolean switchCamera(){
        if(mOpenCvCameraView != null){
            mOpenCvCameraView.disableView();
        }
        if(numberOfCameras >= 2){
            if(mCameraId == 0){
                mOpenCvCameraView.setCameraIndex(1);
                mCameraId = 1;
            } else {
                mOpenCvCameraView.setCameraIndex(0);
                mCameraId = 0;
            }
            mOpenCvCameraView.enableView();
        }else{
            return false;
        }
        return true;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_cvcamera);
        mOpenCvCameraView = findViewById(R.id.mr_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCameraIndex(0);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        numberOfCameras = Camera.getNumberOfCameras();
    }
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
        thread = new Thread() {
            @Override
            public void run() {
                while(!killed){
                    if(!isMatready){
                        continue;
                    }
                    Mat tmp;
                    synchronized (mImg2Recog) {
                        tmp=mImg2Recog.clone();
                        isMatready = false;
                    }
                    if(mlibLoaded){
                        String str=MRCar.plateLive(tmp.nativeObj);
                        Log.i(TAG,str);
                        if(str.length()>=6){
                            regcallback.onRecognized(str);
                        }
                    }
                }
            }
        };
        thread.start();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mOpenCvCameraView!=null)
            mOpenCvCameraView.disableView();
        if(mlibLoaded){
            MRCar.release();
            mlibLoaded=false;
        }
    }

    private void initFile(){
        MRAssetUtil.CopyAssets(this,"mrcar",sdcarddir);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mImg2Recog=mRgba.clone();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mImg2Recog.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        synchronized (mImg2Recog){
            mImg2Recog=mRgba.clone();
            isMatready=true;
        }
//        if(mlibLoaded){
//            MRCar.plateLive(mRgba.nativeObj);
//        }
        return mRgba;
    }
}