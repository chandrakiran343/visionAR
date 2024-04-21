package com.chunky.visionar.utilities;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

public class PermissionHelper {
        private Activity activity;

        public PermissionHelper(Activity activity){
                this.activity = activity;
        }


        public boolean isCameraPermitted(){
                return ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }



}
