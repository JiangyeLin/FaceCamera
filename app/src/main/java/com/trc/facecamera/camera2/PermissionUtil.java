package com.trc.facecamera.camera2;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class PermissionUtil {
    public static void requestPermission(FragmentActivity activity, Callback callback, String... permissions) {
        forceRequestPermission(null, activity, callback, permissions);
    }

    public static void forceRequestPermission(String tips, FragmentActivity activity, Callback callback, String... permissions) {
        for (String permission : permissions) {
            if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(activity, permission)) {
                PermissionFragment fragment = new PermissionFragment();
                fragment.setPermissions(callback, permissions);
                fragment.setTips(tips);
                activity.getSupportFragmentManager().beginTransaction().add(Window.ID_ANDROID_CONTENT, fragment).commit();
                return;
            }
        }
        callback.onPermissionGranted();
    }


    public interface Callback {
        void onPermissionGranted();

        void onPermissionDenied(List<String> permissions);
    }

    public static class PermissionFragment extends Fragment {
        private String[] permissions;
        private Callback callback;
        private String tips;
        private AlertDialog alertDialog;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            requestPermissions(permissions, 0);
        }

        public void setPermissions(Callback callback, String... permissions) {
            this.permissions = permissions;
            this.callback = callback;
        }

        View invisiableView;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            if (invisiableView == null) {
                invisiableView = new View(getContext()) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        setMeasuredDimension(0, 0);
                    }
                };
            }
            return invisiableView;
        }


        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            List<String> deniedList = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    deniedList.add(permissions[i]);
                    Toast.makeText(getContext(), "权限申请失败：" + permissions[i], Toast.LENGTH_LONG).show();
                }
            }
            if (deniedList.size() == 0) {
                callback.onPermissionGranted();
                getFragmentManager().beginTransaction().remove(this).commit();
            } else {
                if (tips == null) {
                    callback.onPermissionDenied(deniedList);
                } else {
                    boolean hasPermissionCanRequest = false;
                    for (String deniedPermission : deniedList) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), deniedPermission)) {
                            hasPermissionCanRequest = true;
                            break;
                        }
                    }
                    if (hasPermissionCanRequest) {
                        requestPermissions(deniedList.toArray(new String[deniedList.size()]), 0);
                    } else {
                        if (null == alertDialog) {
                            alertDialog = new AlertDialog.Builder(getContext())
                                    .setTitle("权限申请")
                                    .setMessage(tips)
                                    .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent();
                                            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
                                            intent.setData(uri);
                                            startActivity(intent);
                                            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                                                startActivity(intent);
                                            } else {
                                                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
                                            }
                                        }
                                    })
                                    .setCancelable(false)
                                    .create();
                        }
                        alertDialog.show();

                    }
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (null != alertDialog) {
                boolean isAllGranted = true;
                for (String permission : permissions) {
                    if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(getContext(), permission)) {
                        isAllGranted = false;
                        break;
                    }
                }
                if (isAllGranted) {
                    callback.onPermissionGranted();
                    getFragmentManager().beginTransaction().remove(this).commit();
                } else {
                    alertDialog.show();
                }
            }

        }

        public void setTips(String tips) {
            this.tips = tips;
        }
    }

}
