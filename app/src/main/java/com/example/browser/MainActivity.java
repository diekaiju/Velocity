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

        Tab(String url) {
            addHistory(url);
        }

        void addHistory(String url) {
            if (historyIndex < history.size() - 1) {
                history = new ArrayList<>(history.subList(0, historyIndex + 1));
            }
            history.add(url);
            historyIndex++;
            currentUrl = url;
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
                currentUrl = history.get(historyIndex);
            }
        }

        void goForward() {
            if (canGoForward()) {
                historyIndex++;
                currentUrl = history.get(historyIndex);
            }
        }
    }

    public String processUrl(String url) {
        if (!url.contains(".")) {
            try {
                return "https://html.duckduckgo.com/html?q=" + java.net.URLEncoder.encode(url, "UTF-8");
            } catch (Exception e) {
                return "https://html.duckduckgo.com/html?q=" + url;
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
    private LinearLayout tabContainer;
    private EditText urlInput;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnHome, btnRefresh, btnGo, btnNewTab, btnCloseTab;

    private List<Tab> tabList = new ArrayList<>();
    private int currentTabIdx = -1;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private QuickJs quickJs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        scrollView = findViewById(R.id.scrollView);
        homePageContainer = findViewById(R.id.homePageContainer);
        homeSearchInput = findViewById(R.id.homeSearchInput);
        btnHomeSearchGo = findViewById(R.id.btnHomeSearchGo);

        htmlTextView = findViewById(R.id.htmlTextView);
        tabContainer = findViewById(R.id.tabContainer);
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

        btnBack.setOnClickListener(v -> {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                if (tab.canGoBack()) {
                    tab.goBack();
                    if (tab.currentUrl.equals("home")) {
                        switchToTab(currentTabIdx);
                    } else {
                        loadUrl(tab.currentUrl, false);
                    }
                }
            }
        });

        btnForward.setOnClickListener(v -> {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                if (tab.canGoForward()) {
                    tab.goForward();
                    if (tab.currentUrl.equals("home")) {
                        switchToTab(currentTabIdx);
                    } else {
                        loadUrl(tab.currentUrl, false);
                    }
                }
            }
        });

        btnHome.setOnClickListener(v -> {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                tab.addHistory("home");
                tab.pageContent = null;
                tab.pageTitle = "Home";
                switchToTab(currentTabIdx);
            }
        });

        btnRefresh.setOnClickListener(v -> {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                String currentUrl = tabList.get(currentTabIdx).currentUrl;
                if (!currentUrl.equals("home")) {
                    loadUrl(currentUrl, false);
                }
            }
        });

        btnNewTab.setOnClickListener(v -> createNewTab("home"));

        btnCloseTab.setOnClickListener(v -> closeCurrentTab());
    }

    private void triggerHomeSearch() {
        String query = homeSearchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            homeSearchInput.setText("");
            try {
                String searchUrl = "https://html.duckduckgo.com/html?q=" + java.net.URLEncoder.encode(query, "UTF-8");
                loadUrl(searchUrl, true);
            } catch (Exception e) {
                loadUrl("https://html.duckduckgo.com/html?q=" + query, true);
            }
        }
    }

    private void closeCurrentTab() {
        if (tabList.size() <= 1) {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                tab.addHistory("home");
                tab.pageContent = null;
                tab.pageTitle = "Home";
                switchToTab(currentTabIdx);
            }
            return;
        }

        tabList.remove(currentTabIdx);
        tabContainer.removeViewAt(currentTabIdx);

        int nextSelection = Math.max(0, currentTabIdx - 1);
        switchToTab(nextSelection);
    }

    private void switchToTab(int position) {
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
                if (tab.pageContent != null) {
                    htmlTextView.setText(tab.pageContent);
                    htmlTextView.setMovementMethod(LinkMovementMethod.getInstance());
                } else {
                    htmlTextView.setText("");
                }
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

    private void handleUrlInput() {
        String input = urlInput.getText().toString().trim();
        if (!input.isEmpty()) {
            String processedUrl = processUrl(input);
            loadUrl(processedUrl, true);
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
                
                // Parse HTML
                Spanned parsedHtml = Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_LEGACY);
                
                // Replace URLSpans with custom ClickableSpans
                SpannableStringBuilder spannable = new SpannableStringBuilder(parsedHtml);
                URLSpan[] urls = spannable.getSpans(0, spannable.length(), URLSpan.class);
                for (URLSpan span : urls) {
                    int start = spannable.getSpanStart(span);
                    int end = spannable.getSpanEnd(span);
                    int flags = spannable.getSpanFlags(span);
                    String href = span.getURL();

                    ClickableSpan clickableSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            String resolvedUrl = href;
                            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                                try {
                                    URL base = new URL(tab.currentUrl);
                                    resolvedUrl = new URL(base, href).toString();
                                } catch (Exception ignored) {}
                            }
                            
                            // Evaluate the redirect in QuickJS sandbox as requested
                            if (quickJs != null) {
                                try {
                                    String jsCode = "window.parent.location.replace('" + resolvedUrl + "');";
                                    quickJs.evaluate(jsCode);
                                } catch (Exception e) {
                                    // Fallback if evaluating fails
                                    loadUrl(resolvedUrl, true);
                                }
                            } else {
                                loadUrl(resolvedUrl, true);
                            }
                        }
                    };

                    spannable.setSpan(clickableSpan, start, end, flags);
                    spannable.removeSpan(span);
                }

                tab.pageContent = spannable;
                tab.pageTitle = finalPageTitle;
                
                // Update active TextView
                if (tabList.indexOf(tab) == currentTabIdx) {
                    htmlTextView.setText(tab.pageContent);
                    htmlTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    
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
        
        btnBack.setEnabled(tab.canGoBack());
        btnForward.setEnabled(tab.canGoForward());
        
        btnBack.setAlpha(tab.canGoBack() ? 1.0f : 0.3f);
        btnForward.setAlpha(tab.canGoForward() ? 1.0f : 0.3f);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String data = intent.getDataString();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            createNewTab(data);
        } else if (tabList.isEmpty()) {
            createNewTab("home");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                if (tab.canGoBack()) {
                    tab.goBack();
                    if (tab.currentUrl.equals("home")) {
                        switchToTab(currentTabIdx);
                    } else {
                        loadUrl(tab.currentUrl, false);
                    }
                    return true;
                }
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (quickJs != null) {
            try {
                quickJs.close();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}