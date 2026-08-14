package com.example.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ImageViewerActivity extends Activity {

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
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.setTitle("Downloading Image");
                request.setDescription("Velocity Browser Image Download");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                if (filename.contains("?")) {
                    filename = filename.substring(0, filename.indexOf("?"));
                }
                if (filename.isEmpty()) {
                    filename = "image_" + System.currentTimeMillis() + ".jpg";
                }

                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to download image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        progressBar.setVisibility(View.VISIBLE);
        ImageLoader.getInstance(this).load(imageUrl, 1200, 1200, new ImageLoader.ImageLoadCallback() {
            @Override
            public void onImageLoaded(Bitmap bitmap) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    fullImageView.setImageBitmap(bitmap);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ImageViewerActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
