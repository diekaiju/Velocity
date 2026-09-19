package com.velocity.browser;

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

    /**
     * Save page bookmark with its offline Markdown (.md) content.
     */
    public static boolean saveBookmark(Context context, String title, String url, String markdownContent) {
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

            if (markdownContent != null && !markdownContent.isEmpty()) {
                File dir = new File(context.getFilesDir(), "offline_bookmarks");
                if (!dir.exists()) dir.mkdirs();

                // Save as .md file with metadata frontmatter
                File file = new File(dir, id + ".md");
                StringBuilder fileContent = new StringBuilder();
                fileContent.append("---\n");
                fileContent.append("title: ").append(title != null ? title.replace("\n", " ") : "Untitled").append("\n");
                fileContent.append("url: ").append(url).append("\n");
                fileContent.append("saved_at: ").append(System.currentTimeMillis()).append("\n");
                fileContent.append("---\n\n");
                fileContent.append(markdownContent);

                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(fileContent.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieve offline Markdown (.md) content for the bookmark ID.
     */
    public static String getOfflineContent(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return null;
        try {
            File mdFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".md");
            if (mdFile.exists()) {
                try (FileInputStream in = new FileInputStream(mdFile)) {
                    byte[] bytes = new byte[(int) mdFile.length()];
                    in.read(bytes);
                    String raw = new String(bytes, StandardCharsets.UTF_8);
                    // Strip YAML frontmatter if present
                    if (raw.startsWith("---")) {
                        int secondDash = raw.indexOf("---", 3);
                        if (secondDash != -1) {
                            return raw.substring(secondDash + 3).trim();
                        }
                    }
                    return raw;
                }
            }

            // Fallback check for legacy .html files
            File htmlFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".html");
            if (htmlFile.exists()) {
                try (FileInputStream in = new FileInputStream(htmlFile)) {
                    byte[] bytes = new byte[(int) htmlFile.length()];
                    in.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static File getBookmarkFile(Context context, String id) {
        if (context == null || id == null) return null;
        File mdFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".md");
        if (mdFile.exists()) return mdFile;
        File htmlFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".html");
        if (htmlFile.exists()) return htmlFile;
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

            File mdFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".md");
            if (mdFile.exists()) mdFile.delete();

            File htmlFile = new File(new File(context.getFilesDir(), "offline_bookmarks"), id + ".html");
            if (htmlFile.exists()) htmlFile.delete();
        } catch (Exception ignored) {}
    }
}
