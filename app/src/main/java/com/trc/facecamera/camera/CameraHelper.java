package com.trc.facecamera.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

/**
 * JiangyeLin on 2019-07-10
 */
public class CameraHelper {
    private static CameraHelper cameraHelper;
    private Activity activity;
    private CameraUtil cameraUtil;

    private int previewWidth = 1280;
    private int previewHeight = 720;
    private int shotInterval = 400;

    private boolean enableDowngradeCamera1 = false;

    private CameraHelper() {
    }

    public static CameraHelper getInstance() {
        if (null == cameraHelper) {
            synchronized (CameraHelper.class) {
                if (null == cameraHelper) {
                    cameraHelper = new CameraHelper();
                }
            }
        }
        return cameraHelper;
    }

    public void register(AppCompatActivity activity) {
        this.activity = activity;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        activity.getLifecycle().addObserver(new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            public void resume() {
                if (null == cameraUtil) {
                    cameraUtil = new CameraUtil();
                }
                cameraUtil.initCamera();
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            public void release() {
                //释放资源
                if (null != cameraUtil) {
                    cameraUtil.release();
                }
            }
        });
    }

    public Activity getActivity() {
        return activity;
    }

    /**
     * 预览宽高
     * 注意这是设定相机预览帧数据的尺寸，非裁剪后图片的尺寸
     * 该尺寸不允许随意设置，必须为相机支持尺寸之一
     * 目前默认1280*720，是因为大部分传感器均支持该尺寸
     *
     * @param previewWidth
     * @param previewHeight
     * @return
     */
    public CameraHelper setPrevieSize(int previewWidth, int previewHeight) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        return this;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public int getShotInterval() {
        return shotInterval;
    }

    /**
     * 拍照间隔，由于一般预览帧数可达30fps左右
     * 防止过于频繁截取人脸，节省资源，设置拍摄间隔
     *
     * @param shotInterval 毫秒
     * @return
     */
    public CameraHelper setShotInterval(int shotInterval) {
        this.shotInterval = shotInterval;
        return this;
    }

    public boolean enableDowngradeCamera1() {
        return enableDowngradeCamera1;
    }

    /**
     * 如果设备不支持是否允许降级至camera1
     * <p>
     * 未实现
     *
     * @param enableDowngradeCamera1
     */
    public void enableDowngradeCamera1(boolean enableDowngradeCamera1) {
        this.enableDowngradeCamera1 = enableDowngradeCamera1;
    }
}
