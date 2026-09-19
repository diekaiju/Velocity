package com.velocity.browser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HtmlToMarkdownConverter {

    public static class MarkdownDocument {
        public final String markdown;
        public final String title;
        public final List<HeadingItem> headings;

        public MarkdownDocument(
                String markdown,
                String title,
                List<HeadingItem> headings
        ) {
            this.markdown = markdown;
            this.title = title;
            this.headings = headings;
        }
    }

    public static class HeadingItem {
        public final int level;
        public final String title;
        public final String anchorId;

        public HeadingItem(int level, String title, String anchorId) {
            this.level = level;
            this.title = title;
            this.anchorId = anchorId;
        }
    }

    private static final Set<String> BLOCK_TAGS = new HashSet<>();

    static {
        String[] tags = {
                "address", "article", "aside", "blockquote", "div",
                "dl", "fieldset", "figcaption", "figure", "footer",
                "form", "h1", "h2", "h3", "h4", "h5", "h6",
                "header", "hr", "li", "main", "nav", "ol", "p",
                "pre", "section", "table", "ul"
        };

        for (String tag : tags) {
            BLOCK_TAGS.add(tag);
        }
    }

    private HtmlToMarkdownConverter() {
    }

    public static MarkdownDocument convert(String html, String baseUrl) {
        if (html == null || html.trim().isEmpty()) {
            return new MarkdownDocument(
                    "",
                    "Untitled",
                    new ArrayList<>()
            );
        }

        Document document;

        try {
            document = Jsoup.parse(
                    html,
                    baseUrl == null ? "" : baseUrl
            );
        } catch (Exception exception) {
            return new MarkdownDocument(
                    html,
                    "Untitled",
                    new ArrayList<>()
            );
        }

        removeUnwantedElements(document);

        String title = extractTitle(document);

        Element root = selectMainContent(document);

        RenderContext context = new RenderContext(baseUrl);

        renderChildren(root, context, 0);

        String markdown = cleanupMarkdown(context.output.toString());

        return new MarkdownDocument(
                markdown,
                title,
                context.headings
        );
    }

    private static void removeUnwantedElements(Document document) {
        String selector =
                "script, style, noscript, template, svg, canvas, " +
                "iframe, object, embed, video, audio, source, track, " +
                "form, dialog, [hidden], [aria-hidden='true'], " +
                ".advertisement, .advert, .ads, .ad, .adsbygoogle, " +
                ".cookie, .cookies, .cookie-banner, .popup, .modal, " +
                ".newsletter, .social-share, .share-buttons, " +
                ".comments, .comment-section, nav, footer";

        document.select(selector).remove();

        // Remove elements that are commonly used for tracking pixels.
        document.select("img[width='1'][height='1'], img[style*='display:none']")
                .remove();
    }

    private static String extractTitle(Document document) {
        String title = document.title();

        if (title != null && !title.trim().isEmpty()) {
            return normalizeWhitespace(title);
        }

        Element heading = document.selectFirst("h1");

        if (heading != null && !heading.text().trim().isEmpty()) {
            return normalizeWhitespace(heading.text());
        }

        return "Untitled Page";
    }

    private static Element selectMainContent(Document document) {
        Element article = document.selectFirst("article");

        if (article != null && article.text().trim().length() > 100) {
            return article;
        }

        Element main = document.selectFirst("main");

        if (main != null && main.text().trim().length() > 100) {
            return main;
        }

        String[] selectors = {
                "[role=main]",
                "#content",
                "#main-content",
                "#main",
                ".content",
                ".main-content",
                ".article-content",
                ".post-content",
                ".entry-content",
                ".article-body",
                ".post-body"
        };

        for (String selector : selectors) {
            Element candidate = document.selectFirst(selector);

            if (candidate != null && candidate.text().trim().length() > 100) {
                return candidate;
            }
        }

        return document.body() != null
                ? document.body()
                : document;
    }

    private static void renderChildren(
            Node parent,
            RenderContext context,
            int listDepth
    ) {
        for (Node child : parent.childNodes()) {
            renderNode(child, context, listDepth);
        }
    }

    private static void renderNode(
            Node node,
            RenderContext context,
            int listDepth
    ) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();

            if (!text.isEmpty()) {
                context.output.append(normalizeText(text));
            }

            return;
        }

        if (!(node instanceof Element)) {
            return;
        }

        Element element = (Element) node;
        String tag = element.tagName().toLowerCase(Locale.ROOT);

        switch (tag) {
            case "html":
            case "body":
            case "head":
            case "main":
            case "article":
            case "section":
            case "header":
            case "footer":
            case "aside":
            case "div":
                    renderBlockElement(element, context, listDepth);
                    break;

            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                    renderHeading(element, context, listDepth);
                    break;

            case "p":
                    renderParagraph(element, context, listDepth);
                    break;

            case "br":
                    context.output.append("  \n");
                    break;

            case "hr":
                    ensureBlankLines(context.output, 2);
                    context.output.append("---\n\n");
                    break;

            case "strong":
            case "b":
                    renderInlineWrapped(element, context, "**", listDepth);
                    break;

            case "em":
            case "i":
                    renderInlineWrapped(element, context, "*", listDepth);
                    break;

            case "del":
            case "s":
            case "strike":
                    renderInlineWrapped(element, context, "~~", listDepth);
                    break;

            case "mark":
                    renderInlineWrapped(element, context, "==", listDepth);
                    break;

            case "code":
                    renderInlineCode(element, context);
                    break;

            case "pre":
                    renderCodeBlock(element, context);
                    break;

            case "blockquote":
                    renderBlockquote(element, context, listDepth);
                    break;

            case "a":
                    renderLink(element, context, listDepth);
                    break;

            case "img":
                    renderImage(element, context);
                    break;

            case "picture":
                    renderPicture(element, context);
                    break;

            case "ul":
            case "ol":
                    renderList(element, context, listDepth);
                    break;

            case "li":
                    renderChildren(element, context, listDepth);
                    break;

            case "table":
                    renderTable(element, context);
                    break;

            case "figure":
                    renderFigure(element, context, listDepth);
                    break;

            case "figcaption":
                    ensureBlankLines(context.output, 1);
                    context.output.append("*");
                    renderChildren(element, context, listDepth);
                    context.output.append("*\n\n");
                    break;

            case "details":
                    renderDetails(element, context, listDepth);
                    break;

            case "summary":
                    renderChildren(element, context, listDepth);
                    break;

            case "dl":
            case "dt":
            case "dd":
                    renderDefinitionList(element, context, listDepth);
                    break;

            default:
                    renderChildren(element, context, listDepth);
                    break;
        }
    }

    private static void renderBlockElement(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);

        if (BLOCK_TAGS.contains(tag)) {
            ensureBlankLines(context.output, 1);
        }

        renderChildren(element, context, listDepth);

        if (BLOCK_TAGS.contains(tag)) {
            ensureBlankLines(context.output, 1);
        }
    }

    private static void renderHeading(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 2);

        int level = Character.getNumericValue(
                element.tagName().charAt(1)
        );

        String headingText = normalizeWhitespace(element.text());

        if (headingText.isEmpty()) {
            return;
        }

        String id = element.id();

        if (id == null || id.trim().isEmpty()) {
            id = element.attr("name");
        }

        // Check child elements for id or name (e.g. MediaWiki <span class="mw-headline" id="...">)
        if (id == null || id.trim().isEmpty()) {
            Element childWithId = element.selectFirst("[id], [name], .mw-headline");
            if (childWithId != null) {
                if (childWithId.hasAttr("id") && !childWithId.id().isEmpty()) {
                    id = childWithId.id();
                } else if (childWithId.hasAttr("name") && !childWithId.attr("name").isEmpty()) {
                    id = childWithId.attr("name");
                }
            }
        }

        // Check preceding anchor tags, e.g. <a id="name"></a><h2>Title</h2>
        if (id == null || id.trim().isEmpty()) {
            Element prev = element.previousElementSibling();
            if (prev != null && "a".equalsIgnoreCase(prev.tagName()) && (prev.hasAttr("id") || prev.hasAttr("name"))) {
                id = prev.hasAttr("id") ? prev.id() : prev.attr("name");
            }
        }

        if (id == null || id.trim().isEmpty()) {
            id = slugify(headingText);
        }

        id = makeUniqueId(id, context.usedHeadingIds);

        context.headings.add(
                new HeadingItem(level, headingText, id)
        );

        StringBuilder hashes = new StringBuilder();
        for (int i = 0; i < level; i++) {
            hashes.append("#");
        }
        hashes.append(" ");

        context.output.append(hashes.toString());

        StringBuilder headingContent = new StringBuilder();
        RenderContext headingContext = context.copyFor(headingContent);

        renderChildren(element, headingContext, listDepth);

        context.output.append(
                headingContent.toString().trim()
        );
        if (id != null && !id.isEmpty()) {
            context.output.append(" {#").append(id).append("}");
        }
        context.output.append("\n\n");
    }

    private static void renderParagraph(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 2);

        renderChildren(element, context, listDepth);

        ensureBlankLines(context.output, 2);
    }

    private static void renderInlineWrapped(
            Element element,
            RenderContext context,
            String wrapper,
            int listDepth
    ) {
        String content = renderInlineContent(
                element,
                context,
                listDepth
        );

        if (!content.trim().isEmpty()) {
            context.output.append(wrapper)
                    .append(content.trim())
                    .append(wrapper);
        }
    }

    private static String renderInlineContent(
            Element element,
            RenderContext parentContext,
            int listDepth
    ) {
        StringBuilder result = new StringBuilder();

        RenderContext childContext =
                parentContext.copyFor(result);

        renderChildren(element, childContext, listDepth);

        return result.toString();
    }

    private static void renderInlineCode(
            Element element,
            RenderContext context
    ) {
        String value = element.text();

        if (value == null || value.isEmpty()) {
            return;
        }

        String delimiter = value.contains("`")
                ? "``"
                : "`";

        context.output.append(delimiter)
                .append(value.trim())
                .append(delimiter);
    }

    private static void renderCodeBlock(
            Element element,
            RenderContext context
    ) {
        ensureBlankLines(context.output, 2);

        Element code = element.selectFirst(":root > code");

        String codeText;
        String className = "";

        if (code != null) {
            codeText = code.wholeText();
            className = code.className();
        } else {
            codeText = element.wholeText();
        }

        String language = extractLanguage(className);

        context.output.append("```")
                .append(language)
                .append("\n");

        context.output.append(
                removeTrailingNewlines(codeText)
        );

        context.output.append("\n```\n\n");
    }

    private static String extractLanguage(String className) {
        if (className == null) {
            return "";
        }

        for (String token : className.split("\\s+")) {
            if (token.startsWith("language-")) {
                return token.substring("language-".length());
            }

            if (token.startsWith("lang-")) {
                return token.substring("lang-".length());
            }
        }

        return "";
    }

    private static void renderBlockquote(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 2);

        StringBuilder quote = new StringBuilder();
        RenderContext quoteContext = context.copyFor(quote);

        renderChildren(element, quoteContext, listDepth);

        String[] lines = cleanupMarkdown(quote.toString())
                .split("\\R");

        for (String line : lines) {
            context.output.append("> ");

            if (!line.trim().isEmpty()) {
                context.output.append(line);
            }

            context.output.append("\n");
        }

        context.output.append("\n");
    }

    private static void renderLink(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        String href = element.attr("abs:href");

        if (href == null || href.trim().isEmpty()) {
            href = element.attr("href");
        }

        if (href != null && !href.trim().isEmpty()) {
            if (href.startsWith("//")) {
                href = "https:" + href;
            } else if (!href.startsWith("http://") && !href.startsWith("https://")
                    && !href.startsWith("file://") && !href.startsWith("content://")
                    && !href.startsWith("#") && !href.startsWith("mailto:") && !href.startsWith("tel:") && !href.startsWith("javascript:")) {
                if (context.baseUrl != null && !context.baseUrl.isEmpty()) {
                    try {
                        java.net.URL base = new java.net.URL(context.baseUrl);
                        href = new java.net.URL(base, href).toString();
                    } catch (Exception ignored) {}
                }
            }
        }

        String text = renderInlineContent(
                element,
                context,
                listDepth
        ).trim();

        if (text.isEmpty()) {
            text = normalizeWhitespace(element.text());
        }

        // If text is still empty, check if element has an img
        if (text.isEmpty()) {
            Element img = element.selectFirst("img");
            if (img != null) {
                StringBuilder imgSb = new StringBuilder();
                RenderContext imgContext = context.copyFor(imgSb);
                renderImage(img, imgContext);
                text = imgSb.toString().trim();
            }
        }

        if (text.isEmpty()) {
            text = href != null ? href : "";
        }

        if (href == null || href.trim().isEmpty() || href.startsWith("javascript:")) {
            context.output.append(text);
            return;
        }

        String cleanHref = href.replace(" ", "%20");

        // Clean link text: If text contains markdown image ![...](...), preserve its brackets!
        String cleanText;
        if (text.contains("![")) {
            cleanText = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        } else {
            cleanText = text.replace("[", "\\[").replace("]", "\\]").replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        }

        context.output.append("[")
                .append(cleanText)
                .append("](")
                .append(cleanHref)
                .append(")");
    }

    private static void renderImage(
            Element element,
            RenderContext context
    ) {
        String src = firstNonEmpty(
                element.attr("abs:src"),
                element.attr("src"),
                element.attr("data-src"),
                element.attr("data-lazy-src"),
                element.attr("data-original"),
                element.attr("data-url"),
                element.attr("data-full-src"),
                element.attr("data-actualsrc"),
                element.attr("data-zoom-src"),
                element.attr("data-hires"),
                element.attr("data-orig-src")
        );

        if (isPlaceholderUrl(src)) {
            if (element.hasAttr("data-srcset")) {
                String candidate = extractUrlFromSrcset(element.attr("data-srcset"));
                if (!candidate.isEmpty()) src = candidate;
            } else if (element.hasAttr("srcset")) {
                String candidate = extractUrlFromSrcset(element.attr("srcset"));
                if (!candidate.isEmpty()) src = candidate;
            }
        }

        if ((src == null || src.trim().isEmpty()) && element.hasAttr("srcset")) {
            src = extractUrlFromSrcset(element.attr("srcset"));
        }

        if ((src == null || src.trim().isEmpty()) && element.hasAttr("data-srcset")) {
            src = extractUrlFromSrcset(element.attr("data-srcset"));
        }

        // Check if image is inside a <picture> tag with <source> elements
        if (src == null || src.trim().isEmpty() || isPlaceholderUrl(src)) {
            Element parent = element.parent();
            if (parent != null && "picture".equalsIgnoreCase(parent.tagName())) {
                for (Element source : parent.select("source")) {
                    String sourceCandidate = firstNonEmpty(
                            extractUrlFromSrcset(source.attr("srcset")),
                            extractUrlFromSrcset(source.attr("data-srcset")),
                            source.attr("src"),
                            source.attr("data-src")
                    );
                    if (!sourceCandidate.isEmpty() && !isPlaceholderUrl(sourceCandidate)) {
                        src = sourceCandidate;
                        break;
                    }
                }
            }
        }

        if (src == null || src.trim().isEmpty()) {
            return;
        }

        if (src.startsWith("//")) {
            src = "https:" + src;
        } else if (!src.startsWith("http://") && !src.startsWith("https://")
                && !src.startsWith("file://") && !src.startsWith("content://") && !src.startsWith("data:")) {
            if (context.baseUrl != null && !context.baseUrl.isEmpty()) {
                try {
                    java.net.URL base = new java.net.URL(context.baseUrl);
                    src = new java.net.URL(base, src).toString();
                } catch (Exception ignored) {}
            }
        }

        String alt = firstNonEmpty(element.attr("alt"), element.attr("title"), "Image");

        context.output.append("![")
                .append(escapeMarkdown(alt.trim()))
                .append("](")
                .append(src.replace(" ", "%20"))
                .append(")");
    }

    private static boolean isPlaceholderUrl(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/") && lower.length() < 250) {
            return true;
        }
        return lower.contains("placeholder") || lower.contains("blank.gif") || lower.contains("pixel.gif") || lower.contains("spacer.gif") || lower.contains("trans.gif");
    }

    private static String extractUrlFromSrcset(String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) {
            return "";
        }
        String[] candidates = srcset.split(",");
        String selected = "";
        int maxVal = -1;
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) continue;
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length > 0) {
                String url = tokens[0];
                if (selected.isEmpty()) {
                    selected = url;
                }
                if (tokens.length > 1) {
                    try {
                        String desc = tokens[1].toLowerCase(Locale.ROOT);
                        if (desc.endsWith("w")) {
                            int w = Integer.parseInt(desc.substring(0, desc.length() - 1));
                            if (w > maxVal) {
                                maxVal = w;
                                selected = url;
                            }
                        } else if (desc.endsWith("x")) {
                            float x = Float.parseFloat(desc.substring(0, desc.length() - 1));
                            int val = (int)(x * 100);
                            if (val > maxVal) {
                                maxVal = val;
                                selected = url;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return selected;
    }

    private static void renderPicture(
            Element element,
            RenderContext context
    ) {
        Element image = element.selectFirst("img");

        if (image != null) {
            String src = firstNonEmpty(image.attr("src"), image.attr("data-src"));
            if (src.isEmpty() || isPlaceholderUrl(src)) {
                Element source = element.selectFirst("source[srcset]");
                if (source != null) {
                    String best = extractUrlFromSrcset(source.attr("srcset"));
                    if (!best.isEmpty()) {
                        image.attr("src", best);
                    }
                } else {
                    source = element.selectFirst("source[src]");
                    if (source != null) {
                        String s = source.attr("src");
                        if (!s.isEmpty()) {
                            image.attr("src", s);
                        }
                    }
                }
            }
            renderImage(image, context);
        }
    }

    private static void renderFigure(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 1);

        renderChildren(element, context, listDepth);

        ensureBlankLines(context.output, 1);
    }

    private static void renderDetails(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 1);

        Element summary = element.selectFirst(":root > summary");

        if (summary != null) {
            context.output.append("**")
                    .append(normalizeWhitespace(summary.text()))
                    .append("**\n\n");
        }

        for (Node child : element.childNodes()) {
            if (child instanceof Element
                    && "summary".equalsIgnoreCase(
                    ((Element) child).tagName())) {
                continue;
            }

            renderNode(child, context, listDepth);
        }

        ensureBlankLines(context.output, 1);
    }

    private static void renderList(
            Element list,
            RenderContext context,
            int listDepth
    ) {
        ensureBlankLines(context.output, 2);

        boolean ordered = "ol".equalsIgnoreCase(list.tagName());
        int itemNumber = 1;

        for (Element item : list.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }

            for (int i = 0; i < listDepth; i++) {
                context.output.append("  ");
            }

            if (ordered) {
                context.output.append(itemNumber++)
                        .append(". ");
            } else {
                context.output.append("- ");
            }

            StringBuilder itemContent = new StringBuilder();
            RenderContext itemContext =
                    context.copyFor(itemContent);

            boolean checkbox = isTaskItem(item);

            if (checkbox) {
                itemContent.append(
                        isChecked(item) ? "[x] " : "[ ] "
                );
            }

            for (Node child : item.childNodes()) {
                if (child instanceof Element
                        && "ul".equalsIgnoreCase(
                        ((Element) child).tagName())) {
                    continue;
                }

                if (child instanceof Element
                        && "ol".equalsIgnoreCase(
                        ((Element) child).tagName())) {
                    continue;
                }

                renderNode(child, itemContext, listDepth + 1);
            }

            String text = cleanupInlineWhitespace(
                    itemContent.toString()
            ).trim();

            context.output.append(text).append("\n");

            // Render nested lists after the current list item.
            for (Element child : item.children()) {
                if ("ul".equalsIgnoreCase(child.tagName())
                        || "ol".equalsIgnoreCase(child.tagName())) {
                    renderList(child, context, listDepth + 1);
                }
            }
        }

        ensureBlankLines(context.output, 1);
    }

    private static boolean isTaskItem(Element item) {
        String text = item.text().trim();

        return text.startsWith("[ ]")
                || text.startsWith("[x]")
                || text.startsWith("[X]")
                || item.selectFirst("input[type=checkbox]") != null;
    }

    private static boolean isChecked(Element item) {
        Element checkbox = item.selectFirst(
                "input[type=checkbox]"
        );

        if (checkbox != null) {
            return checkbox.hasAttr("checked");
        }

        String text = item.text().trim();

        return text.startsWith("[x]")
                || text.startsWith("[X]");
    }

    private static void renderDefinitionList(
            Element element,
            RenderContext context,
            int listDepth
    ) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);

        if ("dt".equals(tag)) {
            ensureBlankLines(context.output, 1);
            context.output.append("**");
            renderChildren(element, context, listDepth);
            context.output.append("**\n");
        } else if ("dd".equals(tag)) {
            context.output.append(": ");
            renderChildren(element, context, listDepth);
            context.output.append("\n");
        } else {
            renderChildren(element, context, listDepth);
        }
    }

    private static void renderTable(
            Element table,
            RenderContext context
    ) {
        ensureBlankLines(context.output, 2);

        // Collect direct child rows only (handling thead, tbody, tfoot, or direct tr)
        List<Element> trElements = new ArrayList<>();
        for (Element child : table.children()) {
            String cTag = child.tagName().toLowerCase(Locale.ROOT);
            if ("tr".equals(cTag)) {
                trElements.add(child);
            } else if ("thead".equals(cTag) || "tbody".equals(cTag) || "tfoot".equals(cTag)) {
                for (Element subChild : child.children()) {
                    if ("tr".equalsIgnoreCase(subChild.tagName())) {
                        trElements.add(subChild);
                    }
                }
            }
        }

        if (trElements.isEmpty()) {
            trElements = table.select("tr");
        }

        if (trElements.isEmpty()) {
            renderChildren(table, context, 0);
            return;
        }

        // Check if this is purely a layout wrapper table with 1 row holding independent nested tables
        if (trElements.size() == 1) {
            Elements rowCells = trElements.get(0).select("> td, > th");
            boolean isOnlyTableWrappers = !rowCells.isEmpty();
            for (Element c : rowCells) {
                if (c.select("table").isEmpty() || c.ownText().trim().length() > 20) {
                    isOnlyTableWrappers = false;
                    break;
                }
            }
            if (isOnlyTableWrappers) {
                for (Element c : rowCells) {
                    for (Element nestedTbl : c.select("> table, > div > table, table")) {
                        renderTable(nestedTbl, context);
                    }
                }
                return;
            }
        }

        int currentTableId = ++context.tableCounter[0];
        String mainTableAnchor = "table-" + currentTableId;

        List<NestedTableItem> nestedTablesToRender = new ArrayList<>();
        List<List<String>> grid = new ArrayList<>();
        int maxCols = 0;
        int subIdx = 0;

        for (Element tr : trElements) {
            List<String> rowCells = new ArrayList<>();
            for (Element cell : tr.children()) {
                String cellTag = cell.tagName().toLowerCase(Locale.ROOT);
                if (!"td".equals(cellTag) && !"th".equals(cellTag)) {
                    continue;
                }

                // Render cell content (extracting multi-value nested tables with anchor links, or displaying single-value subtables directly)
                StringBuilder cellContentSb = new StringBuilder();
                for (Node innerNode : cell.childNodes()) {
                    if (innerNode instanceof Element && "table".equalsIgnoreCase(((Element) innerNode).tagName())) {
                        Element nestedTbl = (Element) innerNode;
                        if (isSingleValueSubTable(nestedTbl)) {
                            String singleVal = renderSingleValueSubTable(nestedTbl, context);
                            if (!singleVal.isEmpty()) {
                                if (cellContentSb.length() > 0) cellContentSb.append(" ");
                                cellContentSb.append(singleVal);
                            }
                        } else {
                            subIdx++;
                            String subLabel = extractSubTableLabel(tr, cell, nestedTbl);
                            String subTableAnchor = "subtable-" + currentTableId + "-" + subIdx;
                            nestedTablesToRender.add(new NestedTableItem(nestedTbl, subLabel, subTableAnchor, mainTableAnchor));
                            if (cellContentSb.length() > 0) {
                                cellContentSb.append(" ");
                            }
                            cellContentSb.append("[📊 **Sub-table: ").append(subLabel.isEmpty() ? "Details" : subLabel).append("**](#").append(subTableAnchor).append(")");
                        }
                    } else if (innerNode instanceof Element && !((Element) innerNode).select("table").isEmpty()) {
                        Element innerEl = (Element) innerNode;
                        for (Element nestedTbl : innerEl.select("table")) {
                            if (isSingleValueSubTable(nestedTbl)) {
                                String singleVal = renderSingleValueSubTable(nestedTbl, context);
                                if (!singleVal.isEmpty()) {
                                    if (cellContentSb.length() > 0) cellContentSb.append(" ");
                                    cellContentSb.append(singleVal);
                                }
                            } else {
                                subIdx++;
                                String subLabel = extractSubTableLabel(tr, cell, nestedTbl);
                                String subTableAnchor = "subtable-" + currentTableId + "-" + subIdx;
                                nestedTablesToRender.add(new NestedTableItem(nestedTbl, subLabel, subTableAnchor, mainTableAnchor));
                                if (cellContentSb.length() > 0) {
                                    cellContentSb.append(" ");
                                }
                                cellContentSb.append("[📊 **Sub-table: ").append(subLabel.isEmpty() ? "Details" : subLabel).append("**](#").append(subTableAnchor).append(")");
                            }
                        }
                        Element cloneEl = innerEl.clone();
                        cloneEl.select("table").remove();
                        StringBuilder nodeSb = new StringBuilder();
                        RenderContext nodeContext = context.copyFor(nodeSb);
                        renderNode(cloneEl, nodeContext, 0);
                        String nodeText = nodeSb.toString().trim();
                        if (!nodeText.isEmpty()) {
                            if (cellContentSb.length() > 0 && !cellContentSb.toString().endsWith(" ")) {
                                cellContentSb.append(" ");
                            }
                            cellContentSb.append(nodeText);
                        }
                    } else {
                        StringBuilder nodeSb = new StringBuilder();
                        RenderContext nodeContext = context.copyFor(nodeSb);
                        renderNode(innerNode, nodeContext, 0);
                        String nodeText = nodeSb.toString().trim();
                        if (!nodeText.isEmpty()) {
                            if (cellContentSb.length() > 0 && !cellContentSb.toString().endsWith(" ")) {
                                cellContentSb.append(" ");
                            }
                            cellContentSb.append(nodeText);
                        }
                    }
                }

                String cellContent = cellContentSb.toString().trim()
                        .replace("\r\n", " <br> ")
                        .replace("\n", " <br> ")
                        .replace("\r", " <br> ")
                        .replace("|", "\\|")
                        .replaceAll("\\s+", " ")
                        .trim();

                if (cellContent.isEmpty()) {
                    cellContent = " ";
                }

                int colspan = parsePositiveInt(cell.attr("colspan"), 1);
                rowCells.add(cellContent);

                for (int i = 1; i < colspan; i++) {
                    rowCells.add(" ");
                }
            }

            if (!rowCells.isEmpty()) {
                maxCols = Math.max(maxCols, rowCells.size());
                grid.add(rowCells);
            }
        }

        if (grid.isEmpty() || maxCols == 0) {
            return;
        }

        // If this table has sub-tables extracted from it, emit a heading anchor for return jumps
        if (!nestedTablesToRender.isEmpty()) {
            context.output.append("### 📋 Table ").append(currentTableId).append(" {#").append(mainTableAnchor).append("}\n\n");
            context.headings.add(new HeadingItem(3, "Table " + currentTableId, mainTableAnchor));
        }

        // Pad all rows to match maxCols
        for (List<String> row : grid) {
            while (row.size() < maxCols) {
                row.add(" ");
            }
        }

        // Header row
        List<String> header = grid.get(0);
        appendTableRow(context.output, header, maxCols);

        // Separator row
        context.output.append("|");
        for (int i = 0; i < maxCols; i++) {
            context.output.append(" --- |");
        }
        context.output.append("\n");

        // Body rows
        for (int r = 1; r < grid.size(); r++) {
            appendTableRow(context.output, grid.get(r), maxCols);
        }

        ensureBlankLines(context.output, 2);

        // Render extracted sub-tables as first-class standalone native tables with return links
        for (NestedTableItem item : nestedTablesToRender) {
            ensureBlankLines(context.output, 2);
            String titleText = "Sub-Table: " + (item.label != null && !item.label.isEmpty() ? item.label : "Details");
            context.output.append("### 📊 ").append(titleText).append(" {#").append(item.anchorId).append("}\n\n");
            context.output.append("[⬆️ **Back to Main Table (Table ").append(currentTableId).append(")**](#").append(item.parentAnchor).append(")\n\n");
            context.headings.add(new HeadingItem(3, titleText, item.anchorId));

            renderTable(item.tableElement, context);

            ensureBlankLines(context.output, 1);
            context.output.append("[⬆️ **Back to Main Table (Table ").append(currentTableId).append(")**](#").append(item.parentAnchor).append(")\n\n");
        }
    }

    private static boolean isSingleValueSubTable(Element nestedTable) {
        Elements cells = nestedTable.select("td, th");
        if (cells.isEmpty()) return true;
        if (cells.size() == 1) return true;

        // Exactly 1 key-value pair
        if (cells.size() == 2) {
            Element c1 = cells.get(0);
            Element c2 = cells.get(1);
            boolean hasHeader = "th".equalsIgnoreCase(c1.tagName()) || "th".equalsIgnoreCase(c2.tagName());
            if (hasHeader) {
                return true;
            }
        }

        // Count non-empty cells with text/links/images
        int nonEmpty = 0;
        for (Element cell : cells) {
            if (!cell.text().trim().isEmpty() || !cell.select("img, a").isEmpty()) {
                nonEmpty++;
            }
        }
        return nonEmpty <= 1;
    }

    private static String renderSingleValueSubTable(Element nestedTable, RenderContext context) {
        Elements cells = nestedTable.select("td, th");
        if (cells.isEmpty()) return "";

        if (cells.size() == 1) {
            return renderCellInline(cells.get(0), context);
        }

        if (cells.size() == 2) {
            Element c1 = cells.get(0);
            Element c2 = cells.get(1);
            String t1 = renderCellInline(c1, context);
            String t2 = renderCellInline(c2, context);
            if (t1.isEmpty()) return t2;
            if (t2.isEmpty()) return t1;
            if ("th".equalsIgnoreCase(c1.tagName())) {
                return "**" + t1 + "**: " + t2;
            } else if ("th".equalsIgnoreCase(c2.tagName())) {
                return "**" + t2 + "**: " + t1;
            }
            return t1 + " - " + t2;
        }

        for (Element cell : cells) {
            String text = renderCellInline(cell, context);
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static class NestedTableItem {
        final Element tableElement;
        final String label;
        final String anchorId;
        final String parentAnchor;

        NestedTableItem(Element tableElement, String label, String anchorId, String parentAnchor) {
            this.tableElement = tableElement;
            this.label = label;
            this.anchorId = anchorId;
            this.parentAnchor = parentAnchor;
        }
    }

    private static String extractSubTableLabel(Element tr, Element cell, Element nestedTbl) {
        Element caption = nestedTbl.selectFirst("caption");
        if (caption != null && !caption.text().trim().isEmpty()) {
            return caption.text().trim();
        }
        Element firstTh = nestedTbl.selectFirst("th");
        if (firstTh != null && !firstTh.text().trim().isEmpty()) {
            return firstTh.text().trim();
        }
        Element rowTh = tr.selectFirst("th");
        if (rowTh != null && !rowTh.text().trim().isEmpty()) {
            return rowTh.text().trim();
        }
        String cellText = cell.ownText().trim();
        if (!cellText.isEmpty()) {
            return cellText;
        }
        return "Table Details";
    }

    private static String renderCellInline(Element cell, RenderContext context) {
        StringBuilder sb = new StringBuilder();
        RenderContext cellContext = context.copyFor(sb);
        renderChildren(cell, cellContext, 0);
        return sb.toString().trim()
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("|", "\\|")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void appendTableRow(
            StringBuilder output,
            List<String> row,
            int columnCount
    ) {
        output.append("|");

        for (int i = 0; i < columnCount; i++) {
            String value = i < row.size()
                    ? row.get(i)
                    : " ";

            output.append(" ")
                    .append(value)
                    .append(" |");
        }

        output.append("\n");
    }

    private static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Preserve newlines but collapse spaces and tabs.
        return text
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String cleanupInlineWhitespace(String text) {
        return text
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n");
    }

    private static String cleanupMarkdown(String markdown) {
        if (markdown == null) {
            return "";
        }

        String result = markdown
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return result;
    }

    private static void ensureBlankLines(
            StringBuilder output,
            int count
    ) {
        if (output.length() == 0) {
            return;
        }

        int newlines = 0;

        for (int i = output.length() - 1;
             i >= 0 && output.charAt(i) == '\n';
             i--) {
            newlines++;
        }

        while (newlines < count) {
            output.append('\n');
            newlines++;
        }
    }

    private static String slugify(String text) {
        String slug = normalizeWhitespace(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        return slug.isEmpty() ? "heading" : slug;
    }

    private static String makeUniqueId(
            String id,
            Set<String> usedIds
    ) {
        String base = slugify(id);
        String result = base;
        int counter = 2;

        while (usedIds.contains(result)) {
            result = base + "-" + counter++;
        }

        usedIds.add(result);
        return result;
    }

    private static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String removeTrailingNewlines(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\n+$", "");
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private static int parsePositiveInt(
            String value,
            int fallback
    ) {
        try {
            int parsed = Integer.parseInt(value);

            return parsed > 0 ? parsed : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static final class RenderContext {
        final String baseUrl;
        final StringBuilder output;
        final List<HeadingItem> headings;
        final Set<String> usedHeadingIds;
        final int[] tableCounter;

        RenderContext(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            this.output = new StringBuilder();
            this.headings = new ArrayList<>();
            this.usedHeadingIds = new HashSet<>();
            this.tableCounter = new int[]{0};
        }

        private RenderContext(
                String baseUrl,
                StringBuilder output,
                List<HeadingItem> headings,
                Set<String> usedHeadingIds,
                int[] tableCounter
        ) {
            this.baseUrl = baseUrl;
            this.output = output;
            this.headings = headings;
            this.usedHeadingIds = usedHeadingIds;
            this.tableCounter = tableCounter;
        }

        RenderContext copyFor(StringBuilder newOutput) {
            return new RenderContext(
                    baseUrl,
                    newOutput,
                    headings,
                    usedHeadingIds,
                    tableCounter
            );
        }
    }
}
