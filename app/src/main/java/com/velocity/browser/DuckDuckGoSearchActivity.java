package com.velocity.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DuckDuckGoSearchActivity extends AppCompatActivity {

    public static class SearchResultItem {
        public final String title;
        public final String url;
        public final String snippet;

        public SearchResultItem(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }

    private EditText etSearchQuery;
    private ImageButton btnSearchBack;
    private ImageButton btnExecuteSearch;
    private ProgressBar searchProgressBar;
    private TextView tvSearchInfo;
    private RecyclerView rvSearchResults;

    private SearchAdapter searchAdapter;
    private final List<SearchResultItem> resultList = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duckduckgo_search);

        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnSearchBack = findViewById(R.id.btnSearchBack);
        btnExecuteSearch = findViewById(R.id.btnExecuteSearch);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        tvSearchInfo = findViewById(R.id.tvSearchInfo);
        rvSearchResults = findViewById(R.id.rvSearchResults);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchAdapter(resultList, item -> {
            Intent intent = new Intent(DuckDuckGoSearchActivity.this, MainActivity.class);
            intent.putExtra("TARGET_URL", item.url);
            intent.putExtra("TAB_INDEX", getIntent().getIntExtra("TAB_INDEX", -1));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        rvSearchResults.setAdapter(searchAdapter);

        btnSearchBack.setOnClickListener(v -> finish());

        btnExecuteSearch.setOnClickListener(v -> performSearch());

        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch();
                return true;
            }
            return false;
        });

        // Check if passed query via Intent
        Intent intent = getIntent();
        if (intent != null) {
            String initialQuery = intent.getStringExtra("QUERY");
            if (initialQuery != null && !initialQuery.trim().isEmpty()) {
                etSearchQuery.setText(initialQuery);
                etSearchQuery.setSelection(initialQuery.length());
                performSearch();
            }
        }
    }

    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
            HistoryManager.addHistory(DuckDuckGoSearchActivity.this, "🔍 " + query, searchUrl);
        } catch (Exception ignored) {}

        searchProgressBar.setVisibility(View.VISIBLE);
        tvSearchInfo.setText("Searching DuckDuckGo HTML for \"" + query + "\"...");

        executor.execute(() -> {
            List<SearchResultItem> fetchedResults = new ArrayList<>();
            String statusMsg = "";

            try {
                String htmlBody = "";
                int responseCode = -1;

                String[] endpoints = new String[]{
                    "https://html.duckduckgo.com/html/",
                    "https://lite.duckduckgo.com/lite/",
                    "https://duckduckgo.com/html/"
                };

                for (String endpointUrl : endpoints) {
                    try {
                        URL url = new URL(endpointUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0");
                        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                        conn.setRequestProperty("Referer", "https://duckduckgo.com/");
                        conn.setRequestProperty("DNT", "1");
                        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("Origin", "https://duckduckgo.com");
                        conn.setDoOutput(true);

                        String postData = "q=" + URLEncoder.encode(query, "UTF-8") + "&b=&kl=wt-wt";
                        conn.getOutputStream().write(postData.getBytes("UTF-8"));

                        responseCode = conn.getResponseCode();
                        java.io.InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
                        if (is != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();
                            String respText = sb.toString();
                            if (!respText.trim().isEmpty()) {
                                htmlBody = respText;
                                if (htmlBody.contains("result__title") || htmlBody.contains("result-link") || htmlBody.contains("class=\"result\"")) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (htmlBody.isEmpty() || (!htmlBody.contains("result__title") && !htmlBody.contains("result-link"))) {
                    try {
                        String getUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
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
                        java.io.InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
                        if (is != null) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
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
                    Document doc = Jsoup.parse(htmlBody, "https://html.duckduckgo.com/html/");
                    Elements results = doc.select(".result, .results_links, .web-result");

                    for (Element res : results) {
                        Element titleElem = res.selectFirst(".result__title a, .result__a, a.result-link, a.large");
                        Element snippetElem = res.selectFirst(".result__snippet");
                        Element urlElem = res.selectFirst(".result__url");

                        if (titleElem != null) {
                            String title = titleElem.text();
                            String href = titleElem.attr("href");

                            // Unpack DDG redirect link if needed
                            if (href.contains("uddg=")) {
                                try {
                                    Uri uri = Uri.parse(href.startsWith("http") ? href : "https://html.duckduckgo.com" + href);
                                    String rawTarget = uri.getQueryParameter("uddg");
                                    if (rawTarget != null && !rawTarget.isEmpty()) {
                                        href = java.net.URLDecoder.decode(rawTarget, "UTF-8");
                                    }
                                } catch (Exception e) {
                                    // fallback
                                }
                            }

                            String snippet = snippetElem != null ? snippetElem.text() : "";
                            if (href != null && !href.isEmpty() && !href.startsWith("javascript:")) {
                                fetchedResults.add(new SearchResultItem(title, href, snippet));
                            }
                        }
                    }

                    // DDG Lite fallback
                    if (fetchedResults.isEmpty()) {
                        Elements liteLinks = doc.select("a.result-link");
                        for (Element link : liteLinks) {
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

                            String snippet = "";
                            try {
                                Element parentTr = link.closest("tr");
                                if (parentTr != null) {
                                    Element snippetTr = parentTr.nextElementSibling();
                                    if (snippetTr != null) {
                                        Element snipTd = snippetTr.selectFirst(".result-snippet");
                                        if (snipTd != null) snippet = snipTd.text().trim();
                                    }
                                }
                            } catch (Exception ignored) {}

                            if (href != null && !href.isEmpty()) {
                                fetchedResults.add(new SearchResultItem(title, href, snippet));
                            }
                        }
                    }

                    statusMsg = "These results are provided by DuckDuckGo (" + fetchedResults.size() + " results)";
                } else {
                    statusMsg = "Search HTTP error code: " + responseCode;
                }

            } catch (Exception e) {
                statusMsg = "Search error: " + e.getLocalizedMessage();
            }

            final List<SearchResultItem> finalResults = fetchedResults;
            final String finalStatus = statusMsg;

            runOnUiThread(() -> {
                searchProgressBar.setVisibility(View.GONE);
                tvSearchInfo.setText(finalStatus);
                resultList.clear();
                resultList.addAll(finalResults);
                searchAdapter.notifyDataSetChanged();
            });
        });
    }

    private interface OnResultClickListener {
        void onItemClick(SearchResultItem item);
    }

    private static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final List<SearchResultItem> items;
        private final OnResultClickListener listener;

        SearchAdapter(List<SearchResultItem> items, OnResultClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_duckduckgo_result, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResultItem item = items.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvUrl.setText(item.url);
            holder.tvSnippet.setText(item.snippet);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            TextView tvUrl;
            TextView tvSnippet;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvResultTitle);
                tvUrl = itemView.findViewById(R.id.tvResultUrl);
                tvSnippet = itemView.findViewById(R.id.tvResultSnippet);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
