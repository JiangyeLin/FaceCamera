package com.trc.facecamera.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static android.content.Context.CAMERA_SERVICE;

public class CameraUtil {
    private String TAG = getClass().getSimpleName();

    private TextureView textureView;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;

    private boolean saving = false;
    private Face[] currentFaces;
    private long lastSaveTime;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int mSensorOrientation;
    private Rect sensorRect;

    /**
     * 打开相机
     */
    @Deprecated
    public Camera getCamera() {
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
    @Deprecated
    public Camera getFrontCamera() {
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
    @Deprecated
    public Size getBestCameraSize(List<Size> sizes, int screenWidth, int screenHeight) {
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

    /**
     * 相机的坐标系与屏幕的坐标系不同，需要进行矩阵转换
     * <p>
     * camera2与camera1的坐标系也不一致，此方法适配camera2
     * <p>
     * camera1坐标系左上角为 -1000，-1000，总大小固定为2000*2000
     * camera2坐标系左上角为0，0，大小不固定，依据传感器实际情况,camera2不需要矩阵旋转及矩阵位移
     *
     * @param face
     * @param width        iamge的实际大小
     * @param height
     * @param sensorWidth  传感器矩阵大小
     * @param sensorHeight
     * @return
     */
    private RectF convertRect(Face face, int width, int height, int sensorWidth, int sensorHeight) {
        RectF mRect = new RectF();
        Matrix matrix = new Matrix();

        boolean mirror = false;
        matrix.setScale(mirror ? -1 : 1, 1);
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        //matrix.postRotate(getOrientation(mSensorOrientation));
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        matrix.postScale((width * 1.0f / sensorWidth), height * 1.0f / sensorHeight);
        //matrix.postTranslate(width / 2f, height / 2f);

        mRect.set(face.getBounds());
        matrix.mapRect(mRect);

        return mRect;
    }

    /**
     * 人脸检测初始化
     */
    public void initCamera() {
        Activity activity = CameraHelper.getInstance().getActivity();
        textureView = new TextureView(activity);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(1, 1);
        activity.addContentView(textureView, layoutParams);

        //获取拍照数据流
        imageReader = ImageReader.newInstance(CameraHelper.getInstance().getPreviewWidth(), CameraHelper.getInstance().getPreviewHeight(), ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                //该行代码与image.close必须调用
                Image image = reader.acquireNextImage();

                if (saving) {
                    Log.d(TAG, "onImageAvailable: 保存照片");
                    String fileName = String.valueOf(System.currentTimeMillis());

                    File file = new File(CameraHelper.getInstance().getActivity().getExternalCacheDir(), fileName + ".jpg");

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(file);

                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);

                        //遍历裁剪人脸图片
                        FileOutputStream fileOutputStreamTemp;

                        File fileTemp;
                        for (Face face : currentFaces) {
                            fileTemp = new File(CameraHelper.getInstance().getActivity().getExternalCacheDir(), fileName + "_" + face.hashCode() + ".jpg");
                            fileOutputStreamTemp = new FileOutputStream(fileTemp, false);

                            //换算出人脸区域坐标
                            //Rect origin = face.getBounds();
                            //String format = String.format("左=%1$d  右= %2$d 上=%3$d  下=%4$d", origin.left, origin.right, origin.top, origin.bottom);
                            //Log.d(TAG, "onImageAvailable: 原矩阵 " + format);
                            RectF rectF = convertRect(face, CameraHelper.getInstance().getPreviewWidth(), CameraHelper.getInstance().getPreviewHeight(), sensorRect.width(), sensorRect.height());

                            //format = String.format("左=%1$d  右= %2$d 上=%3$d  下=%4$d", (int) rectF.left, (int) rectF.right, (int) rectF.top, (int) rectF.bottom);
                            //Log.d(TAG, "onImageAvailable: 新矩阵  " + format);

                            //裁剪bitmap
                            Bitmap bitmap1 = Bitmap.createBitmap(bitmap, (int) rectF.left, (int) rectF.top, (int) rectF.width(), (int) rectF.height());
                            //输出人脸部分bitmap
                            bitmap1.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStreamTemp);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        image.close();
                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    saving = false;
                } else {
                    image.close();
                }
            }
        }, null);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable: ");
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged: ");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "onSurfaceTextureDestroyed: ");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) CameraHelper.getInstance().getActivity().getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                Log.d(TAG, "camera2支持程度: " + cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));

                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                for (android.util.Size outputSize : map.getOutputSizes(SurfaceTexture.class)) {
                    //输出到textureview时支持的size
                    Log.d(TAG, "openCamera: " + outputSize.toString());
                }

                // 使用前置摄像头。
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    //前置摄像头
                    mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    //fuck 成像矩阵坐标系与camera1不一致
                    sensorRect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    String format = String.format("左=%1$d  右= %2$d 上=%3$d  下=%4$d", sensorRect.left, sensorRect.right, sensorRect.top, sensorRect.bottom);

                    Log.d(TAG, "openCamera: 成像矩阵大小" + format);

                    Log.d(TAG, "openCamera: 人脸支持级别" + Arrays.toString(cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)));
                    Log.d(TAG, "openCamera: 最高人脸检测数量" + cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT));

                    cameraManager.openCamera(cameraId, stateCallback, null);
                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 摄像头 状态变更 监听,
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            Log.d(TAG, "onOpened: ");
            mCameraDevice = cameraDevice;
            createCameraPreviewSession(cameraDevice);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            Log.d(TAG, "onDisconnected: ");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            Log.d(TAG, "onError: " + error);
        }
    };

    /**
     * 为相机预览创建新的CameraCaptureSession
     */
    private void createCameraPreviewSession(CameraDevice cameraDevice) {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();

            texture.setDefaultBufferSize(CameraHelper.getInstance().getPreviewWidth(), CameraHelper.getInstance().getPreviewHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            //设置了一个具有输出Surface的CaptureRequest.Builder
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //captureRequestBuilder.addTarget(surface);  //显示预览画面
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Orientation
            /*int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));*/

            //设置人脸检测
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE);

            //创建一个CameraCaptureSession来进行相机预览。
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "onConfigured: ");
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Finally, we start displaying the camera preview.
                                CaptureRequest mPreviewRequest = captureRequestBuilder.build();
                                cameraCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, " onConfigureFailed 开启预览失败");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, " CameraAccessException 开启预览失败");
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            if (faces != null && faces.length > 0) {

                if (System.currentTimeMillis() - lastSaveTime < CameraHelper.getInstance().getShotInterval() || saving) {
                    return;
                }
                lastSaveTime = System.currentTimeMillis();
                saving = true;
                currentFaces = faces;
                Log.d(TAG, "onCaptureCompleted: 检测到人脸");
            } else {
                saving = false;
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.d(TAG, "onCaptureFailed: " + failure);
        }
    };

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    public void release() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
}
