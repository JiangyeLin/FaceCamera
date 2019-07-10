package com.trc.facecamera;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.trc.facecamera.camera.CameraHelper;

/**
 * JiangyeLin on 2019-07-08
 */
public class Camera2Activity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initFaceDetection();
    }

    /**
     * 人脸检测 初始化
     */
    private void initFaceDetection() {
        CameraHelper.getInstance()
                .setPrevieSize(1280, 720)
                .setShotInterval(500)
                .register(this);
    }
}
