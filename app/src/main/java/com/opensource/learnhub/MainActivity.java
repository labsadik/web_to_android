package com.opensource.learnhub;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    // CONFIGURATION
    private static final String WEBSITE_URL = "https://lms-zeta-gray.vercel.app/auth";
    private static final int FILE_CHOOSER_REQUEST = 100;

    // UI COMPONENTS
    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout fullscreenContainer;

    // FULLSCREEN STATE
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean isFullscreen = false;

    // FILE UPLOAD STATE
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Secure flag prevents screenshots/screen recording (Optional, remove if not needed)
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_main);

        // Initialize Views
        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeContainer);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        setupWebView();
        setupSwipeRefresh();
        setupBackPressedHandler();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // ⚡ PERFORMANCE & COMPATIBILITY
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // Essential for modern web apps
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false); // Auto-play video support
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // Allow HTTP content on HTTPS site if needed

        // Hardware acceleration for smooth scrolling/video
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 🔥 FIX 1: FORCE IN-APP BROWSING (No External Browser)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Handle intent:// links (e.g., maps, phones) if necessary,
                // but for pure in-app experience, we load everything here.
                view.loadUrl(url);
                return true; // Return true means "I handled it, don't open external app"
            }

            // Deprecated but kept for older Android versions compatibility
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        // 🔥 FIX 2: FULLSCREEN VIDEO & FILE UPLOADS
        webView.setWebChromeClient(new WebChromeClient() {

            // --- Fullscreen Video Handling ---
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }

                customView = view;
                customViewCallback = callback;
                isFullscreen = true;

                // Hide WebView and Show Fullscreen Container
                webView.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view);

                // Enter Immersive Mode
                enterImmersiveMode();

                // Force Landscape for Video
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;

                isFullscreen = false;

                // Remove Fullscreen View
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                customView = null;

                // Show WebView again
                webView.setVisibility(View.VISIBLE);

                // Exit Immersive Mode
                exitImmersiveMode();

                // Restore Orientation
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
            }

            // --- File Upload Handling (Android 5.0+) ---
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // Load Initial URL
        webView.loadUrl(WEBSITE_URL);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
            // Stop refreshing after a short delay or when page finishes loading (optional improvement)
            swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1000);
        });
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 1. If in fullscreen video, exit fullscreen first
                if (isFullscreen) {
                    webView.getWebChromeClient().onHideCustomView();
                    return;
                }

                // 2. If WebView can go back, go back
                if (webView.canGoBack()) {
                    webView.goBack();
                }
                // 3. Otherwise, ask to exit
                else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Exit App")
                            .setMessage("Are you sure you want to close?")
                            .setPositiveButton("Yes", (dialog, which) -> finish())
                            .setNegativeButton("No", null)
                            .show();
                }
            }
        });
    }

    // 🎬 IMMERSIVE MODE HELPERS
    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    // 📁 FILE UPLOAD RESULT HANDLER
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        // Re-enter immersive mode if we were in fullscreen before pause
        if (isFullscreen) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}