package com.trc.facecamera;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * JiangyeLin on 2018/6/26
 * 自定义 预览界面
 * 暂不支持直接从xml配置
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraPreview";
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private boolean isNeedFaceDetection;

    /**
     * @param context
     * @param camera
     * @param isNeedFaceDetection 是否需要开启人脸识别
     */
    public CameraPreview(Context context, Camera camera, boolean isNeedFaceDetection) {
        super(context);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setKeepScreenOn(true);

        this.camera = camera;
        this.isNeedFaceDetection = isNeedFaceDetection;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder);  //连接预览
            camera.startPreview(); //开始预览
            if (isNeedFaceDetection) {
                camera.startFaceDetection();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            if (isNeedFaceDetection) {
                camera.startFaceDetection();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
    }

    /**
     * 如果在预览界面直接锁屏的话，不会触发{@link #surfaceDestroyed(SurfaceHolder)}
     * 请在activity pause的时候手动释放资源
     */
    public void release() {
        surfaceHolder.removeCallback(this);
    }
}