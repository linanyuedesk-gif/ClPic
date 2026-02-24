package com.cl.pic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import android.view.ScaleGestureDetector;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PicPrefs";
    private static final String KEY_URI_PUBLIC = "last_image_uri_public";
    private static final String KEY_URI_PRIVATE = "last_image_uri_private";
    private static final String KEY_HISTORY_PUBLIC = "history_public";
    private static final String KEY_HISTORY_PRIVATE = "history_private";
    private static final String TAG = "CarPicViewer";
    private static final int MAX_HISTORY = 10;

    private FrameLayout rootLayout;
    private ImageView imageView;
    private View blackOverlay;
    private LinearLayout configPanel;
    private LinearLayout historyContainer;
    private EditText etUrl;
    private ProgressBar progressBar;
    
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    
    // Mode State
    private float mode1Level = 0.8f; // Default Bright
    private float mode2Level = 0.2f; // Default Dark
    private boolean isMode2 = false;
    private float startLevel; // For gesture calculation
    private int touchSlop;
    private boolean isAdjustingMode = false;
    private boolean isZoomingOrPanning = false;
    private float startX, startY;
    

    
    // Two-finger triple tap state
    private long lastTwoFingerDownTime = 0;
    private int twoFingerTapCount = 0;
    private static final long MULTI_TAP_TIMEOUT = 500;

    // Playlist State
    private List<String> playlist = new ArrayList<>();
    private int currentPlaylistIndex = -1;
    private boolean isScanning = false;
    private boolean isPrivateMode = false;
    
    // Geometry State
    private int screenWidth;
    private int screenHeight;
    private Matrix currentMatrix = new Matrix();
    private String currentUriString;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
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
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_main);
        
        rootLayout = findViewById(R.id.rootLayout);
        imageView = findViewById(R.id.imageView);
        blackOverlay = findViewById(R.id.blackOverlay);
        configPanel = findViewById(R.id.configPanel);
        historyContainer = findViewById(R.id.historyContainer);
        etUrl = findViewById(R.id.etUrl);
        progressBar = findViewById(R.id.loading);
        
        Button btnLocal = findViewById(R.id.btnSelectLocal);
        Button btnUrl = findViewById(R.id.btnLoadUrl);
        Button btnScan = findViewById(R.id.btnScanDevice);
        
        updateScreenDimensions();
        
        touchSlop = 24; // Hardcoded to avoid context issues
        
        // Load saved levels
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mode1Level = prefs.getFloat("mode1_level", 0.8f);
        mode2Level = prefs.getFloat("mode2_level", 0.2f);
        isMode2 = prefs.getBoolean("is_mode2", false);
        
        // Apply initial brightness
        applyBrightness(isMode2 ? mode2Level : mode1Level);
        
        setupGestures();
        hideSystemUI();

        btnLocal.setOnClickListener(v -> openFilePicker());
        
        btnScan.setOnClickListener(v -> startScan());

        btnUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if(!url.isEmpty()) saveAndLoad(url);
        });

        // Always start in public mode
        String last = prefs.getString(KEY_URI_PUBLIC, null);
        if (last != null) {
            currentUriString = last;
            loadImage(last);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        updateScreenDimensions();
    }

    private void updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
    
    private void hideSystemUI() {
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

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (configPanel.getVisibility() == View.VISIBLE) {
                    toggleConfig(); // Close config if open
                } else {
                    toggleMode(); // Toggle Mode 1/Mode 2
                }
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                currentMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                checkBoundsAndFix();
                imageView.setImageMatrix(currentMatrix);
                return true;
            }
        });

        rootLayout.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            
            int action = event.getActionMasked();
            int pointerCount = event.getPointerCount();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    isAdjustingMode = false;
                    isZoomingOrPanning = false;
                    break;
                    
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (pointerCount == 2) {
                        long now = System.currentTimeMillis();
                        if (now - lastTwoFingerDownTime < MULTI_TAP_TIMEOUT) {
                            twoFingerTapCount++;
                        } else {
                            twoFingerTapCount = 1;
                        }
                        lastTwoFingerDownTime = now;
                        
                        if (twoFingerTapCount == 3) {
                            togglePrivateMode();
                            twoFingerTapCount = 0;
                        }
                        
                        isZoomingOrPanning = true;
                        isAdjustingMode = false; // Cancel single finger
                        startX = (event.getX(0) + event.getX(1)) / 2;
                        startY = (event.getY(0) + event.getY(1)) / 2;
                    }
                    break;
                
                case MotionEvent.ACTION_POINTER_UP:
                    // If we were zooming, keep isZoomingOrPanning true so we don't switch to brightness
                    if (pointerCount - 1 < 2) {
                        // Dropping to 1 finger
                        // Update startX/Y to prevent jump if we were panning?
                        // But we are ignoring moves anyway if isZoomingOrPanning is true.
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (scaleGestureDetector.isInProgress()) {
                        isZoomingOrPanning = true;
                        isAdjustingMode = false;
                    } 
                    
                    if (pointerCount == 1) {
                        if (isZoomingOrPanning) {
                            // Ignore single finger moves if we were/are zooming
                            break; 
                        }
                        
                        if (!isAdjustingMode) {
                            float dx = event.getX() - startX;
                            float dy = event.getY() - startY;
                            if (Math.sqrt(dx * dx + dy * dy) > touchSlop) {
                                isAdjustingMode = true;
                                if (Math.abs(dx) > Math.abs(dy)) {
                                    // Horizontal -> Mode 1
                                    isMode2 = false;
                                    startLevel = mode1Level;
                                    startX = event.getX(); // Reset reference
                                    showModeToast("Mode 1");
                                    applyBrightness(mode1Level); // Switch immediately
                                } else {
                                    // Vertical -> Mode 2
                                    isMode2 = true;
                                    startLevel = mode2Level;
                                    startY = event.getY(); // Reset reference
                                    showModeToast("Mode 2");
                                    applyBrightness(mode2Level); // Switch immediately
                                }
                            }
                        }
                        
                        if (isAdjustingMode) {
                            if (!isMode2) {
                                // Mode 1 (Horizontal)
                                float deltaX = event.getX() - startX;
                                float change = deltaX / rootLayout.getWidth();
                                mode1Level = clamp(startLevel + change);
                                applyBrightness(mode1Level);
                            } else {
                                // Mode 2 (Vertical)
                                // Up is negative deltaY, but we want Up -> Brighter (Higher Level)
                                // So we subtract deltaY (or use -deltaY)
                                float deltaY = startY - event.getY(); 
                                float change = deltaY / rootLayout.getHeight();
                                mode2Level = clamp(startLevel + change);
                                applyBrightness(mode2Level);
                            }
                        }
                    } else if (pointerCount == 2) {
                        isZoomingOrPanning = true;
                        isAdjustingMode = false;
                        // Pan
                        float currX = (event.getX(0) + event.getX(1)) / 2;
                        float currY = (event.getY(0) + event.getY(1)) / 2;
                        float dx = currX - startX;
                        float dy = currY - startY;
                        
                        currentMatrix.postTranslate(dx, dy);
                        checkBoundsAndFix();
                        imageView.setImageMatrix(currentMatrix);
                        
                        startX = currX;
                        startY = currY;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isAdjustingMode = false;
                    isZoomingOrPanning = false;
                    savePosition();
                    break;
            }
            return true;
        });
    }

    private float clamp(float val) {
        return Math.max(0.0f, Math.min(1.0f, val));
    }

    private void applyBrightness(float level) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float screenBrightness;
        float overlayAlpha;
        
        // 0.0 -> 0.5: Overlay 1.0 -> 0.0 (Screen Min)
        // 0.5 -> 1.0: Screen Min -> Max (Overlay 0.0)
        
        if (level < 0.5f) {
            screenBrightness = 0.01f;
            // Map 0.0-0.5 to Alpha 1.0-0.0
            overlayAlpha = 1.0f - (level * 2.0f);
        } else {
            overlayAlpha = 0.0f;
            // Map 0.5-1.0 to Brightness 0.01-1.0
            screenBrightness = 0.01f + (level - 0.5f) * 2.0f * 0.99f;
        }
        
        lp.screenBrightness = screenBrightness;
        getWindow().setAttributes(lp);
        
        blackOverlay.setVisibility(overlayAlpha > 0 ? View.VISIBLE : View.GONE);
        blackOverlay.setAlpha(overlayAlpha);
    }

    private void toggleMode() {
        isMode2 = !isMode2;
        applyBrightness(isMode2 ? mode2Level : mode1Level);
        showModeToast(isMode2 ? "Mode 2" : "Mode 1");
        
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("is_mode2", isMode2).apply();
    }
    
    private void showModeToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    
    private void checkBoundsAndFix() {
        if (imageView.getDrawable() == null) return;
        
        float[] values = new float[9];
        currentMatrix.getValues(values);
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        
        int imgW = imageView.getDrawable().getIntrinsicWidth();
        int imgH = imageView.getDrawable().getIntrinsicHeight();
        float displayW = imgW * scaleX;
        float displayH = imgH * scaleY;
        
        // 1. Min Scale Check: Width cannot be less than screen width
        if (displayW < screenWidth) {
            float fixScale = screenWidth / (float)imgW;
            currentMatrix.setScale(fixScale, fixScale);
            // Re-get values after reset
            currentMatrix.getValues(values);
            transX = values[Matrix.MTRANS_X];
            transY = values[Matrix.MTRANS_Y];
            scaleX = values[Matrix.MSCALE_X];
            scaleY = values[Matrix.MSCALE_Y];
            displayW = imgW * scaleX;
            displayH = imgH * scaleY;
        }
        
        // 2. Pan Bounds Check
        float minTransX, maxTransX, minTransY, maxTransY;
        
        if (displayW <= screenWidth) {
            minTransX = 0;
            maxTransX = 0; // Center or stick to left? Usually center if smaller, but here we force min width = screen width
            // If strictly equal, both 0.
        } else {
            minTransX = screenWidth - displayW;
            maxTransX = 0;
        }
        
        if (displayH <= screenHeight) {
            // Center vertically
            float offset = (screenHeight - displayH) / 2f;
            minTransY = offset;
            maxTransY = offset;
        } else {
            minTransY = screenHeight - displayH;
            maxTransY = 0;
        }
        
        float newTransX = transX;
        float newTransY = transY;
        
        if (newTransX > maxTransX) newTransX = maxTransX;
        if (newTransX < minTransX) newTransX = minTransX;
        
        if (newTransY > maxTransY) newTransY = maxTransY;
        if (newTransY < minTransY) newTransY = minTransY;
        
        values[Matrix.MTRANS_X] = newTransX;
        values[Matrix.MTRANS_Y] = newTransY;
        currentMatrix.setValues(values);
    }

    private void togglePrivateMode() {
        isPrivateMode = !isPrivateMode;
        String modeName = isPrivateMode ? "Private Mode" : "Public Mode";
        Toast.makeText(this, "Switched to " + modeName, Toast.LENGTH_SHORT).show();
        
        // Clear current playlist and reload history for the new mode
        playlist.clear();
        currentPlaylistIndex = -1;
        
        // Load history
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = isPrivateMode ? KEY_HISTORY_PRIVATE : KEY_HISTORY_PUBLIC;
        String jsonString = prefs.getString(key, "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            if (jsonArray.length() > 0) {
                String last = jsonArray.getString(0);
                saveAndLoad(last); // This will reload playlist from history
            } else {
                // No history in this mode
                imageView.setImageDrawable(null);
                currentUriString = null;
                refreshHistoryUI();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    private void toggleConfig() {
        if (configPanel.getVisibility() == View.VISIBLE) {
            configPanel.setVisibility(View.GONE);
            hideSystemUI();
        } else {
            // Ensure black overlay is off when opening config
            blackOverlay.setVisibility(View.GONE);
            refreshHistoryUI();
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
        // Update playlist index if loading manually
        if (playlist.contains(uri)) {
            currentPlaylistIndex = playlist.indexOf(uri);
        } else {
            // Add to playlist if not present
            playlist.add(0, uri);
            currentPlaylistIndex = 0;
        }

        // Save current URI
        currentUriString = uri;
        String key = isPrivateMode ? KEY_URI_PRIVATE : KEY_URI_PUBLIC;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, uri)
                .apply();
        
        // Add to history
        addToHistory(uri);
        
        loadImage(uri);
        configPanel.setVisibility(View.GONE);
        hideSystemUI();
    }

    private void addToHistory(String uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = isPrivateMode ? KEY_HISTORY_PRIVATE : KEY_HISTORY_PUBLIC;
        String jsonString = prefs.getString(key, "[]");
        List<String> list = new ArrayList<>();
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                String item = jsonArray.getString(i);
                if (!item.equals(uri)) { // Remove duplicates
                    list.add(item);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Add to top
        list.add(0, uri);
        
        // Limit size
        if (list.size() > MAX_HISTORY) {
            list = list.subList(0, MAX_HISTORY);
        }

        // Save back
        JSONArray newArray = new JSONArray(list);
        prefs.edit().putString(key, newArray.toString()).apply();
    }

    private void refreshHistoryUI() {
        historyContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = isPrivateMode ? KEY_HISTORY_PRIVATE : KEY_HISTORY_PUBLIC;
        String jsonString = prefs.getString(key, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            if (jsonArray.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText(R.string.history_empty);
                empty.setTextColor(Color.GRAY);
                empty.setPadding(0, 20, 0, 20);
                empty.setGravity(Gravity.CENTER);
                historyContainer.addView(empty);
                return;
            }

            for (int i = 0; i < jsonArray.length(); i++) {
                final String uri = jsonArray.getString(i);
                
                // Format display text (filename or short url)
                String displayText = uri;
                try {
                    Uri u = Uri.parse(uri);
                    if (u.getScheme() != null && u.getScheme().startsWith("content")) {
                        // Try to get last segment for content URIs
                        String path = u.getLastPathSegment();
                        if (path != null) {
                            // Clean up standard Android file picker IDs like "image:1234"
                            int split = path.lastIndexOf(':');
                            if (split > -1) path = path.substring(split + 1);
                            displayText = "Local Image: ..." + path;
                        }
                    } 
                } catch (Exception ignored) {}

                TextView tv = new TextView(this);
                tv.setText(displayText);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(14f);
                tv.setPadding(16, 24, 16, 24);
                tv.setBackgroundResource(android.R.drawable.list_selector_background);
                tv.setMaxLines(1);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                
                tv.setOnClickListener(v -> saveAndLoad(uri));
                
                historyContainer.addView(tv);
                
                // Divider
                if (i < jsonArray.length() - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                    historyContainer.addView(divider);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadImage(String uriString) {
        if (uriString == null || uriString.isEmpty()) return;
        
        progressBar.setVisibility(View.VISIBLE);
        
        RequestOptions options = new RequestOptions()
                .timeout(30000) 
                .override(2048, 2048); 

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
                    if (resource != null) {
                        renderImage(resource);
                    }
                    return true; 
                }
            })
            .into(imageView);
    }

    private void renderImage(Bitmap bitmap) {
        int imgW = bitmap.getWidth();
        int imgH = bitmap.getHeight();
        
        if (imgW == 0 || imgH == 0) return;

        if (screenWidth == 0 || screenHeight == 0) {
            updateScreenDimensions();
        }

        // Initial scale: Fit Width
        float scale = (float) screenWidth / imgW;
        currentMatrix.reset();
        currentMatrix.setScale(scale, scale);
        
        // Center vertically if it fits, otherwise top aligned (handled by checkBoundsAndFix logic usually, but let's set initial pos)
        float displayH = imgH * scale;
        float transY = 0;
        if (displayH < screenHeight) {
            transY = (screenHeight - displayH) / 2f;
        }
        
        // Check for saved position
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = "pos_" + currentUriString;
        
        if (currentUriString != null && prefs.contains(key)) {
            try {
                float savedY = prefs.getFloat(key, 0f);
                transY = savedY;
                
                float savedX = 0f;
                if (prefs.contains("pos_x_" + currentUriString)) {
                    savedX = prefs.getFloat("pos_x_" + currentUriString, 0f);
                }
                
                float savedScale = 0f;
                if (prefs.contains("scale_" + currentUriString)) {
                    savedScale = prefs.getFloat("scale_" + currentUriString, 0f);
                }
                
                if (savedScale > 0) {
                    currentMatrix.setScale(savedScale, savedScale);
                }
                currentMatrix.postTranslate(savedX, transY);
                
            } catch (ClassCastException e) {
                currentMatrix.postTranslate(0, transY);
            }
        } else {
            currentMatrix.postTranslate(0, transY);
        }
        
        imageView.setImageMatrix(currentMatrix);
        imageView.setImageBitmap(bitmap);
        
        // Ensure bounds are valid (e.g. if screen rotated)
        checkBoundsAndFix();
        imageView.setImageMatrix(currentMatrix);
    }

    private void savePosition() {
        if (currentUriString == null) return;
        
        float[] values = new float[9];
        currentMatrix.getValues(values);
        float x = values[Matrix.MTRANS_X];
        float y = values[Matrix.MTRANS_Y];
        float scale = values[Matrix.MSCALE_X];
        
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("pos_" + currentUriString, y)
            .putFloat("pos_x_" + currentUriString, x)
            .putFloat("scale_" + currentUriString, scale)
            .apply();
            
        // Also save brightness levels
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("mode1_level", mode1Level)
            .putFloat("mode2_level", mode2Level)
            .putBoolean("is_mode2", isMode2)
            .apply();
    }

    private void startScan() {
        if (isScanning) return;
        isScanning = true;
        
        Toast.makeText(this, R.string.msg_scanning, Toast.LENGTH_SHORT).show();
        playlist.clear();
        currentPlaylistIndex = -1;
        
        new Thread(() -> {
            // Scan common directories
            File[] dirs = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                new File(Environment.getExternalStorageDirectory(), "ClPic") // Custom folder
            };
            
            for (File dir : dirs) {
                if (dir.exists()) {
                    scanDirectory(dir);
                }
            }
            
            // Sort playlist
            Collections.sort(playlist, String::compareToIgnoreCase);
            
            new Handler(Looper.getMainLooper()).post(() -> {
                isScanning = false;
                String msg = String.format(getString(R.string.msg_scan_complete), playlist.size());
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                
                if (!playlist.isEmpty()) {
                    // Load first image
                    currentPlaylistIndex = 0;
                    saveAndLoad(playlist.get(0));
                } else {
                    Toast.makeText(MainActivity.this, R.string.msg_no_images, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void scanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                    name.endsWith(".png") || name.endsWith(".bmp") || 
                    name.endsWith(".webp")) {
                    playlist.add(Uri.fromFile(file).toString());
                }
            }
        }
    }
}