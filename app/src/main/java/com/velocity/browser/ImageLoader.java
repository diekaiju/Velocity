package com.velocity.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Base64;
import android.util.LruCache;

import com.caverock.androidsvg.SVG;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    public long getDiskCacheSize(String url) {
        if (url == null || url.trim().isEmpty() || url.startsWith("data:")) return 0;
        File file = getDiskCacheFile(url);
        return (file != null && file.exists()) ? file.length() : 0;
    }

    public File getCachedFile(String url) {
        if (url == null || url.trim().isEmpty() || url.startsWith("data:")) return null;
        File f = getDiskCacheFile(url);
        return (f != null && f.exists() && f.length() > 0) ? f : null;
    }

    public byte[] getOrDownloadImageBytes(String url) throws Exception {
        if (url == null || url.trim().isEmpty()) return null;
        if (url.startsWith("data:image/")) {
            int commaIndex = url.indexOf(',');
            if (commaIndex != -1) {
                String base64Data = url.substring(commaIndex + 1);
                return Base64.decode(base64Data, Base64.DEFAULT);
            }
        }
        File cached = getDiskCacheFile(url);
        if (cached.exists() && cached.length() > 0) {
            try (FileInputStream fis = new FileInputStream(cached);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    bos.write(buf, 0, r);
                }
                byte[] cachedBytes = bos.toByteArray();
                if (!isHtmlContent(cachedBytes)) {
                    return cachedBytes;
                } else {
                    cached.delete();
                }
            }
        }
        byte[] downloaded = downloadBytes(url, 0);
        if (downloaded != null && downloaded.length > 0) {
            saveToDiskCache(cached, downloaded);
        }
        return downloaded;
    }

    public Future<?> load(String url, int targetWidth, int targetHeight, ImageLoadCallback callback) {
        if (url == null || url.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Image URL is empty"));
            return null;
        }

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
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        bitmap = decodeFile(cachedFile, targetWidth, targetHeight);
                        if (bitmap != null) {
                            memoryCache.put(url, bitmap);
                        } else {
                            // Delete corrupted / invalid cache file
                            cachedFile.delete();
                        }
                    }

                    if (bitmap == null) {
                        // Download
                        byte[] bytes = downloadBytes(url, 0);
                        if (bytes != null && bytes.length > 0) {
                            if (isHtmlContent(bytes)) {
                                throw new Exception("URL returned an HTML web page instead of an image");
                            }
                            bitmap = decodeByteArray(bytes, targetWidth, targetHeight);
                            if (bitmap != null) {
                                saveToDiskCache(cachedFile, bytes);
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
                    throw new Exception("Unable to decode image format");
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
            digest.update(url.getBytes(StandardCharsets.UTF_8));
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

    private boolean isHtmlContent(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return true;
        int checkLen = Math.min(bytes.length, 256);
        String header = new String(bytes, 0, checkLen, StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
        return header.startsWith("<!doctype") || header.startsWith("<html") || header.contains("<head") || header.contains("<body");
    }

    private boolean isSvgContent(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        int checkLen = Math.min(bytes.length, 512);
        String header = new String(bytes, 0, checkLen, StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
        return header.startsWith("<?xml") && header.contains("<svg") || header.startsWith("<svg") || header.contains("xmlns=\"http://www.w3.org/2000/svg\"");
    }

    private byte[] downloadBytes(String urlStr, int redirectCount) throws Exception {
        if (urlStr == null || urlStr.trim().isEmpty()) return null;
        if (redirectCount > 5) throw new Exception("Too many redirects for " + urlStr);

        urlStr = urlStr.trim().replace(" ", "%20");
        if (urlStr.startsWith("//")) {
            urlStr = "https:" + urlStr;
        } else if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://" + urlStr;
        }

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setRequestProperty("Sec-Fetch-Dest", "image");
        conn.setRequestProperty("Sec-Fetch-Mode", "no-cors");
        conn.setRequestProperty("Sec-Fetch-Site", "cross-site");

        String hostReferer = url.getProtocol() + "://" + url.getHost() + "/";
        conn.setRequestProperty("Referer", hostReferer);

        int status = conn.getResponseCode();

        // Handle manual redirect across HTTP/HTTPS or domains
        if (status >= 300 && status <= 308 && status != 306) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null && !redirectUrl.trim().isEmpty()) {
                conn.disconnect();
                if (redirectUrl.startsWith("//")) {
                    redirectUrl = "https:" + redirectUrl;
                } else if (!redirectUrl.startsWith("http://") && !redirectUrl.startsWith("https://")) {
                    try {
                        redirectUrl = new URL(url, redirectUrl).toString();
                    } catch (Exception ignored) {}
                }
                return downloadBytes(redirectUrl, redirectCount + 1);
            }
        }

        // If 403 Forbidden with Referer, retry without Referer
        if (status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            status = conn.getResponseCode();
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP error code: " + status + " for URL: " + urlStr);
        }

        String contentType = conn.getContentType();
        if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
            conn.disconnect();
            throw new Exception("URL returned HTML instead of an image");
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
        if (bytes == null || bytes.length == 0 || isHtmlContent(bytes)) return;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        } catch (Exception ignored) {}
    }

    private Bitmap decodeByteArray(byte[] bytes, int reqWidth, int reqHeight) {
        if (bytes == null || bytes.length == 0 || isHtmlContent(bytes)) return null;

        // 1. Check if SVG
        if (isSvgContent(bytes)) {
            Bitmap svgBmp = decodeSvg(bytes, reqWidth, reqHeight);
            if (svgBmp != null) return svgBmp;
        }

        // 2. Standard Android BitmapFactory
        if (reqWidth <= 0 || reqHeight <= 0) {
            reqWidth = 1024;
            reqHeight = 1024;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            // Might be SVG without XML header
            Bitmap svgBmp = decodeSvg(bytes, reqWidth, reqHeight);
            if (svgBmp != null) return svgBmp;
            return null;
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bmp == null) {
            bmp = decodeSvg(bytes, reqWidth, reqHeight);
        }
        return bmp;
    }

    private Bitmap decodeFile(File file, int reqWidth, int reqHeight) {
        if (file == null || !file.exists() || file.length() == 0) return null;

        try {
            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    bos.write(buf, 0, r);
                }
                bytes = bos.toByteArray();
            }
            return decodeByteArray(bytes, reqWidth, reqHeight);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeSvg(byte[] bytes, int reqWidth, int reqHeight) {
        try {
            SVG svg = SVG.getFromInputStream(new ByteArrayInputStream(bytes));
            if (svg != null) {
                float docWidth = svg.getDocumentWidth();
                float docHeight = svg.getDocumentHeight();
                if (docWidth <= 0 || docHeight <= 0) {
                    RectF viewBox = svg.getDocumentViewBox();
                    if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                        docWidth = viewBox.width();
                        docHeight = viewBox.height();
                    } else {
                        docWidth = (reqWidth > 0) ? reqWidth : 512;
                        docHeight = (reqHeight > 0) ? reqHeight : 512;
                    }
                }
                float scale = 1.0f;
                if (reqWidth > 0 && reqHeight > 0) {
                    scale = Math.min((float) reqWidth / docWidth, (float) reqHeight / docHeight);
                    if (scale <= 0) scale = 1.0f;
                }
                int finalW = Math.max(1, Math.round(docWidth * scale));
                int finalH = Math.max(1, Math.round(docHeight * scale));

                Bitmap bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                svg.renderToCanvas(canvas);
                return bmp;
            }
        } catch (Exception ignored) {}
        return null;
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
        return Math.max(1, inSampleSize);
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
