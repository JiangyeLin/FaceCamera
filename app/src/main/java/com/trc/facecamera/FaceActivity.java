package com.trc.facecamera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * JiangyeLin on 2019/7/4
 */
public class FaceActivity extends AppCompatActivity {
    private String TAG = getClass().getSimpleName();

    private Camera camera;
    private final int MAX_PICTURE_COUNT = 8;   //节省资源，单张人脸最多拍照次数

    private int previewOrientation; //屏幕预览的方向
    private int cameraOrientation;  //摄像头实际的方向

    private int lastFaceId = -1;
    private int pictureCount = 0;
    private CameraPreview cameraPreview;

    private int screenWidth;
    private int screenHeight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);

        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metric);
        screenWidth = metric.widthPixels;
        screenHeight = metric.heightPixels;
    }

    @Override
    protected void onResume() {
        super.onResume();
        initCameraPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraPreview.release();
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    /**
     * 初始化预览界面
     */
    private void initCameraPreview() {
        if (null == camera) {
            camera = CameraUtil.getFrontCamera();
        }
        if (null == camera) {
            //ToastUtil.showNormalToast("抱歉，摄像头被占用,暂无法使用本功能");
            finish();
            return;
        }
        previewOrientation = calCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_FRONT);

        camera.setDisplayOrientation(previewOrientation);
        camera.setFaceDetectionListener(new MyFaceDetectionListener());

        Camera.Parameters params = camera.getParameters();

        for (Camera.Size size : params.getSupportedPictureSizes()) {
            //Log.d(TAG, size.width + "  " + size.height);
        }
       /*  Camera.Size size = CameraUtil.getBestCameraSize(params.getSupportedPreviewSizes(), screenWidth, screenHeight);
        if (null == size) {
            params.setPictureSize(1280, 720);
        } else {
            params.setPictureSize(size.width, size.height);
        }*/

        params.setPictureSize(1280, 720);
        camera.setParameters(params);

        //如果不设置预览，camera不会返回数据流
        cameraPreview = new CameraPreview(this, camera, true);
        FrameLayout preview = findViewById(R.id.framelayout);
        preview.removeAllViews();
        preview.addView(cameraPreview);

        cameraOrientation = previewOrientation;
        if (90 == previewOrientation) {
            cameraOrientation = 270;
        } else if (270 == previewOrientation) {
            cameraOrientation = 90;
        }
    }

    /**
     * 计算相机旋转角度
     *
     * @param activity
     * @param cameraId
     */
    public int calCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
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
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;   // compensate the mirror
        } else {
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File file = new File(getExternalCacheDir(), String.valueOf(System.currentTimeMillis()) + ".jpg");

            Log.d(TAG, "准备保存照片");

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(file, false);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Matrix matrix = new Matrix();
                matrix.preRotate(cameraOrientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                pictureCount++;
                camera.startPreview();
                takePicturing = false;
                Log.d(TAG, "onPictureTaken: 保存完毕" + file.getAbsolutePath());
            }
        }
    };

    private boolean takePicturing = false;  //是否正在拍照

    class MyFaceDetectionListener implements Camera.FaceDetectionListener {
        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            if (faces.length < 1) {
                return;
            }
            if (takePicturing) {
                return;
            }
            Camera.Face face = faces[0];

            //检测到人脸的处理
            boolean result = checkFacePosition(face, screenWidth / 2, screenHeight / 3);//检测人脸是否位于范围内
            if (!result) {
                return;
            }

            if (lastFaceId == face.id && pictureCount > MAX_PICTURE_COUNT) {
                return;
            }
            if (lastFaceId != face.id) {
                lastFaceId = face.id;
                pictureCount = 0;
            }

            takePicturing = true;
            camera.takePicture(null, null, pictureCallback);
            lastFaceId = face.id;
        }
    }

    /**
     * 校验人脸位置
     *
     * @param face 人脸信息
     * @param x    对照点的坐标
     * @param y
     * @return 人脸是否位于参照范围内
     */
    private boolean checkFacePosition(Camera.Face face, int x, int y) {
        int diff = 150;
        if (face.score < 50) {
            //置信度过低
            return false;
        }

        RectF mRect = new RectF();
        Matrix matrix = new Matrix();
        prepareMatrix(matrix, false, previewOrientation, screenWidth, screenHeight);
        mRect.set(face.rect);
        matrix.mapRect(mRect);

        int faceX = (int) mRect.centerX();
        int faceY = (int) mRect.centerY();

        return (Math.abs(faceX - x) <= diff) && (Math.abs(faceY - y) <= diff);
    }

    /**
     * camera拍出来的坐标系与我们屏幕的坐标系不同，需要变换
     *
     * @param matrix
     * @param mirror             是否需要翻转，后置摄像头（手机背面）不需要翻转，前置摄像头需要翻转
     * @param displayOrientation 旋转的角度
     * @param viewWidth          预览View的宽高
     * @param viewHeight
     */
    public void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                              int viewWidth, int viewHeight) {
        // Need mirror for front camera.
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        matrix.postRotate(displayOrientation);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height)
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
    }
}
