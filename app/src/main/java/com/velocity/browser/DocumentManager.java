package com.velocity.browser;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentManager {

    public enum SortOrder {
        NAME_ASC,
        NAME_DESC,
        DATE_DESC,
        DATE_ASC
    }

    public static class DocItem {
        public final File file;
        public final boolean isDirectory;
        public final String name;
        public final String displayName;
        public final long lastModified;
        public final long size;
        public String title;
        public String url;
        public int depth = 0;
        public boolean isExpanded = false;
        public List<DocItem> children = new ArrayList<>();

        public DocItem(File file, boolean isDirectory, int depth) {
            this.file = file;
            this.isDirectory = isDirectory;
            this.name = file.getName();
            this.lastModified = file.lastModified();
            this.size = file.length();
            this.depth = depth;

            if (isDirectory) {
                this.displayName = file.getName();
            } else {
                // Strip .md extension for display if present
                if (file.getName().toLowerCase().endsWith(".md")) {
                    this.displayName = file.getName().substring(0, file.getName().length() - 3);
                } else {
                    this.displayName = file.getName();
                }
            }
        }
    }

    public static class LibraryStats {
        public int fileCount = 0;
        public int folderCount = 0;
    }

    private static final String DOCUMENTS_DIR_NAME = "documents";

    /**
     * Get or create the root documents directory in app storage.
     */
    public static File getDocumentsRoot(Context context) {
        File dir = new File(context.getFilesDir(), DOCUMENTS_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        migrateLegacyBookmarks(context, dir);
        return dir;
    }

    /**
     * Migrate legacy offline_bookmarks into documents root directory.
     */
    private static void migrateLegacyBookmarks(Context context, File targetRoot) {
        if (context == null || targetRoot == null) return;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("velocity_documents_pref", Context.MODE_PRIVATE);
            boolean alreadyMigrated = prefs.getBoolean("bookmarks_migrated_v1", false);
            if (alreadyMigrated) return;

            File legacyDir = new File(context.getFilesDir(), "offline_bookmarks");
            if (legacyDir.exists() && legacyDir.isDirectory()) {
                File[] files = legacyDir.listFiles();
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        if (f.isFile()) {
                            if (f.getName().endsWith(".md")) {
                                String title = extractTitleFromMdFile(f);
                                String safeName = sanitizeFilename(title);
                                if (safeName.isEmpty() || "Untitled".equalsIgnoreCase(safeName)) {
                                    safeName = f.getName();
                                } else {
                                    safeName = safeName + ".md";
                                }
                                File dest = new File(targetRoot, safeName);
                                if (!dest.exists()) {
                                    copyFile(f, dest);
                                }
                            }
                            f.delete(); // Remove from legacy folder so it never duplicates
                        }
                    }
                }
            }
            prefs.edit().putBoolean("bookmarks_migrated_v1", true).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Build the hierarchical tree structure for folders and files.
     */
    public static List<DocItem> getDocumentTree(Context context, SortOrder sortOrder) {
        File root = getDocumentsRoot(context);
        return scanDirectoryRecursive(root, 0, sortOrder);
    }

    private static List<DocItem> scanDirectoryRecursive(File dir, int depth, SortOrder sortOrder) {
        List<DocItem> items = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return items;

        File[] files = dir.listFiles();
        if (files == null) return items;

        List<DocItem> folderList = new ArrayList<>();
        List<DocItem> fileList = new ArrayList<>();

        for (File f : files) {
            if (f.getName().startsWith(".")) continue; // Skip hidden files

            if (f.isDirectory()) {
                DocItem folderItem = new DocItem(f, true, depth);
                folderItem.children = scanDirectoryRecursive(f, depth + 1, sortOrder);
                folderList.add(folderItem);
            } else {
                DocItem fileItem = new DocItem(f, false, depth);
                fileList.add(fileItem);
            }
        }

        // Apply sorting
        Comparator<DocItem> comparator = (a, b) -> {
            switch (sortOrder) {
                case NAME_DESC:
                    return b.displayName.compareToIgnoreCase(a.displayName);
                case DATE_DESC:
                    return Long.compare(b.lastModified, a.lastModified);
                case DATE_ASC:
                    return Long.compare(a.lastModified, b.lastModified);
                case NAME_ASC:
                default:
                    return a.displayName.compareToIgnoreCase(b.displayName);
            }
        };

        Collections.sort(folderList, comparator);
        Collections.sort(fileList, comparator);

        items.addAll(folderList);
        items.addAll(fileList);
        return items;
    }

    /**
     * Compute total count of files and folders in library.
     */
    public static LibraryStats getStats(Context context) {
        LibraryStats stats = new LibraryStats();
        File root = getDocumentsRoot(context);
        countRecursive(root, stats);
        return stats;
    }

    private static void countRecursive(File dir, LibraryStats stats) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                stats.folderCount++;
                countRecursive(f, stats);
            } else {
                stats.fileCount++;
            }
        }
    }

    /**
     * Get flat list of all directories for the "Move to Folder" selector dialog.
     */
    public static List<File> getAllFolders(Context context) {
        List<File> folders = new ArrayList<>();
        File root = getDocumentsRoot(context);
        folders.add(root);
        collectFoldersRecursive(root, folders);
        return folders;
    }

    private static void collectFoldersRecursive(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                list.add(f);
                collectFoldersRecursive(f, list);
            }
        }
    }

    /**
     * Create a new folder.
     */
    public static boolean createFolder(File parentDir, String folderName) {
        if (parentDir == null || folderName == null || folderName.trim().isEmpty()) return false;
        String sanitized = sanitizeFilename(folderName.trim());
        File newDir = new File(parentDir, sanitized);
        if (newDir.exists()) return false;
        return newDir.mkdirs();
    }

    /**
     * Rename a folder.
     */
    public static boolean renameFolder(File folder, String newName) {
        if (folder == null || !folder.exists() || newName == null || newName.trim().isEmpty()) return false;
        String sanitized = sanitizeFilename(newName.trim());
        File parent = folder.getParentFile();
        if (parent == null) return false;
        File dest = new File(parent, sanitized);
        if (dest.exists()) return false;
        return folder.renameTo(dest);
    }

    /**
     * Delete a folder and its contents recursively.
     */
    public static boolean deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return false;
        if (folder.isDirectory()) {
            File[] children = folder.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolder(child);
                }
            }
        }
        return folder.delete();
    }

    /**
     * Save/Create a Markdown file.
     */
    public static File saveDocument(Context context, File targetFolder, String title, String url, String markdownContent) {
        if (context == null) return null;
        if (targetFolder == null || !targetFolder.exists()) {
            targetFolder = getDocumentsRoot(context);
        }

        String safeTitle = (title == null || title.trim().isEmpty()) ? "Untitled" : title.trim();
        String safeName = sanitizeFilename(safeTitle);
        if (safeName.isEmpty()) safeName = "Document_" + System.currentTimeMillis();

        File file = new File(targetFolder, safeName + ".md");
        int counter = 1;
        while (file.exists()) {
            file = new File(targetFolder, safeName + " (" + counter + ").md");
            counter++;
        }

        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("title: ").append(safeTitle.replace("\n", " ")).append("\n");
        if (url != null && !url.isEmpty()) {
            content.append("url: ").append(url).append("\n");
        }
        content.append("saved_at: ").append(System.currentTimeMillis()).append("\n");
        content.append("---\n\n");
        if (markdownContent != null) {
            content.append(markdownContent);
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.toString().getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Move a file into another folder.
     */
    public static boolean moveFile(File sourceFile, File targetFolder) {
        if (sourceFile == null || !sourceFile.exists() || targetFolder == null || !targetFolder.isDirectory()) {
            return false;
        }
        File destFile = new File(targetFolder, sourceFile.getName());
        if (destFile.getAbsolutePath().equals(sourceFile.getAbsolutePath())) {
            return true; // already there
        }
        int counter = 1;
        String baseName = sourceFile.getName();
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            ext = baseName.substring(dot);
            baseName = baseName.substring(0, dot);
        }
        while (destFile.exists()) {
            destFile = new File(targetFolder, baseName + " (" + counter + ")" + ext);
            counter++;
        }
        boolean success = sourceFile.renameTo(destFile);
        if (!success) {
            try {
                copyFile(sourceFile, destFile);
                success = sourceFile.delete();
            } catch (Exception e) {
                return false;
            }
        }
        return success;
    }

    /**
     * Rename a file.
     */
    public static boolean renameFile(File file, String newName) {
        if (file == null || !file.exists() || newName == null || newName.trim().isEmpty()) return false;
        String sanitized = sanitizeFilename(newName.trim());
        if (!sanitized.toLowerCase().endsWith(".md")) {
            sanitized = sanitized + ".md";
        }
        File parent = file.getParentFile();
        if (parent == null) return false;
        File dest = new File(parent, sanitized);
        if (dest.exists() && !dest.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
            return false;
        }
        return file.renameTo(dest);
    }

    /**
     * Duplicate a document file (make a copy).
     */
    public static File duplicateDocument(File file) {
        if (file == null || !file.exists()) return null;
        File parent = file.getParentFile();
        if (parent == null) return null;

        String baseName = file.getName();
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            ext = baseName.substring(dot);
            baseName = baseName.substring(0, dot);
        }

        File copyFile = new File(parent, baseName + " (copy)" + ext);
        int counter = 2;
        while (copyFile.exists()) {
            copyFile = new File(parent, baseName + " (copy " + counter + ")" + ext);
            counter++;
        }

        try {
            copyFile(file, copyFile);
            return copyFile;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Delete a file.
     */
    public static boolean deleteFile(File file) {
        if (file == null || !file.exists()) return false;
        return file.delete();
    }

    /**
     * Read the markdown content of a file (clean without YAML frontmatter, plus extracted title/url).
     */
    public static class DocumentContent {
        public String title = "Untitled";
        public String url = "";
        public String markdown = "";
        public String rawContent = "";
    }

    public static DocumentContent readDocument(File file) {
        DocumentContent doc = new DocumentContent();
        if (file == null || !file.exists()) return doc;
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                in.read(bytes);
            }
            String raw = new String(bytes, StandardCharsets.UTF_8);
            doc.rawContent = raw;
            doc.markdown = raw;
            doc.title = file.getName().replace(".md", "");

            if (raw.startsWith("---")) {
                int secondDash = raw.indexOf("---", 3);
                if (secondDash != -1) {
                    String frontmatter = raw.substring(3, secondDash);
                    doc.markdown = raw.substring(secondDash + 3).trim();
                    for (String line : frontmatter.split("\n")) {
                        if (line.startsWith("title:")) {
                            doc.title = line.substring(6).trim();
                        } else if (line.startsWith("url:")) {
                            doc.url = line.substring(4).trim();
                        }
                    }
                }
            }

            if (doc.title.equals("Untitled") || doc.title.isEmpty()) {
                Pattern p = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
                Matcher m = p.matcher(doc.markdown);
                if (m.find()) {
                    doc.title = m.group(1).trim();
                }
            }
        } catch (Exception ignored) {}
        return doc;
    }

    /**
     * Create an Android Share Intent for the .md file.
     */
    public static Intent createShareIntent(Context context, File file) {
        if (context == null || file == null || !file.exists()) return null;
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/markdown");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName().replace(".md", ""));
            
            DocumentContent doc = readDocument(file);
            shareIntent.putExtra(Intent.EXTRA_TEXT, doc.markdown);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return Intent.createChooser(shareIntent, "Share Markdown Note");
        } catch (Exception e) {
            // Fallback plain text share
            DocumentContent doc = readDocument(file);
            Intent textIntent = new Intent(Intent.ACTION_SEND);
            textIntent.setType("text/plain");
            textIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            textIntent.putExtra(Intent.EXTRA_TEXT, doc.markdown);
            return Intent.createChooser(textIntent, "Share Markdown Note");
        }
    }

    /**
     * Export the .md file directly to the public Downloads folder.
     */
    public static boolean exportToDownloads(Context context, File sourceFile) {
        if (context == null || sourceFile == null || !sourceFile.exists()) return false;
        try {
            String fileName = sourceFile.getName();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Velocity");

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    return true;
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File targetDir = new File(downloadsDir, "Velocity");
                if (!targetDir.exists()) targetDir.mkdirs();
                File dest = new File(targetDir, fileName);
                copyFile(sourceFile, dest);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static String sanitizeFilename(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String extractTitleFromMdFile(File file) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("title:")) {
                        return line.substring(6).trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return file.getName().replace(".md", "");
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
