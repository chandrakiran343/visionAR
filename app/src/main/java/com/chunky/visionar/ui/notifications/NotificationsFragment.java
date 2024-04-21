package com.chunky.visionar.ui.notifications;

import android.graphics.SurfaceTexture;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.chunky.visionar.R;
import com.chunky.visionar.databinding.FragmentNotificationsBinding;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsFragment extends Fragment {

    private static final int IMAGE_ANALYSIS_WIDTH = 1920;
    private static final int IMAGE_ANALYSIS_HEIGHT = 1080;

    private TextureView textureView;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textureView = view.findViewById(R.id.textureView);
    }

    @Override
    public void onResume() {
        super.onResume();
        startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCamera();
    }

    private void startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        ObjectDetectorOptions objectDetectorOptions =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableClassification()// Optional: Enable object classification
                        .build();

        objectDetector = ObjectDetection.getClient(objectDetectorOptions);

        ProcessCameraProvider cameraProvider = null;
        try {
            cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(IMAGE_ANALYSIS_WIDTH, IMAGE_ANALYSIS_HEIGHT))
                .build();

        preview.setSurfaceProvider(new Preview.SurfaceProvider() {
            @Override
            public void onSurfaceRequested(@NonNull SurfaceRequest request) {
                SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                if (surfaceTexture != null) {
                    Executor executor = ContextCompat.getMainExecutor(requireContext());
                    request.provideSurface(new Surface(surfaceTexture), executor, new Consumer<SurfaceRequest.Result>() {
                        @Override
                        public void accept(SurfaceRequest.Result result) {

                        }
                    });
                } else {
//                    request.handleError();
                }
            }
        });

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(IMAGE_ANALYSIS_WIDTH, IMAGE_ANALYSIS_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void  analyze(@NonNull ImageProxy image) {
                // Convert image to InputImage for ML Kit
                InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

                // Perform object detection
                objectDetector.process(inputImage)
                        .addOnSuccessListener(detectedObjects -> {
                            // Handle detected objects
                            processDetectedObjects(detectedObjects);
                        })
                        .addOnFailureListener(e -> {
                            e.printStackTrace();
                            // Handle failure
                        })
                        .addOnCompleteListener(result -> {
                            // Close the image when processing is done
                            image.close();
                        });
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void stopCamera() {
        cameraExecutor.shutdown();
        objectDetector.close();
    }

    private void processDetectedObjects(List<DetectedObject> detectedObjects) {
        for (DetectedObject detectedObject : detectedObjects) {
            List<DetectedObject.Label> labels = detectedObject.getLabels();
            for (DetectedObject.Label label : labels) {
                String objectName = label.getText();
                float confidence = label.getConfidence();
                // Handle the detected object
                TextView disp = (TextView) getView().findViewById(R.id.detectedObjectsTextView);
                disp.setText(objectName +" "+ confidence*100+"%");
                // For example, you can log it or update UI
                Log.d("ObjectDetection", "Detected Object: " + objectName + ", Confidence: " + confidence);
            }
        }
    }
}