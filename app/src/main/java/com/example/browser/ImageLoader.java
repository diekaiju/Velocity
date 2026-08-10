package com.example.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ImageLoader {

    public interface ImageLoadCallback {
        void onImageLoaded(Bitmap bitmap);
        void onError(Throwable error);
    }

    private static ImageLoader instance;
    private final Context context;
    private final LruCache<String, Bitmap> memoryCache;
    private final File diskCacheDir;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private ImageLoader(Context context) {
        this.context = context.getApplicationContext();
        
        // Memory cache size: 1/8th of available runtime memory
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        diskCacheDir = new File(this.context.getCacheDir(), "image_cache");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }

    public static synchronized ImageLoader getInstance(Context context) {
        if (instance == null) {
            instance = new ImageLoader(context);
        }
        return instance;
    }

    public Future<?> load(String url, int targetWidth, int targetHeight, ImageLoadCallback callback) {
        // Handle memory cache hit
        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            callback.onImageLoaded(cached);
            return null;
        }

        // Run asynchronously
        return executor.submit(() -> {
            try {
                Bitmap bitmap = null;

                // Base64 check
                if (url.startsWith("data:image/")) {
                    bitmap = decodeBase64(url, targetWidth, targetHeight);
                } else {
                    // Check disk cache
                    File cachedFile = getDiskCacheFile(url);
                    if (cachedFile.exists()) {
                        bitmap = decodeFile(cachedFile, targetWidth, targetHeight);
                        if (bitmap != null) {
                            memoryCache.put(url, bitmap);
                        }
                    }

                    if (bitmap == null) {
                        // Download
                        byte[] bytes = downloadBytes(url);
                        if (bytes != null && bytes.length > 0) {
                            // Save to disk cache
                            saveToDiskCache(cachedFile, bytes);
                            bitmap = decodeByteArray(bytes, targetWidth, targetHeight);
                            if (bitmap != null) {
                                memoryCache.put(url, bitmap);
                            }
                        }
                    }
                }

                if (bitmap != null) {
                    final Bitmap finalBitmap = bitmap;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onImageLoaded(finalBitmap);
                    });
                } else {
                    throw new Exception("Failed to decode bitmap");
                }
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onError(e);
                });
            }
        });
    }

    private Bitmap decodeBase64(String dataUrl, int reqWidth, int reqHeight) throws Exception {
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex == -1) {
            throw new IllegalArgumentException("Invalid base64 URL format");
        }
        String base64Data = dataUrl.substring(commaIndex + 1);
        byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
        return decodeByteArray(bytes, reqWidth, reqHeight);
    }

    private File getDiskCacheFile(String url) {
        String key;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            key = hexString.toString();
        } catch (Exception e) {
            key = String.valueOf(url.hashCode());
        }
        return new File(diskCacheDir, key);
    }

    private byte[] downloadBytes(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

        int status = conn.getResponseCode();
        // Handle manual redirect if auto redirect fails or for certain redirect codes
        if (status >= 300 && status <= 307 && status != 306) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                conn.disconnect();
                return downloadBytes(redirectUrl);
            }
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP error code: " + status + " for URL: " + urlStr);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private void saveToDiskCache(File file, byte[] bytes) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        } catch (Exception ignored) {}
    }

    private Bitmap decodeByteArray(byte[] bytes, int reqWidth, int reqHeight) {
        if (reqWidth <= 0 || reqHeight <= 0) {
            reqWidth = 1024;
            reqHeight = 1024;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private Bitmap decodeFile(File file, int reqWidth, int reqHeight) {
        if (reqWidth <= 0 || reqHeight <= 0) {
            reqWidth = 1024;
            reqHeight = 1024;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    
    public void clearCache() {
        memoryCache.evictAll();
        File[] files = diskCacheDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }
}
