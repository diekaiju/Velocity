package com.example.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;

import com.example.browser.reconstruction.WebsiteReconstructionEngine;

import app.cash.quickjs.QuickJs;

import com.example.browser.HtmlCleaner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    public interface LocationCallback {
        void replace(String url);
    }

    private static class Tab {
        String currentUrl;
        List<String> history = new ArrayList<>();
        int historyIndex = -1;
        Spanned pageContent;
        String pageTitle = "Home";
        final List<java.util.concurrent.Future<?>> imageLoadTasks = new ArrayList<>();
        java.util.Map<String, Integer> anchorMap = new java.util.HashMap<>();
        com.example.browser.reconstruction.LayoutNode rootLayout;

        List<Integer> historyScrollY = new ArrayList<>();
        List<com.example.browser.reconstruction.LayoutNode> historyLayouts = new ArrayList<>();
        List<Spanned> historyContents = new ArrayList<>();
        List<String> historyTitles = new ArrayList<>();
        List<java.util.Map<String, Integer>> historyAnchorMaps = new ArrayList<>();

        Tab(String url) {
            addHistory(url);
        }

        void clearTasks() {
            synchronized (imageLoadTasks) {
                for (java.util.concurrent.Future<?> task : imageLoadTasks) {
                    if (task != null && !task.isDone() && !task.isCancelled()) {
                        task.cancel(true);
                    }
                }
                imageLoadTasks.clear();
            }
        }

        void addHistory(String url) {
            if (historyIndex < history.size() - 1) {
                history = new ArrayList<>(history.subList(0, historyIndex + 1));
                historyScrollY = new ArrayList<>(historyScrollY.subList(0, historyIndex + 1));
                historyLayouts = new ArrayList<>(historyLayouts.subList(0, historyIndex + 1));
                historyContents = new ArrayList<>(historyContents.subList(0, historyIndex + 1));
                historyTitles = new ArrayList<>(historyTitles.subList(0, historyIndex + 1));
                historyAnchorMaps = new ArrayList<>(historyAnchorMaps.subList(0, historyIndex + 1));
            }
            history.add(url);
            historyScrollY.add(0);
            historyLayouts.add(null);
            historyContents.add(null);
            historyTitles.add("Untitled Page");
            historyAnchorMaps.add(new java.util.HashMap<>());
            historyIndex++;
            currentUrl = url;
            restoreCurrentState();
        }

        void saveCurrentState(com.example.browser.reconstruction.LayoutNode root, Spanned content, String title, java.util.Map<String, Integer> anchors) {
            if (historyIndex >= 0 && historyIndex < history.size()) {
                historyLayouts.set(historyIndex, root);
                historyContents.set(historyIndex, content);
                historyTitles.set(historyIndex, title);
                historyAnchorMaps.set(historyIndex, anchors);
            }
            this.rootLayout = root;
            this.pageContent = content;
            this.pageTitle = title;
            this.anchorMap = anchors;
        }

        void restoreCurrentState() {
            if (historyIndex >= 0 && historyIndex < history.size()) {
                this.rootLayout = historyLayouts.get(historyIndex);
                this.pageContent = historyContents.get(historyIndex);
                this.pageTitle = historyTitles.get(historyIndex);
                this.anchorMap = historyAnchorMaps.get(historyIndex);
                this.currentUrl = history.get(historyIndex);
            }
        }

        void saveCurrentScrollY(int scrollY) {
            if (historyIndex >= 0 && historyIndex < historyScrollY.size()) {
                historyScrollY.set(historyIndex, scrollY);
            }
        }

        int getCurrentScrollY() {
            if (historyIndex >= 0 && historyIndex < historyScrollY.size()) {
                return historyScrollY.get(historyIndex);
            }
            return 0;
        }

        boolean canGoBack() {
            return historyIndex > 0;
        }

        boolean canGoForward() {
            return historyIndex < history.size() - 1;
        }

        void goBack() {
            if (canGoBack()) {
                historyIndex--;
                restoreCurrentState();
            }
        }

        void goForward() {
            if (canGoForward()) {
                historyIndex++;
                restoreCurrentState();
            }
        }
    }

    public static class UrlDrawable extends android.graphics.drawable.Drawable {
        private android.graphics.drawable.Drawable drawable;
        private final String url;

        public UrlDrawable(String url, android.graphics.drawable.Drawable placeholder) {
            this.url = url;
            this.drawable = placeholder;
            if (placeholder != null) {
                setBounds(0, 0, placeholder.getIntrinsicWidth(), placeholder.getIntrinsicHeight());
            }
        }

        public void setActualDrawable(android.graphics.drawable.Drawable drawable) {
            this.drawable = drawable;
            if (drawable != null) {
                setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            }
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            if (drawable != null) {
                drawable.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            if (drawable != null) {
                drawable.setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            if (drawable != null) {
                drawable.setColorFilter(colorFilter);
            }
        }

        @Override
        public int getOpacity() {
            return drawable != null ? drawable.getOpacity() : android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            if (drawable != null) {
                drawable.setBounds(left, top, right, bottom);
            }
        }
    }

    public String processUrl(String url) {
        if (!url.contains(".")) {
            try {
                return "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(url, "UTF-8");
            } catch (Exception e) {
                return "https://html.duckduckgo.com/html/?q=" + url;
            }
        }
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        return url;
    }

    public String getBrowserName() {
        return "Velocity Private Browser";
    }

    private View scrollView;
    private View homePageContainer;
    private EditText homeSearchInput;
    private ImageButton btnHomeSearchGo;

    private TextView htmlTextView;
    private LinearLayout pageLayoutContainer;
    private LinearLayout tabContainer;
    private EditText urlInput;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, btnGo, btnNewTab, btnCloseTab;

    private List<Tab> tabList = new ArrayList<>();
    private int currentTabIdx = -1;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private QuickJs quickJs;

    private ImageButton btnOutline;
    private ImageButton btnMore;
    private String currentRawHtml = "";
    private com.example.browser.reconstruction.ReaderTheme currentReaderTheme = com.example.browser.reconstruction.ReaderTheme.LIGHT;
    private View topHeaderContainer;
    private View bottomBarContainer;
    private boolean isBarsVisible = true;
    private android.speech.tts.TextToSpeech textToSpeech;
    private boolean isSpeaking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);

        // Set crash logger to clipboard
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                String stackTrace = android.util.Log.getStackTraceString(throwable);
                try {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("App Crash Log", stackTrace);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                    }
                } catch (Exception ignored) {}
                
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    Tab tab = tabList.get(currentTabIdx);
                    if (tab.canGoBack()) {
                        if (tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                            tab.saveCurrentScrollY(scrollView.getScrollY());
                        }
                        tab.goBack();
                        if (tab.currentUrl.equals("home")) {
                            switchToTab(currentTabIdx);
                        } else {
                            if (tab.rootLayout != null || tab.pageContent != null) {
                                urlInput.setText(tab.currentUrl);
                                renderNativeTree(tab);
                                final int savedY = getUrlScrollPosition(tab.currentUrl);
                                scrollView.post(() -> scrollView.scrollTo(0, savedY));
                                updateButtons();
                            } else {
                                loadUrl(tab.currentUrl, false);
                            }
                        }
                        return;
                    }
                }
                setEnabled(false);
                onBackPressed();
                setEnabled(true);
            }
        });

        // Initialize TTS
        textToSpeech = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(java.util.Locale.US);
            }
        });

        // Initialize QuickJS context and map JS parent redirect callback
        try {
            quickJs = QuickJs.create();
            
            LocationCallback callback = new LocationCallback() {
                @Override
                public void replace(String url) {
                    runOnUiThread(() -> {
                        loadUrl(url, true);
                    });
                }
            };
            
            quickJs.set("locationCallback", LocationCallback.class, callback);
            
            // Build JS window parent location replace structure
            quickJs.evaluate(
                "var window = {\n" +
                "    parent: {\n" +
                "        location: {\n" +
                "            replace: function(url) {\n" +
                "                locationCallback.replace(url);\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "};"
            );
        } catch (Exception e) {
            Toast.makeText(this, "Failed to initialize JS Engine: " + e.getMessage(), Toast.LENGTH_LONG).show();
            android.util.Log.e("Velocity", "Failed to initialize QuickJS", e);
            throw new RuntimeException("QuickJS init failed: " + e.getMessage(), e);
        }

        // Initialize UI
        topHeaderContainer = findViewById(R.id.topHeaderContainer);
        bottomBarContainer = findViewById(R.id.bottomBarContainer);

        // Load saved reader theme
        String savedTheme = getSharedPreferences("reader_prefs", MODE_PRIVATE).getString("selected_theme", com.example.browser.reconstruction.ReaderTheme.LIGHT.name());
        try {
            currentReaderTheme = com.example.browser.reconstruction.ReaderTheme.valueOf(savedTheme);
        } catch (Exception e) {
            currentReaderTheme = com.example.browser.reconstruction.ReaderTheme.LIGHT;
        }

        scrollView = findViewById(R.id.scrollView);
        if (scrollView instanceof androidx.core.widget.NestedScrollView) {
            ((androidx.core.widget.NestedScrollView) scrollView).setOnScrollChangeListener(
                (androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    int dy = scrollY - oldScrollY;
                    if (dy > 15 && isBarsVisible) {
                        hideNavigationBars();
                    } else if (dy < -15 && !isBarsVisible) {
                        showNavigationBars();
                    }
                    if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                        Tab tab = tabList.get(currentTabIdx);
                        tab.saveCurrentScrollY(scrollY);
                        saveUrlScrollPosition(tab.currentUrl, scrollY);
                    }
                }
            );
            scrollView.post(() -> {
                int topHeight = topHeaderContainer != null ? topHeaderContainer.getHeight() : 0;
                int bottomHeight = bottomBarContainer != null ? bottomBarContainer.getHeight() : 0;
                scrollView.setPadding(scrollView.getPaddingLeft(), topHeight, scrollView.getPaddingRight(), bottomHeight);
                ((androidx.core.widget.NestedScrollView) scrollView).setClipToPadding(false);
            });
        }

        homePageContainer = findViewById(R.id.homePageContainer);
        homeSearchInput = findViewById(R.id.homeSearchInput);
        btnHomeSearchGo = findViewById(R.id.btnHomeSearchGo);

        htmlTextView = findViewById(R.id.htmlTextView);
        pageLayoutContainer = findViewById(R.id.pageLayoutContainer);
        tabContainer = findViewById(R.id.tabContainer);
        urlInput = findViewById(R.id.urlInput);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnOutline = findViewById(R.id.btnOutline);
        btnHome = findViewById(R.id.btnHome);
        btnGo = findViewById(R.id.btnGo);
        btnNewTab = findViewById(R.id.btnNewTab);
        btnMore = findViewById(R.id.btnMore);

        if (btnOutline != null) {
            btnOutline.setOnClickListener(v -> showArticleOutline());
        }
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> showMoreOptionsSheet());
        }

        setupListeners();

        // Check for incoming intent
        handleIntent(getIntent());
        
        // Show browser name
        Toast.makeText(this, "Welcome to " + getBrowserName(), Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void createNewTab(String url) {
        Tab tab = new Tab(url);
        tabList.add(tab);
        
        // Create simple styled text tab element with max width and ellipsis
        android.widget.TextView tabView = new android.widget.TextView(this);
        tabView.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        tabView.setText(tab.pageTitle);
        tabView.setSingleLine(true);
        tabView.setMaxWidth(dpToPx(120));
        tabView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        tabView.setLayoutParams(params);
        tabView.setGravity(android.view.Gravity.CENTER);

        tabView.setOnClickListener(v -> {
            int idx = tabContainer.indexOfChild(v);
            if (idx != -1) {
                switchToTab(idx);
            }
        });
        tabContainer.addView(tabView);
        
        switchToTab(tabList.size() - 1);
        if (!url.equals("home")) {
            loadUrl(url, false);
        }
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

        // Home Page Search input trigger
        btnHomeSearchGo.setOnClickListener(v -> triggerHomeSearch());
        homeSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                triggerHomeSearch();
                return true;
            }
            return false;
        });

        // Reading Hub Quick Topic & Library Buttons
        android.widget.Button btnCategoryWiki = findViewById(R.id.btnCategoryWiki);
        android.widget.Button btnCategoryNews = findViewById(R.id.btnCategoryNews);
        android.widget.Button btnCategoryTech = findViewById(R.id.btnCategoryTech);
        android.widget.Button btnCategoryBooks = findViewById(R.id.btnCategoryBooks);
        android.widget.Button btnHomeBookmarks = findViewById(R.id.btnHomeBookmarks);
        android.widget.Button btnHomeHistory = findViewById(R.id.btnHomeHistory);

        if (btnCategoryWiki != null) btnCategoryWiki.setOnClickListener(v -> loadUrl("https://en.wikipedia.org/wiki/Special:Random", true));
        if (btnCategoryNews != null) btnCategoryNews.setOnClickListener(v -> loadUrl("https://lite.cnn.com", true));
        if (btnCategoryTech != null) btnCategoryTech.setOnClickListener(v -> loadUrl("https://news.ycombinator.com/", true));
        if (btnCategoryBooks != null) btnCategoryBooks.setOnClickListener(v -> loadUrl("https://gutenberg.org/", true));

        if (btnHomeBookmarks != null) btnHomeBookmarks.setOnClickListener(v -> showBookmarksSheet());
        if (btnHomeHistory != null) btnHomeHistory.setOnClickListener(v -> showHistorySheet());

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    Tab tab = tabList.get(currentTabIdx);
                    if (tab.canGoBack()) {
                        if (tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                            tab.saveCurrentScrollY(scrollView.getScrollY());
                        }
                        tab.goBack();
                        if (tab.currentUrl.equals("home")) {
                            switchToTab(currentTabIdx);
                        } else {
                            if (tab.rootLayout != null || tab.pageContent != null) {
                                urlInput.setText(tab.currentUrl);
                                renderNativeTree(tab);
                                final int savedY = getUrlScrollPosition(tab.currentUrl);
                                scrollView.post(() -> scrollView.scrollTo(0, savedY));
                                updateButtons();
                            } else {
                                loadUrl(tab.currentUrl, false);
                            }
                        }
                    }
                }
            });
        }

        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    Tab tab = tabList.get(currentTabIdx);
                    if (tab.canGoForward()) {
                        if (tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                            tab.saveCurrentScrollY(scrollView.getScrollY());
                        }
                        tab.goForward();
                        if (tab.currentUrl.equals("home")) {
                            switchToTab(currentTabIdx);
                        } else {
                            if (tab.rootLayout != null || tab.pageContent != null) {
                                urlInput.setText(tab.currentUrl);
                                renderNativeTree(tab);
                                final int savedY = getUrlScrollPosition(tab.currentUrl);
                                scrollView.post(() -> scrollView.scrollTo(0, savedY));
                                updateButtons();
                            } else {
                                loadUrl(tab.currentUrl, false);
                            }
                        }
                    }
                }
            });
        }

        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    Tab tab = tabList.get(currentTabIdx);
                    tab.addHistory("home");
                    tab.pageContent = null;
                    tab.pageTitle = "Home";
                    switchToTab(currentTabIdx);
                }
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    String currentUrl = tabList.get(currentTabIdx).currentUrl;
                    if (!currentUrl.equals("home")) {
                        loadUrl(currentUrl, false);
                    }
                }
            });
        }

        if (btnNewTab != null) btnNewTab.setOnClickListener(v -> createNewTab("home"));

        if (btnCloseTab != null) btnCloseTab.setOnClickListener(v -> closeCurrentTab());
    }

    private void triggerHomeSearch() {
        String query = homeSearchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            homeSearchInput.setText("");
            Intent intent = new Intent(MainActivity.this, DuckDuckGoSearchActivity.class);
            intent.putExtra("QUERY", query);
            startActivity(intent);
        }
    }

    private void closeCurrentTab() {
        if (tabList.size() <= 1) {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                tab.clearTasks();
                tab.addHistory("home");
                tab.pageContent = null;
                tab.pageTitle = "Home";
                switchToTab(currentTabIdx);
            }
            return;
        }

        tabList.get(currentTabIdx).clearTasks();
        tabList.remove(currentTabIdx);
        tabContainer.removeViewAt(currentTabIdx);

        int nextSelection = Math.max(0, currentTabIdx - 1);
        switchToTab(nextSelection);
    }

    private void hideNavigationBars() {
        if (!isBarsVisible) return;
        isBarsVisible = false;
        if (topHeaderContainer != null) {
            topHeaderContainer.animate()
                    .translationY(-topHeaderContainer.getHeight())
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        if (bottomBarContainer != null) {
            bottomBarContainer.animate()
                    .translationY(bottomBarContainer.getHeight())
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void showNavigationBars() {
        if (isBarsVisible) return;
        isBarsVisible = true;
        if (topHeaderContainer != null) {
            topHeaderContainer.animate()
                    .translationY(0)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        if (bottomBarContainer != null) {
            bottomBarContainer.animate()
                    .translationY(0)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void switchToTab(int position) {
        showNavigationBars();
        if (position >= 0 && position < tabList.size()) {
            currentTabIdx = position;
            Tab tab = tabList.get(position);
            
            if (tab.currentUrl.equals("home")) {
                urlInput.setText("");
                homePageContainer.setVisibility(View.VISIBLE);
                scrollView.setVisibility(View.GONE);
            } else {
                urlInput.setText(tab.currentUrl);
                homePageContainer.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE);
                renderNativeTree(tab);
                final int savedY = getUrlScrollPosition(tab.currentUrl);
                scrollView.post(() -> scrollView.scrollTo(0, savedY));
            }
            
            updateButtons();

            // Visually style tabs with clean rounded background drawable
            for (int i = 0; i < tabContainer.getChildCount(); i++) {
                android.widget.TextView tv = (android.widget.TextView) tabContainer.getChildAt(i);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(dpToPx(6));
                if (i == position) {
                    tv.setTextColor(0xFF202124); // Dark text for active tab
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                    gd.setColor(0xFFFFFFFF); // White active tab background
                    gd.setStroke(dpToPx(1), 0xFFDADCE0); // Subtle border
                } else {
                    tv.setTextColor(0xFF5F6368); // Gray text for inactive tabs
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                    gd.setColor(0xFFE8EAED); // Light grey inactive background
                    gd.setStroke(dpToPx(1), 0xFFDADCE0);
                }
                tv.setBackground(gd);
            }
        }
    }

    private boolean isUrl(String input) {
        if (input == null || input.isEmpty()) return false;
        if (input.contains(" ")) return false;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://") || input.startsWith("about:")) return true;
        if (input.contains(".")) {
            String[] parts = input.split("\\.");
            if (parts.length >= 2 && !parts[parts.length - 1].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void handleUrlInput() {
        String input = urlInput.getText().toString().trim();
        if (!input.isEmpty()) {
            if (isUrl(input)) {
                String processedUrl = processUrl(input);
                loadUrl(processedUrl, true);
            } else {
                Intent intent = new Intent(MainActivity.this, DuckDuckGoSearchActivity.class);
                intent.putExtra("QUERY", input);
                startActivity(intent);
            }
        }
    }

    private void loadUrl(String url, boolean addToHistory) {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        
        // Intercept and resolve DuckDuckGo redirect wall links
        if (url.startsWith("https://duckduckgo.com/l/?") || url.startsWith("http://duckduckgo.com/l/?") ||
            url.contains("duckduckgo.com/l/?")) {
            try {
                Uri uri = Uri.parse(url);
                String uddg = uri.getQueryParameter("uddg");
                if (uddg != null && !uddg.isEmpty()) {
                    url = uddg;
                }
            } catch (Exception ignored) {}
        }

        final String finalUrl = url;
        final Tab tab = tabList.get(currentTabIdx);
        tab.clearTasks();
        if (addToHistory) {
            tab.addHistory(finalUrl);
        }

        urlInput.setText(finalUrl);
        
        // Hide home page container immediately on loading a URL
        runOnUiThread(() -> {
            homePageContainer.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(20);
        });

        executor.execute(() -> {
            String response = "";
            int responseCode = -1;
            try {
                URL urlObj = new URL(finalUrl);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

                responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine).append("\n");
                    }
                    in.close();
                    response = content.toString();
                } else {
                    response = "<h3>Error " + responseCode + "</h3><p>Unable to load the requested page.</p>";
                }
                conn.disconnect();
            } catch (Exception e) {
                response = "<h3>Connection Error</h3><p>" + e.getMessage() + "</p>";
            }

            final String htmlContent = response;
            
            // Extract title
            String pageTitle = "Untitled Page";
            Pattern titlePattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = titlePattern.matcher(htmlContent);
            if (matcher.find()) {
                pageTitle = matcher.group(1).trim();
            }

            final String finalPageTitle = pageTitle;

            // Execute scripts inside the htmlContent using QuickJS
            executeScripts(htmlContent);

            runOnUiThread(() -> {
                progressBar.setProgress(80);
                
                // Clean modern tags to retro equivalents
                HtmlCleaner.Config config = HtmlCleaner.Config.defaultConfig();
                config.baseUrl = tab.currentUrl;
                String cleanedHtml = HtmlCleaner.clean(htmlContent, config).html;
                android.util.Log.d("Velocity", "Cleaned HTML: " + cleanedHtml);
                
                Html.ImageGetter imageGetter = new Html.ImageGetter() {
                    @Override
                    public Drawable getDrawable(String source) {
                        int reqWidth = 0;
                        int reqHeight = 0;
                        String cleanUrl = source;
                        try {
                            int hashIdx = source.indexOf('#');
                            if (hashIdx != -1) {
                                cleanUrl = source.substring(0, hashIdx);
                                String fragment = source.substring(hashIdx + 1);
                                String[] pairs = fragment.split("&");
                                for (String pair : pairs) {
                                    String[] kv = pair.split("=");
                                    if (kv.length == 2) {
                                        if (kv[0].equals("vw")) {
                                            reqWidth = Integer.parseInt(kv[1]);
                                        } else if (kv[0].equals("vh")) {
                                            reqHeight = Integer.parseInt(kv[1]);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        int targetWidth = reqWidth > 0 ? dpToPx(reqWidth) : dpToPx(120);
                        int targetHeight = reqHeight > 0 ? dpToPx(reqHeight) : dpToPx(120);

                        GradientDrawable placeholder = new GradientDrawable();
                        placeholder.setColor(0xFFF1F3F4);
                        placeholder.setCornerRadius(dpToPx(4));
                        placeholder.setStroke(dpToPx(1), 0xFFDADCE0);
                        placeholder.setSize(targetWidth, targetHeight);
                        placeholder.setBounds(0, 0, targetWidth, targetHeight);

                        UrlDrawable urlDrawable = new UrlDrawable(source, placeholder);

                        final String finalCleanUrl = cleanUrl;
                        java.util.concurrent.Future<?> task = ImageLoader.getInstance(MainActivity.this).load(
                            finalCleanUrl,
                            targetWidth,
                            targetHeight,
                            new ImageLoader.ImageLoadCallback() {
                                @Override
                                public void onImageLoaded(android.graphics.Bitmap bitmap) {
                                    BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
                                    bitmapDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                                    urlDrawable.setActualDrawable(bitmapDrawable);
                                    
                                    runOnUiThread(() -> {
                                        if (tabList.indexOf(tab) == currentTabIdx) {
                                            htmlTextView.setText(htmlTextView.getText());
                                        }
                                    });
                                }

                                @Override
                                public void onError(Throwable error) {
                                    GradientDrawable errorDrawable = new GradientDrawable();
                                    errorDrawable.setColor(0xFFFCE8E6);
                                    errorDrawable.setCornerRadius(dpToPx(4));
                                    errorDrawable.setStroke(dpToPx(1), 0xFFD93025);
                                    errorDrawable.setSize(targetWidth, targetHeight);
                                    errorDrawable.setBounds(0, 0, targetWidth, targetHeight);
                                    urlDrawable.setActualDrawable(errorDrawable);
                                    
                                    runOnUiThread(() -> {
                                        if (tabList.indexOf(tab) == currentTabIdx) {
                                            htmlTextView.setText(htmlTextView.getText());
                                        }
                                    });
                                }
                            }
                        );

                        if (task != null) {
                            synchronized (tab.imageLoadTasks) {
                                tab.imageLoadTasks.add(task);
                            }
                        }

                        return urlDrawable;
                    }
                };

                currentRawHtml = htmlContent;

                // Reconstruct the page using the WebsiteReconstructionEngine
                WebsiteReconstructionEngine.ReconstructedPage reconstructedPage = WebsiteReconstructionEngine.reconstruct(cleanedHtml, config.baseUrl);
                tab.saveCurrentState(reconstructedPage.rootLayout, null, finalPageTitle, reconstructedPage.anchorMap);

                HistoryManager.addHistory(MainActivity.this, finalPageTitle, config.baseUrl);

                if (tabList.indexOf(tab) == currentTabIdx) {
                    renderNativeTree(tab);

                    final int savedY = getUrlScrollPosition(tab.currentUrl);
                    scrollView.post(() -> scrollView.scrollTo(0, savedY));

                    // Update tab text UI
                    android.widget.TextView tv = (android.widget.TextView) tabContainer.getChildAt(currentTabIdx);
                    if (tv != null) {
                        tv.setText(tab.pageTitle);
                    }
                }

                progressBar.setVisibility(View.GONE);
                updateButtons();
            });
        });
    }

    private void renderNativeTree(Tab tab) {
        if (tab == null || pageLayoutContainer == null) return;
        if (tab.rootLayout != null) {
            com.example.browser.reconstruction.ReaderTheme theme = currentReaderTheme;
            scrollView.setBackgroundColor(theme.backgroundColor);
            pageLayoutContainer.setBackgroundColor(theme.backgroundColor);

            com.example.browser.reconstruction.NativeLayoutRenderer.renderTree(
                    MainActivity.this,
                    pageLayoutContainer,
                    tab.rootLayout,
                    href -> handleLinkClick(href, tab),
                    id -> scrollToElement(id),
                    theme
            );
        } else if (tab.pageContent != null) {
            pageLayoutContainer.removeAllViews();
            htmlTextView.setText(tab.pageContent);
            htmlTextView.setVisibility(View.VISIBLE);
            pageLayoutContainer.addView(htmlTextView);
        }
    }

    private void showArticleOutline() {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        if (tab.rootLayout == null) {
            Toast.makeText(this, "No article outline available", Toast.LENGTH_SHORT).show();
            return;
        }

        List<com.example.browser.reconstruction.ArticleOutlineExtractor.OutlineItem> outline =
                com.example.browser.reconstruction.ArticleOutlineExtractor.extractOutline(tab.rootLayout);

        if (outline.isEmpty()) {
            Toast.makeText(this, "No headings found in this article", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1C1B1F"));

        TextView title = new TextView(this);
        title.setText("Table of Contents");
        title.setTextSize(18f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        layout.addView(title);

        androidx.core.widget.NestedScrollView sheetScroll = new androidx.core.widget.NestedScrollView(this);
        LinearLayout itemsList = new LinearLayout(this);
        itemsList.setOrientation(LinearLayout.VERTICAL);

        for (com.example.browser.reconstruction.ArticleOutlineExtractor.OutlineItem item : outline) {
            TextView itemTv = new TextView(this);
            int indent = (item.level - 1) * dpToPx(16);
            itemTv.setPadding(indent + dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
            itemTv.setText((item.level > 1 ? "• " : "") + item.title);
            itemTv.setTextSize(item.level == 1 ? 16f : 14f);
            itemTv.setTextColor(item.level == 1 ? android.graphics.Color.parseColor("#90CAF9") : android.graphics.Color.WHITE);
            itemTv.setOnClickListener(v -> {
                dialog.dismiss();
                if (!item.id.isEmpty()) {
                    scrollToElement(item.id);
                }
            });
            itemsList.addView(itemTv);
        }

        sheetScroll.addView(itemsList);
        layout.addView(sheetScroll);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void showThemeSelector() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1C1B1F"));

        TextView title = new TextView(this);
        title.setText("Select Reader Theme");
        title.setTextSize(18f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        layout.addView(title);

        com.example.browser.reconstruction.ReaderTheme[] themes = com.example.browser.reconstruction.ReaderTheme.values();
        for (com.example.browser.reconstruction.ReaderTheme t : themes) {
            TextView themeOption = new TextView(this);
            themeOption.setText(t.displayName);
            themeOption.setTextSize(16f);
            themeOption.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

            int bg = t.backgroundColor;
            int textColor = t.textColor;
            int border = t.borderColor;

            themeOption.setTextColor(textColor);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(bg);
            gd.setStroke(dpToPx(2), border);
            gd.setCornerRadius(dpToPx(8));
            themeOption.setBackground(gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
            themeOption.setLayoutParams(lp);

            themeOption.setOnClickListener(v -> {
                currentReaderTheme = t;
                getSharedPreferences("reader_prefs", MODE_PRIVATE).edit().putString("selected_theme", t.name()).apply();
                dialog.dismiss();
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    renderNativeTree(tabList.get(currentTabIdx));
                }
            });

            layout.addView(themeOption);
        }

        dialog.setContentView(layout);
        dialog.show();
    }

    private int getDynamicColor(int attr, int defaultColor) {
        try {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            if (getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
        } catch (Exception ignored) {}
        return defaultColor;
    }

    private void saveCurrentPageBookmark() {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        if ("home".equals(tab.currentUrl)) {
            Toast.makeText(this, "Cannot bookmark Home page", Toast.LENGTH_SHORT).show();
            return;
        }
        String html = currentRawHtml != null ? currentRawHtml : "";
        boolean saved = BookmarkManager.saveBookmark(this, tab.pageTitle, tab.currentUrl, html);
        if (saved) {
            Toast.makeText(this, "Saved Offline Bookmark!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to save bookmark", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBookmarksSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1C1B1F"));

        TextView title = new TextView(this);
        title.setText("Saved Offline Bookmarks");
        title.setTextSize(18f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        layout.addView(title);

        List<BookmarkManager.BookmarkItem> bookmarks = BookmarkManager.getBookmarks(this);
        if (bookmarks.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No saved bookmarks yet.\nClick the star button on any page to save it for offline reading.");
            emptyTv.setTextSize(14f);
            emptyTv.setTextColor(android.graphics.Color.parseColor("#E6E1E5"));
            emptyTv.setPadding(0, dpToPx(16), 0, dpToPx(16));
            layout.addView(emptyTv);
        } else {
            androidx.core.widget.NestedScrollView sheetScroll = new androidx.core.widget.NestedScrollView(this);
            LinearLayout itemsList = new LinearLayout(this);
            itemsList.setOrientation(LinearLayout.VERTICAL);

            for (BookmarkManager.BookmarkItem bm : bookmarks) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView itemTv = new TextView(this);
                itemTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                itemTv.setText(bm.title + "\n" + bm.url);
                itemTv.setTextSize(14f);
                itemTv.setTextColor(android.graphics.Color.WHITE);
                itemTv.setOnClickListener(v -> {
                    dialog.dismiss();
                    String offlineHtml = BookmarkManager.getOfflineContent(MainActivity.this, bm.id);
                    if (offlineHtml != null && !offlineHtml.isEmpty()) {
                        renderOfflinePage(bm.title, bm.url, offlineHtml);
                    } else {
                        loadUrl(bm.url, true);
                    }
                });
                row.addView(itemTv);

                ImageButton btnDel = new ImageButton(this);
                btnDel.setImageResource(android.R.drawable.ic_menu_delete);
                btnDel.setBackgroundResource(android.R.drawable.btn_dialog);
                btnDel.setOnClickListener(v -> {
                    BookmarkManager.deleteBookmark(MainActivity.this, bm.id);
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                });
                row.addView(btnDel);

                itemsList.addView(row);
            }
            sheetScroll.addView(itemsList);
            layout.addView(sheetScroll);
        }

        dialog.setContentView(layout);
        dialog.show();
    }

    private void showHistorySheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1C1B1F"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dpToPx(12));

        TextView title = new TextView(this);
        title.setText("Browsing History");
        title.setTextSize(18f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView clearBtn = new TextView(this);
        clearBtn.setText("CLEAR HISTORY");
        clearBtn.setTextSize(12f);
        clearBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        clearBtn.setTextColor(android.graphics.Color.parseColor("#FF8A80"));
        clearBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        clearBtn.setOnClickListener(v -> {
            HistoryManager.clearHistory(MainActivity.this);
            dialog.dismiss();
            Toast.makeText(MainActivity.this, "History cleared", Toast.LENGTH_SHORT).show();
        });
        header.addView(clearBtn);

        layout.addView(header);

        List<HistoryManager.HistoryItem> history = HistoryManager.getHistory(this);
        if (history.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No browsing history found.");
            emptyTv.setTextSize(14f);
            emptyTv.setTextColor(android.graphics.Color.parseColor("#E6E1E5"));
            emptyTv.setPadding(0, dpToPx(16), 0, dpToPx(16));
            layout.addView(emptyTv);
        } else {
            androidx.core.widget.NestedScrollView sheetScroll = new androidx.core.widget.NestedScrollView(this);
            LinearLayout itemsList = new LinearLayout(this);
            itemsList.setOrientation(LinearLayout.VERTICAL);

            for (HistoryManager.HistoryItem item : history) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView itemTv = new TextView(this);
                itemTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                itemTv.setText(item.title + "\n" + item.url);
                itemTv.setTextSize(14f);
                itemTv.setTextColor(android.graphics.Color.WHITE);
                itemTv.setOnClickListener(v -> {
                    dialog.dismiss();
                    loadUrl(item.url, true);
                });
                row.addView(itemTv);

                ImageButton btnDel = new ImageButton(this);
                btnDel.setImageResource(android.R.drawable.ic_menu_delete);
                btnDel.setBackgroundResource(android.R.drawable.btn_dialog);
                btnDel.setOnClickListener(v -> {
                    HistoryManager.deleteHistoryItem(MainActivity.this, item.id);
                    dialog.dismiss();
                    showHistorySheet();
                });
                row.addView(btnDel);

                itemsList.addView(row);
            }
            sheetScroll.addView(itemsList);
            layout.addView(sheetScroll);
        }

        dialog.setContentView(layout);
        dialog.show();
    }

    private void renderOfflinePage(String pageTitle, String url, String html) {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        tab.currentUrl = url;

        executor.execute(() -> {
            com.example.browser.reconstruction.WebsiteReconstructionEngine.ReconstructedPage reconstructed =
                    com.example.browser.reconstruction.WebsiteReconstructionEngine.reconstruct(html, url);
            tab.saveCurrentState(reconstructed.rootLayout, null, pageTitle, reconstructed.anchorMap);

            runOnUiThread(() -> {
                renderNativeTree(tab);
                urlInput.setText(url);
                scrollView.post(() -> scrollView.scrollTo(0, 0));
                Toast.makeText(MainActivity.this, "Reading Offline Saved Copy", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void toggleTextToSpeech() {
        if (textToSpeech == null) return;
        if (isSpeaking) {
            textToSpeech.stop();
            isSpeaking = false;
            Toast.makeText(this, "Speech stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        if (tab.rootLayout == null) {
            Toast.makeText(this, "No content to read", Toast.LENGTH_SHORT).show();
            return;
        }

        String readableText = extractAllText(tab.rootLayout);
        if (readableText.trim().isEmpty()) {
            Toast.makeText(this, "No text found to read", Toast.LENGTH_SHORT).show();
            return;
        }

        isSpeaking = true;
        Toast.makeText(this, "Reading aloud...", Toast.LENGTH_SHORT).show();
        textToSpeech.speak(readableText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "VelocityTTS");
    }

    private void showMoreOptionsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1C1B1F"));

        TextView title = new TextView(this);
        title.setText("More Options");
        title.setTextSize(18f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(16));
        layout.addView(title);

        addMoreOptionItem(layout, "📖  Table of Contents", v -> {
            dialog.dismiss();
            showArticleOutline();
        });

        addMoreOptionItem(layout, "🗣️  Read Aloud (TTS)", v -> {
            dialog.dismiss();
            toggleTextToSpeech();
        });

        addMoreOptionItem(layout, "🎨  Reader Theme", v -> {
            dialog.dismiss();
            showThemeSelector();
        });

        addMoreOptionItem(layout, "⭐  Save Page Offline", v -> {
            dialog.dismiss();
            saveCurrentPageBookmark();
        });

        addMoreOptionItem(layout, "🔖  Saved Bookmarks", v -> {
            dialog.dismiss();
            showBookmarksSheet();
        });

        addMoreOptionItem(layout, "🕒  Browsing History", v -> {
            dialog.dismiss();
            showHistorySheet();
        });

        addMoreOptionItem(layout, "🔄  Reload Page", v -> {
            dialog.dismiss();
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                if (!"home".equals(tab.currentUrl)) {
                    loadUrl(tab.currentUrl, false);
                }
            }
        });

        addMoreOptionItem(layout, "❌  Close Current Tab", v -> {
            dialog.dismiss();
            closeCurrentTab();
        });

        dialog.setContentView(layout);
        dialog.show();
    }

    private void addMoreOptionItem(LinearLayout container, String label, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16f);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14));
        tv.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(2), 0, dpToPx(2));
        tv.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#2B2930"));
        gd.setCornerRadius(dpToPx(8));
        tv.setBackground(gd);

        container.addView(tv);
    }

    private String extractAllText(com.example.browser.reconstruction.LayoutNode node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.element != null) {
            String tag = node.element.tagName().toLowerCase(java.util.Locale.ROOT);
            if (tag.equals("p") || tag.equals("h1") || tag.equals("h2") || tag.equals("h3") || tag.equals("li") || tag.equals("blockquote")) {
                sb.append(node.element.text()).append(". ");
            }
        }
        for (com.example.browser.reconstruction.LayoutNode child : node.children) {
            sb.append(extractAllText(child));
        }
        return sb.toString();
    }

    private void handleLinkClick(String href, Tab tab) {
        if (href == null || href.isEmpty()) return;
        if (href.startsWith("#")) {
            String id = href.substring(1);
            if (!id.isEmpty()) {
                scrollToElement(id);
            }
            return;
        }
        if (tab != null && scrollView != null) {
            tab.saveCurrentScrollY(scrollView.getScrollY());
        }
        String resolvedUrl = href;
        if (!href.startsWith("http://") && !href.startsWith("https://")) {
            try {
                URL base = new URL(tab.currentUrl);
                resolvedUrl = new URL(base, href).toString();
            } catch (Exception ignored) {}
        }
        if (resolvedUrl.contains("#")) {
            String[] parts = resolvedUrl.split("#", 2);
            String baseUrlWithoutHash = tab.currentUrl.split("#")[0];
            if (parts[0].equals(baseUrlWithoutHash)) {
                String id = parts[1];
                if (!id.isEmpty()) {
                    scrollToElement(id);
                }
                return;
            }
        }
        if (quickJs != null) {
            try {
                String jsCode = "window.parent.location.replace('" + resolvedUrl + "');";
                quickJs.evaluate(jsCode);
            } catch (Exception e) {
                loadUrl(resolvedUrl, true);
            }
        } else {
            loadUrl(resolvedUrl, true);
        }
    }

    private View findViewWithTagRecursive(View parent, String tag) {
        if (parent == null || tag == null) return null;
        if (tag.equals(parent.getTag())) return parent;
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewWithTagRecursive(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void scrollToElement(String id) {
        if (id == null || id.isEmpty() || pageLayoutContainer == null || scrollView == null) return;

        View targetView = findViewWithTagRecursive(pageLayoutContainer, "anchor:" + id);
        if (targetView != null) {
            int[] viewLoc = new int[2];
            int[] containerLoc = new int[2];
            targetView.getLocationOnScreen(viewLoc);
            pageLayoutContainer.getLocationOnScreen(containerLoc);
            final int targetY = Math.max(0, viewLoc[1] - containerLoc[1]);
            scrollView.post(() -> {
                if (scrollView instanceof androidx.core.widget.NestedScrollView) {
                    ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, targetY);
                } else {
                    scrollView.scrollTo(0, targetY);
                }
            });
            return;
        }

        if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
            Tab tab = tabList.get(currentTabIdx);
            Integer targetY = tab.anchorMap.get(id);
            if (targetY != null) {
                final int scrollY = Math.max(0, dpToPx(targetY));
                scrollView.post(() -> {
                    if (scrollView instanceof androidx.core.widget.NestedScrollView) {
                        ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, scrollY);
                    } else {
                        scrollView.scrollTo(0, scrollY);
                    }
                });
            }
        }
    }

    private void executeScripts(String htmlContent) {
        if (quickJs == null) return;
        
        // Extract script blocks
        Pattern scriptPattern = Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = scriptPattern.matcher(htmlContent);
        while (matcher.find()) {
            String script = matcher.group(1).trim();
            if (!script.isEmpty()) {
                try {
                    // Evaluate JavaScript code inside QuickJS sandbox
                    quickJs.evaluate(script);
                } catch (Exception ignored) {
                    // Suppress JS evaluation errors from pages as it has no DOM context
                }
            }
        }
    }

    private void updateButtons() {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        
        if (btnBack != null) {
            btnBack.setEnabled(tab.canGoBack());
            btnBack.setAlpha(tab.canGoBack() ? 1.0f : 0.3f);
        }
        if (btnForward != null) {
            btnForward.setEnabled(tab.canGoForward());
            btnForward.setAlpha(tab.canGoForward() ? 1.0f : 0.3f);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String data = intent.getDataString();
        String targetUrl = intent.getStringExtra("TARGET_URL");

        if (targetUrl != null && !targetUrl.isEmpty()) {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                loadUrl(targetUrl, true);
            } else {
                createNewTab(targetUrl);
            }
        } else if (Intent.ACTION_VIEW.equals(action) && data != null) {
            createNewTab(data);
        } else if (tabList.isEmpty()) {
            createNewTab("home");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
            Tab tab = tabList.get(currentTabIdx);
            if (tab.canGoBack()) {
                if (tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                    tab.saveCurrentScrollY(scrollView.getScrollY());
                }
                tab.goBack();
                if (tab.currentUrl.equals("home")) {
                    switchToTab(currentTabIdx);
                } else {
                    if (tab.rootLayout != null || tab.pageContent != null) {
                        urlInput.setText(tab.currentUrl);
                        renderNativeTree(tab);
                        final int savedY = getUrlScrollPosition(tab.currentUrl);
                        scrollView.post(() -> scrollView.scrollTo(0, savedY));
                        updateButtons();
                    } else {
                        loadUrl(tab.currentUrl, false);
                    }
                }
                return;
            }
        }
        super.onBackPressed();
    }

    private void saveUrlScrollPosition(String url, int scrollY) {
        if (url == null || url.trim().isEmpty() || url.equals("home") || url.equals("about:blank")) return;
        getSharedPreferences("scroll_history", MODE_PRIVATE)
            .edit()
            .putInt(url, scrollY)
            .apply();
    }

    private int getUrlScrollPosition(String url) {
        if (url == null || url.trim().isEmpty() || url.equals("home") || url.equals("about:blank")) return 0;
        return getSharedPreferences("scroll_history", MODE_PRIVATE).getInt(url, 0);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        executor.shutdownNow();
        if (quickJs != null) {
            try {
                quickJs.close();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}