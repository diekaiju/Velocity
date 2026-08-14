package com.example.browser;

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

        searchProgressBar.setVisibility(View.VISIBLE);
        tvSearchInfo.setText("Searching DuckDuckGo HTML for \"" + query + "\"...");

        executor.execute(() -> {
            List<SearchResultItem> fetchedResults = new ArrayList<>();
            String statusMsg = "";

            try {
                String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
                URL url = new URL(searchUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setDoOutput(true);

                String postData = "q=" + URLEncoder.encode(query, "UTF-8") + "&b=";
                conn.getOutputStream().write(postData.getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    Document doc = Jsoup.parse(sb.toString(), "https://html.duckduckgo.com/html/");
                    Elements results = doc.select(".result");

                    for (Element res : results) {
                        Element titleElem = res.selectFirst(".result__title a");
                        Element snippetElem = res.selectFirst(".result__snippet");
                        Element urlElem = res.selectFirst(".result__url");

                        if (titleElem != null) {
                            String title = titleElem.text();
                            String href = titleElem.attr("href");

                            // Unpack DDG redirect link if needed
                            if (href.contains("uddg=")) {
                                try {
                                    Uri uri = Uri.parse("https://html.duckduckgo.com" + href);
                                    String rawTarget = uri.getQueryParameter("uddg");
                                    if (rawTarget != null && !rawTarget.isEmpty()) {
                                        href = rawTarget;
                                    }
                                } catch (Exception e) {
                                    // fallback
                                }
                            }

                            String snippet = snippetElem != null ? snippetElem.text() : "";
                            String displayUrl = urlElem != null ? urlElem.text().trim() : href;

                            if (href != null && !href.isEmpty()) {
                                fetchedResults.add(new SearchResultItem(title, href, snippet));
                            }
                        }
                    }

                    statusMsg = "Found " + fetchedResults.size() + " DuckDuckGo HTML results for \"" + query + "\"";
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
