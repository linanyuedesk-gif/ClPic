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

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PicPrefs";
    private static final String KEY_URI = "last_image_uri";
    private static final String KEY_HISTORY = "history_json";
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
    private float lastTouchY;
    private boolean isTwoFingerDrag = false;
    
    // Gesture State
    private float startX, startY;
    private float startBrightness;
    private boolean isBrightnessGesture = false;
    private boolean isSwipeGesture = false;
    private static final int SWIPE_THRESHOLD = 150;

    // Playlist State
    private List<String> playlist = new ArrayList<>();
    private int currentPlaylistIndex = -1;
    private boolean isScanning = false;
    
    // Geometry State
    private int screenWidth;
    private int screenHeight;
    private float currentImageHeight = 0f;
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
        setupGestures();
        hideSystemUI();

        btnLocal.setOnClickListener(v -> openFilePicker());
        
        btnScan.setOnClickListener(v -> startScan());

        btnUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if(!url.isEmpty()) saveAndLoad(url);
        });

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String last = prefs.getString(KEY_URI, null);
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
                    toggleBlackScreen(); // Toggle black screen otherwise
                }
                return true;
            }
        });

        // Attach listener to rootLayout so it works even if ImageView is empty or hidden
        rootLayout.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            
            int action = event.getActionMasked();
            int pointerCount = event.getPointerCount();

            // Only allow interactions if screen is NOT blacked out (except for brightness gesture to wake up)
            // Actually, we want brightness control even if blacked out to restore it.
            
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    if (pointerCount == 1) {
                        isBrightnessGesture = true;
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        startBrightness = lp.screenBrightness;
                        if (startBrightness < 0) {
                            try {
                                startBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
                            } catch (Settings.SettingNotFoundException e) {
                                startBrightness = 0.5f;
                            }
                        }
                    }
                    break;
                    
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (pointerCount == 2) {
                        isTwoFingerDrag = true;
                        lastTouchY = (event.getY(0) + event.getY(1)) / 2;
                        isBrightnessGesture = false; // Cancel brightness if 2 fingers
                    } else if (pointerCount == 3) {
                        isSwipeGesture = true;
                        startX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3;
                        isTwoFingerDrag = false;
                        isBrightnessGesture = false;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isBrightnessGesture && pointerCount == 1) {
                        float deltaX = event.getX() - startX;
                        // Threshold to avoid accidental brightness change on tap
                        if (Math.abs(deltaX) > 50) {
                            updateBrightness(deltaX);
                        }
                    } else if (isTwoFingerDrag && pointerCount == 2) {
                        float avgY = (event.getY(0) + event.getY(1)) / 2;
                        float dy = avgY - lastTouchY;
                        updatePosition(dy);
                        lastTouchY = avgY;
                    } else if (isSwipeGesture && pointerCount == 3) {
                        float avgX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3;
                        float deltaX = avgX - startX;
                        if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                            if (deltaX > 0) {
                                switchImage(-1); // Prev
                            } else {
                                switchImage(1); // Next
                            }
                            isSwipeGesture = false; // Consume swipe
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    if (pointerCount == 2) { // One finger lifted from 2
                        if (isTwoFingerDrag) {
                            savePosition();
                            isTwoFingerDrag = false;
                        }
                    } else if (pointerCount == 3) { // One finger lifted from 3
                         isSwipeGesture = false;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isTwoFingerDrag) {
                        savePosition();
                        isTwoFingerDrag = false;
                    }
                    isBrightnessGesture = false;
                    isSwipeGesture = false;
                    break;
            }
            return true;
        });
    }

    private void updateBrightness(float deltaX) {
        float width = rootLayout.getWidth();
        if (width <= 0) return;
        
        float change = deltaX / width; // Sensitivity: full width = 1.0 change
        float newBrightness = startBrightness + change;
        
        // Logic: 
        // 0.01 to 1.0 is normal brightness.
        // < 0.01 is "ultra low" simulated by black overlay.
        // Let's map -0.5 to 1.0 range.
        // -0.5 to 0.0: Overlay Alpha 1.0 to 0.0, Screen Brightness 0.01
        // 0.0 to 1.0: Overlay Alpha 0.0, Screen Brightness 0.01 to 1.0
        
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        
        if (newBrightness < 0.01f) {
            lp.screenBrightness = 0.01f;
            float alpha = Math.min(0.95f, Math.abs(newBrightness) * 2); // Max alpha 0.95
            blackOverlay.setAlpha(alpha);
            blackOverlay.setVisibility(View.VISIBLE);
        } else {
            if (newBrightness > 1.0f) newBrightness = 1.0f;
            lp.screenBrightness = newBrightness;
            blackOverlay.setVisibility(View.GONE);
            blackOverlay.setAlpha(0f);
        }
        
        getWindow().setAttributes(lp);
    }

    private void switchImage(int direction) {
        if (playlist.isEmpty()) {
            Toast.makeText(this, R.string.history_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentPlaylistIndex += direction;
        if (currentPlaylistIndex < 0) currentPlaylistIndex = playlist.size() - 1;
        if (currentPlaylistIndex >= playlist.size()) currentPlaylistIndex = 0;
        
        String nextUri = playlist.get(currentPlaylistIndex);
        saveAndLoad(nextUri);
        Toast.makeText(this, (currentPlaylistIndex + 1) + "/" + playlist.size(), Toast.LENGTH_SHORT).show();
    }

    private void startScan() {
        if (isScanning) return;
        isScanning = true;
        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.msg_scanning, Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            List<String> foundImages = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            
            // Scan common directories
            File[] roots = {
                Environment.getExternalStorageDirectory(),
                new File("/storage"),
                new File("/mnt")
            };
            
            for (File root : roots) {
                scanDirectory(root, foundImages, visited);
            }
            
            // Sort by name
            Collections.sort(foundImages, String::compareToIgnoreCase);
            
            runOnUiThread(() -> {
                isScanning = false;
                progressBar.setVisibility(View.GONE);
                if (!foundImages.isEmpty()) {
                    playlist.clear();
                    playlist.addAll(foundImages);
                    currentPlaylistIndex = 0;
                    Toast.makeText(this, String.format(getString(R.string.msg_scan_complete), foundImages.size()), Toast.LENGTH_LONG).show();
                    // Auto load first found
                    saveAndLoad(playlist.get(0));
                } else {
                    Toast.makeText(this, R.string.msg_no_images, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void scanDirectory(File dir, List<String> results, Set<String> visited) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        try {
            // Avoid loops and system dirs
            if (visited.contains(dir.getCanonicalPath())) return;
            visited.add(dir.getCanonicalPath());
            if (dir.getName().startsWith(".")) return; // Skip hidden
            
            File[] files = dir.listFiles();
            if (files == null) return;
            
            for (File f : files) {
                if (f.isDirectory()) {
                    scanDirectory(f, results, visited);
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                        results.add(Uri.fromFile(f).toString());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning " + dir, e);
        }
    }

    private void updatePosition(float dy) {
        if (currentImageHeight <= 0 || screenHeight <= 0) return;

        float[] values = new float[9];
        currentMatrix.getValues(values);
        float transY = values[Matrix.MTRANS_Y];
        
        float newTransY = transY + dy;
        float minTransY, maxTransY;

        if (currentImageHeight <= screenHeight) {
            float offset = (screenHeight - currentImageHeight) / 2f;
            minTransY = offset;
            maxTransY = offset;
        } else {
            minTransY = screenHeight - currentImageHeight;
            maxTransY = 0;
        }

        if (newTransY > maxTransY) newTransY = maxTransY;
        if (newTransY < minTransY) newTransY = minTransY;

        values[Matrix.MTRANS_Y] = newTransY;
        currentMatrix.setValues(values);
        imageView.setImageMatrix(currentMatrix);
    }

    private void savePosition() {
        if (currentUriString == null) return;
        
        float[] values = new float[9];
        currentMatrix.getValues(values);
        float y = values[Matrix.MTRANS_Y];
        
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("pos_" + currentUriString, y)
            .apply();
    }

    private void toggleBlackScreen() {
        if (blackOverlay.getVisibility() == View.VISIBLE) {
            blackOverlay.setVisibility(View.GONE);
        } else {
            blackOverlay.setVisibility(View.VISIBLE);
        }
        hideSystemUI();
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
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_URI, uri)
                .apply();
        
        // Add to history
        addToHistory(uri);
        
        loadImage(uri);
        configPanel.setVisibility(View.GONE);
        hideSystemUI();
    }

    private void addToHistory(String uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_HISTORY, "[]");
        List<String> list = new ArrayList<>();
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                String item = jsonArray.getString(i);
                if (!item.equals(uri)) { // Remove duplicates (will add to top later)
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
        prefs.edit().putString(KEY_HISTORY, newArray.toString()).apply();
    }

    private void refreshHistoryUI() {
        historyContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_HISTORY, "[]");

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

        float scale = (float) screenWidth / imgW;
        currentMatrix.reset();
        currentMatrix.setScale(scale, scale);
        
        currentImageHeight = imgH * scale;
        
        float transY = 0;
        
        float minTransY, maxTransY;
        if (currentImageHeight <= screenHeight) {
            float offset = (screenHeight - currentImageHeight) / 2f;
            minTransY = offset;
            maxTransY = offset;
        } else {
            minTransY = screenHeight - currentImageHeight;
            maxTransY = 0;
        }

        // Check for saved position
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = "pos_" + currentUriString;
        
        boolean restored = false;
        if (currentUriString != null && prefs.contains(key)) {
            transY = prefs.getFloat(key, 0f);
            
            // Validate bounds (in case screen size changed or image changed)
            if (transY > maxTransY) transY = maxTransY;
            if (transY < minTransY) transY = minTransY;
            restored = true;
        }

        if (!restored) {
            if (currentImageHeight < screenHeight) {
                transY = (screenHeight - currentImageHeight) / 2f;
            } else {
                transY = 0; 
            }
        }
        
        currentMatrix.postTranslate(0, transY);
        
        imageView.setImageMatrix(currentMatrix);
        imageView.setImageBitmap(bitmap);
    }
}