package com.example.browser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HtmlCleaner {

    private HtmlCleaner() {
    }

    public static final class Config {
        public String baseUrl = null;
        public boolean removeJavaScript = true;
        public boolean removeCss = true;
        public boolean preserveMinimalEvents = true;
        public boolean preserveMediaLinks = true;
        public boolean preserveFrameLinks = true;
        public boolean preserveCanvasPlaceholder = true;
        public boolean preserveSvgAccessibilityText = true;
        public boolean keepImages = true;
        public boolean keepForms = true;
        public boolean keepTables = true;
        public boolean removeTrackingParameters = true;
        public boolean removeAdvertisements = true;
        public boolean removeHiddenElements = false;
        public boolean removeEmptyContainers = false;
        public boolean simplifySemanticElements = true;
        public boolean removeClasses = true;
        public boolean removeStyles = true;
        public int maxNodes = 100_000;
        public int maxOutputCharacters = 0;
        public boolean keepTitle = true;
        public boolean keepDescription = true;

        public static Config defaultConfig() {
            return new Config();
        }
    }

    public static final class Result {
        public final String html;
        public final String title;

        private Result(String html, String title) {
            this.html = html;
            this.title = title;
        }
    }

    public static String clean(String html) {
        return clean(html, Config.defaultConfig()).html;
    }

    public static Result clean(String html, Config config) {
        if (html == null || html.trim().isEmpty()) {
            return new Result("", "");
        }

        if (config == null) {
            config = Config.defaultConfig();
        }

        Document document = Jsoup.parse(html, config.baseUrl == null ? "" : config.baseUrl, Parser.htmlParser());

        // Stage 1 - remove comments
        removeComments(document);

        // Stage 2 - remove scripts/styles
        if (config.removeJavaScript) {
            removeJavaScript(document);
        }
        if (config.removeCss) {
            removeCss(document);
        }

        // Stage 3 - remove useless meta/preload tags
        removeMetadataJunk(document);

        // Stage 4 - remove ads
        if (config.removeAdvertisements) {
            removeAdvertisementElements(document);
        }

        // Stage 5 - remove hidden tags
        if (config.removeHiddenElements) {
            removeHiddenElements(document);
        }

        // Stage 6 - media, svg, canvas, picture
        transformMedia(document, config);
        transformSvg(document, config);
        transformCanvas(document, config);
        transformPicture(document);

        // Stage 7 - semantic HTML
        if (config.simplifySemanticElements) {
            simplifySemanticElements(document);
        }

        // Stage 8 - modern / custom Web Components
        transformModernElements(document);

        // Stage 9 - forms
        if (config.keepForms) {
            simplifyForms(document);
        } else {
            document.select("form, input, textarea, select, option, button").remove();
        }

        // Stage 10 - links
        normalizeLinks(document, config);

        // Stage 11 - images
        if (config.keepImages) {
            normalizeImages(document, config);
        } else {
            document.select("img, picture, source").remove();
        }

        // Stage 12 - tables
        if (config.keepTables) {
            simplifyTables(document);
        }

        // Stage 13 - attributes cleanup (classes, styles, framework junk)
        cleanAttributes(document, config);

        // Stage 14 - unsupported elements (unwrap them so text content is kept)
        transformUnsupportedElements(document);

        // Stage 15 - empty elements
        if (config.removeEmptyContainers) {
            removeEmptyContainers(document);
        }

        // Stage 16 - text whitespace cleanup
        normalizeText(document);

        // Stage 17 - title
        String title = "";
        Element titleElement = document.selectFirst("title");
        if (titleElement != null) {
            title = normalizeWhitespace(titleElement.text());
        }
        if (!config.keepTitle) {
            document.select("title").remove();
        }

        String output = document.body() != null ? document.body().html() : document.html();
        if (config.maxOutputCharacters > 0 && output.length() > config.maxOutputCharacters) {
            output = output.substring(0, config.maxOutputCharacters);
        }

        return new Result(output, title);
    }

    private static void removeComments(Document document) {
        List<Comment> comments = new ArrayList<>();
        findComments(document, comments);
        for (Comment comment : comments) {
            comment.remove();
        }
    }

    private static void findComments(Node node, List<Comment> comments) {
        for (int i = 0; i < node.childNodeSize(); i++) {
            Node child = node.childNode(i);
            if (child instanceof Comment) {
                comments.add((Comment) child);
            } else {
                findComments(child, comments);
            }
        }
    }

    private static void removeJavaScript(Document document) {
        document.select("script, noscript, template").remove();
        for (Element element : document.select("[href], [src], [action]")) {
            if (isJavascriptUrl(element.attr("href"))) {
                element.removeAttr("href");
            }
            if (isJavascriptUrl(element.attr("src"))) {
                element.removeAttr("src");
            }
            if (isJavascriptUrl(element.attr("action"))) {
                element.removeAttr("action");
            }
        }
    }

    private static boolean isJavascriptUrl(String value) {
        if (value == null) return false;
        return value.trim().toLowerCase(Locale.ROOT).startsWith("javascript:");
    }

    private static void removeCss(Document document) {
        document.select("style, link[rel=stylesheet]").remove();
    }

    private static void removeMetadataJunk(Document document) {
        document.select("meta[name=viewport], meta[http-equiv=refresh], meta[property^=og:], meta[name^=twitter:], link[rel=preload], link[rel=prefetch], link[rel=preconnect], link[rel=dns-prefetch], link[rel=manifest], link[rel=icon]").remove();
    }

    private static final Set<String> AD_KEYWORDS = new HashSet<>(Arrays.asList(
            "ad", "ads", "advert", "advertisement", "advertising", "sponsor", "sponsored",
            "doubleclick", "adsbygoogle", "banner", "cookie-banner", "cookie-consent", "consent-banner", "popup-ad"
    ));

    private static void removeAdvertisementElements(Document document) {
        List<Element> candidates = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            if (containsAdKeyword(element.id()) || containsAdKeyword(element.className())) {
                candidates.add(element);
            }
        }
        for (Element element : candidates) {
            String tag = element.tagName();
            if (!tag.equals("body") && !tag.equals("html")) {
                element.remove();
            }
        }
    }

    private static boolean containsAdKeyword(String value) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        // Split ID or Class names by common delimiters (like hyphens, underscores, spaces)
        String[] tokens = normalized.split("[\\-_\\s]+");
        for (String token : tokens) {
            if (AD_KEYWORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void removeHiddenElements(Document document) {
        document.select("[hidden], [aria-hidden=true], input[type=hidden]").remove();
        for (Element element : document.getAllElements()) {
            String style = element.attr("style").toLowerCase(Locale.ROOT);
            if (style.contains("display:none") || style.contains("display: none") ||
                style.contains("visibility:hidden") || style.contains("visibility: hidden")) {
                element.remove();
            }
        }
    }

    private static void transformMedia(Document document, Config config) {
        for (Element video : new ArrayList<>(document.select("video"))) {
            String src = firstNonEmpty(video.attr("src"), findSource(video));
            Element p = new Element("p");
            if (config.preserveMediaLinks && !src.isEmpty()) {
                Element link = new Element("a").attr("href", src);
                link.text("[Video Link: click to watch]");
                p.appendChild(link);
            } else {
                p.text("[Video Component]");
            }
            video.replaceWith(p);
        }

        for (Element audio : new ArrayList<>(document.select("audio"))) {
            String src = firstNonEmpty(audio.attr("src"), findSource(audio));
            Element p = new Element("p");
            if (config.preserveMediaLinks && !src.isEmpty()) {
                Element link = new Element("a").attr("href", src);
                link.text("[Audio Link: click to listen]");
                p.appendChild(link);
            } else {
                p.text("[Audio Component]");
            }
            audio.replaceWith(p);
        }

        for (Element iframe : new ArrayList<>(document.select("iframe"))) {
            String src = iframe.attr("src");
            Element p = new Element("p");
            if (config.preserveFrameLinks && !src.isEmpty()) {
                Element link = new Element("a").attr("href", src);
                link.text("[Embedded Content Link]");
                p.appendChild(link);
            } else {
                p.text("[Embedded Content]");
            }
            iframe.replaceWith(p);
        }
    }

    private static String findSource(Element media) {
        Element source = media.selectFirst("source[src]");
        return source != null ? source.attr("src") : "";
    }

    private static void transformSvg(Document document, Config config) {
        for (Element svg : new ArrayList<>(document.select("svg"))) {
            String accText = "";
            if (config.preserveSvgAccessibilityText) {
                accText = firstNonEmpty(svg.attr("aria-label"), svg.attr("title"), svg.select("title").text());
            }
            Element span = new Element("span");
            span.text(!accText.isEmpty() ? "[" + accText + "]" : "[Icon]");
            svg.replaceWith(span);
        }
    }

    private static void transformCanvas(Document document, Config config) {
        if (!config.preserveCanvasPlaceholder) {
            document.select("canvas").remove();
            return;
        }
        for (Element canvas : new ArrayList<>(document.select("canvas"))) {
            canvas.replaceWith(new Element("span").text("[Drawing Canvas]"));
        }
    }

    private static void transformPicture(Document document) {
        for (Element picture : new ArrayList<>(document.select("picture"))) {
            Element img = picture.selectFirst("img");
            if (img != null) {
                Element source = picture.selectFirst("source[srcset]");
                if (source != null) {
                    String srcset = source.attr("srcset");
                    String bestUrl = extractUrlFromSrcset(srcset);
                    if (!bestUrl.isEmpty()) {
                        img.attr("src", bestUrl);
                    }
                } else {
                    source = picture.selectFirst("source[src]");
                    if (source != null) {
                        String src = source.attr("src");
                        if (!src.isEmpty()) {
                            img.attr("src", src);
                        }
                    }
                }
                picture.replaceWith(img.clone());
            } else {
                picture.unwrap();
            }
        }
        document.select("source").remove();
    }

    private static void simplifySemanticElements(Document document) {
        renameAndPrepend(document, "aside", "div", "[SIDEBAR]");
        renameAndPrepend(document, "main", "div", "[CONTENT]");
        document.select("section, article, figure").tagName("div");
        document.select("figcaption").tagName("p");
    }

    private static void renameAndPrepend(Document doc, String targetTag, String newTag, String marker) {
        for (Element el : doc.select(targetTag)) {
            el.tagName(newTag);
            Element label = new Element("b").text(marker);
            el.prependChild(label);
            el.prependChild(new Element("br"));
        }
    }
    private static void transformModernElements(Document document) {
        for (Element el : document.getAllElements()) {
            if (el.tagName().contains("-")) {
                el.unwrap();
            }
        }
    }

    private static void simplifyForms(Document document) {
        for (Element form : document.select("form")) {
            form.removeAttr("target").removeAttr("onsubmit");
        }
        for (Element input : document.select("input")) {
            input.removeAttr("onclick").removeAttr("onchange").removeAttr("oninput");
            String type = input.attr("type").toLowerCase(Locale.ROOT);
            if (Arrays.asList("search", "email", "url", "tel", "number", "date", "time").contains(type)) {
                input.attr("type", "text");
            }
        }
        document.select("select, option").remove();
    }

    private static void normalizeLinks(Document document, Config config) {
        for (Element element : document.select("[href]")) {
            String href = element.attr("href").trim();
            if (isJavascriptUrl(href)) {
                element.removeAttr("href");
                continue;
            }
            if (href.startsWith("#")) {
                continue;
            }
            href = resolveUrl(config.baseUrl, href);
            if (config.removeTrackingParameters) {
                href = removeTrackingParameters(href);
            }
            element.attr("href", href);
            element.removeAttr("target");
        }
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

    private static boolean isPlaceholder(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/") && lower.length() < 250) {
            return true;
        }
        return lower.contains("placeholder") || lower.contains("blank.gif") || lower.contains("pixel.gif") || lower.contains("spacer.gif") || lower.contains("trans.gif");
    }

    private static void normalizeImages(Document document, Config config) {
        for (Element img : document.select("img")) {
            String src = firstNonEmpty(
                    img.attr("src"),
                    img.attr("data-src"),
                    img.attr("data-original"),
                    img.attr("data-lazy-src")
            );

            if ((src == null || src.trim().isEmpty()) && img.hasAttr("srcset")) {
                String srcsetSrc = extractUrlFromSrcset(img.attr("srcset"));
                if (!srcsetSrc.isEmpty()) {
                    src = srcsetSrc;
                }
            }

            if (src != null && !src.trim().isEmpty()) {
                // Strip existing fragment if present before resolving
                if (src.contains("#")) {
                    src = src.substring(0, src.indexOf('#'));
                }
                src = resolveUrl(config.baseUrl, src);
                img.attr("src", src);
            }

            String alt = img.attr("alt");
            if (alt == null || alt.trim().isEmpty()) {
                img.attr("alt", "[Image]");
            }
        }
    }

    private static void simplifyTables(Document document) {
        for (Element table : document.select("table")) {
            table.removeAttr("width").removeAttr("height").removeAttr("cellspacing").removeAttr("cellpadding");
            for (Element row : table.select("tr")) {
                row.removeAttr("style");
            }
            for (Element cell : table.select("th, td")) {
                cell.removeAttr("style").removeAttr("width").removeAttr("height");
            }
        }
    }

    private static void cleanAttributes(Document document, Config config) {
        for (Element element : document.getAllElements()) {
            List<String> toRemove = new ArrayList<>();
            for (Attribute attr : element.attributes()) {
                String key = attr.getKey();
                String lower = key.toLowerCase(Locale.ROOT);
                if (config.removeClasses && lower.equals("class")) {
                    toRemove.add(key);
                } else if (config.removeStyles && lower.equals("style")) {
                    toRemove.add(key);
                } else if (lower.startsWith("on")) {
                    toRemove.add(key);
                } else if (lower.startsWith("data-react") || lower.startsWith("data-v-") || lower.startsWith("ng-")) {
                    toRemove.add(key);
                } else if (lower.equals("loading") || lower.equals("decoding") || lower.equals("fetchpriority") ||
                           lower.equals("srcset") || lower.equals("sizes") || lower.equals("data-src") ||
                           lower.equals("data-srcset") || lower.equals("data-lazy-src") || lower.equals("data-original")) {
                    toRemove.add(key);
                }
            }
            for (String key : toRemove) {
                element.removeAttr(key);
            }
        }
    }

    private static final Set<String> SUPPORTED_TAGS = new HashSet<>(Arrays.asList(
            "html", "head", "body", "title", "h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "hr", "div", "span",
            "a", "img", "strong", "b", "em", "i", "u", "small", "code", "pre", "ul", "ol", "li", "table", "thead", "tbody", "tfoot", "tr", "th", "td",
            "form", "label", "input", "textarea", "select", "option", "button", "blockquote", "details", "summary", "section"
    ));

    private static void transformUnsupportedElements(Document document) {
        List<Element> unsupported = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_TAGS.contains(tag)) {
                unsupported.add(element);
            }
        }
        Collections.reverse(unsupported);
        for (Element element : unsupported) {
            if (element.parent() != null) {
                element.unwrap();
            }
        }
    }

    private static void removeEmptyContainers(Document document) {
        boolean changed;
        do {
            changed = false;
            for (Element element : new ArrayList<>(document.select("div, span"))) {
                if (element.children().isEmpty() && element.text().trim().isEmpty()) {
                    element.remove();
                    changed = true;
                }
            }
        } while (changed);
    }

    private static void normalizeText(Document document) {
        for (TextNode text : new ArrayList<>(document.textNodes())) {
            String value = normalizeWhitespace(text.getWholeText());
            if (!value.isEmpty()) {
                text.text(value);
            }
        }
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n")
                .trim();
    }

    private static void finalCleanup(Document document) {
        // Strip out any empty head blocks or redundant html containers
    }

    private static String resolveUrl(String baseUrl, String relativeUrl) {
        if (baseUrl == null || baseUrl.isEmpty() || relativeUrl == null) {
            return relativeUrl;
        }
        try {
            String formattedRelative = relativeUrl.replace(" ", "%20");
            String formattedBase = baseUrl.replace(" ", "%20");
            URI base = new URI(formattedBase);
            return base.resolve(formattedRelative).toString();
        } catch (Exception e) {
            return relativeUrl;
        }
    }

    private static String removeTrackingParameters(String url) {
        if (url == null) return null;
        int queryStart = url.indexOf('?');
        if (queryStart == -1) return url;
        String base = url.substring(0, queryStart);
        String query = url.substring(queryStart + 1);
        String[] params = query.split("&");
        StringBuilder newQuery = new StringBuilder();
        for (String param : params) {
            String name = param.split("=")[0].toLowerCase(Locale.ROOT);
            if (name.startsWith("utm_") || name.equals("fbclid") || name.equals("gclid")) {
                continue;
            }
            if (newQuery.length() > 0) newQuery.append('&');
            newQuery.append(param);
        }
        return newQuery.length() > 0 ? base + "?" + newQuery.toString() : base;
    }

    private static boolean detectLikelySpaShell(Document document) {
        return document.select("script").size() > 5 && document.body().text().trim().length() < 100;
    }

    private static void removeLargeUnwantedStructures(Document document) {
        document.select("svg, iframe, embed, object").remove();
    }

    private static void preserveDescription(Document document) {
        Element descMeta = document.selectFirst("meta[name=description]");
        if (descMeta != null) {
            String desc = descMeta.attr("content");
            if (!desc.isEmpty()) {
                Element body = document.body();
                if (body != null) {
                    body.prependChild(new Element("p").text(desc).prependChild(new Element("b").text("[Description] ")));
                }
            }
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
