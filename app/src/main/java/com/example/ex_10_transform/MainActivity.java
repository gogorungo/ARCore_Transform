package com.example.ex_10_transform;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView myTextView;

    GLSurfaceView mSurfaceView;
    MainRenderer mRenderer;

    Session mSession;
    Config mConfig;

    boolean mUserRequestedInstall = true, mTouched = false, isModelInit = false;

    float mCurrentX, mCurrentY;

    float mRotateFactor = 0f;
    float mScaleFactor = 1f;

    //이동, 회전 이벤트 처리할 객체
    GestureDetector mGestureDetector;

    // 크기조절 이벤트 처리할 객체
    ScaleGestureDetector mScaleGestureDetector;

    float [] modelMatrix = new float[16];



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideStatusBarAndTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        myTextView = (TextView) findViewById(R.id.myTextView);

        // 제스처이벤트 콜백함수 객체를 생성자 매개변수로 처리 (이벤트 핸들러)
        // 내꺼에서처리 this
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
            // 따닥 더블클릭 처리 (이동)
            @Override
            public boolean onDoubleTap(MotionEvent event) {
                mTouched = true; // 그려라
                isModelInit = false; // 새로 좌표를 받아라
                mCurrentX = event.getX();
                mCurrentY = event.getY();
                Log.d("확인 더블클릭",event.getX()+" , "+event.getY());
                return true;
            }

            // 드래그 처리 (회전)
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

                // 그려졌을때만 회전
                if(isModelInit) {
                    
                    // 변화량을 누적하여 초기화시 변화된 총량을 회전값으로 넣는다
                    mRotateFactor += -distanceX / 5;

                    // 지금의 변화량만 넣는다
                    // 회전(각도)               옵셋,       각도,   축이 0 혹은 양수, 음수만 중요
                    // 수치조절은 각도로 하기 때문에 축의 숫자는 큰 의미가 없다. 음수, 양수만 중요
                    Matrix.rotateM(modelMatrix, 0, -distanceX / 5, 0f, 1f, 0f);

                    Log.d("확인 드래그", distanceX + " , " + distanceY);
                }
                return true;
            }
        });
        
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // 두 손가락으로 스케일 시 작동

                // 그려졌을때만 회전
                if(isModelInit) {
                    // 변화량을 누적하여 초기화시 변화된 총량을 회전값으로 넣는다
//                    mRotateFactor += -distanceX / 5;

                    // 비율이므로 곱하기
                    mScaleFactor *= detector.getScaleFactor();

                   // x,y,z 를 같은 비율로 증가
                    Matrix.scaleM(modelMatrix,0,
                            detector.getScaleFactor(),
                            detector.getScaleFactor(),
                            detector.getScaleFactor());
                }
                Log.d("확인 스케일", detector.getScaleFactor() + " " );
                return true;
            }
        });

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if(displayManager != null){
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {

                }

                @Override
                public void onDisplayRemoved(int i) {

                }

                @Override
                public void onDisplayChanged(int i) {
                    synchronized (this){
                        mRenderer.mViewportChanged = true;
                    }
                }
            }, null);
        }

        mRenderer = new MainRenderer(this, new MainRenderer.RenderCallback() {
            @Override
            public void preRender() {
                if(mRenderer.mViewportChanged){
                    Display display = getWindowManager().getDefaultDisplay();
                    int displayRotation = display.getRotation();
                    mRenderer.updateSession(mSession, displayRotation);
                }

                // 카메라에 알려준다
                mSession.setCameraTextureName(mRenderer.getTextureId());

                // 세션으로부터 정보를 받아올 프레임
                Frame frame = null;

                try {
                    frame = mSession.update();
                } catch (CameraNotAvailableException e) {
                    e.printStackTrace();
                }

                // 프레임 변경시
                if(frame.hasDisplayGeometryChanged()){
                    mRenderer.mCamera.transformDisplayGeometry(frame);
                }

                PointCloud pointCloud = frame.acquirePointCloud();
                mRenderer.mPointCloud.update(pointCloud);
                // 자원해제
                pointCloud.release();

                //터치하였다면
                if(mTouched){
                    List<HitResult> results = frame.hitTest(mCurrentX, mCurrentY);
                    for(HitResult result : results){
                        Pose pose = result.getHitPose(); // 증강 공간에서의 좌표
//                        float [] modelMatrix = new float[16];

                        if(!isModelInit) {
                            isModelInit = true;
                            pose.toMatrix(modelMatrix, 0); // 좌표를 가지고 matrix 화 함

                            // 초기화시 기존의 회전값으로 회전한다
                            Matrix.rotateM(modelMatrix,0,mRotateFactor,0f,1f,0f);

                            // 초기화 시 기존의 스케일로 조절
                            Matrix.scaleM(modelMatrix,0,
                                    mScaleFactor,
                                    mScaleFactor,
                                    mScaleFactor);
                        }

//                        float [] cubeMatrix = new float[16];
//                        pose.toMatrix(cubeMatrix, 0); // 좌표를 가지고 matrix 화 함

                        // 증강 공간의 좌표에 객체 있는지 받아온다(Plane 이 걸려 있는지 확인)
                        Trackable trackable = result.getTrackable();

                        // 크기 변경(비율)
//                        Matrix.scaleM(modelMatrix,0,1f,2f, 1f);
                        Log.d("모델 매트릭스", Arrays.toString(modelMatrix));

                        // 이동(거리)
//                        Matrix.translateM(modelMatrix, 0,0f,0.1f,0f);

                        // 회전(각도)               옵셋,       각도,   축이 0 혹은 양수, 음수만 중요
                        // 수치조절은 각도로 하기 때문에 축의 숫자는 큰 의미가 없다. 음수, 양수만 중요
//                        Matrix.rotateM(modelMatrix,0,45,1f,0f,1f);

                        // 좌표에 걸린 객체가 Plane 인가
                        if(trackable instanceof Plane &&
                                // Plane 폴리곤(면) 안에 좌표가 있는가?
                                ((Plane)trackable).isPoseInPolygon(pose)
                        ){
                            mRenderer.mObj.setModelMatrix(modelMatrix);
                        }
                    }
//                    mTouched = false;
                }

                //Session으로부터 증강현실 속에서의 평면이나 점 객체를 얻을 수 있다.
                //                                   Plane   Point
                Collection<Plane> planes = mSession.getAllTrackables(Plane.class);


                boolean isPlaneDetected = false;

                //ARCore 상의 Plane 들을 얻는다.
                for(Plane plane: planes){

                    // plane 이 정상이라면
                    if(plane.getTrackingState() == TrackingState.TRACKING &&
                            plane.getSubsumedBy() == null){ // 다른 평면이 존재하는지

                        isPlaneDetected = true;

                        //렌더링에서 plane 정보를 갱신하여 출력
                        mRenderer.mPlane.update(plane);
                    }
                }

                if(isPlaneDetected) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("평면을 찾았다");
                        }
                    });
                }else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("평면이 안보인다");
                        }
                    });
                }

                // 카메라 세팅
                Camera camera = frame.getCamera();
                float [] projMatrix = new float[16];
                camera.getProjectionMatrix(projMatrix,0,0.1f,100f);
                float [] viewMatrix = new float[16];
                camera.getViewMatrix(viewMatrix,0);

                mRenderer.setProjectionMatrix(projMatrix);
                mRenderer.updateViewMatrix(viewMatrix);
            }
        });

        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8,8,8,8,16,0);
        mSurfaceView.setRenderer(mRenderer);
    }

    // 버튼에 의한 조명 색상 변경
    public void btnClick(View view){
        int color = ((ColorDrawable)view.getBackground()).getColor();

        float [] colorCorrection = {
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                0.5f
        };

        mRenderer.mObj.setColorCorrection(colorCorrection);
    }


    @Override
    protected void onResume() {
        super.onResume();
        requestCameraPermission();
        try {
            // 세션이 없다면 세션 생성
            if(mSession == null){
                switch (ArCoreApk.getInstance().requestInstall(this, true)){
                    case INSTALLED:
                        mSession = new Session(this);
                        Log.d("메인"," ARCore session 생성");
                        break;
                    //설치가 안되는 기계
                    case INSTALL_REQUESTED:
                        mUserRequestedInstall = false;
                        Log.d("메인"," ARCore 설치가 필요함");
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        mConfig = new Config(mSession);

        mSession.configure(mConfig);

        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }

        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        mSession.pause();
    }

    void hideStatusBarAndTitleBar(){
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    void requestCameraPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    this,
                    new String [] {Manifest.permission.CAMERA},
                    0
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 위임해서 정보를 받아옴
        mGestureDetector.onTouchEvent(event);
        mScaleGestureDetector.onTouchEvent(event);

//        if(event.getAction() == MotionEvent.ACTION_DOWN){
//            mTouched = true;
//            mCurrentX = event.getX();
//            mCurrentY = event.getY();
//        }
        return true;
    }
}