package com.example.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    public static class HistoryItem {
        public final String id;
        public final String title;
        public final String url;
        public final long timestamp;

        public HistoryItem(String id, String title, String url, long timestamp) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private static final String PREF_NAME = "velocity_history_pref";
    private static final String KEY_HISTORY = "history_json";
    private static final int MAX_HISTORY_ITEMS = 200;

    public static List<HistoryItem> getHistory(Context context) {
        List<HistoryItem> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String jsonStr = pref.getString(KEY_HISTORY, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new HistoryItem(
                        obj.getString("id"),
                        obj.getString("title"),
                        obj.getString("url"),
                        obj.getLong("timestamp")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addHistory(Context context, String title, String url) {
        if (context == null || url == null || url.isEmpty() || "home".equals(url)) return;
        try {
            List<HistoryItem> list = getHistory(context);

            // Deduplicate if already top history item
            if (!list.isEmpty() && list.get(0).url.equals(url)) {
                return;
            }

            String id = "hist_" + System.currentTimeMillis() + "_" + Math.abs(url.hashCode() % 1000);
            String displayTitle = (title == null || title.isEmpty()) ? url : title;

            list.add(0, new HistoryItem(id, displayTitle, url, System.currentTimeMillis()));

            if (list.size() > MAX_HISTORY_ITEMS) {
                list = list.subList(0, MAX_HISTORY_ITEMS);
            }

            saveList(context, list);
        } catch (Exception ignored) {}
    }

    public static void deleteHistoryItem(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return;
        try {
            List<HistoryItem> list = getHistory(context);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).id.equals(id)) {
                    list.remove(i);
                }
            }
            saveList(context, list);
        } catch (Exception ignored) {}
    }

    public static void clearHistory(Context context) {
        if (context == null) return;
        try {
            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            pref.edit().remove(KEY_HISTORY).apply();
        } catch (Exception ignored) {}
    }

    private static void saveList(Context context, List<HistoryItem> list) {
        try {
            JSONArray array = new JSONArray();
            for (HistoryItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            }
            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            pref.edit().putString(KEY_HISTORY, array.toString()).apply();
        } catch (Exception ignored) {}
    }
}
