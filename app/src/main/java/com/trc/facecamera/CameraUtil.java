package com.trc.facecamera;

import android.hardware.Camera;
import android.hardware.Camera.Size;

import java.util.Iterator;
import java.util.List;

public class CameraUtil {

    /**
     * 打开相机
     */
    public static Camera getCamera() {
        try {
            return Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 前置
     *
     * @return
     */
    public static Camera getFrontCamera() {
        try {
            return Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 匹配 最合适的尺寸
     * 原则：
     * 1.剔除分辨率比屏幕高的尺寸
     * 2.剔除宽高比与屏幕差距过大的尺寸
     *
     * @param sizes
     * @param screenWidth
     * @param screenHeight
     * @return
     */
    public static Size getBestCameraSize(List<Size> sizes, int screenWidth, int screenHeight) {
        float screenRadio;
        if (screenWidth > screenHeight) {
            screenRadio = (float) screenWidth / (float) screenHeight;//屏幕宽高比
        } else {
            screenRadio = (float) screenHeight / (float) screenWidth;//竖屏时候需要反一下
            int tmpWidth = screenHeight;
            int tmpHeight = screenWidth;
            screenHeight = tmpHeight;
            screenWidth = tmpWidth;
        }

        Iterator it = sizes.iterator();
        while (it.hasNext()) {
            Size size = (Size) it.next();
            float curRadio = (float) size.width / (float) size.height;//该尺寸宽高比
            if (size.width == 1920 && size.height == 1080) {
                //标准分辨率，保留
            } else if (size.width == 1280 && size.height == 720) {
                //标准分辨率，保留
            } else if (size.width > screenWidth || size.height > screenHeight) {
                //比屏幕分辨率还高，没必要
                it.remove();
            } else if (Math.abs(screenRadio - curRadio) > 0.2) {
                //如果该尺寸的宽高比与屏幕宽高比差距太大，一并剔除 以免变形
                it.remove();
            }
        }

        Size bestSize = null;
        if (0 == sizes.size()) {
            return bestSize;
        }
        if (sizes.size() > 2) {
            bestSize = sizes.get(sizes.size() / 2);
        } else {
            bestSize = sizes.get(0);
        }
        return bestSize;
    }
}
