package com.velocity.browser;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class ImageViewerActivity extends Activity {

    private Bitmap loadedBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_image_viewer);

        String imageUrl = getIntent().getStringExtra("image_url");
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ProgressBar progressBar = findViewById(R.id.progressBar);
        ImageView fullImageView = findViewById(R.id.fullImageView);
        ImageButton backButton = findViewById(R.id.backButton);
        FloatingActionButton downloadButton = findViewById(R.id.downloadButton);

        backButton.setOnClickListener(v -> finish());

        downloadButton.setOnClickListener(v -> {
            Toast.makeText(this, "Saving image...", Toast.LENGTH_SHORT).show();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    byte[] bytes = ImageLoader.getInstance(this).getOrDownloadImageBytes(imageUrl);
                    if (bytes != null && bytes.length > 0) {
                        saveImageBytes(imageUrl, bytes);
                    } else if (loadedBitmap != null) {
                        saveBitmap(imageUrl, loadedBitmap);
                    } else {
                        throw new Exception("Unable to retrieve image data");
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        progressBar.setVisibility(View.VISIBLE);
        ImageLoader.getInstance(this).load(imageUrl, 1600, 1600, new ImageLoader.ImageLoadCallback() {
            @Override
            public void onImageLoaded(Bitmap bitmap) {
                loadedBitmap = bitmap;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    fullImageView.setImageBitmap(bitmap);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ImageViewerActivity.this, "Failed to load image: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveImageBytes(String originalUrl, byte[] bytes) throws Exception {
        String ext = ".jpg";
        String mime = "image/jpeg";
        if (originalUrl.contains(".png") || (bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')) {
            ext = ".png";
            mime = "image/png";
        } else if (originalUrl.contains(".webp") || (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F')) {
            ext = ".webp";
            mime = "image/webp";
        } else if (originalUrl.contains(".gif") || (bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F')) {
            ext = ".gif";
            mime = "image/gif";
        } else if (originalUrl.contains(".svg") || (bytes.length > 4 && new String(bytes, 0, Math.min(bytes.length, 100), java.nio.charset.StandardCharsets.UTF_8).contains("<svg"))) {
            ext = ".svg";
            mime = "image/svg+xml";
        }

        String fileName = "velocity_img_" + System.currentTimeMillis() + ext;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(bytes);
                        os.flush();
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_SHORT).show());
                return;
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        File dest = new File(downloadsDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(bytes);
            fos.flush();
        }

        MediaScannerConnection.scanFile(this, new String[]{dest.getAbsolutePath()}, new String[]{mime}, null);
        runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_SHORT).show());
    }

    private void saveBitmap(String originalUrl, Bitmap bitmap) throws Exception {
        String fileName = "velocity_img_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.flush();
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_SHORT).show());
                return;
            }
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        File dest = new File(downloadsDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        }

        MediaScannerConnection.scanFile(this, new String[]{dest.getAbsolutePath()}, new String[]{"image/png"}, null);
        runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_SHORT).show());
    }
}
