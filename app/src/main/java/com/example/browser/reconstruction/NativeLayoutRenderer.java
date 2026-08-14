package com.example.browser.reconstruction;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.util.TypedValue;

import com.example.browser.ImageLoader;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advanced native HTML reconstruction renderer.
 *
 * This renderer intentionally DOES NOT use WebView.
 *
 * Pipeline:
 *
 *     HTML/DOM
 *        ↓
 *     LayoutNode
 *        ↓
 *     CSS interpretation
 *        ↓
 *     Native Android View hierarchy
 *
 * It is designed for rendering extracted/reconstructed pages where
 * the original DOM and selected CSS properties are already available.
 */
public final class NativeLayoutRenderer {

    private NativeLayoutRenderer() {
    }

    // ------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------

    private static final int DEFAULT_TEXT_COLOR = Color.rgb(32, 33, 36);
    private static final int DEFAULT_LINK_COLOR = Color.rgb(25, 103, 210);
    private static final int DEFAULT_BORDER_COLOR = Color.rgb(218, 220, 224);

    private static final float DEFAULT_TEXT_SIZE = 15f;

    private static final int MAX_DEPTH = 150;

    private static final Set<String> BLOCK_TAGS = new HashSet<>();

    private static final Set<String> INLINE_TAGS = new HashSet<>();

    static {
        String[] blocks = {
                "html",
                "body",
                "main",
                "article",
                "section",
                "header",
                "footer",
                "nav",
                "aside",
                "div",
                "p",
                "address",
                "blockquote",
                "pre",
                "figure",
                "figcaption",
                "form",
                "fieldset",
                "legend",
                "table",
                "thead",
                "tbody",
                "tfoot",
                "tr",
                "ul",
                "ol",
                "li",
                "dl",
                "dt",
                "dd",
                "details",
                "summary",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "hr"
        };

        for (String s : blocks) {
            BLOCK_TAGS.add(s);
        }

        String[] inline = {
                "span",
                "a",
                "b",
                "strong",
                "i",
                "em",
                "u",
                "s",
                "del",
                "ins",
                "small",
                "big",
                "mark",
                "code",
                "kbd",
                "samp",
                "var",
                "sub",
                "sup",
                "abbr",
                "cite",
                "q",
                "time",
                "label"
        };

        for (String s : inline) {
            INLINE_TAGS.add(s);
        }
    }

    // ------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------

    public interface LinkClickListener {
        void onLinkClick(String href);
    }

    public interface AnchorListener {
        void onAnchor(String id);
    }

    /**
     * Original compatible API.
     */
    public static void renderTree(
            Context context,
            ViewGroup container,
            LayoutNode rootNode,
            LinkClickListener listener
    ) {
        renderTree(
                context,
                container,
                rootNode,
                listener,
                null
        );
    }

    public static void renderTree(
            Context context,
            ViewGroup container,
            LayoutNode rootNode,
            LinkClickListener listener,
            AnchorListener anchorListener
    ) {
        renderTree(
                context,
                container,
                rootNode,
                listener,
                anchorListener,
                ReaderTheme.LIGHT
        );
    }

    public static void renderTree(
            Context context,
            ViewGroup container,
            LayoutNode rootNode,
            LinkClickListener listener,
            AnchorListener anchorListener,
            ReaderTheme theme
    ) {
        if (context == null ||
                container == null ||
                rootNode == null) {
            return;
        }

        ReaderTheme activeTheme = theme != null ? theme : ReaderTheme.LIGHT;

        container.removeAllViews();

        RendererState state = new RendererState(
                context,
                listener,
                anchorListener,
                activeTheme
        );

        container.setBackgroundColor(state.getBackgroundColor());

        collectAnchors(rootNode, state);

        renderNode(
                state,
                container,
                rootNode,
                0
        );
    }

    // ------------------------------------------------------------
    // Renderer state
    // ------------------------------------------------------------

    private static final class RendererState {

        final Context context;
        final LinkClickListener linkListener;
        final AnchorListener anchorListener;
        final ReaderTheme theme;
        final Map<String, View> anchors = new HashMap<>();
        final Map<org.jsoup.nodes.Element, EditText> inputFields = new HashMap<>();

        RendererState(
                Context context,
                LinkClickListener linkListener,
                AnchorListener anchorListener,
                ReaderTheme theme
        ) {
            this.context = context;
            this.linkListener = linkListener;
            this.anchorListener = anchorListener;
            this.theme = theme != null ? theme : ReaderTheme.LIGHT;
        }

        int getBackgroundColor() {
            return theme.backgroundColor;
        }

        int getTextColor() {
            return theme.textColor;
        }

        private int resolveAttr(String name, int defaultColor) {
            int id = context.getResources().getIdentifier(name, "attr", context.getPackageName());
            if (id == 0) {
                id = context.getResources().getIdentifier(name, "attr", "com.example.browser");
            }
            if (id == 0) {
                id = context.getResources().getIdentifier(name, "attr", "androidx.appcompat");
            }
            if (id == 0) {
                id = context.getResources().getIdentifier(name, "attr", "com.google.android.material");
            }
            if (id == 0) {
                id = context.getResources().getIdentifier(name, "attr", "android");
            }

            if (id != 0) {
                return resolveDynamicColor(context, id, defaultColor);
            }
            return defaultColor;
        }

        int getLinkColor() {
            return resolveAttr("colorPrimary", theme.linkColor);
        }

        int getCardBackgroundColor() {
            return resolveAttr("colorSurfaceContainer", theme.cardBackgroundColor);
        }

        int getBorderColor() {
            return resolveAttr("colorOutline", theme.borderColor);
        }

        int getCodeBackgroundColor() {
            return resolveAttr("colorSecondaryContainer", theme.codeBackgroundColor);
        }

        int getAccentBarColor() {
            return resolveAttr("colorPrimary", theme.accentBarColor);
        }

        int getButtonBackgroundColor() {
            return resolveAttr("colorPrimary", theme.buttonBackgroundColor);
        }

        int getButtonTextColor() {
            return resolveAttr("colorOnPrimary", theme.buttonTextColor);
        }
    }

    private static boolean isDarkColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance < 0.4;
    }

    public static int resolveDynamicColor(Context context, int attr, int defaultColor) {
        try {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
        } catch (Exception ignored) {}
        return defaultColor;
    }

    // ------------------------------------------------------------
    // DOM traversal
    // ------------------------------------------------------------

    private static void collectAnchors(
            LayoutNode node,
            RendererState state
    ) {
        if (node == null || node.element == null) {
            return;
        }

        String id = node.element.id();

        if (id != null && !id.trim().isEmpty()) {
            /*
             * View is registered later after creation.
             *
             * We use a temporary marker map through the node itself.
             */
        }

        for (LayoutNode child : node.children) {
            collectAnchors(child, state);
        }
    }

    private static View renderNode(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        if (node == null) {
            return null;
        }

        if (depth > MAX_DEPTH) {
            return null;
        }

        if (node.element == null) {
            return renderChildren(
                    state,
                    parent,
                    node,
                    depth
            );
        }

        Element element = node.element;

        String tag = element
                .tagName()
                .toLowerCase(Locale.ROOT);

        // --------------------------------------------------------
        // Hidden elements
        // --------------------------------------------------------

        if (isHidden(node)) {
            return null;
        }

        // --------------------------------------------------------
        // Explicit special elements
        // --------------------------------------------------------

        View v = null;
        switch (tag) {
            case "br":
                v = renderBreak(state, parent);
                break;
            case "hr":
                v = renderHr(state, parent, node);
                break;
            case "img":
            case "image":
                v = renderImage(state, parent, node);
                break;
            case "table":
                v = renderTable(state, parent, node, depth);
                break;
            case "ul":
                v = renderList(state, parent, node, false, depth);
                break;
            case "ol":
                v = renderList(state, parent, node, true, depth);
                break;
            case "blockquote":
                v = renderBlockquote(state, parent, node, depth);
                break;
            case "pre":
                v = renderPre(state, parent, node);
                break;
            case "button":
                v = renderButton(state, parent, node);
                break;
            case "input":
                v = renderInput(state, parent, node);
                break;
            case "textarea":
                v = renderTextarea(state, parent, node);
                break;
            case "select":
                v = renderSelect(state, parent, node);
                break;
            case "details":
                v = renderDetails(state, parent, node, depth);
                break;
            case "summary":
                v = renderSummary(state, parent, node);
                break;
            case "figure":
                v = renderFigure(state, parent, node, depth);
                break;
            case "header":
                v = renderHeader(state, parent, node, depth);
                break;
            case "nav":
                v = renderNav(state, parent, node, depth);
                break;
            case "footer":
                v = renderFooter(state, parent, node, depth);
                break;
            default:
                if (isTextLike(node)) {
                    v = renderTextLike(state, parent, node);
                } else {
                    v = renderContainer(state, parent, node, depth);
                }
                break;
        }

        if (v != null && node.element != null) {
            String id = node.element.id();
            if (id != null && !id.trim().isEmpty()) {
                v.setTag("anchor:" + id.trim());
            } else {
                String name = node.element.attr("name");
                if (name != null && !name.trim().isEmpty()) {
                    v.setTag("anchor:" + name.trim());
                }
            }
        }

        return v;
    }

    // ------------------------------------------------------------
    // Generic containers
    // ------------------------------------------------------------

    private static View renderContainer(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        Element element = node.element;

        String display = css(node, "display");

        if (display == null || display.isEmpty()) {
            display = inferDisplay(node);
        }

        if ("none".equalsIgnoreCase(display)) {
            return null;
        }

        boolean horizontal =
                isFlexRow(node, display);

        LinearLayout container =
                new LinearLayout(state.context);

        container.setOrientation(
                horizontal
                        ? LinearLayout.HORIZONTAL
                        : LinearLayout.VERTICAL
        );

        container.setGravity(
                resolveGravity(node)
        );

        applyContainerStyle(
                container,
                node,
                horizontal
        );

        ViewGroup.LayoutParams params =
                createContainerParams(
                        node,
                        horizontal
                );

        container.setLayoutParams(params);

        String href = element.attr("href");
        if ("a".equalsIgnoreCase(element.tagName()) || (href != null && !href.isEmpty())) {
            container.setClickable(true);
            container.setFocusable(true);
            container.setOnClickListener(v -> handleLink(state, href));
        }

        String id = element.id();

        if (id != null && !id.isEmpty()) {
            container.setTag(
                    "anchor:" + id
            );
        }

        parent.addView(container);

        // --------------------------------------------------------
        // Flex alignment
        // --------------------------------------------------------

        applyFlexProperties(
                container,
                node,
                horizontal
        );

        // --------------------------------------------------------
        // Children
        // --------------------------------------------------------

        for (LayoutNode child : node.children) {

            if (child == null) {
                continue;
            }

            renderNode(
                    state,
                    container,
                    child,
                    depth + 1
            );
        }

        return container;
    }

    private static View renderChildren(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        for (LayoutNode child : node.children) {
            renderNode(
                    state,
                    parent,
                    child,
                    depth + 1
            );
        }

        return parent;
    }

    // ------------------------------------------------------------
    // Text rendering
    // ------------------------------------------------------------

    private static View renderTextLike(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        Element element = node.element;

        String tag = element
                .tagName()
                .toLowerCase(Locale.ROOT);

        String text = extractReadableText(node);

        if (text.isEmpty()) {
            return null;
        }

        TextView tv = new TextView(
                state.context
        );

        tv.setIncludeFontPadding(true);
        tv.setTextIsSelectable(true);

        tv.setText(
                buildStyledText(
                        state,
                        node,
                        text
                )
        );

        applyTextStyle(
                state,
                tv,
                node,
                tag
        );

        // --------------------------------------------------------
        // Links
        // --------------------------------------------------------

        if ("a".equals(tag) ||
                element.hasAttr("href")) {

            String href =
                    element.attr("href");

            makeLink(
                    state,
                    tv,
                    href
            );
        }

        // --------------------------------------------------------
        // Semantic inline formatting
        // --------------------------------------------------------

        if ("strong".equals(tag) ||
                "b".equals(tag)) {

            tv.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        if ("em".equals(tag) ||
                "i".equals(tag)) {

            tv.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.ITALIC
            );
        }

        if ("u".equals(tag) ||
                "ins".equals(tag)) {

            tv.setPaintFlags(
                    tv.getPaintFlags() |
                            Paint.UNDERLINE_TEXT_FLAG
            );
        }

        if ("s".equals(tag) ||
                "del".equals(tag)) {

            tv.setPaintFlags(
                    tv.getPaintFlags() |
                            Paint.STRIKE_THRU_TEXT_FLAG
            );
        }

        parent.addView(tv);

        return tv;
    }

    private static SpannableStringBuilder buildStyledText(
            RendererState state,
            LayoutNode node,
            String text
    ) {
        SpannableStringBuilder builder =
                new SpannableStringBuilder(text);

        if (node.element == null) {
            return builder;
        }

        String tag =
                node.element
                        .tagName()
                        .toLowerCase(Locale.ROOT);

        if ("strong".equals(tag) ||
                "b".equals(tag)) {

            builder.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        if ("em".equals(tag) ||
                "i".equals(tag)) {

            builder.setSpan(
                    new StyleSpan(Typeface.ITALIC),
                    0,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        if ("u".equals(tag) ||
                "ins".equals(tag)) {

            builder.setSpan(
                    new UnderlineSpan(),
                    0,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        String color =
                css(node, "color");

        if (color != null) {

            Integer parsed =
                    parseColorSafe(color);

            if (parsed != null) {
                boolean isLink = false;
                org.jsoup.nodes.Element temp = node.element;
                while (temp != null) {
                    if ("a".equalsIgnoreCase(temp.tagName())) {
                        isLink = true;
                        break;
                    }
                    temp = temp.parent();
                }

                if (isLink) {
                    parsed = state.getLinkColor();
                } else {
                    boolean bgIsDark = isDarkColor(state.getBackgroundColor());
                    boolean colorIsDark = isDarkColor(parsed);
                    if ((bgIsDark && colorIsDark) || (!bgIsDark && !colorIsDark)) {
                        parsed = state.getTextColor();
                    }
                }
                builder.setSpan(
                        new ForegroundColorSpan(parsed),
                        0,
                        builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return builder;
    }

    private static void applyTextStyle(
            RendererState state,
            TextView tv,
            LayoutNode node,
            String tag
    ) {
        float size =
                getTextSize(node, tag);

        tv.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                size
        );

        Integer color =
                parseColorSafe(
                        css(node, "color")
                );

        boolean isLink = false;
        org.jsoup.nodes.Element temp = node.element;
        while (temp != null) {
            if ("a".equalsIgnoreCase(temp.tagName())) {
                isLink = true;
                break;
            }
            temp = temp.parent();
        }

        if (isLink) {
            color = state.getLinkColor();
        } else if (color != null) {
            boolean bgIsDark = isDarkColor(state.getBackgroundColor());
            boolean colorIsDark = isDarkColor(color);
            if ((bgIsDark && colorIsDark) || (!bgIsDark && !colorIsDark)) {
                color = state.getTextColor();
            }
        }

        tv.setTextColor(
                color != null
                        ? color
                        : (isLink ? state.getLinkColor() : state.getTextColor())
        );

        // --------------------------------------------------------
        // Alignment
        // --------------------------------------------------------

        String align =
                firstNonEmpty(
                        css(node, "text-align"),
                        node.element.attr("align")
                );

        if ("center".equalsIgnoreCase(align)) {
            tv.setGravity(Gravity.CENTER);
        } else if ("right".equalsIgnoreCase(align) ||
                "end".equalsIgnoreCase(align)) {
            tv.setGravity(Gravity.END);
        } else if ("left".equalsIgnoreCase(align) ||
                "start".equalsIgnoreCase(align)) {
            tv.setGravity(Gravity.START);
        }

        // --------------------------------------------------------
        // Weight
        // --------------------------------------------------------

        String weight =
                css(node, "font-weight");

        if ("bold".equalsIgnoreCase(weight) ||
                "700".equals(weight) ||
                "800".equals(weight) ||
                "900".equals(weight)) {

            tv.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        // --------------------------------------------------------
        // Line spacing
        // --------------------------------------------------------

        String lineHeight =
                css(node, "line-height");

        if (lineHeight != null) {

            float multiplier =
                    parseLineHeight(
                            lineHeight,
                            size
                    );

            tv.setLineSpacing(
                    0,
                    multiplier
            );
        }

        // --------------------------------------------------------
        // Padding
        // --------------------------------------------------------

        int left =
                dp(
                        node,
                        "padding-left",
                        0
                );

        int top =
                dp(
                        node,
                        "padding-top",
                        0
                );

        int right =
                dp(
                        node,
                        "padding-right",
                        0
                );

        int bottom =
                dp(
                        node,
                        "padding-bottom",
                        0
                );

        if (left == 0 &&
                top == 0 &&
                right == 0 &&
                bottom == 0) {

            int padding =
                    dp(
                            node,
                            "padding",
                            0
                    );

            left = top =
                    right = bottom =
                            padding;
        }

        tv.setPadding(
                left,
                top,
                right,
                bottom
        );

        // --------------------------------------------------------
        // Margins
        // --------------------------------------------------------

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        applyMargins(
                params,
                node
        );

        tv.setLayoutParams(params);
    }

    // ------------------------------------------------------------
    // Images
    // ------------------------------------------------------------

    private static View renderImage(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        Element element = node.element;

        String src = firstNonEmpty(
                element.absUrl("src"),
                element.attr("src"),
                element.absUrl("data-src"),
                element.attr("data-src"),
                element.absUrl("data-lazy-src"),
                element.attr("data-lazy-src"),
                element.absUrl("data-original"),
                element.attr("data-original")
        );

        if (src == null || src.trim().isEmpty()) {
            String srcset = firstNonEmpty(element.absUrl("srcset"), element.attr("srcset"));
            if (srcset != null && !srcset.trim().isEmpty()) {
                src = srcset.split(",")[0].trim().split(" ")[0];
            }
        }

        if (src == null || src.trim().isEmpty()) {
            return null;
        }

        // Clean fragment # identifiers (like #vw=100) from URL string unless it's a data: URI
        String cleanUrl = src.trim();
        if (!cleanUrl.startsWith("data:")) {
            int hashIdx = cleanUrl.indexOf('#');
            if (hashIdx != -1) {
                cleanUrl = cleanUrl.substring(0, hashIdx);
            }
        }

        // Protocol-relative URLs starting with //
        if (cleanUrl.startsWith("//")) {
            cleanUrl = "https:" + cleanUrl;
        }

        // Resolve relative URLs to absolute HTTP/HTTPS URLs
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://") && !cleanUrl.startsWith("data:")) {
            String baseUri = firstNonEmpty(element.baseUri(), element.ownerDocument() != null ? element.ownerDocument().location() : null);
            if (baseUri != null && !baseUri.isEmpty()) {
                try {
                    cleanUrl = new java.net.URL(new java.net.URL(baseUri), cleanUrl).toString();
                } catch (Exception ignored) {}
            }
        }

        boolean isInsideTable = false;
        String linkHref = null;
        org.jsoup.nodes.Element curr = node.element.parent();
        while (curr != null) {
            String cTag = curr.tagName().toLowerCase(Locale.ROOT);
            if ("table".equalsIgnoreCase(cTag)) {
                isInsideTable = true;
            }
            if ("a".equalsIgnoreCase(cTag)) {
                linkHref = curr.attr("href");
            }
            curr = curr.parent();
        }

        int screenWidth = state.context.getResources().getDisplayMetrics().widthPixels;
        int maxImgWidth = screenWidth - dpToPx(state.context, 32);

        FrameLayout container = new FrameLayout(state.context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        applyMargins(params, node);
        container.setLayoutParams(params);

        int maxTableImgWidth = dpToPx(state.context, 120);
        int maxTableImgHeight = dpToPx(state.context, 100);

        ImageView image = new ImageView(state.context);
        image.setAdjustViewBounds(true);
        image.setMinimumHeight(isInsideTable ? dpToPx(state.context, 30) : dpToPx(state.context, 100));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setMaxWidth(maxImgWidth);
        
        int layoutWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        int layoutHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
        
        if (node.width > 0) {
            int pxWidth = dpToPx(state.context, node.width);
            layoutWidth = pxWidth > maxImgWidth ? maxImgWidth : pxWidth;
        }
        
        if (node.height > 0) {
            layoutHeight = dpToPx(state.context, node.height);
        }

        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                isInsideTable ? maxTableImgWidth : layoutWidth,
                layoutHeight
        );
        image.setLayoutParams(imgParams);

        GradientDrawable background = createBackground(node, Color.TRANSPARENT);
        if (background != null) {
            image.setBackground(background);
            image.setClipToOutline(true);
        }

        container.addView(image);
        parent.addView(container);

        final String finalCleanUrl = cleanUrl;
        if (linkHref != null && !linkHref.trim().isEmpty()) {
            final String finalLink = linkHref.trim();
            
            // Frame container as an interactive outline button
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            border.setStroke(dpToPx(state.context, 2), state.getLinkColor());
            border.setCornerRadius(dpToPx(state.context, 8));
            container.setBackground(border);
            int padding = dpToPx(state.context, 4);
            container.setPadding(padding, padding, padding, padding);

            container.setOnClickListener(v -> handleLink(state, finalLink));
            container.setFocusable(true);
            container.setClickable(true);
            
            image.setOnClickListener(v -> handleLink(state, finalLink));
            image.setFocusable(true);
            image.setClickable(true);
        }

        int reqWidth = node.width > 0 ? dpToPx(state.context, node.width) : (isInsideTable ? maxTableImgWidth : maxImgWidth);
        int reqHeight = node.height > 0 ? dpToPx(state.context, node.height) : (isInsideTable ? maxTableImgHeight : dpToPx(state.context, 800));

        image.setOnLongClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(state.context, com.example.browser.ImageViewerActivity.class);
                intent.putExtra("image_url", finalCleanUrl);
                state.context.startActivity(intent);
            } catch (Exception e) {
                android.widget.Toast.makeText(state.context, "Failed to open image viewer: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        final String finalLinkHref = linkHref;
        ImageLoader.getInstance(state.context).load(
                cleanUrl,
                reqWidth,
                reqHeight,
                new ImageLoader.ImageLoadCallback() {

                    @Override
                    public void onImageLoaded(android.graphics.Bitmap bitmap) {
                        if (bitmap != null) {
                            bitmap.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
                        }
                        image.post(() -> {
                            image.setImageBitmap(bitmap);
                            image.requestLayout();

                            if (bitmap != null) {
                                long bytes = com.example.browser.ImageLoader.getInstance(state.context).getDiskCacheSize(finalCleanUrl);
                                String sizeStr;
                                if (bytes >= 1024 * 1024) {
                                    sizeStr = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
                                } else if (bytes >= 1024) {
                                    sizeStr = (bytes / 1024) + " KB";
                                } else if (bytes > 0) {
                                    sizeStr = bytes + " B";
                                } else {
                                    sizeStr = "";
                                }

                                String info = (finalLinkHref != null ? "🔗 " : "") + bitmap.getWidth() + "×" + bitmap.getHeight() + " px"
                                        + (sizeStr.isEmpty() ? "" : " • " + sizeStr);

                                TextView infoBadge = new TextView(state.context);
                                infoBadge.setText(info);
                                if (finalLinkHref != null) {
                                    infoBadge.setTextColor(state.getButtonTextColor());
                                } else {
                                    infoBadge.setTextColor(Color.WHITE);
                                }
                                infoBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
                                int pxPaddingH = dpToPx(state.context, 8);
                                int pxPaddingV = dpToPx(state.context, 4);
                                infoBadge.setPadding(pxPaddingH, pxPaddingV, pxPaddingH, pxPaddingV);

                                GradientDrawable badgeBg = new GradientDrawable();
                                badgeBg.setColor(finalLinkHref != null ? state.getLinkColor() : Color.argb(180, 20, 20, 20));
                                badgeBg.setCornerRadius(dpToPx(state.context, 10));
                                infoBadge.setBackground(badgeBg);

                                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                  );
                                badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
                                int margin = dpToPx(state.context, 8);
                                badgeParams.setMargins(margin, margin, margin, margin);
                                infoBadge.setLayoutParams(badgeParams);

                                container.addView(infoBadge);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        // Suppress errors silently
                    }
                }
        );

        return container;
    }

    private static void showDownloadDialog(Context context, String imageUrl) {
        new android.app.AlertDialog.Builder(context)
                .setTitle("Download Image")
                .setMessage("Do you want to download this image?")
                .setPositiveButton("Download", (dialog, which) -> {
                    try {
                        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(imageUrl));
                        request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
                        request.setTitle("Downloading Image");
                        request.setDescription("Velocity Browser Image Download");
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                        String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                        if (filename.contains("?")) {
                            filename = filename.substring(0, filename.indexOf("?"));
                        }
                        if (filename.isEmpty()) {
                            filename = "image_" + System.currentTimeMillis() + ".jpg";
                        }

                        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename);

                        android.app.DownloadManager manager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        if (manager != null) {
                            manager.enqueue(request);
                            android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        android.widget.Toast.makeText(context, "Failed to download image: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int resolveImageWidth(
            Context context,
            LayoutNode node
    ) {
        if (node.width > 0) {
            return dpToPx(
                    context,
                    node.width
            );
        }

        int cssWidth =
                dp(
                        node,
                        "width",
                        0
                );

        if (cssWidth > 0) {
            return cssWidth;
        }

        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private static int resolveImageHeight(
            Context context,
            LayoutNode node,
            int width
    ) {
        if (node.height > 0) {
            return dpToPx(
                    context,
                    node.height
            );
        }

        int cssHeight =
                dp(
                        node,
                        "height",
                        0
                );

        if (cssHeight > 0) {
            return cssHeight;
        }

        String ratio =
                firstNonEmpty(
                        css(node, "aspect-ratio"),
                        node.element.attr("width")
                                .isEmpty()
                                ? null
                                : null
                );

        if (ratio != null &&
                ratio.contains("/")) {

            try {
                String[] p =
                        ratio.split("/");

                float a =
                        Float.parseFloat(
                                p[0].trim()
                        );

                float b =
                        Float.parseFloat(
                                p[1].trim()
                        );

                if (a > 0 && b > 0) {
                    return Math.max(
                            1,
                            Math.round(
                                    width * b / a
                            )
                    );
                }

            } catch (Exception ignored) {
            }
        }

        // Better default for responsive images.
        return dpToPx(
                context,
                180
        );
    }

    // ------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------

    private static View renderTable(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        HorizontalScrollView scrollWrapper = new HorizontalScrollView(state.context);
        scrollWrapper.setHorizontalScrollBarEnabled(true);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, dpToPx(state.context, 10), 0, dpToPx(state.context, 10));
        scrollWrapper.setLayoutParams(wrapperParams);

        boolean bgIsDark = isDarkColor(state.getBackgroundColor());
        int tableBorderColor = bgIsDark ? Color.rgb(80, 80, 80) : Color.rgb(220, 220, 220);
        int tableHeaderBg = bgIsDark ? Color.rgb(35, 35, 35) : Color.rgb(240, 240, 240);
        int tableAltBg = bgIsDark ? Color.rgb(22, 22, 22) : Color.rgb(248, 248, 248);

        GradientDrawable wrapperBg = new GradientDrawable();
        wrapperBg.setColor(state.getBackgroundColor());
        wrapperBg.setStroke(dpToPx(state.context, 1), tableBorderColor);
        wrapperBg.setCornerRadius(dpToPx(state.context, 8));
        scrollWrapper.setBackground(wrapperBg);

        android.widget.TableLayout table = new android.widget.TableLayout(state.context);
        table.setShrinkAllColumns(false);

        List<LayoutNode> rows = getTableRows(node);
        int rowIndex = 0;

        for (LayoutNode rowNode : rows) {
            android.widget.TableRow rowView = new android.widget.TableRow(state.context);
            rowView.setLayoutParams(new android.widget.TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            List<LayoutNode> cells = getRowCells(rowNode);
            boolean isRowHeader = false;

            for (LayoutNode cellNode : cells) {
                if ("th".equalsIgnoreCase(cellNode.element.tagName())) {
                    isRowHeader = true;
                    break;
                }
            }

            int cellBgColor;
            if (isRowHeader) {
                cellBgColor = tableHeaderBg;
            } else if (rowIndex % 2 == 1) {
                cellBgColor = tableAltBg;
            } else {
                cellBgColor = state.getBackgroundColor();
            }

            for (LayoutNode cellNode : cells) {
                boolean isCellHeader = "th".equalsIgnoreCase(cellNode.element.tagName()) || isRowHeader;

                LinearLayout cellView = new LinearLayout(state.context);
                cellView.setOrientation(LinearLayout.VERTICAL);
                cellView.setPadding(dpToPx(state.context, 12), dpToPx(state.context, 10), dpToPx(state.context, 12), dpToPx(state.context, 10));

                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setColor(isCellHeader ? tableHeaderBg : cellBgColor);
                cellBg.setStroke(dpToPx(state.context, 1), tableBorderColor);
                cellView.setBackground(cellBg);

                android.widget.TableRow.LayoutParams cellParams = new android.widget.TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                int colSpan = getSpan(cellNode, "colspan", 1);
                if (colSpan > 1) {
                    cellParams.span = colSpan;
                }
                cellView.setLayoutParams(cellParams);

                renderContainerChildren(state, cellView, cellNode, depth + 1);
                applyTableStylesToChildren(state, cellView, isCellHeader);

                rowView.addView(cellView);
            }

            table.addView(rowView);
            rowIndex++;
        }

        scrollWrapper.addView(table);
        parent.addView(scrollWrapper);

        return scrollWrapper;
    }

    private static int calculateColumnCount(
            List<LayoutNode> rows
    ) {
        int max = 0;

        for (LayoutNode row : rows) {

            int count = 0;

            for (LayoutNode cell :
                    getRowCells(row)) {

                count += getSpan(
                        cell,
                        "colspan",
                        1
                );
            }

            max =
                    Math.max(
                            max,
                            count
                    );
        }

        return Math.max(
                1,
                max
        );
    }

    private static int estimateCellWidth(
            Context context,
            LayoutNode cell
    ) {
        String text =
                extractReadableText(cell);

        int length =
                Math.max(
                        4,
                        text.length()
                );

        return Math.min(
                dpToPx(
                        context,
                        320
                ),
                Math.max(
                        dpToPx(
                                context,
                                90
                        ),
                        length *
                                dpToPx(
                                        context,
                                        7
                                )
                )
        );
    }

    private static int getSpan(
            LayoutNode node,
            String attribute,
            int fallback
    ) {
        try {

            String value =
                    node.element.attr(
                            attribute
                    );

            if (value == null ||
                    value.isEmpty()) {
                return fallback;
            }

            return Math.max(
                    1,
                    Integer.parseInt(value)
            );

        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<LayoutNode> getTableRows(
            LayoutNode table
    ) {
        List<LayoutNode> rows =
                new ArrayList<>();

        collectRows(
                table,
                rows
        );

        return rows;
    }

    private static void collectRows(
            LayoutNode node,
            List<LayoutNode> result
    ) {
        if (node == null ||
                node.element == null) {
            return;
        }

        String tag =
                node.element
                        .tagName()
                        .toLowerCase(Locale.ROOT);

        if ("tr".equals(tag)) {
            result.add(node);
            return;
        }

        for (LayoutNode child :
                node.children) {

            collectRows(
                    child,
                    result
            );
        }
    }

    private static List<LayoutNode> getRowCells(
            LayoutNode row
    ) {
        List<LayoutNode> result =
                new ArrayList<>();

        for (LayoutNode child :
                row.children) {

            if (child == null ||
                    child.element == null) {
                continue;
            }

            String tag =
                    child.element
                            .tagName()
                            .toLowerCase(Locale.ROOT);

            if ("td".equals(tag) ||
                    "th".equals(tag)) {

                result.add(child);
            }
        }

        return result;
    }

    // ------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------

    private static View renderList(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            boolean ordered,
            int depth
    ) {
        LinearLayout list =
                new LinearLayout(
                        state.context
                );

        list.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams listParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        applyMargins(
                listParams,
                node
        );

        list.setLayoutParams(
                listParams
        );

        parent.addView(list);

        int index = 1;

        for (LayoutNode child :
                node.children) {

            if (child == null ||
                    child.element == null) {
                continue;
            }

            if (!"li".equalsIgnoreCase(
                    child.element.tagName()
            )) {
                continue;
            }

            LinearLayout item =
                    new LinearLayout(
                            state.context
                    );

            item.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            item.setGravity(
                    Gravity.TOP
            );

            TextView marker =
                    new TextView(
                            state.context
                    );

            marker.setTextColor(state.getTextColor());

            marker.setText(
                    ordered
                            ? index + "."
                            : "•"
            );

            marker.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    15
            );

            marker.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );

            marker.setPadding(
                    0,
                    0,
                    dpToPx(
                            state.context,
                            8
                    ),
                    0
            );

            item.addView(
                    marker,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            LinearLayout content =
                    new LinearLayout(
                            state.context
                    );

            content.setOrientation(
                    LinearLayout.VERTICAL
            );

            item.addView(
                    content,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    )
            );

            if (child.children.isEmpty()) {
                TextView tv = new TextView(state.context);
                tv.setText(
                        buildStyledText(
                                state,
                                child,
                                extractReadableText(child)
                        )
                );
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setTextColor(state.getTextColor());
                content.addView(tv);
            } else {
                renderContainerChildren(
                        state,
                        content,
                        child,
                        depth + 1
                );
            }

            list.addView(item);

            index++;
        }

        return list;
    }

    // ------------------------------------------------------------
    // Blockquote
    // ------------------------------------------------------------

    private static View renderBlockquote(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        LinearLayout box =
                new LinearLayout(
                        state.context
                );

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                dpToPx(
                        state.context,
                        16
                ),
                dpToPx(
                        state.context,
                        10
                ),
                dpToPx(
                        state.context,
                        12
                ),
                dpToPx(
                        state.context,
                        10
                )
        );

        boolean bgIsDark = isDarkColor(state.getBackgroundColor());
        int defaultQuoteBg = bgIsDark ? Color.rgb(20, 20, 20) : Color.rgb(245, 245, 245);
        int defaultQuoteBorder = bgIsDark ? Color.rgb(44, 44, 44) : Color.rgb(224, 224, 224);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                parseColorOrDefault(
                        css(
                                node,
                                "background-color"
                        ),
                        defaultQuoteBg
                )
        );

        background.setCornerRadius(
                dpToPx(
                        state.context,
                        5
                )
        );

        background.setStroke(
                dpToPx(
                        state.context,
                        1
                ),
                parseColorOrDefault(
                        css(
                                node,
                                "border-color"
                        ),
                        defaultQuoteBorder
                )
        );

        box.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                dpToPx(
                        state.context,
                        12
                ),
                dpToPx(
                        state.context,
                        8
                ),
                dpToPx(
                        state.context,
                        12
                ),
                dpToPx(
                        state.context,
                        8
                )
        );

        box.setLayoutParams(params);

        renderContainerChildren(
                state,
                box,
                node,
                depth + 1
        );

        parent.addView(box);

        return box;
    }

    // ------------------------------------------------------------
    // Pre / code
    // ------------------------------------------------------------

    private static View renderPre(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        String codeText = extractRawText(node);

        LinearLayout cardContainer = new LinearLayout(state.context);
        cardContainer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(30, 30, 30));
        background.setCornerRadius(dpToPx(state.context, 8));
        cardContainer.setBackground(background);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dpToPx(state.context, 8), 0, dpToPx(state.context, 8));
        cardContainer.setLayoutParams(cardParams);

        // Header bar with Copy button
        LinearLayout header = new LinearLayout(state.context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(state.context, 12), dpToPx(state.context, 6), dpToPx(state.context, 12), dpToPx(state.context, 6));

        TextView label = new TextView(state.context);
        label.setText("CODE");
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setTextColor(Color.rgb(180, 180, 180));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(label, labelParams);

        TextView copyBtn = new TextView(state.context);
        copyBtn.setText("COPY");
        copyBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        copyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        copyBtn.setTextColor(Color.rgb(144, 202, 249));
        copyBtn.setPadding(dpToPx(state.context, 8), dpToPx(state.context, 4), dpToPx(state.context, 8), dpToPx(state.context, 4));

        copyBtn.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) state.context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Code Snippet", codeText);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(state.context, "Code copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        });

        header.addView(copyBtn);
        cardContainer.addView(header);

        // Horizontal scroll view for code text
        HorizontalScrollView scroll = new HorizontalScrollView(state.context);
        TextView code = new TextView(state.context);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        code.setText(codeText);
        code.setTextColor(Color.rgb(230, 225, 229));
        code.setPadding(dpToPx(state.context, 12), dpToPx(state.context, 4), dpToPx(state.context, 12), dpToPx(state.context, 12));

        scroll.addView(code);
        cardContainer.addView(scroll);

        parent.addView(cardContainer);
        return cardContainer;
    }

    // ------------------------------------------------------------
    // Horizontal rule
    // ------------------------------------------------------------

    private static View renderHr(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        View hr =
                new View(
                        state.context
                );

        int thickness =
                Math.max(
                        1,
                        dp(
                                node,
                                "height",
                                1
                        )
                );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        thickness
                );

        applyMargins(
                params,
                node
        );

        hr.setLayoutParams(params);

        hr.setBackgroundColor(
                parseColorOrDefault(
                        css(
                                node,
                                "border-color"
                        ),
                        DEFAULT_BORDER_COLOR
                )
        );

        parent.addView(hr);

        return hr;
    }

    // ------------------------------------------------------------
    // Button
    // ------------------------------------------------------------

    private static View renderButton(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        Button button =
                new Button(
                        state.context
                );

        button.setText(
                extractReadableText(node)
        );

        button.setAllCaps(false);

        applyButtonStyle(
                state,
                button,
                node
        );

        String href =
                node.element.attr("href");

        if (href != null &&
                !href.isEmpty()) {

            button.setOnClickListener(
                    v -> handleLink(
                            state,
                            href
                    )
            );
        } else {
            button.setOnClickListener(v -> {
                org.jsoup.nodes.Element form = findParentForm(node.element);
                if (form != null) {
                    submitForm(state, form);
                }
            });
        }

        parent.addView(button);

        return button;
    }

    // ------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------

    private static View renderInput(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        String type =
                firstNonEmpty(
                        node.element.attr("type"),
                        "text"
                ).toLowerCase(Locale.ROOT);

        switch (type) {

            case "checkbox": {

                CheckBox box =
                        new CheckBox(
                                state.context
                        );

                box.setText(
                        node.element.attr("aria-label")
                );
                box.setTextColor(state.getTextColor());

                box.setChecked(
                        node.element.hasAttr(
                                "checked"
                        )
                );

                parent.addView(box);

                return box;
            }

            case "radio": {

                RadioButton radio =
                        new RadioButton(
                                state.context
                        );

                radio.setText(
                        node.element.attr(
                                "aria-label"
                        )
                );
                radio.setTextColor(state.getTextColor());

                radio.setChecked(
                        node.element.hasAttr(
                                "checked"
                        )
                );

                parent.addView(radio);

                return radio;
            }

            case "button":
            case "submit":
            case "reset":
                return renderButton(
                        state,
                        parent,
                        node
                );

            case "range": {

                android.widget.SeekBar seek =
                        new android.widget.SeekBar(
                                state.context
                        );

                try {
                    int min =
                            Integer.parseInt(
                                    node.element.attr(
                                            "min"
                                    )
                            );

                    int max =
                            Integer.parseInt(
                                    node.element.attr(
                                            "max"
                                    )
                            );

                    int value =
                            Integer.parseInt(
                                    node.element.attr(
                                            "value"
                                    )
                            );

                    seek.setMax(
                            Math.max(
                                    1,
                                    max - min
                            )
                    );

                    seek.setProgress(
                            Math.max(
                                    0,
                                    value - min
                            )
                    );

                } catch (Exception ignored) {
                }

                parent.addView(seek);

                return seek;
            }
        }

        EditText edit =
                new EditText(
                        state.context
                );

        edit.setText(
                node.element.attr(
                        "value"
                )
        );

        edit.setHint(
                node.element.attr(
                        "placeholder"
                )
        );

        edit.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                15
        );

        if ("password".equals(type)) {

            edit.setInputType(
                    android.text.InputType
                            .TYPE_CLASS_TEXT |
                            android.text.InputType
                            .TYPE_TEXT_VARIATION_PASSWORD
            );

        } else if ("email".equals(type)) {

            edit.setInputType(
                    android.text.InputType
                            .TYPE_CLASS_TEXT |
                            android.text.InputType
                            .TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            );
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        applyMargins(
                params,
                node
        );

        edit.setLayoutParams(params);

        state.inputFields.put(node.element, edit);
        edit.setSingleLine(true);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                org.jsoup.nodes.Element form = findParentForm(node.element);
                if (form != null) {
                    submitForm(state, form);
                    return true;
                }
            }
            return false;
        });

        parent.addView(edit);

        return edit;
    }

    private static View renderTextarea(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        EditText edit =
                new EditText(
                        state.context
                );

        edit.setGravity(
                Gravity.TOP | Gravity.START
        );

        edit.setText(
                extractRawText(node)
        );

        edit.setHint(
                node.element.attr(
                        "placeholder"
                )
        );

        edit.setMinLines(4);

        edit.setInputType(
                android.text.InputType
                        .TYPE_CLASS_TEXT |
                        android.text.InputType
                        .TYPE_TEXT_FLAG_MULTI_LINE
        );

        parent.addView(edit);

        return edit;
    }

    // ------------------------------------------------------------
    // Select
    // ------------------------------------------------------------

    private static View renderSelect(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        Spinner spinner =
                new Spinner(
                        state.context
                );

        List<String> values =
                new ArrayList<>();

        collectOptions(
                node,
                values
        );

        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(
                        state.context,
                        android.R.layout.simple_spinner_item,
                        values
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinner.setAdapter(adapter);

        parent.addView(spinner);

        return spinner;
    }

    private static void collectOptions(
            LayoutNode node,
            List<String> result
    ) {
        if (node == null) {
            return;
        }

        if (node.element != null &&
                "option".equalsIgnoreCase(
                        node.element.tagName()
                )) {

            result.add(
                    extractReadableText(node)
            );

            return;
        }

        for (LayoutNode child :
                node.children) {

            collectOptions(
                    child,
                    result
            );
        }
    }

    // ------------------------------------------------------------
    // Details / summary
    // ------------------------------------------------------------

    private static View renderDetails(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        LinearLayout details =
                new LinearLayout(
                        state.context
                );

        details.setOrientation(
                LinearLayout.VERTICAL
        );

        boolean opened =
                node.element.hasAttr(
                        "open"
                );

        for (LayoutNode child :
                node.children) {

            if (child.element != null &&
                    "summary".equalsIgnoreCase(
                            child.element.tagName()
                    )) {

                View summary =
                        renderSummary(
                                state,
                                details,
                                child
                        );

                if (summary != null) {

                    summary.setOnClickListener(
                            v -> {
                                for (int i = 1;
                                     i < details.getChildCount();
                                     i++) {

                                    View content =
                                            details.getChildAt(i);

                                    content.setVisibility(
                                            content.getVisibility()
                                                    == View.VISIBLE
                                                    ? View.GONE
                                                    : View.VISIBLE
                                    );
                                }
                            }
                    );
                }

                continue;
            }

            View childView =
                    renderNode(
                            state,
                            details,
                            child,
                            depth + 1
                    );

            if (childView != null) {

                childView.setVisibility(
                        opened
                                ? View.VISIBLE
                                : View.GONE
                );
            }
        }

        parent.addView(details);

        return details;
    }

    private static View renderSummary(
            RendererState state,
            ViewGroup parent,
            LayoutNode node
    ) {
        TextView summary =
                new TextView(
                        state.context
                );

        summary.setText(
                extractReadableText(node)
        );

        summary.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        summary.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                15
        );

        summary.setPadding(
                dpToPx(
                        state.context,
                        12
                ),
                dpToPx(
                        state.context,
                        10
                ),
                dpToPx(
                        state.context,
                        12
                ),
                dpToPx(
                        state.context,
                        10
                )
        );

        parent.addView(summary);

        return summary;
    }

    // ------------------------------------------------------------
    // Figure
    // ------------------------------------------------------------

    private static View renderFigure(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        LinearLayout figure =
                new LinearLayout(
                        state.context
                );

        figure.setOrientation(
                LinearLayout.VERTICAL
        );

        applyContainerStyle(
                figure,
                node,
                false
        );

        parent.addView(figure);

        renderContainerChildren(
                state,
                figure,
                node,
                depth + 1
        );

        return figure;
    }

    // ------------------------------------------------------------
    // Reconstructed Header, Nav, Footer Component Renderers
    // ------------------------------------------------------------

    private static View renderHeader(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        LinearLayout box = new LinearLayout(state.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(state.context, 16), dpToPx(state.context, 16), dpToPx(state.context, 16), dpToPx(state.context, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(state.getCardBackgroundColor());
        bg.setCornerRadius(dpToPx(state.context, 12));
        bg.setStroke(dpToPx(state.context, 1), state.getBorderColor());
        box.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(state.context, 16));
        box.setLayoutParams(params);

        renderContainerChildren(state, box, node, depth + 1);
        parent.addView(box);
        return box;
    }

    private static View renderNav(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        HorizontalScrollView scroll = new HorizontalScrollView(state.context);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout container = new LinearLayout(state.context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(dpToPx(state.context, 8), dpToPx(state.context, 8), dpToPx(state.context, 8), dpToPx(state.context, 8));

        List<LayoutNode> flatLinks = new ArrayList<>();
        findNavLinks(node, flatLinks);

        for (LayoutNode linkNode : flatLinks) {
            String linkText = extractReadableText(linkNode);
            if (linkText.isEmpty()) continue;

            TextView chip = new TextView(state.context);
            chip.setText(linkText);
            chip.setTextSize(13);
            chip.setTextColor(state.getLinkColor());
            chip.setPadding(dpToPx(state.context, 14), dpToPx(state.context, 8), dpToPx(state.context, 14), dpToPx(state.context, 8));

            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(state.getCardBackgroundColor());
            chipBg.setCornerRadius(dpToPx(state.context, 16));
            chipBg.setStroke(dpToPx(state.context, 1), state.getBorderColor());
            chip.setBackground(chipBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(dpToPx(state.context, 4), 0, dpToPx(state.context, 4), 0);
            chip.setLayoutParams(lp);

            String href = linkNode.element.attr("href");
            if (href != null && !href.isEmpty()) {
                chip.setOnClickListener(v -> handleLink(state, href));
            }

            container.addView(chip);
        }

        if (container.getChildCount() == 0) {
            return renderContainer(state, parent, node, depth);
        }

        scroll.addView(container);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollParams.setMargins(0, 0, 0, dpToPx(state.context, 16));
        scroll.setLayoutParams(scrollParams);

        parent.addView(scroll);
        return scroll;
    }

    private static void findNavLinks(LayoutNode node, List<LayoutNode> flatLinks) {
        if (node == null) return;
        if ("a".equalsIgnoreCase(node.element.tagName())) {
            flatLinks.add(node);
            return;
        }
        for (LayoutNode child : node.children) {
            findNavLinks(child, flatLinks);
        }
    }

    private static View renderFooter(
            RendererState state,
            ViewGroup parent,
            LayoutNode node,
            int depth
    ) {
        LinearLayout box = new LinearLayout(state.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpToPx(state.context, 16), dpToPx(state.context, 20), dpToPx(state.context, 16), dpToPx(state.context, 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(state.getCardBackgroundColor());
        bg.setCornerRadius(dpToPx(state.context, 12));
        box.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(state.context, 24), 0, 0);
        box.setLayoutParams(params);

        renderContainerChildren(state, box, node, depth + 1);
        parent.addView(box);
        return box;
    }

    // ------------------------------------------------------------
    // Container child rendering
    // ------------------------------------------------------------

    private static void renderContainerChildren(
            RendererState state,
            ViewGroup container,
            LayoutNode node,
            int depth
    ) {
        if (node == null) {
            return;
        }

        // --------------------------------------------------------
        // Direct text
        // --------------------------------------------------------

        String directText =
                extractDirectText(node);

        if (!directText.isEmpty()) {

            TextView tv =
                    new TextView(
                            state.context
                    );

            tv.setText(
                    normalizeWhitespace(
                            directText
                    )
            );

            tv.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    getTextSize(
                            node,
                            "span"
                    )
            );

            tv.setTextColor(
                    parseColorOrDefault(
                            css(node, "color"),
                            DEFAULT_TEXT_COLOR
                    )
            );

            container.addView(tv);
        }

        // --------------------------------------------------------
        // Children
        // --------------------------------------------------------

        for (LayoutNode child :
                node.children) {

            renderNode(
                    state,
                    container,
                    child,
                    depth
            );
        }
    }

    // ------------------------------------------------------------
    // Link handling
    // ------------------------------------------------------------

    private static void makeLink(
            RendererState state,
            TextView tv,
            String href
    ) {
        if (href == null ||
                href.trim().isEmpty()) {
            return;
        }

        tv.setTextColor(
                DEFAULT_LINK_COLOR
        );

        tv.setPaintFlags(
                tv.getPaintFlags() |
                        Paint.UNDERLINE_TEXT_FLAG
        );

        tv.setClickable(true);

        tv.setOnClickListener(
                v -> handleLink(
                        state,
                        href
                )
        );
    }

    private static void handleLink(
            RendererState state,
            String href
    ) {
        if (href == null) {
            return;
        }

        href = href.trim();

        if (href.isEmpty()) {
            return;
        }

        // --------------------------------------------------------
        // Internal anchor
        // --------------------------------------------------------

        if (href.startsWith("#")) {

            String id =
                    href.substring(1);

            if (state.anchorListener != null) {
                state.anchorListener.onAnchor(id);
            }

            return;
        }

        if (state.linkListener != null) {
            state.linkListener.onLinkClick(
                    href
            );
        }
    }

    // ------------------------------------------------------------
    // CSS / layout
    // ------------------------------------------------------------

    private static void applyContainerStyle(
            LinearLayout container,
            LayoutNode node,
            boolean horizontal
    ) {
        Element e = node.element;

        // --------------------------------------------------------
        // Background
        // --------------------------------------------------------

        int backgroundColor =
                parseColorOrDefault(
                        css(
                                node,
                                "background-color"
                        ),
                        Color.TRANSPARENT
                );

        GradientDrawable background =
                createBackground(
                        node,
                        backgroundColor
                );

        if (background != null) {
            container.setBackground(
                    background
            );
        }

        // --------------------------------------------------------
        // Padding
        // --------------------------------------------------------

        int padding =
                dp(
                        node,
                        "padding",
                        -1
                );

        int left =
                dp(
                        node,
                        "padding-left",
                        padding >= 0 ? padding : 0
                );

        int top =
                dp(
                        node,
                        "padding-top",
                        padding >= 0 ? padding : 0
                );

        int right =
                dp(
                        node,
                        "padding-right",
                        padding >= 0 ? padding : 0
                );

        int bottom =
                dp(
                        node,
                        "padding-bottom",
                        padding >= 0 ? padding : 0
                );

        container.setPadding(
                left,
                top,
                right,
                bottom
        );

        // --------------------------------------------------------
        // Minimum dimensions
        // --------------------------------------------------------

        int minHeight =
                dp(
                        node,
                        "min-height",
                        0
                );

        if (minHeight > 0) {
            container.setMinimumHeight(
                    minHeight
            );
        }

        // --------------------------------------------------------
        // Elevation
        // --------------------------------------------------------

        float elevation =
                parseFloat(
                        css(
                                node,
                                "elevation"
                        ),
                        0
                );

        if (elevation > 0) {
            container.setElevation(
                    dpFloat(
                            node,
                            elevation
                    )
            );
        }
    }

    private static void applyFlexProperties(
            LinearLayout container,
            LayoutNode node,
            boolean horizontal
    ) {
        String justify =
                css(
                        node,
                        "justify-content"
                );

        if ("center".equalsIgnoreCase(
                justify
        )) {

            container.setGravity(
                    horizontal
                            ? Gravity.CENTER_HORIZONTAL
                            : Gravity.CENTER_VERTICAL
            );

        } else if ("flex-end".equalsIgnoreCase(
                justify
        )) {

            container.setGravity(
                    horizontal
                            ? Gravity.END
                            : Gravity.BOTTOM
            );

        } else if ("space-between".equalsIgnoreCase(
                justify
        )) {

            /*
             * Android LinearLayout does not have native
             * CSS space-between.
             *
             * We emulate it by using weights where possible.
             */
        }

        String align =
                css(
                        node,
                        "align-items"
                );

        if ("center".equalsIgnoreCase(
                align
        )) {

            container.setGravity(
                    horizontal
                            ? Gravity.CENTER_VERTICAL
                            : Gravity.CENTER_HORIZONTAL
            );

        } else if ("flex-end".equalsIgnoreCase(
                align
        )) {

            container.setGravity(
                    horizontal
                            ? Gravity.BOTTOM
                            : Gravity.END
            );
        }
    }

    private static ViewGroup.LayoutParams
    createContainerParams(
            LayoutNode node,
            boolean horizontal
    ) {
        int width =
                resolveWidth(node);

        int height =
                resolveHeight(node);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        width,
                        height
                );

        applyMargins(
                params,
                node
        );

        return params;
    }

    private static int resolveWidth(
            LayoutNode node
    ) {
        String width =
                css(node, "width");

        if (width == null ||
                width.isEmpty() ||
                "auto".equalsIgnoreCase(width)) {

            return ViewGroup.LayoutParams.MATCH_PARENT;
        }

        if ("fit-content".equalsIgnoreCase(width) ||
                "max-content".equalsIgnoreCase(width)) {

            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        int parsed =
                parseCssDimension(
                        node,
                        width,
                        true
                );

        return parsed > 0
                ? parsed
                : ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private static int resolveHeight(
            LayoutNode node
    ) {
        String height =
                css(node, "height");

        if (height == null ||
                height.isEmpty() ||
                "auto".equalsIgnoreCase(height)) {

            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        if ("fit-content".equalsIgnoreCase(height)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        int parsed =
                parseCssDimension(
                        node,
                        height,
                        false
                );

        return parsed > 0
                ? parsed
                : ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private static void applyMargins(
            ViewGroup.MarginLayoutParams params,
            LayoutNode node
    ) {
        int margin =
                dp(
                        node,
                        "margin",
                        -1
                );

        int left =
                dp(
                        node,
                        "margin-left",
                        margin >= 0 ? margin : 0
                );

        int top =
                dp(
                        node,
                        "margin-top",
                        margin >= 0 ? margin : 0
                );

        int right =
                dp(
                        node,
                        "margin-right",
                        margin >= 0 ? margin : 0
                );

        int bottom =
                dp(
                        node,
                        "margin-bottom",
                        margin >= 0 ? margin : 0
                );

        params.setMargins(
                left,
                top,
                right,
                bottom
        );
    }

    private static boolean isFlexRow(
            LayoutNode node,
            String display
    ) {
        if ("flex".equalsIgnoreCase(display) ||
                "inline-flex".equalsIgnoreCase(display)) {

            String direction =
                    css(
                            node,
                            "flex-direction"
                    );

            return "row".equalsIgnoreCase(
                    direction
            ) ||
                    "row-reverse".equalsIgnoreCase(
                            direction
                    ) ||
                    direction == null ||
                    direction.isEmpty();
        }

        return "row".equalsIgnoreCase(
                css(
                        node,
                        "flex-direction"
                )
        );
    }

    private static String inferDisplay(
            LayoutNode node
    ) {
        if (node.element == null) {
            return "block";
        }

        String tag =
                node.element
                        .tagName()
                        .toLowerCase(Locale.ROOT);

        if (INLINE_TAGS.contains(tag)) {
            return "inline";
        }

        return "block";
    }

    private static int resolveGravity(
            LayoutNode node
    ) {
        String textAlign =
                css(
                        node,
                        "text-align"
                );

        if ("center".equalsIgnoreCase(
                textAlign
        )) {
            return Gravity.CENTER_HORIZONTAL;
        }

        if ("right".equalsIgnoreCase(
                textAlign
        ) ||
                "end".equalsIgnoreCase(
                        textAlign
                )) {
            return Gravity.END;
        }

        return Gravity.START;
    }

    // ------------------------------------------------------------
    // Background / border
    // ------------------------------------------------------------

    private static GradientDrawable createBackground(
            LayoutNode node,
            int backgroundColor
    ) {
        String borderWidth =
                css(
                        node,
                        "border-width"
                );

        String borderColor =
                css(
                        node,
                        "border-color"
                );

        String radius =
                firstNonEmpty(
                        css(
                                node,
                                "border-radius"
                        ),
                        css(
                                node,
                                "border-top-left-radius"
                        )
                );

        boolean hasBackground =
                backgroundColor != Color.TRANSPARENT;

        boolean hasBorder =
                borderWidth != null ||
                        borderColor != null;

        boolean hasRadius =
                radius != null;

        if (!hasBackground &&
                !hasBorder &&
                !hasRadius) {
            return null;
        }

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        if (hasBorder) {

            int width =
                    parseCssDimension(
                            node,
                            borderWidth,
                            false
                    );

            if (width <= 0) {
                width = 1;
            }

            drawable.setStroke(
                    width,
                    parseColorOrDefault(
                            borderColor,
                            DEFAULT_BORDER_COLOR
                    )
            );
        }

        if (hasRadius) {

            float r =
                    dpFloat(
                            node,
                            parseFloat(
                                    radius,
                                    0
                            )
                    );

            drawable.setCornerRadius(r);
        }

        return drawable;
    }

    // ------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------

    private static void applyButtonStyle(
            RendererState state,
            Button button,
            LayoutNode node
    ) {
        button.setAllCaps(false);

        button.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                getTextSize(
                        node,
                        "button"
                )
        );

        int textColor = state.getButtonTextColor();
        int backgroundColor = parseColorOrDefault(
                css(
                        node,
                        "background-color"
                ),
                state.getButtonBackgroundColor()
        );

        button.setTextColor(textColor);

        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dpToPx(state.context, 20));

        button.setBackground(background);
    }

    // ------------------------------------------------------------
    // Text sizes
    // ------------------------------------------------------------

    private static float getTextSize(
            LayoutNode node,
            String tag
    ) {
        String cssSize =
                css(
                        node,
                        "font-size"
                );

        if (cssSize != null &&
                !cssSize.isEmpty()) {

            float parsed =
                    parseFontSize(
                            cssSize
                    );

            if (parsed > 0) {
                return parsed;
            }
        }

        switch (tag) {

            case "h1":
                return 30;

            case "h2":
                return 25;

            case "h3":
                return 21;

            case "h4":
                return 18;

            case "h5":
                return 16;

            case "h6":
                return 15;

            case "small":
                return 12;

            case "big":
                return 18;

            case "button":
                return 14;

            default:
                return DEFAULT_TEXT_SIZE;
        }
    }

    private static float parseFontSize(
            String value
    ) {
        if (value == null) {
            return DEFAULT_TEXT_SIZE;
        }

        value =
                value.trim()
                        .toLowerCase(Locale.ROOT);

        try {

            if (value.endsWith("px")) {
                return Float.parseFloat(
                        value.substring(
                                0,
                                value.length() - 2
                        )
                );
            }

            if (value.endsWith("sp")) {
                return Float.parseFloat(
                        value.substring(
                                0,
                                value.length() - 2
                        )
                );
            }

            if (value.endsWith("rem")) {
                return Float.parseFloat(
                        value.substring(
                                0,
                                value.length() - 3
                        )
                ) * 16f;
            }

            if (value.endsWith("em")) {
                return Float.parseFloat(
                        value.substring(
                                0,
                                value.length() - 2
                        )
                ) * 16f;
            }

            if (value.endsWith("%")) {
                return 16f *
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 1
                                )
                        ) /
                        100f;
            }

            return Float.parseFloat(value);

        } catch (Exception ignored) {
            return DEFAULT_TEXT_SIZE;
        }
    }

    // ------------------------------------------------------------
    // Text extraction
    // ------------------------------------------------------------

    private static String extractReadableText(
            LayoutNode node
    ) {
        if (node == null ||
                node.element == null) {
            return "";
        }

        return normalizeWhitespace(
                node.element.text()
        );
    }

    private static String extractRawText(
            LayoutNode node
    ) {
        if (node == null ||
                node.element == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        appendRawText(
                node.element,
                result
        );

        return result.toString();
    }

    private static void appendRawText(
            Node node,
            StringBuilder result
    ) {
        if (node instanceof TextNode) {

            result.append(
                    ((TextNode) node).getWholeText()
            );

            return;
        }

        for (Node child :
                node.childNodes()) {

            appendRawText(
                    child,
                    result
            );
        }
    }

    private static String extractDirectText(
            LayoutNode node
    ) {
        if (node == null ||
                node.element == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        for (Node child :
                node.element.childNodes()) {

            if (child instanceof TextNode) {

                String value =
                        ((TextNode) child)
                                .getWholeText();

                if (!value.trim().isEmpty()) {

                    if (result.length() > 0) {
                        result.append(" ");
                    }

                    result.append(
                            value.trim()
                    );
                }
            }
        }

        return result.toString();
    }

    private static String normalizeWhitespace(
            String text
    ) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    // ------------------------------------------------------------
    // Element classification
    // ------------------------------------------------------------

    private static boolean isTextLike(
            LayoutNode node
    ) {
        if (node == null ||
                node.element == null) {
            return false;
        }

        String tag =
                node.element
                        .tagName()
                        .toLowerCase(Locale.ROOT);

        if (!node.element.select("img, image").isEmpty()) {
            return false;
        }

        return INLINE_TAGS.contains(tag) ||
                tag.startsWith("h") ||
                "p".equals(tag) ||
                "label".equals(tag);
    }

    private static boolean isHidden(
            LayoutNode node
    ) {
        String display =
                css(
                        node,
                        "display"
                );

        if ("none".equalsIgnoreCase(
                display
        )) {
            return true;
        }

        String visibility =
                css(
                        node,
                        "visibility"
                );

        return "hidden".equalsIgnoreCase(
                visibility
        );
    }

    // ------------------------------------------------------------
    // CSS helper
    // ------------------------------------------------------------

    /**
     * Reads CSS-like values from LayoutNode.style.
     *
     * This assumes your LayoutStyle implementation exposes the
     * common properties used by the current renderer.
     *
     * If your LayoutStyle stores CSS differently, change this
     * single method instead of rewriting the renderer.
     */
    private static String css(
            LayoutNode node,
            String property
    ) {
        if (node == null ||
                node.style == null ||
                property == null) {
            return null;
        }

        String p =
                property
                        .toLowerCase(Locale.ROOT);

        // --------------------------------------------------------
        // Existing fields from your current LayoutNode style
        // --------------------------------------------------------

        switch (p) {

            case "background-color":
                return node.style.backgroundColor;

            case "color":
                return node.style.color;

            case "margin-left":
                return floatToCss(
                        node.style.marginLeft
                );

            case "margin-top":
                return floatToCss(
                        node.style.marginTop
                );

            case "margin-right":
                return floatToCss(
                        node.style.marginRight
                );

            case "margin-bottom":
                return floatToCss(
                        node.style.marginBottom
                );

            default:
                break;
        }

        /*
         * IMPORTANT:
         *
         * If your LayoutStyle already has a generic CSS map,
         * use it here:
         *
         *     return node.style.get(property);
         *
         * Otherwise return null.
         */
        return null;
    }

    private static String floatToCss(
            float value
    ) {
        if (value == 0) {
            return "0";
        }

        return String.valueOf(value);
    }

    // ------------------------------------------------------------
    // CSS dimension helpers
    // ------------------------------------------------------------

    private static int dp(
            LayoutNode node,
            String property,
            int fallback
    ) {
        String value =
                css(
                        node,
                        property
                );

        if (value == null ||
                value.isEmpty()) {
            return fallback;
        }

        return parseCssDimension(
                node,
                value,
                false
        );
    }

    private static int parseCssDimension(
            LayoutNode node,
            String value,
            boolean width
    ) {
        if (value == null) {
            return 0;
        }

        value =
                value.trim()
                        .toLowerCase(Locale.ROOT);

        try {

            if (value.endsWith("px")) {

                return Math.round(
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 2
                                )
                        )
                );
            }

            if (value.endsWith("dp")) {

                return Math.round(
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 2
                                )
                        )
                );
            }

            if (value.endsWith("%")) {

                /*
                 * Parent width is not directly available here.
                 *
                 * MATCH_PARENT is safer for 100%.
                 */

                float percentage =
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 1
                                )
                        );

                if (percentage >= 99) {
                    return ViewGroup.LayoutParams.MATCH_PARENT;
                }

                return ViewGroup.LayoutParams.WRAP_CONTENT;
            }

            if (value.endsWith("rem")) {

                return Math.round(
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 3
                                )
                        ) * 16f
                );
            }

            return Math.round(
                    Float.parseFloat(value)
            );

        } catch (Exception ignored) {
            return 0;
        }
    }

    private static float parseLineHeight(
            String value,
            float fontSize
    ) {
        try {

            value =
                    value.trim()
                            .toLowerCase(Locale.ROOT);

            if ("normal".equals(value)) {
                return 1.2f;
            }

            if (value.endsWith("px")) {

                float px =
                        Float.parseFloat(
                                value.substring(
                                        0,
                                        value.length() - 2
                                )
                        );

                return px / fontSize;
            }

            return Float.parseFloat(value);

        } catch (Exception ignored) {
            return 1.2f;
        }
    }

    private static float parseFloat(
            String value,
            float fallback
    ) {
        if (value == null) {
            return fallback;
        }

        try {
            return Float.parseFloat(
                    value.replace(
                            "px",
                            ""
                    ).trim()
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // ------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------

    private static Integer parseColorSafe(
            String value
    ) {
        if (value == null ||
                value.trim().isEmpty()) {
            return null;
        }

        try {

            String v =
                    value.trim();

            if (v.startsWith("rgb(")) {
                return parseRgb(v);
            }

            if (v.startsWith("rgba(")) {
                return parseRgba(v);
            }

            return Color.parseColor(v);

        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseColorOrDefault(
            String value,
            int fallback
    ) {
        Integer result =
                parseColorSafe(value);

        return result != null
                ? result
                : fallback;
    }

    private static Integer parseRgb(
            String value
    ) {
        try {

            String inside =
                    value.substring(
                            value.indexOf('(') + 1,
                            value.lastIndexOf(')')
                    );

            String[] parts =
                    inside.split(",");

            int r =
                    Integer.parseInt(
                            parts[0].trim()
                    );

            int g =
                    Integer.parseInt(
                            parts[1].trim()
                    );

            int b =
                    Integer.parseInt(
                            parts[2].trim()
                    );

            return Color.rgb(
                    clamp(r),
                    clamp(g),
                    clamp(b)
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseRgba(
            String value
    ) {
        try {

            String inside =
                    value.substring(
                            value.indexOf('(') + 1,
                            value.lastIndexOf(')')
                    );

            String[] parts =
                    inside.split(",");

            int r =
                    Integer.parseInt(
                            parts[0].trim()
                    );

            int g =
                    Integer.parseInt(
                            parts[1].trim()
                    );

            int b =
                    Integer.parseInt(
                            parts[2].trim()
                    );

            float alpha =
                    Float.parseFloat(
                            parts[3].trim()
                    );

            return Color.argb(
                    Math.round(
                            clampFloat(alpha) *
                                    255
                    ),
                    clamp(r),
                    clamp(g),
                    clamp(b)
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    private static int clamp(
            int value
    ) {
        return Math.max(
                0,
                Math.min(
                        255,
                        value
                )
        );
    }

    private static float clampFloat(
            float value
    ) {
        return Math.max(
                0,
                Math.min(
                        1,
                        value
                )
        );
    }

    private static String firstNonEmpty(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {

            if (value != null &&
                    !value.trim().isEmpty()) {

                return value;
            }
        }

        return null;
    }

    private static int dpToPx(
            Context context,
            float dp
    ) {
        if (context == null) {
            return Math.round(dp);
        }

        float density =
                context
                        .getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }

    private static float dpFloat(
            LayoutNode node,
            float value
    ) {
        /*
         * LayoutNode does not currently expose Context,
         * so this is intentionally a raw dp-compatible value.
         *
         * The renderer's normal dimension conversion happens
         * through dpToPx(Context,...).
         */
        return value;
    }

    private static View renderBreak(
            RendererState state,
            ViewGroup parent
    ) {
        TextView br =
                new TextView(
                        state.context
                );

        br.setText("\n");

        parent.addView(br);

        return br;
    }

    private static void applyTableStylesToChildren(RendererState state, ViewGroup parent, boolean isHeader) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setTextIsSelectable(true);
                if (isDarkColor(state.getBackgroundColor())) {
                    int currentColor = tv.getCurrentTextColor();
                    if (isDarkColor(currentColor)) {
                        tv.setTextColor(state.getTextColor());
                    }
                }
                if (isHeader) {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                }
            } else if (child instanceof ViewGroup) {
                applyTableStylesToChildren(state, (ViewGroup) child, isHeader);
            }
        }
    }

    private static int dpToPx(
            Context context,
            int dp
    ) {
        return dpToPx(
                context,
                (float) dp
        );
    }

    // ------------------------------------------------------------
    // Compatibility overload
    // ------------------------------------------------------------

    private static void renderContainerChildren(
            RendererState state,
            ViewGroup container,
            LayoutNode node,
            int depth,
            boolean ignored
    ) {
        renderContainerChildren(
                state,
                container,
                node,
                depth
        );
    }

    private static org.jsoup.nodes.Element findParentForm(org.jsoup.nodes.Element element) {
        org.jsoup.nodes.Element parent = element.parent();
        while (parent != null) {
            if ("form".equalsIgnoreCase(parent.tagName())) {
                return parent;
            }
            parent = parent.parent();
        }
        return null;
    }

    private static void submitForm(RendererState state, org.jsoup.nodes.Element form) {
        if (form == null) return;
        String action = form.attr("action");
        String method = form.attr("method");
        if (method == null || method.isEmpty()) {
            method = "GET";
        }
        
        String baseUrl = form.ownerDocument() != null ? form.ownerDocument().baseUri() : "";
        String targetUrl = action;
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = baseUrl;
        } else if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            try {
                java.net.URI uri = new java.net.URI(baseUrl).resolve(targetUrl);
                targetUrl = uri.toString();
            } catch (Exception ignored) {}
        }
        
        org.jsoup.select.Elements inputs = form.select("input, textarea, select");
        StringBuilder queryBuilder = new StringBuilder();
        for (org.jsoup.nodes.Element input : inputs) {
            String name = input.attr("name");
            if (name == null || name.isEmpty()) continue;
            
            String value = "";
            EditText et = state.inputFields.get(input);
            if (et != null) {
                value = et.getText().toString();
            } else {
                value = input.attr("value");
            }
            
            if (queryBuilder.length() > 0) {
                queryBuilder.append("&");
            }
            try {
                queryBuilder.append(java.net.URLEncoder.encode(name, "UTF-8"))
                            .append("=")
                            .append(java.net.URLEncoder.encode(value, "UTF-8"));
            } catch (Exception ignored) {}
        }
        
        if (queryBuilder.length() > 0) {
            if (targetUrl.contains("?")) {
                targetUrl += "&" + queryBuilder.toString();
            } else {
                targetUrl += "?" + queryBuilder.toString();
            }
        }
        
        if (state.linkListener != null) {
            state.linkListener.onLinkClick(targetUrl);
        }
    }
}