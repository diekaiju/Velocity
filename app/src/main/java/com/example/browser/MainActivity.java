package com.example.browser;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import android.content.res.Configuration;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Load native library
    static {
        System.loadLibrary("browser");
    }

    // Native methods
    public native String processUrl(String url);
    public native String getBrowserName();

    private FrameLayout webViewContainer;
    private TabLayout tabLayout;
    private EditText urlInput;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, btnGo, btnNewTab, btnCloseTab;

    private List<WebView> tabList = new ArrayList<>();
    private WebView currentWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        webViewContainer = findViewById(R.id.webViewContainer);
        tabLayout = findViewById(R.id.tabLayout);
        urlInput = findViewById(R.id.urlInput);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnHome = findViewById(R.id.btnHome);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnGo = findViewById(R.id.btnGo);
        btnNewTab = findViewById(R.id.btnNewTab);
        btnCloseTab = findViewById(R.id.btnCloseTab);

        setupListeners();

        // Create initial tab
        createNewTab("https://www.google.com");
        
        // Show browser name from native
        Toast.makeText(this, "Welcome to " + getBrowserName(), Toast.LENGTH_SHORT).show();
    }

    private void createNewTab(String url) {
        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        setupWebView(webView);
        tabList.add(webView);
        
        TabLayout.Tab tab = tabLayout.newTab();
        tab.setText("New Tab");
        tabLayout.addTab(tab);
        
        tab.select();
        loadUrl(url);
    }

    private void setupWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Dark Mode support for WebView
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            }
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (view == currentWebView) {
                    progressBar.setVisibility(View.VISIBLE);
                    urlInput.setText(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                int index = tabList.indexOf(view);
                if (index != -1) {
                    TabLayout.Tab tab = tabLayout.getTabAt(index);
                    if (tab != null) {
                        String title = view.getTitle();
                        tab.setText(title != null && !title.isEmpty() ? title : "Tab " + (index + 1));
                    }
                }
                
                if (view == currentWebView) {
                    progressBar.setVisibility(View.GONE);
                    updateButtons();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view == currentWebView) {
                    progressBar.setProgress(newProgress);
                }
            }
        });
    }

    private void setupListeners() {
        btnGo.setOnClickListener(v -> handleUrlInput());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handleUrlInput();
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> {
            if (currentWebView != null && currentWebView.canGoBack()) currentWebView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (currentWebView != null && currentWebView.canGoForward()) currentWebView.goForward();
        });

        btnHome.setOnClickListener(v -> loadUrl("https://www.google.com"));

        btnRefresh.setOnClickListener(v -> {
            if (currentWebView != null) currentWebView.reload();
        });

        btnNewTab.setOnClickListener(v -> createNewTab("https://www.google.com"));

        btnCloseTab.setOnClickListener(v -> closeCurrentTab());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchToTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void closeCurrentTab() {
        if (tabList.size() <= 1) {
            // If only one tab, just reset it to home
            if (currentWebView != null) {
                currentWebView.loadUrl("https://www.google.com");
            }
            return;
        }

        int currentPosition = tabLayout.getSelectedTabPosition();
        if (currentPosition != -1) {
            WebView webViewToRemove = tabList.remove(currentPosition);
            webViewToRemove.destroy(); // Clean up memory
            tabLayout.removeTabAt(currentPosition);

            // TabLayout automatically selects the next available tab
        }
    }

    private void switchToTab(int position) {
        if (position >= 0 && position < tabList.size()) {
            WebView webView = tabList.get(position);
            currentWebView = webView;
            
            webViewContainer.removeAllViews();
            webViewContainer.addView(webView);
            
            urlInput.setText(webView.getUrl());
            updateButtons();
        }
    }

    private void handleUrlInput() {
        String input = urlInput.getText().toString().trim();
        if (!input.isEmpty()) {
            String processedUrl = processUrl(input);
            loadUrl(processedUrl);
        }
    }

    private void loadUrl(String url) {
        if (currentWebView != null) {
            currentWebView.loadUrl(url);
            urlInput.clearFocus();
        }
    }

    private void updateButtons() {
        if (currentWebView == null) return;
        
        btnBack.setEnabled(currentWebView.canGoBack());
        btnForward.setEnabled(currentWebView.canGoForward());
        
        btnBack.setAlpha(currentWebView.canGoBack() ? 1.0f : 0.3f);
        btnForward.setAlpha(currentWebView.canGoForward() ? 1.0f : 0.3f);
    }

    @Override
    public void onBackPressed() {
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}