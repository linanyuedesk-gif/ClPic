package com.cl.pic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PicPrefs";
    private static final String KEY_URI = "last_image_uri";

    private ImageView imageView;
    private LinearLayout configPanel;
    private EditText etUrl;
    private ProgressBar progressBar;
    
    private GestureDetector gestureDetector;
    private float lastTouchY;
    private boolean isTwoFingerDrag = false;
    
    // Geometry State
    private int screenWidth;
    private int screenHeight;
    private float currentImageHeight;
    private Matrix currentMatrix = new Matrix();

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openFilePicker();
                else Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show();
            });

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) { e.printStackTrace(); }
                        saveAndLoad(uri.toString());
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();
        
        setContentView(R.layout.activity_main);
        
        imageView = findViewById(R.id.imageView);
        configPanel = findViewById(R.id.configPanel);
        etUrl = findViewById(R.id.etUrl);
        progressBar = findViewById(R.id.loading);
        Button btnLocal = findViewById(R.id.btnSelectLocal);
        Button btnUrl = findViewById(R.id.btnLoadUrl);
        
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        setupGestures();

        btnLocal.setOnClickListener(v -> checkPermission());
        btnUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if(!url.isEmpty()) saveAndLoad(url);
        });

        // Load Last Image
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String last = prefs.getString(KEY_URI, null);
        if (last != null) loadImage(last);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
    
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                toggleConfig();
                return true;
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        isTwoFingerDrag = true;
                        lastTouchY = (event.getY(0) + event.getY(1)) / 2;
                    } else {
                        isTwoFingerDrag = false;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isTwoFingerDrag && event.getPointerCount() == 2) {
                        float avgY = (event.getY(0) + event.getY(1)) / 2;
                        float dy = avgY - lastTouchY;
                        updatePosition(dy);
                        lastTouchY = avgY;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerCount() < 2) isTwoFingerDrag = false;
                    break;
            }
            return true;
        });
    }

    private void updatePosition(float dy) {
        float[] values = new float[9];
        currentMatrix.getValues(values);
        float transY = values[Matrix.MTRANS_Y];
        
        float newTransY = transY + dy;
        float minTransY, maxTransY;

        if (currentImageHeight <= screenHeight) {
            // Image is smaller than screen, keep it centered
            float offset = (screenHeight - currentImageHeight) / 2f;
            minTransY = offset;
            maxTransY = offset;
        } else {
            // Image is taller than screen
            // Top align: 0
            // Bottom align: (screenHeight - currentImageHeight)
            minTransY = screenHeight - currentImageHeight;
            maxTransY = 0;
        }

        // Clamp
        if (newTransY > maxTransY) newTransY = maxTransY;
        if (newTransY < minTransY) newTransY = minTransY;

        values[Matrix.MTRANS_Y] = newTransY;
        currentMatrix.setValues(values);
        imageView.setImageMatrix(currentMatrix);
    }

    private void toggleConfig() {
        if (configPanel.getVisibility() == View.VISIBLE) {
            configPanel.setVisibility(View.GONE);
            hideSystemUI();
        } else {
            configPanel.setVisibility(View.VISIBLE);
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                openFilePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                openFilePicker();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        filePickerLauncher.launch(intent);
    }

    private void saveAndLoad(String uri) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_URI, uri).apply();
        loadImage(uri);
        configPanel.setVisibility(View.GONE);
        hideSystemUI();
    }

    private void loadImage(String uri) {
        progressBar.setVisibility(View.VISIBLE);
        Glide.with(this).asBitmap().load(uri).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                progressBar.setVisibility(View.GONE);
                renderImage(resource);
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {}
            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, R.string.msg_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderImage(Bitmap bitmap) {
        int imgW = bitmap.getWidth();
        int imgH = bitmap.getHeight();
        
        // Update dimensions in case screen rotated or changed
        screenWidth = imageView.getWidth();
        screenHeight = imageView.getHeight();
        
        if (screenWidth == 0) screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (screenHeight == 0) screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Scale to fit width
        float scale = (float) screenWidth / imgW;
        currentMatrix.reset();
        currentMatrix.setScale(scale, scale);
        
        currentImageHeight = imgH * scale;
        
        // Reset to top or center initially
        float transY = 0;
        if (currentImageHeight < screenHeight) {
            transY = (screenHeight - currentImageHeight) / 2f;
        } else {
            transY = 0; // Top align
        }
        currentMatrix.postTranslate(0, transY);
        
        imageView.setImageMatrix(currentMatrix);
        imageView.setImageBitmap(bitmap);
    }
}