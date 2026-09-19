package com.velocity.browser;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.velocity.browser.reconstruction.ReaderTheme;

import java.util.Map;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableLoader;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.image.ImageProps;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;

/**
 * Custom Markwon Renderer configuration for Velocity.
 * Handles:
 * - Direct native rendering into TextView (Zero WebView)
 * - Clickable links & Anchor resolution
 * - Clickable images opening in ImageViewerActivity
 * - Async images loaded via Velocity's disk cache
 * - Full Dark/Light/Sepia reader theme integration
 * - Heading anchor mapping for Table of Contents
 */
public final class MarkdownHelper {

    public interface LinkClickListener {
        void onLinkClicked(String url);
    }

    public interface ImageClickListener {
        void onImageClicked(String imageUrl);
    }

    public static Markwon createMarkwon(
            @NonNull Context context,
            @NonNull ReaderTheme theme,
            @NonNull LinkClickListener linkListener,
            @NonNull ImageClickListener imageListener,
            @NonNull Map<String, Integer> headingPositionsMap
    ) {
        return Markwon.builder(context)
                .usePlugin(CorePlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder
                                .linkColor(theme.linkColor)
                                .codeBackgroundColor(theme.codeBackgroundColor)
                                .codeTextColor(theme.textColor)
                                .codeBlockBackgroundColor(theme.codeBackgroundColor)
                                .codeBlockTextColor(theme.textColor)
                                .blockQuoteColor(theme.accentBarColor)
                                .headingBreakColor(theme.borderColor)
                                .headingTextSizeMultipliers(new float[]{1.7f, 1.45f, 1.25f, 1.1f, 1.0f, 0.9f});
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            if (link != null && !link.isEmpty()) {
                                linkListener.onLinkClicked(link);
                            }
                        });
                        builder.asyncDrawableLoader(new VelocityImageLoader(context));
                    }

                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                        SpanFactory origin = builder.getFactory(Image.class);
                        builder.setFactory(Image.class, (configuration, props) -> {
                            Object span = origin != null ? origin.getSpans(configuration, props) : null;
                            String destination = ImageProps.DESTINATION.get(props);
                            if (destination != null && !destination.isEmpty()) {
                                ClickableSpan clickSpan = new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View widget) {
                                        imageListener.onImageClicked(destination);
                                    }
                                };
                                return new Object[]{span, clickSpan};
                            }
                            return span;
                        });
                    }

                    @Override
                    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
                        builder.on(Heading.class, (visitor, heading) -> {
                            int start = visitor.length();
                            visitor.visitChildren(heading);
                            int end = visitor.length();

                            String rawHeadingText = visitor.builder().subSequence(start, end).toString().trim();
                            String cleanHeading = rawHeadingText;
                            String customId = null;

                            // Support custom IDs {#custom-id} in headings
                            if (rawHeadingText.contains("{#") && rawHeadingText.endsWith("}")) {
                                int idStart = rawHeadingText.lastIndexOf("{#");
                                int idEnd = rawHeadingText.lastIndexOf("}");
                                if (idStart != -1 && idEnd > idStart) {
                                    customId = rawHeadingText.substring(idStart + 2, idEnd).trim();
                                    cleanHeading = rawHeadingText.substring(0, idStart).trim();
                                }
                            }

                            String anchorId = cleanHeading.toLowerCase(java.util.Locale.ROOT)
                                    .replaceAll("[^a-z0-9]+", "-")
                                    .replaceAll("^-|-$", "");

                            headingPositionsMap.put(anchorId, start);
                            headingPositionsMap.put(cleanHeading, start);
                            headingPositionsMap.put(cleanHeading.toLowerCase(java.util.Locale.ROOT), start);

                            if (customId != null && !customId.isEmpty()) {
                                headingPositionsMap.put(customId, start);
                                headingPositionsMap.put(customId.toLowerCase(java.util.Locale.ROOT), start);
                                headingPositionsMap.put(customId.replace("_", "-"), start);
                                headingPositionsMap.put(customId.replace("_", "-").toLowerCase(java.util.Locale.ROOT), start);
                                headingPositionsMap.put(customId.replace("-", "_"), start);
                                headingPositionsMap.put(customId.replace("-", "_").toLowerCase(java.util.Locale.ROOT), start);
                            }
                        });
                    }
                })
                .build();
    }

    /**
     * Async image loader that connects Markwon image requests to Velocity ImageLoader.
     */
    private static class VelocityImageLoader extends AsyncDrawableLoader {
        private final Context context;

        VelocityImageLoader(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void load(@NonNull AsyncDrawable asyncDrawable) {
            String destination = asyncDrawable.getDestination();
            int density = (int) context.getResources().getDisplayMetrics().density;
            int defaultW = 200 * density;
            int defaultH = 150 * density;

            ImageLoader.getInstance(context).load(destination, defaultW, defaultH, new ImageLoader.ImageLoadCallback() {
                @Override
                public void onImageLoaded(android.graphics.Bitmap bitmap) {
                    if (bitmap != null) {
                        BitmapDrawable bd = new BitmapDrawable(context.getResources(), bitmap);
                        bd.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (asyncDrawable.isAttached()) {
                                asyncDrawable.setResult(bd);
                            }
                        });
                    }
                }

                @Override
                public void onError(Throwable error) {
                    GradientDrawable err = new GradientDrawable();
                    err.setColor(0x15FF0000);
                    err.setCornerRadius(6 * density);
                    err.setBounds(0, 0, defaultW, defaultH);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (asyncDrawable.isAttached()) {
                            asyncDrawable.setResult(err);
                        }
                    });
                }
            });
        }

        @Override
        public void cancel(@NonNull AsyncDrawable asyncDrawable) {
        }

        @Override
        public Drawable placeholder(@NonNull AsyncDrawable asyncDrawable) {
            int density = (int) context.getResources().getDisplayMetrics().density;
            GradientDrawable placeholder = new GradientDrawable();
            placeholder.setColor(0x1F888888);
            placeholder.setCornerRadius(6 * density);
            placeholder.setBounds(0, 0, 150 * density, 100 * density);
            return placeholder;
        }
    }
}
