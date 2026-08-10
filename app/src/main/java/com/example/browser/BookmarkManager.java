package com.example.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {

    public static class BookmarkItem {
        public final String id;
        public final String title;
        public final String url;
        public final long timestamp;

        public BookmarkItem(String id, String title, String url, long timestamp) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private static final String PREF_NAME = "velocity_bookmarks_pref";
    private static final String KEY_BOOKMARKS = "bookmarks_json";

    public static List<BookmarkItem> getBookmarks(Context context) {
        List<BookmarkItem> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String jsonStr = pref.getString(KEY_BOOKMARKS, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new BookmarkItem(
                        obj.getString("id"),
                        obj.getString("title"),
                        obj.getString("url"),
                        obj.getLong("timestamp")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static boolean saveBookmark(Context context, String title, String url, String rawHtml) {
        if (context == null || url == null || url.isEmpty()) return false;
        try {
            String id = "bm_" + Math.abs(url.hashCode());
            List<BookmarkItem> list = getBookmarks(context);

            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).url.equals(url)) {
                    list.remove(i);
                }
            }

            BookmarkItem newItem = new BookmarkItem(id, (title == null || title.isEmpty()) ? url : title, url, System.currentTimeMillis());
            list.add(0, newItem);

            JSONArray array = new JSONArray();
            for (BookmarkItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            }

            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            pref.edit().putString(KEY_BOOKMARKS, array.toString()).apply();

            if (rawHtml != null && !rawHtml.isEmpty()) {
                File dir = new File(context.getFilesDir(), "offline_bookmarks");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, id + ".html");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(rawHtml.getBytes(StandardCharsets.UTF_8));
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getOfflineContent(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return null;
        try {
            File file = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".html");
            if (!file.exists()) return null;
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                in.read(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void deleteBookmark(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return;
        try {
            List<BookmarkItem> list = getBookmarks(context);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).id.equals(id)) {
                    list.remove(i);
                }
            }
            JSONArray array = new JSONArray();
            for (BookmarkItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            }
            SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            pref.edit().putString(KEY_BOOKMARKS, array.toString()).apply();

            File file = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".html");
            if (file.exists()) file.delete();
        } catch (Exception ignored) {}
    }
}
