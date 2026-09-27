package com.velocity.browser;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.velocity.browser.reconstruction.ReaderTheme;

import app.cash.quickjs.QuickJs;
import io.noties.markwon.Markwon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONTokener;

public class MainActivity extends AppCompatActivity {

    public interface LocationCallback {
        void replace(String url);
    }

    private static class Tab {
        String currentUrl;
        List<String> history = new ArrayList<>();
        int historyIndex = -1;
        String markdownContent = "";
        String pageTitle = "Home";
        final List<java.util.concurrent.Future<?>> imageLoadTasks = new ArrayList<>();
        Map<String, Integer> anchorMap = new HashMap<>();
        List<HtmlToMarkdownConverter.HeadingItem> headings = new ArrayList<>();

        List<Integer> historyScrollY = new ArrayList<>();
        List<String> historyMarkdown = new ArrayList<>();
        List<String> historyTitles = new ArrayList<>();
        List<Map<String, Integer>> historyAnchorMaps = new ArrayList<>();
        List<List<HtmlToMarkdownConverter.HeadingItem>> historyHeadings = new ArrayList<>();

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
                historyMarkdown = new ArrayList<>(historyMarkdown.subList(0, historyIndex + 1));
                historyTitles = new ArrayList<>(historyTitles.subList(0, historyIndex + 1));
                historyAnchorMaps = new ArrayList<>(historyAnchorMaps.subList(0, historyIndex + 1));
                historyHeadings = new ArrayList<>(historyHeadings.subList(0, historyIndex + 1));
            }
            history.add(url);
            historyScrollY.add(0);
            historyMarkdown.add("");
            historyTitles.add("Untitled Page");
            historyAnchorMaps.add(new HashMap<>());
            historyHeadings.add(new ArrayList<>());
            historyIndex++;
            currentUrl = url;
            restoreCurrentState();
        }

        void saveCurrentState(String markdown, String title, Map<String, Integer> anchors, List<HtmlToMarkdownConverter.HeadingItem> headingsList) {
            if (historyIndex >= 0 && historyIndex < history.size()) {
                historyMarkdown.set(historyIndex, markdown);
                historyTitles.set(historyIndex, title);
                historyAnchorMaps.set(historyIndex, anchors);
                historyHeadings.set(historyIndex, headingsList);
            }
            this.markdownContent = markdown;
            this.pageTitle = title;
            this.anchorMap = anchors;
            this.headings = headingsList != null ? headingsList : new ArrayList<>();
        }

        void restoreCurrentState() {
            if (historyIndex >= 0 && historyIndex < history.size()) {
                this.markdownContent = historyMarkdown.get(historyIndex);
                this.pageTitle = historyTitles.get(historyIndex);
                this.anchorMap = historyAnchorMaps.get(historyIndex);
                this.headings = historyHeadings.get(historyIndex);
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
        return "Velocity Markdown Browser";
    }

    private View scrollView;
    private View homePageContainer;
    private EditText homeSearchInput;
    private ImageButton btnHomeSearchGo;

    private TextView markdownTextView;
    private LinearLayout pageLayoutContainer;
    private LinearLayout tabContainer;
    private EditText urlInput;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnForward, btnNewTab;

    private View btnTopTabs;
    private View btnTabs;
    private TextView tvTabCount;
    private ImageButton btnSearch;
    private ImageButton btnMenu;

    private DrawerLayout drawerLayout;
    private RecyclerView rvDrawerTree;
    private DocumentTreeAdapter documentTreeAdapter;
    private DocumentManager.SortOrder currentSortOrder = DocumentManager.SortOrder.NAME_ASC;
    private File currentActiveDocumentFile = null;
    private File currentSelectedFolder = null;
    private EditText etDrawerSearch;
    private TextView tvVaultStats;
    private TextView tvVaultName;
    private View tvDrawerEmpty;

    private List<Tab> tabList = new ArrayList<>();
    private int currentTabIdx = -1;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private QuickJs quickJs;

    private ImageButton btnOutline;
    private ImageButton btnMore;
    private ReaderTheme currentReaderTheme = ReaderTheme.OLED_DARK;
    private View topHeaderContainer;
    private View bottomBarContainer;
    private boolean isBarsVisible = true;
    private android.speech.tts.TextToSpeech textToSpeech;
    private boolean isSpeaking = false;

    private ActivityResultLauncher<String[]> openMarkdownFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);

        // Crash logger to clipboard
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
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
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#121214"));
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#121214"));
        }
        androidx.core.view.WindowInsetsControllerCompat insetsController =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);

        // Activity Result Launcher for opening local .md files
        openMarkdownFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        openMarkdownUri(uri);
                    }
                }
        );

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
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
                            if (tab.markdownContent != null && !tab.markdownContent.isEmpty()) {
                                urlInput.setText(tab.currentUrl);
                                renderMarkdownContent(tab);
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
            LocationCallback callback = url -> runOnUiThread(() -> loadUrl(url, true));
            quickJs.set("locationCallback", LocationCallback.class, callback);
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
            android.util.Log.e("Velocity", "QuickJS init note: " + e.getMessage());
        }

        // Initialize UI
        topHeaderContainer = findViewById(R.id.topHeaderContainer);
        bottomBarContainer = findViewById(R.id.bottomBarContainer);
        scrollView = findViewById(R.id.scrollView);
        homePageContainer = findViewById(R.id.homePageContainer);
        homeSearchInput = findViewById(R.id.homeSearchInput);
        btnHomeSearchGo = findViewById(R.id.btnHomeSearchGo);

        // System Bar Window Insets handling for edge-to-edge display (Android 5+ API 21-36 compatible)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, windowInsets) -> {
            int topInset = 0;
            int bottomInset = 0;

            try {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                );
                topInset = insets.top;
                bottomInset = insets.bottom;
            } catch (Exception ignored) {}

            if (topInset <= 0) {
                topInset = windowInsets.getSystemWindowInsetTop();
            }
            if (bottomInset <= 0) {
                bottomInset = windowInsets.getSystemWindowInsetBottom();
            }

            if (topHeaderContainer != null) {
                topHeaderContainer.setPadding(0, Math.max(topInset, dpToPx(12)), 0, 0);
            }

            if (bottomBarContainer != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBarContainer.getLayoutParams();
                if (lp != null) {
                    lp.bottomMargin = bottomInset + dpToPx(14);
                    bottomBarContainer.setLayoutParams(lp);
                }
            }

            int topPaddingTotal = Math.max(topInset, dpToPx(24)) + dpToPx(50);
            int bottomPaddingTotal = Math.max(bottomInset, dpToPx(16)) + dpToPx(80);

            if (scrollView != null) {
                scrollView.setPadding(
                        scrollView.getPaddingLeft(),
                        topPaddingTotal,
                        scrollView.getPaddingRight(),
                        bottomPaddingTotal
                );
            }

            if (homePageContainer != null) {
                homePageContainer.setPadding(
                        homePageContainer.getPaddingLeft(),
                        topPaddingTotal,
                        homePageContainer.getPaddingRight(),
                        bottomPaddingTotal
                );
            }

            return windowInsets;
        });

        // Load saved reader theme
        String savedTheme = getSharedPreferences("reader_prefs", MODE_PRIVATE).getString("selected_theme", ReaderTheme.LIGHT.name());
        try {
            currentReaderTheme = ReaderTheme.valueOf(savedTheme);
        } catch (Exception e) {
            currentReaderTheme = ReaderTheme.LIGHT;
        }

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
        }

        markdownTextView = findViewById(R.id.markdownTextView);
        if (markdownTextView != null) {
            markdownTextView.setTextIsSelectable(true);
        }
        pageLayoutContainer = findViewById(R.id.pageLayoutContainer);
        tabContainer = findViewById(R.id.tabContainer);
        urlInput = findViewById(R.id.urlInput);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnOutline = findViewById(R.id.btnOutline);
        btnNewTab = findViewById(R.id.btnNewTab);
        btnMore = findViewById(R.id.btnMore);

        btnTopTabs = findViewById(R.id.btnTopTabs);
        btnTabs = findViewById(R.id.btnTabs);
        tvTabCount = findViewById(R.id.tvTabCount);
        btnSearch = findViewById(R.id.btnSearch);
        btnMenu = findViewById(R.id.btnMenu);

        if (btnOutline != null) {
            btnOutline.setOnClickListener(v -> showArticleOutline());
        }
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> showPageOptionsSheet());
        }
        if (btnTopTabs != null) {
            btnTopTabs.setOnClickListener(v -> toggleDocumentsDrawer());
        }
        if (btnTabs != null) {
            btnTabs.setOnClickListener(v -> showTabsSheet());
        }
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> showSearchDialog());
        }
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> showAppMenuSheet());
        }

        initDocumentsDrawer();
        setupListeners();

        // Check for incoming intent
        handleIntent(getIntent());
        
        Toast.makeText(this, "Welcome to " + getBrowserName(), Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void createNewTab(String url) {
        Tab tab = new Tab(url);
        tabList.add(tab);
        
        if (tabContainer != null) {
            TextView tabView = new TextView(this);
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
        }
        
        switchToTab(tabList.size() - 1);
        updateButtons();
        if (!url.equals("home")) {
            loadUrl(url, false);
        }
    }

    private void setupListeners() {
        if (urlInput != null) {
            urlInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    handleUrlInput();
                    return true;
                }
                return false;
            });
        }

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
        View btnCategoryWiki = findViewById(R.id.btnCategoryWiki);
        View btnCategoryNews = findViewById(R.id.btnCategoryNews);
        View btnCategoryTech = findViewById(R.id.btnCategoryTech);
        View btnCategoryBooks = findViewById(R.id.btnCategoryBooks);
        View btnHomeBookmarks = findViewById(R.id.btnHomeBookmarks);
        View btnOpenMarkdownFile = findViewById(R.id.btnOpenMarkdownFile);
        View btnHomeHistory = findViewById(R.id.btnHomeHistory);

        if (btnCategoryWiki != null) btnCategoryWiki.setOnClickListener(v -> loadUrl("https://en.wikipedia.org/wiki/Special:Random", true));
        if (btnCategoryNews != null) btnCategoryNews.setOnClickListener(v -> loadUrl("https://lite.cnn.com", true));
        if (btnCategoryTech != null) btnCategoryTech.setOnClickListener(v -> loadUrl("https://news.ycombinator.com/", true));
        if (btnCategoryBooks != null) btnCategoryBooks.setOnClickListener(v -> loadUrl("https://gutenberg.org/", true));

        if (btnHomeBookmarks != null) btnHomeBookmarks.setOnClickListener(v -> openDocumentsDrawer());
        if (btnOpenMarkdownFile != null) btnOpenMarkdownFile.setOnClickListener(v -> pickLocalMarkdownFile());
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
                            if (tab.markdownContent != null && !tab.markdownContent.isEmpty()) {
                                urlInput.setText(tab.currentUrl);
                                renderMarkdownContent(tab);
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
                            if (tab.markdownContent != null && !tab.markdownContent.isEmpty()) {
                                urlInput.setText(tab.currentUrl);
                                renderMarkdownContent(tab);
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

        if (btnNewTab != null) {
            btnNewTab.setOnClickListener(v -> {
                createNewTab("home");
                showSearchDialog();
            });
        }
    }

    private void pickLocalMarkdownFile() {
        try {
            openMarkdownFileLauncher.launch(new String[]{"text/markdown", "text/x-markdown", "text/plain", "*/*"});
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open file picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openMarkdownUri(Uri uri) {
        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                String raw = sb.toString();
                String title = "Markdown Document";
                String markdown = raw;

                // Extract frontmatter if present
                if (raw.startsWith("---")) {
                    int secondDash = raw.indexOf("---", 3);
                    if (secondDash != -1) {
                        String frontmatter = raw.substring(3, secondDash);
                        markdown = raw.substring(secondDash + 3).trim();
                        for (String fLine : frontmatter.split("\n")) {
                            if (fLine.startsWith("title:")) {
                                title = fLine.substring(6).trim();
                            }
                        }
                    }
                }

                // If no frontmatter title, parse first heading
                if ("Markdown Document".equals(title)) {
                    Pattern p = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
                    Matcher m = p.matcher(markdown);
                    if (m.find()) {
                        title = m.group(1).trim();
                    } else {
                        String path = uri.getLastPathSegment();
                        if (path != null) title = path;
                    }
                }

                final String finalTitle = title;
                final String finalMarkdown = markdown;
                final String fileUrl = uri.toString();

                runOnUiThread(() -> {
                    if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) {
                        createNewTab(fileUrl);
                    }
                    Tab tab = tabList.get(currentTabIdx);
                    tab.currentUrl = fileUrl;
                    tab.addHistory(fileUrl);
                    tab.saveCurrentState(finalMarkdown, finalTitle, new HashMap<>(), extractMarkdownHeadings(finalMarkdown));

                    urlInput.setText(fileUrl);
                    homePageContainer.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);
                    renderMarkdownContent(tab);
                    scrollView.post(() -> scrollView.scrollTo(0, 0));
                    updateButtons();
                    Toast.makeText(MainActivity.this, "Loaded: " + finalTitle, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error reading .md file: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private List<HtmlToMarkdownConverter.HeadingItem> extractMarkdownHeadings(String markdown) {
        List<HtmlToMarkdownConverter.HeadingItem> list = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return list;

        Pattern pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String heading = matcher.group(2).trim();
            String anchorId = heading.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            list.add(new HtmlToMarkdownConverter.HeadingItem(level, heading, anchorId));
        }
        return list;
    }

    public static String extractDomain(String url) {
        if (url == null || url.trim().isEmpty() || "home".equals(url)) return null;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) {
                host = host.toLowerCase(Locale.ROOT);
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                if (host.startsWith("m.")) {
                    host = host.substring(2);
                }
                return host;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String extractSearchQuery(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() != null ? uri.getPath().toLowerCase(Locale.ROOT) : "";

            // Google Search: google.com/search?q=..., google.co.*/search?q=...
            if (host.contains("google.") && (path.contains("search") || path.contains("webhp") || path.equals("/"))) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Bing Search: bing.com/search?q=...
            if (host.contains("bing.com") && path.contains("search")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // DuckDuckGo Search: duckduckgo.com/?q=..., html.duckduckgo.com/html/?q=...
            if (host.contains("duckduckgo.com")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Yahoo Search: search.yahoo.com/search?p=..., yahoo.com/search?p=...
            if (host.contains("yahoo.com") && path.contains("search")) {
                String p = uri.getQueryParameter("p");
                if (p != null && !p.trim().isEmpty()) return p.trim();
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Ecosia Search: ecosia.org/search?q=...
            if (host.contains("ecosia.org") && path.contains("search")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Brave Search: search.brave.com/search?q=...
            if (host.contains("brave.com") && path.contains("search")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Startpage Search: startpage.com/sp/search?query=...
            if (host.contains("startpage.com")) {
                String q = uri.getQueryParameter("query");
                if (q == null || q.isEmpty()) q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Yandex Search: yandex.com/search/?text=...
            if (host.contains("yandex.") && path.contains("search")) {
                String text = uri.getQueryParameter("text");
                if (text != null && !text.trim().isEmpty()) return text.trim();
            }

            // Baidu Search: baidu.com/s?wd=...
            if (host.contains("baidu.com") && (path.contains("/s") || path.contains("search"))) {
                String wd = uri.getQueryParameter("wd");
                if (wd != null && !wd.trim().isEmpty()) return wd.trim();
                String word = uri.getQueryParameter("word");
                if (word != null && !word.trim().isEmpty()) return word.trim();
            }

            // Qwant Search: qwant.com/?q=...
            if (host.contains("qwant.com")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }

            // Ask.com Search: ask.com/web?q=...
            if (host.contains("ask.com") && path.contains("web")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void openSearchTab(String query, boolean forceNewTab) {
        if (query == null || query.trim().isEmpty()) return;
        String trimmedQuery = query.trim();
        String searchUrl;
        try {
            searchUrl = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(trimmedQuery, "UTF-8");
        } catch (Exception e) {
            searchUrl = "https://html.duckduckgo.com/html/?q=" + trimmedQuery;
        }

        Tab tab;
        if (forceNewTab || currentTabIdx < 0 || currentTabIdx >= tabList.size() || (!"home".equals(tabList.get(currentTabIdx).currentUrl) && tabList.get(currentTabIdx).markdownContent != null && !tabList.get(currentTabIdx).markdownContent.isEmpty())) {
            createNewTab(searchUrl);
            tab = tabList.get(currentTabIdx);
        } else {
            tab = tabList.get(currentTabIdx);
            tab.currentUrl = searchUrl;
            tab.pageTitle = "🔍 " + trimmedQuery;
            tab.addHistory(searchUrl);
            urlInput.setText(searchUrl);
            TextView tv = (TextView) tabContainer.getChildAt(currentTabIdx);
            if (tv != null) {
                tv.setText(tab.pageTitle);
            }
        }

        performEmbeddedSearch(trimmedQuery, null, tab, false);
    }

    private void performEmbeddedSearch(String query, String postPayload, Tab tab, boolean appendResults) {
        if (tab == null || query == null || query.trim().isEmpty()) return;
        final String cleanQuery = query.trim();
        String tempUrl;
        try {
            tempUrl = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(cleanQuery, "UTF-8");
        } catch (Exception e) {
            tempUrl = "https://html.duckduckgo.com/html/?q=" + cleanQuery;
        }
        final String searchUrl = tempUrl;

        tab.currentUrl = searchUrl;
        tab.pageTitle = "🔍 " + cleanQuery;
        urlInput.setText(searchUrl);

        runOnUiThread(() -> {
            homePageContainer.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(30);
            updateButtons();

            TextView tv = (TextView) tabContainer.getChildAt(tabList.indexOf(tab));
            if (tv != null) {
                tv.setText(tab.pageTitle);
            }

            if (!appendResults) {
                String loadingMd = "# 🔍 Search: " + cleanQuery + "\n\n> 🦆 **These search results are provided by DuckDuckGo**\n\n*Searching DuckDuckGo for \"" + cleanQuery + "\"...*";
                tab.markdownContent = loadingMd;
                if (tabList.indexOf(tab) == currentTabIdx) {
                    renderMarkdownContent(tab);
                }
            }
        });

        if (!appendResults) {
            HistoryManager.addHistory(MainActivity.this, "🔍 " + cleanQuery, searchUrl);
        }

        executor.execute(() -> {
            StringBuilder mdResults = new StringBuilder();
            List<HtmlToMarkdownConverter.HeadingItem> headings = new ArrayList<>();

            int startCount = 0;
            if (appendResults && tab.markdownContent != null) {
                String baseMd = tab.markdownContent;
                // Strip previous pagination link if present
                int lastSep = baseMd.lastIndexOf("\n---\n\n### [➡️");
                if (lastSep == -1) {
                    lastSep = baseMd.lastIndexOf("\n---\n\n*End of search results.*");
                }
                if (lastSep != -1) {
                    baseMd = baseMd.substring(0, lastSep).trim() + "\n\n";
                }
                mdResults.append(baseMd);
                if (tab.headings != null) {
                    headings.addAll(tab.headings);
                    startCount = headings.size() - 1;
                    if (startCount < 0) startCount = 0;
                }
            } else {
                mdResults.append("# 🔍 Search: ").append(cleanQuery).append("\n\n");
                mdResults.append("> 🦆 **These search results are provided by DuckDuckGo**\n\n");
                headings.add(new HtmlToMarkdownConverter.HeadingItem(1, "Search: " + cleanQuery, "search"));
            }

            try {
                String htmlBody = "";
                int responseCode = -1;

                // Candidate endpoints & methods to try
                String[] endpoints = new String[]{
                    "https://html.duckduckgo.com/html/",
                    "https://lite.duckduckgo.com/lite/",
                    "https://duckduckgo.com/html/"
                };

                for (String endpointUrl : endpoints) {
                    try {
                        URL url = new URL(endpointUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0");
                        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                        conn.setRequestProperty("Referer", "https://duckduckgo.com/");
                        conn.setRequestProperty("DNT", "1");
                        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");

                        String postData;
                        if (postPayload != null && !postPayload.isEmpty()) {
                            postData = postPayload;
                        } else {
                            postData = "q=" + java.net.URLEncoder.encode(cleanQuery, "UTF-8") + "&b=&kl=wt-wt";
                        }

                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("Origin", "https://duckduckgo.com");
                        conn.setDoOutput(true);
                        conn.getOutputStream().write(postData.getBytes(StandardCharsets.UTF_8));

                        responseCode = conn.getResponseCode();
                        InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
                        if (is != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();
                            String respText = sb.toString();
                            if (!respText.trim().isEmpty()) {
                                htmlBody = respText;
                                // If we got results or valid page, break early
                                if (htmlBody.contains("result__title") || htmlBody.contains("result-link") || htmlBody.contains("class=\"result\"")) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // If POST failed, try GET fallback
                if (htmlBody.isEmpty() || (!htmlBody.contains("result__title") && !htmlBody.contains("result-link"))) {
                    try {
                        String getUrl = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(cleanQuery, "UTF-8");
                        URL url = new URL(getUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0");
                        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                        conn.setRequestProperty("Referer", "https://duckduckgo.com/");
                        responseCode = conn.getResponseCode();
                        InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
                        if (is != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();
                            htmlBody = sb.toString();
                        }
                    } catch (Exception ignored) {}
                }

                if (!htmlBody.isEmpty()) {
                    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlBody, "https://html.duckduckgo.com/html/");
                    int count = startCount;

                    // 1. Try modern DuckDuckGo HTML format (.result)
                    org.jsoup.select.Elements results = doc.select(".result, .results_links, .web-result");
                    for (org.jsoup.nodes.Element res : results) {
                        org.jsoup.nodes.Element titleElem = res.selectFirst(".result__title a, .result__a, a.result-link, a.large");
                        org.jsoup.nodes.Element snippetElem = res.selectFirst(".result__snippet");
                        org.jsoup.nodes.Element urlElem = res.selectFirst(".result__url");

                        if (titleElem != null) {
                            String title = titleElem.text().trim();
                            String href = titleElem.attr("href");

                            if (href.contains("uddg=")) {
                                try {
                                    Uri uri = Uri.parse(href.startsWith("http") ? href : "https://html.duckduckgo.com" + href);
                                    String rawTarget = uri.getQueryParameter("uddg");
                                    if (rawTarget != null && !rawTarget.isEmpty()) {
                                        href = java.net.URLDecoder.decode(rawTarget, "UTF-8");
                                    }
                                } catch (Exception ignored) {}
                            }

                            String snippet = snippetElem != null ? snippetElem.text().trim() : "";
                            String displayUrl = urlElem != null ? urlElem.text().trim() : href;

                            if (href != null && !href.isEmpty() && !href.startsWith("javascript:")) {
                                count++;
                                mdResults.append("---\n\n");
                                mdResults.append("### [").append(count).append(". ").append(title).append("](").append(href).append(")\n\n");
                                if (!snippet.isEmpty()) {
                                    mdResults.append("> ").append(snippet).append("\n\n");
                                }
                                mdResults.append("🔗 `").append(displayUrl).append("`\n\n");

                                String anchor = "result-" + count;
                                headings.add(new HtmlToMarkdownConverter.HeadingItem(3, count + ". " + title, anchor));
                            }
                        }
                    }

                    // 2. If no modern results found, try DDG Lite table format
                    if (count == startCount) {
                        org.jsoup.select.Elements liteLinks = doc.select("a.result-link");
                        for (org.jsoup.nodes.Element link : liteLinks) {
                            String title = link.text().trim();
                            String href = link.attr("href");

                            if (href.contains("uddg=")) {
                                try {
                                    Uri uri = Uri.parse(href.startsWith("http") ? href : "https://duckduckgo.com" + href);
                                    String rawTarget = uri.getQueryParameter("uddg");
                                    if (rawTarget != null && !rawTarget.isEmpty()) {
                                        href = java.net.URLDecoder.decode(rawTarget, "UTF-8");
                                    }
                                } catch (Exception ignored) {}
                            }

                            // Find snippet and display url from subsequent table rows
                            String snippet = "";
                            String displayUrl = href;
                            try {
                                org.jsoup.nodes.Element parentTr = link.closest("tr");
                                if (parentTr != null) {
                                    org.jsoup.nodes.Element snippetTr = parentTr.nextElementSibling();
                                    if (snippetTr != null) {
                                        org.jsoup.nodes.Element snipTd = snippetTr.selectFirst(".result-snippet");
                                        if (snipTd != null) snippet = snipTd.text().trim();

                                        org.jsoup.nodes.Element urlTr = snippetTr.nextElementSibling();
                                        if (urlTr != null) {
                                            org.jsoup.nodes.Element urlTd = urlTr.selectFirst(".url");
                                            if (urlTd != null) displayUrl = urlTd.text().trim();
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}

                            if (href != null && !href.isEmpty()) {
                                count++;
                                mdResults.append("---\n\n");
                                mdResults.append("### [").append(count).append(". ").append(title).append("](").append(href).append(")\n\n");
                                if (!snippet.isEmpty()) {
                                    mdResults.append("> ").append(snippet).append("\n\n");
                                }
                                mdResults.append("🔗 `").append(displayUrl).append("`\n\n");

                                String anchor = "result-" + count;
                                headings.add(new HtmlToMarkdownConverter.HeadingItem(3, count + ". " + title, anchor));
                            }
                        }
                    }

                    // Extract next page form (q, s, nextParams, v, o, dc, api, vqd, kl)
                    org.jsoup.nodes.Element nextForm = doc.selectFirst("form[action*='/html/']:has(input[value*='Next']), form[action*='/lite/']:has(input[value*='Next']), form:has(input[value*='Next']), form:has(input[name='s']), form:has(input[name='nextParams'])");
                    String nextPayload = "";
                    if (nextForm != null) {
                        StringBuilder sbNext = new StringBuilder();
                        for (org.jsoup.nodes.Element inp : nextForm.select("input")) {
                            String name = inp.attr("name");
                            String val = inp.attr("value");
                            if (!name.isEmpty() && !"submit".equalsIgnoreCase(inp.attr("type"))) {
                                if (sbNext.length() > 0) sbNext.append("&");
                                sbNext.append(java.net.URLEncoder.encode(name, "UTF-8"))
                                        .append("=")
                                        .append(java.net.URLEncoder.encode(val, "UTF-8"));
                            }
                        }
                        nextPayload = sbNext.toString();
                    }

                    if (count == 0 && !appendResults) {
                        mdResults.append("*No results found for \"").append(cleanQuery).append("\". Try another search.*");
                    } else if (!nextPayload.isEmpty()) {
                        String encodedNext = java.net.URLEncoder.encode(nextPayload, "UTF-8");
                        String encodedQ = java.net.URLEncoder.encode(cleanQuery, "UTF-8");
                        mdResults.append("---\n\n");
                        mdResults.append("### [➡️ **Load More Results (Next Page)**](velocity://search_next?q=").append(encodedQ).append("&payload=").append(encodedNext).append(")\n\n");
                    } else {
                        mdResults.append("---\n\n*End of search results.*\n\n");
                    }
                } else {
                    mdResults.append("### Search error (HTTP ").append(responseCode).append(")\n\nUnable to fetch search results. Please check your connection.");
                }
            } catch (Exception e) {
                mdResults.append("### Connection Error\n\n").append(e.getMessage());
            }

            final String finalMarkdown = mdResults.toString();
            final String finalTitle = "🔍 " + cleanQuery;

            runOnUiThread(() -> {
                progressBar.setProgress(90);
                tab.saveCurrentState(finalMarkdown, finalTitle, new HashMap<>(), headings);

                if (tabList.indexOf(tab) == currentTabIdx) {
                    renderMarkdownContent(tab);
                    if (!appendResults) {
                        scrollView.post(() -> scrollView.scrollTo(0, 0));
                    }
                    TextView tv = (TextView) tabContainer.getChildAt(currentTabIdx);
                    if (tv != null) {
                        tv.setText(tab.pageTitle);
                    }
                }

                progressBar.setVisibility(View.GONE);
                updateButtons();
            });
        });
    }

    private void triggerHomeSearch() {
        String query = homeSearchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            homeSearchInput.setText("");
            openSearchTab(query, false);
        }
    }

    private void closeCurrentTab() {
        if (tabList.size() <= 1) {
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                tab.addHistory("home");
                tab.markdownContent = "";
                tab.pageTitle = "Home";
                switchToTab(currentTabIdx);
            }
            updateButtons();
            return;
        }
        if (tabContainer != null && tabContainer.getChildCount() > currentTabIdx) {
            tabContainer.removeViewAt(currentTabIdx);
        }
        tabList.remove(currentTabIdx);
        int newIdx = Math.max(0, currentTabIdx - 1);
        switchToTab(newIdx);
        updateButtons();
    }

    private void hideNavigationBars() {
        if (!isBarsVisible) return;
        isBarsVisible = false;
        if (topHeaderContainer != null) {
            topHeaderContainer.animate()
                    .translationY(-topHeaderContainer.getHeight() - dpToPx(30))
                    .alpha(0f)
                    .setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        if (bottomBarContainer != null) {
            bottomBarContainer.animate()
                    .translationY(bottomBarContainer.getHeight() + dpToPx(100))
                    .alpha(0f)
                    .setDuration(240)
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
                    .alpha(1f)
                    .setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        if (bottomBarContainer != null) {
            bottomBarContainer.animate()
                    .translationY(0)
                    .alpha(1f)
                    .setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void showTabsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_tabs_bottom_sheet, null);
        dialog.setContentView(sheetView);

        TextView tvHeader = sheetView.findViewById(R.id.tvTabsHeaderTitle);
        if (tvHeader != null) {
            tvHeader.setText("Open Tabs (" + tabList.size() + ")");
        }

        View btnNewTabInSheet = sheetView.findViewById(R.id.btnNewTabInSheet);
        if (btnNewTabInSheet != null) {
            btnNewTabInSheet.setOnClickListener(v -> {
                dialog.dismiss();
                createNewTab("home");
                showSearchDialog();
            });
        }

        LinearLayout container = sheetView.findViewById(R.id.tabCardsContainer);
        if (container != null) {
            container.removeAllViews();
            for (int i = 0; i < tabList.size(); i++) {
                final int tabIdx = i;
                Tab tab = tabList.get(i);
                View card = getLayoutInflater().inflate(R.layout.item_tab_card, container, false);

                TextView tvBadge = card.findViewById(R.id.tvTabBadgeNumber);
                TextView tvTitle = card.findViewById(R.id.tvTabCardTitle);
                TextView tvUrl = card.findViewById(R.id.tvTabCardUrl);
                View btnClose = card.findViewById(R.id.btnTabCardClose);

                if (tvBadge != null) {
                    tvBadge.setText(String.valueOf(i + 1));
                }
                if (tvTitle != null) {
                    tvTitle.setText(tab.pageTitle != null && !tab.pageTitle.isEmpty() ? tab.pageTitle : "Untitled Tab");
                }
                if (tvUrl != null) {
                    tvUrl.setText(tab.currentUrl != null ? tab.currentUrl : "about:blank");
                }

                if (tabIdx == currentTabIdx) {
                    GradientDrawable activeBg = new GradientDrawable();
                    activeBg.setColor(android.graphics.Color.parseColor("#2E2B38"));
                    activeBg.setStroke(dpToPx(2), android.graphics.Color.parseColor("#D0BCFF"));
                    activeBg.setCornerRadius(dpToPx(20));
                    card.setBackground(activeBg);
                }

                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    switchToTab(tabIdx);
                });

                if (btnClose != null) {
                    btnClose.setOnClickListener(v -> {
                        if (tabList.size() <= 1) {
                            tabList.get(0).currentUrl = "home";
                            tabList.get(0).pageTitle = "Home";
                            tabList.get(0).markdownContent = "";
                            switchToTab(0);
                            dialog.dismiss();
                        } else {
                            tabList.remove(tabIdx);
                            if (tabIdx == currentTabIdx) {
                                int newIdx = Math.max(0, tabIdx - 1);
                                switchToTab(newIdx);
                            } else if (tabIdx < currentTabIdx) {
                                currentTabIdx--;
                            }
                            dialog.dismiss();
                            showTabsSheet();
                        }
                        updateButtons();
                    });
                }

                container.addView(card);
            }
        }

        dialog.show();
    }

    private void showSearchDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_search_bottom_sheet, null);
        dialog.setContentView(sheetView);

        EditText input = sheetView.findViewById(R.id.dialogSearchInput);
        ImageButton btnClear = sheetView.findViewById(R.id.dialogBtnClear);
        View btnGo = sheetView.findViewById(R.id.dialogBtnGo);

        if (input != null && currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
            Tab currentTab = tabList.get(currentTabIdx);
            if (currentTab.currentUrl != null && !currentTab.currentUrl.equals("home")) {
                input.setText(currentTab.currentUrl);
                input.selectAll();
            }
        }

        if (input != null) {
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (btnClear != null) {
                        btnClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnClear != null && input != null) {
            btnClear.setOnClickListener(v -> input.setText(""));
        }

        Runnable executeSearch = () -> {
            if (input == null) return;
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                dialog.dismiss();
                if (urlInput != null) urlInput.setText(text);
                handleUrlInputDirect(text);
            }
        };

        if (btnGo != null) {
            btnGo.setOnClickListener(v -> executeSearch.run());
        }

        if (input != null) {
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    executeSearch.run();
                    return true;
                }
                return false;
            });
        }

        View chipDuck = sheetView.findViewById(R.id.chipDuckDuckGo);
        View chipWiki = sheetView.findViewById(R.id.chipWikipedia);
        View chipHacker = sheetView.findViewById(R.id.chipHackerNews);
        View chipGuten = sheetView.findViewById(R.id.chipGutenberg);

        if (chipDuck != null) chipDuck.setOnClickListener(v -> {
            dialog.dismiss();
            String q = (input != null) ? input.getText().toString().trim() : "";
            openSearchTab(q.isEmpty() ? "DuckDuckGo" : q, false);
        });
        if (chipWiki != null) chipWiki.setOnClickListener(v -> { dialog.dismiss(); loadUrl("https://en.wikipedia.org/wiki/Special:Random", true); });
        if (chipHacker != null) chipHacker.setOnClickListener(v -> { dialog.dismiss(); loadUrl("https://news.ycombinator.com/", true); });
        if (chipGuten != null) chipGuten.setOnClickListener(v -> { dialog.dismiss(); loadUrl("https://gutenberg.org/", true); });

        dialog.setOnShowListener(d -> {
            if (input != null) {
                input.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        dialog.show();
    }

    private void handleUrlInputDirect(String input) {
        if (input == null || input.isEmpty()) return;
        String searchQuery = extractSearchQuery(input);
        if (searchQuery != null) {
            openSearchTab(searchQuery, false);
        } else if (isUrl(input)) {
            String processedUrl = processUrl(input);
            loadUrl(processedUrl, true);
        } else {
            String currentDomain = null;
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                currentDomain = extractDomain(tabList.get(currentTabIdx).currentUrl);
            }
            String finalQuery = input;
            if (currentDomain != null && !currentDomain.contains("duckduckgo.com") && !input.contains("site:")) {
                finalQuery = input + " site:" + currentDomain;
            }
            openSearchTab(finalQuery, false);
        }
    }

    private void switchToTab(int position) {
        showNavigationBars();
        if (position >= 0 && position < tabList.size()) {
            currentTabIdx = position;
            Tab tab = tabList.get(position);
            
            if (tab.currentUrl.equals("home")) {
                if (urlInput != null) {
                    urlInput.setText("");
                    urlInput.setHint("Search or type URL");
                }
                homePageContainer.setVisibility(View.VISIBLE);
                scrollView.setVisibility(View.GONE);
            } else {
                if (urlInput != null) {
                    urlInput.setText(tab.currentUrl);
                    String domain = extractDomain(tab.currentUrl);
                    if (domain != null && !domain.contains("duckduckgo.com")) {
                        urlInput.setHint("Search " + domain + " or type URL");
                    } else {
                        urlInput.setHint("Search or type URL");
                    }
                }
                homePageContainer.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE);
                renderMarkdownContent(tab);
                final int savedY = getUrlScrollPosition(tab.currentUrl);
                scrollView.post(() -> scrollView.scrollTo(0, savedY));
            }
            
            updateButtons();

            if (tabContainer != null) {
                for (int i = 0; i < tabContainer.getChildCount(); i++) {
                    TextView tv = (TextView) tabContainer.getChildAt(i);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setCornerRadius(dpToPx(6));
                    if (i == position) {
                        tv.setTextColor(0xFF202124);
                        tv.setTypeface(null, android.graphics.Typeface.BOLD);
                        gd.setColor(0xFFFFFFFF);
                        gd.setStroke(dpToPx(1), 0xFFDADCE0);
                    } else {
                        tv.setTextColor(0xFF5F6368);
                        tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                        gd.setColor(0xFFE8EAED);
                        gd.setStroke(dpToPx(1), 0xFFDADCE0);
                    }
                    tv.setBackground(gd);
                }
            }
        }
    }

    private boolean isUrl(String input) {
        if (input == null || input.isEmpty()) return false;
        if (input.contains(" ")) return false;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://") || input.startsWith("content://") || input.startsWith("about:")) return true;
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
            String searchQuery = extractSearchQuery(input);
            if (searchQuery != null) {
                openSearchTab(searchQuery, false);
            } else if (isUrl(input)) {
                String processedUrl = processUrl(input);
                loadUrl(processedUrl, true);
            } else {
                // Check if currently viewing an active website -> Scope search to site:domain
                String currentDomain = null;
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    currentDomain = extractDomain(tabList.get(currentTabIdx).currentUrl);
                }
                String finalQuery = input;
                if (currentDomain != null && !currentDomain.contains("duckduckgo.com") && !input.contains("site:")) {
                    finalQuery = input + " site:" + currentDomain;
                }
                openSearchTab(finalQuery, false);
            }
        }
    }

    private void loadUrl(String url, boolean addToHistory) {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;

        // Check if loading a search engine URL with query
        String searchQuery = extractSearchQuery(url);
        if (searchQuery != null) {
            openSearchTab(searchQuery, false);
            return;
        }
        
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
                    String contentType = conn.getContentType();
                    if (contentType != null) {
                        String lowerCt = contentType.toLowerCase(java.util.Locale.ROOT);
                        if (lowerCt.startsWith("image/")) {
                            conn.disconnect();
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Intent intent = new Intent(MainActivity.this, ImageViewerActivity.class);
                                intent.putExtra("image_url", finalUrl);
                                startActivity(intent);
                            });
                            return;
                        }
                        if (!lowerCt.contains("text/html") && !lowerCt.contains("text/plain") && !lowerCt.contains("text/markdown")
                                && !lowerCt.contains("application/xhtml+xml") && !lowerCt.contains("text/xml") && !lowerCt.contains("application/xml")
                                && !lowerCt.contains("application/json") && !lowerCt.contains("text/csv")) {
                            conn.disconnect();
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                openInSystem(finalUrl, contentType.split(";")[0].trim());
                            });
                            return;
                        }
                    }

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine).append("\n");
                    }
                    in.close();
                    response = content.toString();

                    String lowerResp = response.toLowerCase(Locale.ROOT);
                    if (lowerResp.contains("challenges.cloudflare.com") ||
                        lowerResp.contains("cf-browser-verification") ||
                        lowerResp.contains("just a moment...") ||
                        lowerResp.contains("checking your browser before accessing") ||
                        lowerResp.contains("attention required! | cloudflare") ||
                        lowerResp.contains("ddos protection by cloudflare") ||
                        (lowerResp.contains("cloudflare") && lowerResp.contains("turnstile"))) {

                        response = "<h3>🛡️ Cloudflare Protection Detected</h3>" +
                                   "<p>This website (" + finalUrl + ") requires Cloudflare bot verification or JavaScript execution.</p>" +
                                   "<p><a href=\"action://load_web_engine\">👉 <b>Solve &amp; Load with Web Engine</b></a></p>" +
                                   "<hr/>" +
                                   "<p><a href=\"action://retry_load\">🔄 Retry Standard Load</a> &nbsp;|&nbsp; <a href=\"action://open_external\">📱 Open in External Browser</a></p>";
                    }
                } else if (responseCode == 403 || responseCode == 503 || responseCode == 429) {
                    response = "<h3>🛡️ Access Restricted (HTTP " + responseCode + ")</h3>" +
                               "<p>The server returned HTTP " + responseCode + ". This typically occurs when Cloudflare or anti-bot protection is active.</p>" +
                               "<p><a href=\"action://load_web_engine\">👉 <b>Solve &amp; Load with Web Engine</b></a></p>" +
                               "<hr/>" +
                               "<p><a href=\"action://retry_load\">🔄 Retry Standard Load</a> &nbsp;|&nbsp; <a href=\"action://open_external\">📱 Open in External Browser</a></p>";
                } else {
                    response = "<h3>Error " + responseCode + "</h3>" +
                               "<p>Unable to load the requested page (HTTP " + responseCode + ").</p>" +
                               "<p><a href=\"action://load_web_engine\">👉 <b>Try loading with Web Engine</b></a></p>" +
                               "<hr/>" +
                               "<p><a href=\"action://retry_load\">🔄 Retry Standard Load</a> &nbsp;|&nbsp; <a href=\"action://open_external\">📱 Open in External Browser</a></p>";
                }
                conn.disconnect();
            } catch (Exception e) {
                response = "<h3>Connection Notice</h3>" +
                           "<p>" + e.getMessage() + "</p>" +
                           "<p><a href=\"action://load_web_engine\">👉 <b>Try loading with Web Engine</b></a></p>" +
                           "<hr/>" +
                           "<p><a href=\"action://retry_load\">🔄 Retry</a> &nbsp;|&nbsp; <a href=\"action://open_external\">📱 Open in External Browser</a></p>";
            }

            final String htmlContent = response;

            // Clean modern tags to retro equivalents
            HtmlCleaner.Config config = HtmlCleaner.Config.defaultConfig();
            config.baseUrl = tab.currentUrl;
            String cleanedHtml = HtmlCleaner.clean(htmlContent, config).html;

            // Convert cleaned HTML into clean Markdown
            HtmlToMarkdownConverter.MarkdownDocument mdDoc = HtmlToMarkdownConverter.convert(cleanedHtml, config.baseUrl);

            runOnUiThread(() -> {
                progressBar.setProgress(90);

                tab.saveCurrentState(mdDoc.markdown, mdDoc.title, new HashMap<>(), mdDoc.headings);
                HistoryManager.addHistory(MainActivity.this, mdDoc.title, config.baseUrl);

                if (tabList.indexOf(tab) == currentTabIdx) {
                    renderMarkdownContent(tab);

                    final int savedY = getUrlScrollPosition(tab.currentUrl);
                    scrollView.post(() -> scrollView.scrollTo(0, savedY));

                    TextView tv = (TextView) tabContainer.getChildAt(currentTabIdx);
                    if (tv != null) {
                        tv.setText(tab.pageTitle);
                    }
                }

                progressBar.setVisibility(View.GONE);
                updateButtons();
            });
        });
    }

    private void renderMarkdownContent(Tab tab) {
        if (tab == null || markdownTextView == null) return;

        ReaderTheme theme = currentReaderTheme;
        scrollView.setBackgroundColor(theme.backgroundColor);
        pageLayoutContainer.setBackgroundColor(theme.backgroundColor);
        markdownTextView.setTextColor(theme.textColor);

        String md = (tab.markdownContent != null && !tab.markdownContent.isEmpty())
                ? tab.markdownContent
                : "# " + tab.pageTitle + "\n\n*No content available.*";

        String renderedMd = MathSymbolConverter.convertMathInMarkdown(md);

        tab.anchorMap.clear();

        markdownTextView.setMovementMethod(io.noties.markwon.ext.tables.TableAwareMovementMethod.create());

        Markwon markwon = MarkdownHelper.createMarkwon(
                MainActivity.this,
                theme,
                link -> handleLinkClick(link, tab),
                imageUrl -> {
                    if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                        Intent intent = new Intent(MainActivity.this, ImageViewerActivity.class);
                        intent.putExtra("image_url", imageUrl);
                        startActivity(intent);
                    }
                },
                tab.anchorMap
        );

        markwon.setMarkdown(markdownTextView, renderedMd);
        markdownTextView.setTextIsSelectable(true);
    }

    private void showArticleOutline() {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        if (tab.headings == null || tab.headings.isEmpty()) {
            Toast.makeText(this, "No article outline available", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_outline_sheet, null);
        dialog.setContentView(sheetView);

        LinearLayout container = sheetView.findViewById(R.id.outlineListContainer);
        if (container != null) {
            container.removeAllViews();
            for (int i = 0; i < tab.headings.size(); i++) {
                final HtmlToMarkdownConverter.HeadingItem item = tab.headings.get(i);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                int indent = Math.max(0, (item.level - 1) * dpToPx(12));
                row.setPadding(dpToPx(14) + indent, dpToPx(12), dpToPx(14), dpToPx(12));
                row.setClickable(true);
                row.setFocusable(true);

                ImageView icon = new ImageView(this);
                icon.setImageResource(R.drawable.ic_book_reader);
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(item.level == 1 ? "#A8C7FA" : "#9EACB8")));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(18), dpToPx(18));
                iconLp.setMarginEnd(dpToPx(12));
                row.addView(icon, iconLp);

                TextView itemTv = new TextView(this);
                itemTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                itemTv.setText(item.title);
                itemTv.setTextSize(item.level == 1 ? 15f : 14f);
                itemTv.setTextColor(item.level == 1 ? android.graphics.Color.parseColor("#A8C7FA") : android.graphics.Color.WHITE);
                if (item.level == 1) {
                    itemTv.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                row.addView(itemTv);

                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    scrollToElement(item.anchorId, item.title);
                });

                container.addView(row);

                if (i < tab.headings.size() - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
                    divider.setBackgroundColor(android.graphics.Color.parseColor("#313238"));
                    container.addView(divider);
                }
            }
        }

        dialog.show();
    }

    private void showThemeSelector() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1B1C1F"));

        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(4));
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, 0, 0, dpToPx(14));
        handle.setLayoutParams(handleLp);
        handle.setBackgroundResource(R.drawable.bg_tab_badge);
        handle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4E5056")));
        layout.addView(handle);

        TextView title = new TextView(this);
        title.setText("Select Reader Theme");
        title.setTextSize(17f);
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        layout.addView(title);

        LinearLayout cardContainer = new LinearLayout(this);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setBackgroundResource(R.drawable.bg_top_pill);
        cardContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#242529")));
        cardContainer.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        ReaderTheme[] themes = ReaderTheme.values();
        for (int i = 0; i < themes.length; i++) {
            ReaderTheme t = themes[i];
            TextView themeOption = new TextView(this);
            themeOption.setText(t.displayName);
            themeOption.setTextSize(15f);
            themeOption.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
            themeOption.setTextColor(t.textColor);

            GradientDrawable gd = new GradientDrawable();
            gd.setColor(t.backgroundColor);
            gd.setStroke(dpToPx(1.5f), t.borderColor);
            gd.setCornerRadius(dpToPx(12));
            themeOption.setBackground(gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
            themeOption.setLayoutParams(lp);

            themeOption.setOnClickListener(v -> {
                currentReaderTheme = t;
                getSharedPreferences("reader_prefs", MODE_PRIVATE).edit().putString("selected_theme", t.name()).apply();
                dialog.dismiss();
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    renderMarkdownContent(tabList.get(currentTabIdx));
                }
            });

            cardContainer.addView(themeOption);
        }

        layout.addView(cardContainer);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void saveCurrentPageBookmark() {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        if ("home".equals(tab.currentUrl)) {
            Toast.makeText(this, "Cannot bookmark Home page", Toast.LENGTH_SHORT).show();
            return;
        }
        String md = tab.markdownContent != null ? tab.markdownContent : "";
        File savedFile = DocumentManager.saveDocument(this, currentSelectedFolder, tab.pageTitle, tab.currentUrl, md);
        BookmarkManager.saveBookmark(this, tab.pageTitle, tab.currentUrl, md);
        if (savedFile != null) {
            currentActiveDocumentFile = savedFile;
            refreshDocumentsDrawer();
            Toast.makeText(this, "Saved: " + savedFile.getName(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to save document", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // Obsidian Documents Slider Drawer System
    // ==========================================

    private void initDocumentsDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout);
        rvDrawerTree = findViewById(R.id.rvDrawerTree);
        etDrawerSearch = findViewById(R.id.etDrawerSearch);
        tvVaultStats = findViewById(R.id.tvVaultStats);
        tvVaultName = findViewById(R.id.tvVaultName);
        tvDrawerEmpty = findViewById(R.id.tvDrawerEmpty);

        View documentsDrawerContainer = findViewById(R.id.documentsDrawerContainer);
        View drawerHeaderContainer = findViewById(R.id.drawerHeaderContainer);
        View drawerFooterContainer = findViewById(R.id.drawerFooterContainer);

        if (documentsDrawerContainer != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(documentsDrawerContainer, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                );
                if (drawerHeaderContainer != null) {
                    drawerHeaderContainer.setPadding(dpToPx(16), insets.top + dpToPx(8), dpToPx(16), dpToPx(8));
                }
                if (drawerFooterContainer != null) {
                    drawerFooterContainer.setPadding(dpToPx(16), dpToPx(6), dpToPx(16), insets.bottom + dpToPx(16));
                }
                return windowInsets;
            });
        }

        View btnDrawerClearSearch = findViewById(R.id.btnDrawerClearSearch);
        View btnDrawerNewNote = findViewById(R.id.btnDrawerNewNote);
        View btnDrawerNewFolder = findViewById(R.id.btnDrawerNewFolder);
        View btnDrawerSort = findViewById(R.id.btnDrawerSort);
        View btnDrawerCollapseAll = findViewById(R.id.btnDrawerCollapseAll);
        View btnDrawerSettings = findViewById(R.id.btnDrawerSettings);
        View layoutVaultTitle = findViewById(R.id.layoutVaultTitle);

        if (rvDrawerTree != null) {
            rvDrawerTree.setLayoutManager(new LinearLayoutManager(this));
            documentTreeAdapter = new DocumentTreeAdapter(this, new DocumentTreeAdapter.OnItemInteractionListener() {
                @Override
                public void onFileClicked(DocumentManager.DocItem item) {
                    openLocalDocumentFile(item.file);
                }

                @Override
                public void onFileOptions(DocumentManager.DocItem item, View anchor) {
                    showFileOptionsMenu(item, anchor);
                }

                @Override
                public void onFolderOptions(DocumentManager.DocItem item, View anchor) {
                    showFolderOptionsMenu(item, anchor);
                }

                @Override
                public void onFolderToggled(DocumentManager.DocItem item, boolean isExpanded) {
                    if (isExpanded) {
                        currentSelectedFolder = item.file;
                    }
                }
            });
            rvDrawerTree.setAdapter(documentTreeAdapter);
        }

        if (etDrawerSearch != null) {
            etDrawerSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s.toString();
                    if (btnDrawerClearSearch != null) {
                        btnDrawerClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (documentTreeAdapter != null) {
                        documentTreeAdapter.setSearchQuery(q);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnDrawerClearSearch != null) {
            btnDrawerClearSearch.setOnClickListener(v -> {
                if (etDrawerSearch != null) etDrawerSearch.setText("");
            });
        }

        if (btnDrawerNewNote != null) {
            btnDrawerNewNote.setOnClickListener(v -> showCreateNoteDialog(currentSelectedFolder));
        }

        if (btnDrawerNewFolder != null) {
            btnDrawerNewFolder.setOnClickListener(v -> showCreateFolderDialog(currentSelectedFolder));
        }

        if (btnDrawerSort != null) {
            btnDrawerSort.setOnClickListener(this::showSortOrderPicker);
        }

        if (btnDrawerCollapseAll != null) {
            btnDrawerCollapseAll.setOnClickListener(v -> {
                if (documentTreeAdapter != null) {
                    documentTreeAdapter.collapseAll();
                    Toast.makeText(MainActivity.this, "Collapsed all folders", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDrawerSettings != null) {
            btnDrawerSettings.setOnClickListener(v -> showVaultSettingsDialog());
        }

        if (layoutVaultTitle != null) {
            layoutVaultTitle.setOnClickListener(v -> showVaultSettingsDialog());
        }

        refreshDocumentsDrawer();
    }

    public void toggleDocumentsDrawer() {
        if (drawerLayout == null) return;
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            refreshDocumentsDrawer();
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void openDocumentsDrawer() {
        if (drawerLayout == null) return;
        refreshDocumentsDrawer();
        drawerLayout.openDrawer(GravityCompat.START);
    }

    private void refreshDocumentsDrawer() {
        executor.execute(() -> {
            List<DocumentManager.DocItem> tree = DocumentManager.getDocumentTree(MainActivity.this, currentSortOrder);
            DocumentManager.LibraryStats stats = DocumentManager.getStats(MainActivity.this);

            runOnUiThread(() -> {
                if (documentTreeAdapter != null) {
                    documentTreeAdapter.setData(tree);
                    if (currentActiveDocumentFile != null) {
                        documentTreeAdapter.setSelectedFile(currentActiveDocumentFile);
                    }
                }
                if (tvVaultStats != null) {
                    tvVaultStats.setText(stats.fileCount + " files, " + stats.folderCount + " folders");
                }
                if (tvDrawerEmpty != null) {
                    tvDrawerEmpty.setVisibility(tree.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private void openLocalDocumentFile(File file) {
        if (file == null || !file.exists()) return;
        currentActiveDocumentFile = file;
        currentSelectedFolder = file.getParentFile();
        DocumentManager.DocumentContent doc = DocumentManager.readDocument(file);

        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) {
            createNewTab(file.toURI().toString());
        }
        Tab tab = tabList.get(currentTabIdx);
        tab.currentUrl = (doc.url != null && !doc.url.isEmpty()) ? doc.url : file.toURI().toString();
        tab.pageTitle = doc.title;
        tab.addHistory(tab.currentUrl);
        tab.saveCurrentState(doc.markdown, doc.title, new HashMap<>(), extractMarkdownHeadings(doc.markdown));

        urlInput.setText(tab.currentUrl);
        homePageContainer.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
        renderMarkdownContent(tab);
        scrollView.post(() -> scrollView.scrollTo(0, 0));
        updateButtons();

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        if (documentTreeAdapter != null) {
            documentTreeAdapter.setSelectedFile(file);
        }
        Toast.makeText(this, "Opened: " + doc.title, Toast.LENGTH_SHORT).show();
    }

    private void showFileOptionsMenu(DocumentManager.DocItem item, View anchor) {
        final File file = item.file;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_document_options_sheet, null);
        sheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetDocTitle);
        if (tvTitle != null) tvTitle.setText(item.displayName);

        View btnDuplicate = sheetView.findViewById(R.id.btnSheetDuplicate);
        View btnMove = sheetView.findViewById(R.id.btnSheetMove);
        View btnCopyLink = sheetView.findViewById(R.id.btnSheetCopyLink);
        View btnShare = sheetView.findViewById(R.id.btnSheetShare);
        View btnExport = sheetView.findViewById(R.id.btnSheetExport);
        View btnRename = sheetView.findViewById(R.id.btnSheetRename);
        View btnDelete = sheetView.findViewById(R.id.btnSheetDelete);

        if (btnDuplicate != null) btnDuplicate.setOnClickListener(v -> {
            sheet.dismiss();
            File copy = DocumentManager.duplicateDocument(file);
            if (copy != null) {
                refreshDocumentsDrawer();
                Toast.makeText(MainActivity.this, "Created copy: " + copy.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to duplicate file", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnMove != null) btnMove.setOnClickListener(v -> {
            sheet.dismiss();
            showMoveFileDialog(file);
        });

        if (btnCopyLink != null) btnCopyLink.setOnClickListener(v -> {
            sheet.dismiss();
            DocumentManager.DocumentContent doc = DocumentManager.readDocument(file);
            String copyText = (doc.url != null && !doc.url.isEmpty()) ? doc.url : file.getAbsolutePath();
            try {
                android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Document Link", copyText));
                    Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        });

        if (btnShare != null) btnShare.setOnClickListener(v -> {
            sheet.dismiss();
            shareDocumentFile(file);
        });

        if (btnExport != null) btnExport.setOnClickListener(v -> {
            sheet.dismiss();
            exportDocumentFile(file);
        });

        if (btnRename != null) btnRename.setOnClickListener(v -> {
            sheet.dismiss();
            showRenameFileDialog(file);
        });

        if (btnDelete != null) btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            showDeleteFileDialog(file);
        });

        sheet.show();
    }

    private void showFolderOptionsMenu(DocumentManager.DocItem item, View anchor) {
        final File folder = item.file;
        currentSelectedFolder = folder;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_folder_options_sheet, null);
        sheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetFolderTitle);
        if (tvTitle != null) tvTitle.setText("📁 " + item.displayName);

        View btnNewNote = sheetView.findViewById(R.id.btnSheetFolderNewNote);
        View btnNewSubfolder = sheetView.findViewById(R.id.btnSheetFolderNewSubfolder);
        View btnRename = sheetView.findViewById(R.id.btnSheetFolderRename);
        View btnDelete = sheetView.findViewById(R.id.btnSheetFolderDelete);

        if (btnNewNote != null) btnNewNote.setOnClickListener(v -> {
            sheet.dismiss();
            showCreateNoteDialog(folder);
        });

        if (btnNewSubfolder != null) btnNewSubfolder.setOnClickListener(v -> {
            sheet.dismiss();
            showCreateFolderDialog(folder);
        });

        if (btnRename != null) btnRename.setOnClickListener(v -> {
            sheet.dismiss();
            showRenameFolderDialog(folder);
        });

        if (btnDelete != null) btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            showDeleteFolderDialog(folder);
        });

        sheet.show();
    }

    private void showMoveFileDialog(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) return;
        List<File> allFolders = DocumentManager.getAllFolders(this);
        File rootDir = DocumentManager.getDocumentsRoot(this);

        String[] folderNames = new String[allFolders.size()];
        for (int i = 0; i < allFolders.size(); i++) {
            File f = allFolders.get(i);
            if (f.getAbsolutePath().equals(rootDir.getAbsolutePath())) {
                folderNames[i] = "📁 Documents (Root)";
            } else {
                String relative = f.getAbsolutePath().replace(rootDir.getAbsolutePath(), "").replace(File.separator, " / ");
                folderNames[i] = "📁" + relative;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Move '" + sourceFile.getName() + "' to:")
                .setItems(folderNames, (dialog, which) -> {
                    File targetFolder = allFolders.get(which);
                    boolean moved = DocumentManager.moveFile(sourceFile, targetFolder);
                    if (moved) {
                        refreshDocumentsDrawer();
                        Toast.makeText(MainActivity.this, "Moved to " + folderNames[which], Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to move file", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateFolderDialog(File parentFolder) {
        if (parentFolder == null || !parentFolder.exists()) {
            parentFolder = DocumentManager.getDocumentsRoot(this);
        }
        final File finalParent = parentFolder;

        EditText input = new EditText(this);
        input.setHint("Folder name");
        input.setSingleLine(true);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("New Folder")
                .setMessage("Location: " + (finalParent.getName().equals("documents") ? "Root Documents" : finalParent.getName()))
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        boolean created = DocumentManager.createFolder(finalParent, folderName);
                        if (created) {
                            refreshDocumentsDrawer();
                            Toast.makeText(MainActivity.this, "Folder '" + folderName + "' created", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Folder already exists or invalid name", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameFolderDialog(File folder) {
        if (folder == null || !folder.exists()) return;

        EditText input = new EditText(this);
        input.setText(folder.getName());
        input.setSelection(folder.getName().length());
        input.setSingleLine(true);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Rename Folder")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(folder.getName())) {
                        boolean renamed = DocumentManager.renameFolder(folder, newName);
                        if (renamed) {
                            refreshDocumentsDrawer();
                            Toast.makeText(MainActivity.this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to rename folder", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteFolderDialog(File folder) {
        if (folder == null || !folder.exists()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete Folder")
                .setMessage("Are you sure you want to delete '" + folder.getName() + "' and all files inside it?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = DocumentManager.deleteFolder(folder);
                    if (deleted) {
                        refreshDocumentsDrawer();
                        Toast.makeText(MainActivity.this, "Folder deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to delete folder", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateNoteDialog(File parentFolder) {
        if (parentFolder == null || !parentFolder.exists()) {
            parentFolder = DocumentManager.getDocumentsRoot(this);
        }
        final File finalParent = parentFolder;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(8));

        EditText titleInput = new EditText(this);
        titleInput.setHint("Document name (e.g. Quantum Computing)");
        titleInput.setSingleLine(true);
        titleInput.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        titleInput.setTextColor(android.graphics.Color.WHITE);
        titleInput.setHintTextColor(android.graphics.Color.parseColor("#7A7A85"));
        layout.addView(titleInput);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(10)));
        layout.addView(spacer);

        EditText urlInputEdit = new EditText(this);
        urlInputEdit.setHint("Web link / URL (optional, e.g. wikipedia.org/...)");
        urlInputEdit.setSingleLine(true);
        urlInputEdit.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        urlInputEdit.setTextColor(android.graphics.Color.WHITE);
        urlInputEdit.setHintTextColor(android.graphics.Color.parseColor("#7A7A85"));
        layout.addView(urlInputEdit);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Create Document")
                .setMessage("Folder: " + (finalParent.getName().equals("documents") ? "Root Documents" : finalParent.getName()))
                .setView(layout)
                .setPositiveButton("Generate & Save", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String link = urlInputEdit.getText().toString().trim();
                    generateAndSaveDocument(finalParent, title, link);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateAndSaveDocument(File targetFolder, String title, String rawUrl) {
        if (rawUrl != null && !rawUrl.isEmpty()) {
            String url = rawUrl;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            final String finalUrl = url;
            Toast.makeText(this, "Fetching page & converting to Markdown...", Toast.LENGTH_SHORT).show();

            executor.execute(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    HtmlCleaner.Config cleanerConfig = HtmlCleaner.Config.defaultConfig();
                    cleanerConfig.baseUrl = finalUrl;
                    HtmlCleaner.Result cleanResult = HtmlCleaner.clean(sb.toString(), cleanerConfig);
                    HtmlToMarkdownConverter.MarkdownDocument mdDoc = HtmlToMarkdownConverter.convert(cleanResult.html, finalUrl);

                    String finalDocTitle = (title != null && !title.isEmpty()) ? title : mdDoc.title;
                    if (finalDocTitle == null || finalDocTitle.isEmpty()) finalDocTitle = "Web Note";

                    File savedFile = DocumentManager.saveDocument(MainActivity.this, targetFolder, finalDocTitle, finalUrl, mdDoc.markdown);

                    runOnUiThread(() -> {
                        if (savedFile != null) {
                            refreshDocumentsDrawer();
                            openLocalDocumentFile(savedFile);
                            Toast.makeText(MainActivity.this, "Generated & Saved: " + savedFile.getName(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to save generated document", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        String finalDocTitle = (title != null && !title.isEmpty()) ? title : "Untitled";
                        File savedFile = DocumentManager.saveDocument(MainActivity.this, targetFolder, finalDocTitle, finalUrl, "# " + finalDocTitle + "\n\nSource: " + finalUrl + "\n\n(Could not fetch content: " + e.getMessage() + ")");
                        if (savedFile != null) {
                            refreshDocumentsDrawer();
                            openLocalDocumentFile(savedFile);
                            Toast.makeText(MainActivity.this, "Saved placeholder (Offline)", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } else {
            String finalDocTitle = (title != null && !title.isEmpty()) ? title : "Untitled Note";
            File newFile = DocumentManager.saveDocument(this, targetFolder, finalDocTitle, "", "# " + finalDocTitle + "\n\n");
            if (newFile != null) {
                refreshDocumentsDrawer();
                openLocalDocumentFile(newFile);
                Toast.makeText(this, "Created: " + newFile.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to create note", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showRenameFileDialog(File file) {
        if (file == null || !file.exists()) return;

        String baseName = file.getName();
        if (baseName.toLowerCase().endsWith(".md")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        }

        EditText input = new EditText(this);
        input.setText(baseName);
        input.setSelection(baseName.length());
        input.setSingleLine(true);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Rename Note")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        boolean renamed = DocumentManager.renameFile(file, newName);
                        if (renamed) {
                            refreshDocumentsDrawer();
                            Toast.makeText(MainActivity.this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to rename file", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteFileDialog(File file) {
        if (file == null || !file.exists()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete Note")
                .setMessage("Delete '" + file.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = DocumentManager.deleteFile(file);
                    if (deleted) {
                        if (currentActiveDocumentFile != null && currentActiveDocumentFile.getAbsolutePath().equals(file.getAbsolutePath())) {
                            currentActiveDocumentFile = null;
                        }
                        refreshDocumentsDrawer();
                        Toast.makeText(MainActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to delete note", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareDocumentFile(File file) {
        Intent shareIntent = DocumentManager.createShareIntent(this, file);
        if (shareIntent != null) {
            try {
                startActivity(shareIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void exportDocumentFile(File file) {
        boolean exported = DocumentManager.exportToDownloads(this, file);
        if (exported) {
            Toast.makeText(this, "Exported to Downloads/Velocity/" + file.getName(), Toast.LENGTH_LONG).show();
        } else {
            // Fallback share chooser
            shareDocumentFile(file);
        }
    }

    private void showSortOrderPicker(View anchor) {
        String[] orders = new String[]{
                "Name (A to Z)",
                "Name (Z to A)",
                "Date Modified (Newest first)",
                "Date Modified (Oldest first)"
        };

        int currentIdx = 0;
        switch (currentSortOrder) {
            case NAME_DESC: currentIdx = 1; break;
            case DATE_DESC: currentIdx = 2; break;
            case DATE_ASC: currentIdx = 3; break;
            case NAME_ASC: default: currentIdx = 0; break;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Sort Documents")
                .setSingleChoiceItems(orders, currentIdx, (dialog, which) -> {
                    dialog.dismiss();
                    switch (which) {
                        case 0: currentSortOrder = DocumentManager.SortOrder.NAME_ASC; break;
                        case 1: currentSortOrder = DocumentManager.SortOrder.NAME_DESC; break;
                        case 2: currentSortOrder = DocumentManager.SortOrder.DATE_DESC; break;
                        case 3: currentSortOrder = DocumentManager.SortOrder.DATE_ASC; break;
                    }
                    refreshDocumentsDrawer();
                })
                .show();
    }

    private void showVaultSettingsDialog() {
        DocumentManager.LibraryStats stats = DocumentManager.getStats(this);
        File rootDir = DocumentManager.getDocumentsRoot(this);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Documents Library")
                .setMessage("Vault location:\n" + rootDir.getAbsolutePath() + "\n\nTotal: " + stats.fileCount + " files in " + stats.folderCount + " folders")
                .setPositiveButton("Open External .md", (d, w) -> pickLocalMarkdownFile())
                .setNeutralButton("Refresh", (d, w) -> refreshDocumentsDrawer())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showBookmarksSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_bookmarks_sheet, null);
        dialog.setContentView(sheetView);

        View emptyState = sheetView.findViewById(R.id.bookmarksEmptyState);
        View scrollViewContainer = sheetView.findViewById(R.id.bookmarksScrollView);
        LinearLayout container = sheetView.findViewById(R.id.bookmarksListContainer);

        List<BookmarkManager.BookmarkItem> bookmarks = BookmarkManager.getBookmarks(this);
        if (bookmarks.isEmpty()) {
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (scrollViewContainer != null) scrollViewContainer.setVisibility(View.GONE);
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (scrollViewContainer != null) scrollViewContainer.setVisibility(View.VISIBLE);

            if (container != null) {
                container.removeAllViews();
                for (int i = 0; i < bookmarks.size(); i++) {
                    final BookmarkManager.BookmarkItem bm = bookmarks.get(i);

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
                    row.setClickable(true);
                    row.setFocusable(true);

                    ImageView icon = new ImageView(this);
                    icon.setImageResource(R.drawable.ic_bookmark_star);
                    icon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#A8C7FA")));
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
                    iconLp.setMarginEnd(dpToPx(12));
                    row.addView(icon, iconLp);

                    LinearLayout textCol = new LinearLayout(this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView titleTv = new TextView(this);
                    titleTv.setText(bm.title);
                    titleTv.setTextSize(14.5f);
                    titleTv.setTextColor(android.graphics.Color.WHITE);
                    titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
                    titleTv.setSingleLine(true);
                    titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textCol.addView(titleTv);

                    TextView urlTv = new TextView(this);
                    urlTv.setText(bm.url);
                    urlTv.setTextSize(12f);
                    urlTv.setTextColor(android.graphics.Color.parseColor("#9EACB8"));
                    urlTv.setSingleLine(true);
                    urlTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textCol.addView(urlTv);

                    row.addView(textCol);

                    ImageButton btnDel = new ImageButton(this);
                    btnDel.setImageResource(R.drawable.ic_close_m3);
                    btnDel.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8E929B")));
                    btnDel.setBackgroundResource(R.drawable.bg_top_pill);
                    btnDel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
                    btnDel.setLayoutParams(delLp);
                    btnDel.setOnClickListener(v -> {
                        BookmarkManager.deleteBookmark(MainActivity.this, bm.id);
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, "Document deleted", Toast.LENGTH_SHORT).show();
                        showBookmarksSheet();
                    });
                    row.addView(btnDel);

                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        String offlineMd = BookmarkManager.getOfflineContent(MainActivity.this, bm.id);
                        if (offlineMd != null && !offlineMd.isEmpty()) {
                            renderOfflineMarkdown(bm.title, bm.url, offlineMd);
                        } else {
                            loadUrl(bm.url, true);
                        }
                    });

                    container.addView(row);

                    if (i < bookmarks.size() - 1) {
                        View divider = new View(this);
                        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
                        divider.setBackgroundColor(android.graphics.Color.parseColor("#313238"));
                        container.addView(divider);
                    }
                }
            }
        }

        dialog.show();
    }

    private void renderOfflineMarkdown(String pageTitle, String url, String markdown) {
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) {
            createNewTab(url);
        }
        Tab tab = tabList.get(currentTabIdx);
        tab.currentUrl = url;
        tab.pageTitle = pageTitle;
        tab.addHistory(url);
        tab.saveCurrentState(markdown, pageTitle, new HashMap<>(), extractMarkdownHeadings(markdown));

        runOnUiThread(() -> {
            homePageContainer.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            renderMarkdownContent(tab);
            if (urlInput != null) urlInput.setText(url);
            scrollView.post(() -> scrollView.scrollTo(0, 0));
            updateButtons();
            Toast.makeText(MainActivity.this, "Loaded: " + pageTitle, Toast.LENGTH_SHORT).show();
        });
    }

    private void showHistorySheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_history_sheet, null);
        dialog.setContentView(sheetView);

        View emptyState = sheetView.findViewById(R.id.historyEmptyState);
        View scrollViewContainer = sheetView.findViewById(R.id.historyScrollView);
        LinearLayout container = sheetView.findViewById(R.id.historyListContainer);
        View btnClearAll = sheetView.findViewById(R.id.btnHistoryClearAll);

        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                HistoryManager.clearHistory(MainActivity.this);
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "History cleared", Toast.LENGTH_SHORT).show();
            });
        }

        List<HistoryManager.HistoryItem> history = HistoryManager.getHistory(this);
        if (history.isEmpty()) {
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (scrollViewContainer != null) scrollViewContainer.setVisibility(View.GONE);
            if (btnClearAll != null) btnClearAll.setVisibility(View.GONE);
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (scrollViewContainer != null) scrollViewContainer.setVisibility(View.VISIBLE);
            if (btnClearAll != null) btnClearAll.setVisibility(View.VISIBLE);

            if (container != null) {
                container.removeAllViews();
                for (int i = 0; i < history.size(); i++) {
                    final HistoryManager.HistoryItem item = history.get(i);

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
                    row.setClickable(true);
                    row.setFocusable(true);

                    ImageView icon = new ImageView(this);
                    icon.setImageResource(R.drawable.ic_history_clock);
                    icon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#A8C7FA")));
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
                    iconLp.setMarginEnd(dpToPx(12));
                    row.addView(icon, iconLp);

                    LinearLayout textCol = new LinearLayout(this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView titleTv = new TextView(this);
                    titleTv.setText(item.title);
                    titleTv.setTextSize(14.5f);
                    titleTv.setTextColor(android.graphics.Color.WHITE);
                    titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
                    titleTv.setSingleLine(true);
                    titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textCol.addView(titleTv);

                    TextView urlTv = new TextView(this);
                    urlTv.setText(item.url);
                    urlTv.setTextSize(12f);
                    urlTv.setTextColor(android.graphics.Color.parseColor("#9EACB8"));
                    urlTv.setSingleLine(true);
                    urlTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textCol.addView(urlTv);

                    row.addView(textCol);

                    ImageButton btnDel = new ImageButton(this);
                    btnDel.setImageResource(R.drawable.ic_close_m3);
                    btnDel.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8E929B")));
                    btnDel.setBackgroundResource(R.drawable.bg_top_pill);
                    btnDel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
                    btnDel.setLayoutParams(delLp);
                    btnDel.setOnClickListener(v -> {
                        HistoryManager.deleteHistoryItem(MainActivity.this, item.id);
                        dialog.dismiss();
                        showHistorySheet();
                    });
                    row.addView(btnDel);

                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        loadUrl(item.url, true);
                    });

                    container.addView(row);

                    if (i < history.size() - 1) {
                        View divider = new View(this);
                        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
                        divider.setBackgroundColor(android.graphics.Color.parseColor("#313238"));
                        container.addView(divider);
                    }
                }
            }
        }

        dialog.show();
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
        if (tab.markdownContent == null || tab.markdownContent.trim().isEmpty()) {
            Toast.makeText(this, "No content to read", Toast.LENGTH_SHORT).show();
            return;
        }

        String readableText = tab.markdownContent.replaceAll("[#*_`\\[\\]\\(\\)]", " ");
        if (readableText.trim().isEmpty()) {
            Toast.makeText(this, "No text found to read", Toast.LENGTH_SHORT).show();
            return;
        }

        isSpeaking = true;
        Toast.makeText(this, "Reading aloud...", Toast.LENGTH_SHORT).show();
        textToSpeech.speak(readableText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "VelocityTTS");
    }

    private void showPageOptionsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_page_options_sheet, null);
        dialog.setContentView(sheetView);

        View itemOutline = sheetView.findViewById(R.id.menuItemOutline);
        View itemTTS = sheetView.findViewById(R.id.menuItemTTS);
        View itemTheme = sheetView.findViewById(R.id.menuItemTheme);
        View itemSaveOffline = sheetView.findViewById(R.id.menuItemSaveOffline);
        View itemSiteSearch = sheetView.findViewById(R.id.menuItemSiteSearch);
        View dividerSiteSearch = sheetView.findViewById(R.id.dividerSiteSearch);
        TextView tvSiteSearch = sheetView.findViewById(R.id.tvSiteSearchTitle);
        View itemReload = sheetView.findViewById(R.id.menuItemReload);

        String currentDomain = (currentTabIdx >= 0 && currentTabIdx < tabList.size())
                ? extractDomain(tabList.get(currentTabIdx).currentUrl)
                : null;

        if (currentDomain != null && !currentDomain.contains("duckduckgo.com")) {
            if (tvSiteSearch != null) tvSiteSearch.setText("Search on " + currentDomain);
            if (itemSiteSearch != null) {
                final String domain = currentDomain;
                itemSiteSearch.setOnClickListener(v -> {
                    dialog.dismiss();
                    showSiteSearchDialog(domain);
                });
            }
        } else {
            if (itemSiteSearch != null) itemSiteSearch.setVisibility(View.GONE);
            if (dividerSiteSearch != null) dividerSiteSearch.setVisibility(View.GONE);
        }

        if (itemOutline != null) itemOutline.setOnClickListener(v -> { dialog.dismiss(); showArticleOutline(); });
        if (itemTTS != null) itemTTS.setOnClickListener(v -> { dialog.dismiss(); toggleTextToSpeech(); });
        if (itemTheme != null) itemTheme.setOnClickListener(v -> { dialog.dismiss(); showThemeSelector(); });
        if (itemSaveOffline != null) itemSaveOffline.setOnClickListener(v -> { dialog.dismiss(); saveCurrentPageBookmark(); });
        if (itemReload != null) itemReload.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                if (!"home".equals(tab.currentUrl)) {
                    loadUrl(tab.currentUrl, false);
                }
            }
        });

        View itemWebEngine = sheetView.findViewById(R.id.menuItemWebEngine);
        if (itemWebEngine != null) {
            itemWebEngine.setOnClickListener(v -> {
                dialog.dismiss();
                if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                    Tab tab = tabList.get(currentTabIdx);
                    if (!"home".equals(tab.currentUrl)) {
                        loadWithWebEngine(tab.currentUrl);
                    } else {
                        Toast.makeText(MainActivity.this, "Enter a URL first", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        dialog.show();
    }

    private void showAppMenuSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_app_menu_sheet, null);
        dialog.setContentView(sheetView);

        View itemHome = sheetView.findViewById(R.id.appMenuItemHome);
        View itemOpenMd = sheetView.findViewById(R.id.appMenuItemOpenMd);
        View itemSavedDocs = sheetView.findViewById(R.id.appMenuItemSavedDocs);
        View itemHistory = sheetView.findViewById(R.id.appMenuItemHistory);
        View itemTabs = sheetView.findViewById(R.id.appMenuItemTabs);
        View itemCloseTab = sheetView.findViewById(R.id.appMenuItemCloseTab);

        if (itemHome != null) itemHome.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                Tab tab = tabList.get(currentTabIdx);
                tab.addHistory("home");
                tab.markdownContent = "";
                tab.pageTitle = "Home";
                switchToTab(currentTabIdx);
            }
        });

        if (itemOpenMd != null) itemOpenMd.setOnClickListener(v -> { dialog.dismiss(); pickLocalMarkdownFile(); });
        if (itemSavedDocs != null) itemSavedDocs.setOnClickListener(v -> { dialog.dismiss(); openDocumentsDrawer(); });
        if (itemHistory != null) itemHistory.setOnClickListener(v -> { dialog.dismiss(); showHistorySheet(); });
        if (itemTabs != null) itemTabs.setOnClickListener(v -> { dialog.dismiss(); showTabsSheet(); });
        if (itemCloseTab != null) itemCloseTab.setOnClickListener(v -> { dialog.dismiss(); closeCurrentTab(); });

        dialog.show();
    }

    private void showSiteSearchDialog(String domain) {
        EditText input = new EditText(this);
        input.setHint("Search keywords on " + domain);
        input.setSingleLine(true);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Search " + domain)
                .setView(input)
                .setPositiveButton("Search", (d, which) -> {
                    String query = input.getText().toString().trim();
                    if (!query.isEmpty()) {
                        openSearchTab(query + " site:" + domain, false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    private void handleLinkClick(String href, Tab tab) {
        if (href == null || href.isEmpty()) return;

        if (href.startsWith("action://")) {
            if (href.contains("load_web_engine")) {
                if (tab != null && tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                    loadWithWebEngine(tab.currentUrl);
                } else if (urlInput != null && urlInput.getText() != null) {
                    loadWithWebEngine(urlInput.getText().toString());
                }
                return;
            }
            if (href.contains("retry_load")) {
                if (tab != null && tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                    loadUrl(tab.currentUrl, false);
                }
                return;
            }
            if (href.contains("open_external")) {
                if (tab != null && tab.currentUrl != null && !tab.currentUrl.equals("home")) {
                    openInSystem(tab.currentUrl, null);
                }
                return;
            }
        }

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
        if (!href.startsWith("http://") && !href.startsWith("https://") && !href.startsWith("file://") && !href.startsWith("content://")) {
            if (href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("sms:") || href.startsWith("intent:") || href.startsWith("magnet:") || href.startsWith("geo:")) {
                openInSystem(href, null);
                return;
            }
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

        if (isImageUrl(resolvedUrl)) {
            Intent intent = new Intent(MainActivity.this, ImageViewerActivity.class);
            intent.putExtra("image_url", resolvedUrl);
            startActivity(intent);
            return;
        }

        if (isExternalSystemUrl(resolvedUrl)) {
            openInSystem(resolvedUrl, null);
            return;
        }

        String searchQuery = extractSearchQuery(resolvedUrl);
        if (searchQuery != null) {
            openSearchTab(searchQuery, true);
            return;
        }

        loadUrl(resolvedUrl, true);
    }

    private boolean isImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        int qIdx = lower.indexOf('?');
        if (qIdx != -1) {
            lower = lower.substring(0, qIdx);
        }
        int hIdx = lower.indexOf('#');
        if (hIdx != -1) {
            lower = lower.substring(0, hIdx);
        }
        // Exclude Wikipedia and wiki media/file description HTML pages!
        if (lower.contains("/wiki/file:") || lower.contains("/wiki/image:") || lower.contains("/wiki/datei:") || lower.contains("/wiki/media:")) {
            return false;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".php") || lower.endsWith(".asp") || lower.endsWith(".aspx") || lower.endsWith(".jsp")) {
            return false;
        }
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")
                || lower.endsWith(".svg") || lower.endsWith(".ico")
                || (lower.contains("upload.wikimedia.org/") && (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".svg") || lower.endsWith(".webp") || lower.endsWith(".jpeg")));
    }

    private boolean isExternalSystemUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("file") && !scheme.equalsIgnoreCase("content")) {
            return true;
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) return false;
        
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot == -1) return false;
        
        String ext = lower.substring(dot + 1);
        if (ext.isEmpty()) return false;

        // Native HTML / Web formats
        if (ext.equals("html") || ext.equals("htm") || ext.equals("xhtml") || ext.equals("php")
                || ext.equals("asp") || ext.equals("aspx") || ext.equals("jsp") || ext.equals("cgi")
                || ext.equals("cfm") || ext.equals("pl")) {
            return false;
        }
        // Native Markdown formats
        if (ext.equals("md") || ext.equals("markdown") || ext.equals("mdown") || ext.equals("mkd")) {
            return false;
        }
        // Native Text / Code formats
        if (ext.equals("txt") || ext.equals("text") || ext.equals("log") || ext.equals("json")
                || ext.equals("xml") || ext.equals("csv") || ext.equals("tsv") || ext.equals("js")
                || ext.equals("css") || ext.equals("java") || ext.equals("py") || ext.equals("c")
                || ext.equals("cpp") || ext.equals("h") || ext.equals("rs") || ext.equals("go")
                || ext.equals("kt") || ext.equals("sh")) {
            return false;
        }
        // Native Image viewer formats
        if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif")
                || ext.equals("webp") || ext.equals("bmp") || ext.equals("svg") || ext.equals("ico")) {
            return false;
        }

        // All other file extensions (pdf, doc, docx, xls, xlsx, ppt, pptx, zip, rar, 7z, tar, gz,
        // apk, mp3, wav, ogg, flac, mp4, mkv, avi, webm, epub, mobi, torrent, etc.) are delegated to system!
        return true;
    }

    private void openInSystem(String url, String optMimeType) {
        try {
            Uri uri = Uri.parse(url);
            String mimeType = optMimeType;
            if (mimeType == null || mimeType.isEmpty() || mimeType.equals("*/*")) {
                String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(url);
                if (ext == null || ext.isEmpty()) {
                    int lastDot = url.lastIndexOf('.');
                    int lastSlash = url.lastIndexOf('/');
                    if (lastDot > lastSlash && lastDot != -1) {
                        int queryIdx = url.indexOf('?', lastDot);
                        if (queryIdx != -1) {
                            ext = url.substring(lastDot + 1, queryIdx);
                        } else {
                            ext = url.substring(lastDot + 1);
                        }
                    }
                }
                if (ext != null && !ext.isEmpty()) {
                    mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(java.util.Locale.ROOT));
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (mimeType != null && !mimeType.isEmpty()) {
                intent.setDataAndType(uri, mimeType);
            } else {
                intent.setData(uri);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                Toast.makeText(this, "Opening in external app...", Toast.LENGTH_SHORT).show();
            } catch (ActivityNotFoundException ex) {
                Intent fallback = new Intent(Intent.ACTION_VIEW, uri);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                Toast.makeText(this, "Opening in external app...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToElement(String idOrTitle) {
        scrollToElement(idOrTitle, null);
    }

    private void scrollToElement(String idOrTitle, String fallbackTitle) {
        if (markdownTextView == null || scrollView == null) return;
        if ((idOrTitle == null || idOrTitle.isEmpty()) && (fallbackTitle == null || fallbackTitle.isEmpty())) return;

        if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
            Tab tab = tabList.get(currentTabIdx);
            Integer charOffset = null;

            String decodedId = null;
            if (idOrTitle != null && !idOrTitle.isEmpty()) {
                try {
                    decodedId = java.net.URLDecoder.decode(idOrTitle, "UTF-8");
                } catch (Exception ignored) {
                    decodedId = idOrTitle;
                }
            }

            String decodedFallback = null;
            if (fallbackTitle != null && !fallbackTitle.isEmpty()) {
                try {
                    decodedFallback = java.net.URLDecoder.decode(fallbackTitle, "UTF-8");
                } catch (Exception ignored) {
                    decodedFallback = fallbackTitle;
                }
            }

            // 1. Check anchorMap with various normalizations
            String[] candidates = new String[]{
                    idOrTitle,
                    decodedId,
                    idOrTitle != null ? idOrTitle.toLowerCase(java.util.Locale.ROOT) : null,
                    decodedId != null ? decodedId.toLowerCase(java.util.Locale.ROOT) : null,
                    idOrTitle != null ? idOrTitle.replace("_", "-") : null,
                    idOrTitle != null ? idOrTitle.replace("-", "_") : null,
                    idOrTitle != null ? idOrTitle.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") : null,
                    decodedId != null ? decodedId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") : null,
                    fallbackTitle,
                    decodedFallback,
                    fallbackTitle != null ? fallbackTitle.toLowerCase(java.util.Locale.ROOT) : null,
                    fallbackTitle != null ? fallbackTitle.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") : null
            };

            for (String c : candidates) {
                if (c != null && !c.isEmpty()) {
                    charOffset = tab.anchorMap.get(c);
                    if (charOffset != null) break;
                }
            }

            // 2. Search headings list for corresponding title or anchorId
            if (charOffset == null && tab.headings != null) {
                for (HtmlToMarkdownConverter.HeadingItem h : tab.headings) {
                    boolean match = (idOrTitle != null && (idOrTitle.equalsIgnoreCase(h.anchorId) || idOrTitle.equalsIgnoreCase(h.title))) ||
                                    (decodedId != null && (decodedId.equalsIgnoreCase(h.anchorId) || decodedId.equalsIgnoreCase(h.title))) ||
                                    (fallbackTitle != null && (fallbackTitle.equalsIgnoreCase(h.anchorId) || fallbackTitle.equalsIgnoreCase(h.title))) ||
                                    (decodedFallback != null && (decodedFallback.equalsIgnoreCase(h.anchorId) || decodedFallback.equalsIgnoreCase(h.title)));
                    if (match) {
                        charOffset = tab.anchorMap.get(h.title);
                        if (charOffset == null && h.anchorId != null) {
                            charOffset = tab.anchorMap.get(h.anchorId);
                        }
                        if (charOffset == null) {
                            String norm = h.title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                            charOffset = tab.anchorMap.get(norm);
                        }
                        if (charOffset != null) break;
                    }
                }
            }

            // 3. Fallback: scan rendered text directly for custom anchor tag `{#id}` or title
            if (charOffset == null && markdownTextView.getText() != null) {
                String fullText = markdownTextView.getText().toString();
                String lowerFull = fullText.toLowerCase(java.util.Locale.ROOT);

                // Check for `{#id}`
                if (idOrTitle != null && !idOrTitle.isEmpty()) {
                    int tagIdx = lowerFull.indexOf("{#" + idOrTitle.toLowerCase(java.util.Locale.ROOT) + "}");
                    if (tagIdx != -1) {
                        charOffset = tagIdx;
                    }
                }
                if (charOffset == null && decodedId != null && !decodedId.isEmpty()) {
                    int tagIdx = lowerFull.indexOf("{#" + decodedId.toLowerCase(java.util.Locale.ROOT) + "}");
                    if (tagIdx != -1) {
                        charOffset = tagIdx;
                    }
                }

                // Check for target text / title in the rendered markdown
                if (charOffset == null) {
                    String targetText = (decodedFallback != null && !decodedFallback.isEmpty()) ? decodedFallback : decodedId;
                    if (targetText != null && !targetText.isEmpty()) {
                        String cleanTarget = targetText.replaceAll("[#*_`\\[\\]]", "").trim();
                        int idx = lowerFull.indexOf(cleanTarget.toLowerCase(java.util.Locale.ROOT));
                        if (idx == -1 && cleanTarget.contains(" ")) {
                            String[] words = cleanTarget.split("\\s+");
                            if (words.length > 1) {
                                String prefix = (words[0] + " " + words[1]).toLowerCase(java.util.Locale.ROOT);
                                idx = lowerFull.indexOf(prefix);
                            }
                        }
                        if (idx != -1) {
                            charOffset = idx;
                        }
                    }
                }
            }

            if (charOffset != null && markdownTextView.getLayout() != null) {
                int line = markdownTextView.getLayout().getLineForOffset(charOffset);
                int y = markdownTextView.getLayout().getLineTop(line);
                final int targetY = Math.max(0, y - dpToPx(16));
                scrollView.post(() -> {
                    if (scrollView instanceof androidx.core.widget.NestedScrollView) {
                        ((androidx.core.widget.NestedScrollView) scrollView).smoothScrollTo(0, targetY);
                    } else {
                        scrollView.scrollTo(0, targetY);
                    }
                });
            }
        }
    }

    private void updateButtons() {
        if (tvTabCount != null) {
            tvTabCount.setText(String.valueOf(Math.max(1, tabList.size())));
        }
        if (currentTabIdx < 0 || currentTabIdx >= tabList.size()) return;
        Tab tab = tabList.get(currentTabIdx);
        
        if (btnBack != null) {
            btnBack.setEnabled(tab.canGoBack());
            btnBack.setAlpha(tab.canGoBack() ? 1.0f : 0.35f);
        }
        if (btnForward != null) {
            btnForward.setEnabled(tab.canGoForward());
            btnForward.setAlpha(tab.canGoForward() ? 1.0f : 0.35f);
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
        Uri data = intent.getData();
        String targetUrl = intent.getStringExtra("TARGET_URL");

        if (targetUrl != null && !targetUrl.isEmpty()) {
            int targetTabIdx = intent.getIntExtra("TAB_INDEX", -1);
            if (targetTabIdx >= 0 && targetTabIdx < tabList.size()) {
                switchToTab(targetTabIdx);
                loadUrl(targetUrl, true);
            } else if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                loadUrl(targetUrl, true);
            } else {
                createNewTab(targetUrl);
            }
        } else if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String scheme = data.getScheme();
            if ("file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)) {
                openMarkdownUri(data);
            } else {
                String dataStr = data.toString();
                String query = extractSearchQuery(dataStr);
                if (query != null) {
                    openSearchTab(query, true);
                } else {
                    createNewTab(dataStr);
                }
            }
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
                    if (tab.markdownContent != null && !tab.markdownContent.isEmpty()) {
                        urlInput.setText(tab.currentUrl);
                        renderMarkdownContent(tab);
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

    public void loadWithWebEngine(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty() || targetUrl.equals("home")) {
            Toast.makeText(this, "No valid URL to load", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalUrl = targetUrl.startsWith("http://") || targetUrl.startsWith("https://") 
                ? targetUrl 
                : "https://" + targetUrl;

        try {
            com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            View sheetView = getLayoutInflater().inflate(R.layout.dialog_floating_webview, null);
            sheet.setContentView(sheetView);

            sheet.setOnShowListener(dialog -> {
                com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
                android.widget.FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior =
                            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            });

            TextView tvUrl = sheetView.findViewById(R.id.tvFloatingWebviewUrl);
            ProgressBar webProgress = sheetView.findViewById(R.id.floatingWebviewProgress);
            View btnClose = sheetView.findViewById(R.id.btnFloatingWebviewClose);
            View btnExtract = sheetView.findViewById(R.id.btnFloatingWebviewExtract);
            WebView webView = sheetView.findViewById(R.id.floatingWebView);

            if (tvUrl != null) {
                tvUrl.setText(finalUrl);
            }

            if (webView == null) {
                Toast.makeText(this, "System WebView is not available on this device", Toast.LENGTH_LONG).show();
                sheet.dismiss();
                return;
            }

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

            final boolean[] extracted = {false};

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (webProgress != null) {
                        webProgress.setProgress(newProgress);
                        webProgress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void onReceivedTitle(WebView view, String title) {
                    super.onReceivedTitle(view, title);
                    if (tvUrl != null && title != null && !title.isEmpty()) {
                        tvUrl.setText(title + " • " + finalUrl);
                    }
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (webProgress != null) webProgress.setVisibility(View.VISIBLE);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (webProgress != null) webProgress.setVisibility(View.GONE);
                    String currentTitle = view.getTitle();
                    boolean isChallenge = currentTitle != null && (
                            currentTitle.toLowerCase(Locale.ROOT).contains("just a moment") ||
                            currentTitle.toLowerCase(Locale.ROOT).contains("checking your browser") ||
                            currentTitle.toLowerCase(Locale.ROOT).contains("cloudflare")
                    );

                    // If not a challenge page, auto-extract after a brief interval for smooth user experience
                    if (!isChallenge && !extracted[0]) {
                        webView.postDelayed(() -> {
                            if (!extracted[0] && sheet.isShowing() && !isFinishing() && !isDestroyed()) {
                                extracted[0] = true;
                                extractAndProcessHtmlFromWebView(webView, finalUrl, sheet);
                            }
                        }, 2500);
                    }
                }
            });

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> sheet.dismiss());
            }

            if (btnExtract != null) {
                btnExtract.setOnClickListener(v -> {
                    extracted[0] = true;
                    extractAndProcessHtmlFromWebView(webView, finalUrl, sheet);
                });
            }

            sheet.setOnDismissListener(dialog -> {
                try {
                    webView.stopLoading();
                    webView.loadUrl("about:blank");
                    webView.destroy();
                } catch (Throwable ignored) {}
            });

            webView.loadUrl(finalUrl);
            sheet.show();

        } catch (Throwable t) {
            // Safely prevent any crashes if WebView implementation is missing or throws
            Toast.makeText(this, "System WebView is not available on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void extractAndProcessHtmlFromWebView(WebView webView, String currentUrl, com.google.android.material.bottomsheet.BottomSheetDialog dialogToDismiss) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript(
                    "(function() { return document.documentElement ? document.documentElement.outerHTML : (document.body ? document.body.innerHTML : ''); })();",
                    value -> {
                        if (value == null || value.equals("null") || value.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Could not extract page HTML yet. Please wait for page to load.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String html = value;
                        try {
                            Object parsed = new org.json.JSONTokener(value).nextValue();
                            if (parsed instanceof String) {
                                html = (String) parsed;
                            }
                        } catch (Exception ignored) {}

                        final String extractedHtml = html;
                        runOnUiThread(() -> {
                            if (dialogToDismiss != null && dialogToDismiss.isShowing()) {
                                try {
                                    dialogToDismiss.dismiss();
                                } catch (Exception ignored) {}
                            }
                            processExtractedWebEngineHtml(currentUrl, extractedHtml);
                        });
                    }
            );
        } catch (Throwable t) {
            Toast.makeText(this, "Failed to extract page HTML: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processExtractedWebEngineHtml(String url, String rawHtml) {
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            Toast.makeText(this, "Extracted empty HTML content", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(30);
            Toast.makeText(this, "Rendering page in native Markdown...", Toast.LENGTH_SHORT).show();
        });

        executor.execute(() -> {
            try {
                HtmlCleaner.Config config = HtmlCleaner.Config.defaultConfig();
                config.baseUrl = url;
                String cleanedHtml = HtmlCleaner.clean(rawHtml, config).html;

                HtmlToMarkdownConverter.MarkdownDocument mdDoc = HtmlToMarkdownConverter.convert(cleanedHtml, url);

                runOnUiThread(() -> {
                    if (currentTabIdx >= 0 && currentTabIdx < tabList.size()) {
                        Tab tab = tabList.get(currentTabIdx);
                        tab.currentUrl = url;
                        tab.saveCurrentState(mdDoc.markdown, mdDoc.title, new HashMap<>(), mdDoc.headings);
                        HistoryManager.addHistory(MainActivity.this, mdDoc.title, url);

                        homePageContainer.setVisibility(View.GONE);
                        scrollView.setVisibility(View.VISIBLE);
                        renderMarkdownContent(tab);

                        if (urlInput != null) urlInput.setText(url);
                        scrollView.post(() -> scrollView.scrollTo(0, 0));

                        if (tabContainer != null && currentTabIdx < tabContainer.getChildCount()) {
                            TextView tv = (TextView) tabContainer.getChildAt(currentTabIdx);
                            if (tv != null) {
                                tv.setText(tab.pageTitle);
                            }
                        }
                    }
                    progressBar.setVisibility(View.GONE);
                    updateButtons();
                    Toast.makeText(MainActivity.this, "Loaded: " + mdDoc.title, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Conversion error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}