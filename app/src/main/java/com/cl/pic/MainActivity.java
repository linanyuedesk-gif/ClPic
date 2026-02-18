package com.cl.pic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PicPrefs";
    private static final String KEY_URI = "last_image_uri";
    private static final String TAG = "CarPicViewer";

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
    private float currentImageHeight = 0f;
    private Matrix currentMatrix = new Matrix();

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            // Persist permission so we can load it later
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            Log.w(TAG, "Failed to take persistable permission: " + e.getMessage());
                        }
                        saveAndLoad(uri.toString());
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Fullscreen and Keep Screen On (Important for Car/Digital Frame usage)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_main);
        
        imageView = findViewById(R.id.imageView);
        configPanel = findViewById(R.id.configPanel);
        etUrl = findViewById(R.id.etUrl);
        progressBar = findViewById(R.id.loading);
        Button btnLocal = findViewById(R.id.btnSelectLocal);
        Button btnUrl = findViewById(R.id.btnLoadUrl);
        
        updateScreenDimensions();

        setupGestures();
        hideSystemUI();

        // No runtime permissions needed for ACTION_OPEN_DOCUMENT
        btnLocal.setOnClickListener(v -> openFilePicker());
        
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
        updateScreenDimensions();
    }

    private void updateScreenDimensions() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
    
    private void hideSystemUI() {
        // Use legacy flags for wider compatibility on car head units, 
        // while also attempting modern API for newer Android versions.
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        
        getWindow().getDecorView().setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final Window window = getWindow();
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
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
        // Prevent matrix operations if image isn't loaded or invalid dimensions
        if (currentImageHeight <= 0 || screenHeight <= 0) return;

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

    private void loadImage(String uriString) {
        if (uriString == null || uriString.isEmpty()) return;
        
        progressBar.setVisibility(View.VISIBLE);
        
        // Setup options: Timeout and Size limit to prevent OOM
        // Reduced to 2048 to prevent crashes on car head units with limited VRAM/RAM
        RequestOptions options = new RequestOptions()
                .timeout(30000) 
                .override(2048, 2048); 

        // Use Uri.parse to handle both content:// and http:// correctly
        Uri uri = Uri.parse(uriString);

        Glide.with(this)
            .asBitmap()
            .load(uri)
            .apply(options)
            .listener(new RequestListener<Bitmap>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to load image: " + uriString, e);
                    Toast.makeText(MainActivity.this, R.string.msg_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }

                @Override
                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                    progressBar.setVisibility(View.GONE);
                    // Manually render logic to setup Matrix
                    if (resource != null) {
                        renderImage(resource);
                    }
                    // Return true to tell Glide "I handled setting the resource", preventing it 
                    // from resetting the ImageView's state/matrix with a standard setImageBitmap call
                    return true; 
                }
            })
            .into(imageView);
    }

    private void renderImage(Bitmap bitmap) {
        int imgW = bitmap.getWidth();
        int imgH = bitmap.getHeight();
        
        if (imgW == 0 || imgH == 0) return;

        // Ensure we have valid screen dimensions
        if (screenWidth == 0 || screenHeight == 0) {
            updateScreenDimensions();
        }

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