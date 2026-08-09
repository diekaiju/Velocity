package com.example.browser.reconstruction;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WebsiteReconstructionEngine {

    public static class ReconstructedPage {
        public final String html;
        public final String css;

        public ReconstructedPage(String html, String css) {
            this.html = html;
            this.css = css;
        }
    }

    public static ReconstructedPage reconstruct(String rawHtml, String baseUrl) {
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            return new ReconstructedPage("", "");
        }

        Document document = Jsoup.parse(rawHtml, baseUrl == null ? "" : baseUrl);

        // 1. Analyze and Clean DOM structure
        cleanDom(document);

        // 2. Build Layout Tree and Compute styles/layout coordinates
        LayoutNode rootLayout = buildLayoutTree(document.body());
        
        // Compute layout bounds (assume viewport width is 1080px for layout planning)
        calculateLayout(rootLayout, 0, 0, 1080);

        // 3. Generate simplified HTML and inline/stylesheet CSS
        StringBuilder htmlBuilder = new StringBuilder();
        StringBuilder cssBuilder = new StringBuilder();
        
        htmlBuilder.append("<div class=\"page\">\n");
        generateReconstructedHtml(rootLayout, htmlBuilder, cssBuilder);
        htmlBuilder.append("</div>\n");

        return new ReconstructedPage(htmlBuilder.toString(), cssBuilder.toString());
    }

    private static void cleanDom(Document document) {
        // Strip scripts, CSS styles, canvas, svg
        document.select("script, style, iframe, canvas, svg, noscript, link[rel=stylesheet]").remove();
    }

    private static LayoutNode buildLayoutTree(Element element) {
        if (element == null) return null;

        ComputedStyle style = ComputedStyle.fromInlineStyle(element.attr("style"));
        
        // Simple CSS display mapping from tag name if not specified
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if (style.display.equals("block")) {
            if (tag.equals("span") || tag.equals("a") || tag.equals("strong") || tag.equals("b") || tag.equals("em") || tag.equals("i")) {
                style.display = "inline";
            }
        }

        LayoutNode node = new LayoutNode(element, style);

        for (Element child : element.children()) {
            LayoutNode childLayout = buildLayoutTree(child);
            if (childLayout != null) {
                node.children.add(childLayout);
            }
        }

        return node;
    }

    private static void calculateLayout(LayoutNode node, int currentX, int currentY, int parentWidth) {
        node.x = currentX + node.style.marginLeft + node.style.paddingLeft;
        node.y = currentY + node.style.marginTop + node.style.paddingTop;
        
        // Determine width
        int widthConstraint = parentWidth - node.style.marginLeft - node.style.marginRight - node.style.paddingLeft - node.style.paddingRight;
        if (node.style.width > 0) {
            node.width = Math.min(node.style.width, widthConstraint);
        } else {
            node.width = widthConstraint;
        }

        // Sizing children
        int childY = node.y;
        int maxChildHeight = 0;
        int inlineX = node.x;

        for (LayoutNode child : node.children) {
            if (child.style.display.equals("block")) {
                // Return to next line for block elements
                if (inlineX > node.x) {
                    childY += maxChildHeight;
                    inlineX = node.x;
                    maxChildHeight = 0;
                }
                calculateLayout(child, node.x, childY, node.width);
                childY += child.height + child.style.marginTop + child.style.marginBottom;
            } else {
                // Inline layout flow
                if (inlineX + child.style.width > node.x + node.width) {
                    // Wrap inline elements
                    childY += maxChildHeight;
                    inlineX = node.x;
                    maxChildHeight = 0;
                }
                calculateLayout(child, inlineX, childY, node.width);
                inlineX += child.width + child.style.marginLeft + child.style.marginRight;
                maxChildHeight = Math.max(maxChildHeight, child.height + child.style.marginTop + child.style.marginBottom);
            }
        }

        // Determine node height
        if (node.style.height > 0) {
            node.height = node.style.height;
        } else {
            int totalChildrenHeight = childY - node.y;
            if (inlineX > node.x) {
                totalChildrenHeight += maxChildHeight;
            }
            node.height = Math.max(50, totalChildrenHeight + node.style.paddingTop + node.style.paddingBottom);
        }
    }

    private static void generateReconstructedHtml(LayoutNode node, StringBuilder html, StringBuilder css) {
        if (node == null) return;
        
        String tag = node.element.tagName().toLowerCase(Locale.ROOT);
        String text = "";
        
        // Keep text nodes directly
        List<Node> childNodes = node.element.childNodes();
        for (Node childNode : childNodes) {
            if (childNode instanceof TextNode) {
                text += ((TextNode) childNode).text().trim() + " ";
            }
        }
        text = text.trim();

        String classId = "reconstruct_" + Math.abs(node.element.hashCode());
        
        // CSS properties mapping
        css.append(".").append(classId).append(" {\n")
           .append("  display: ").append(node.style.display).append(";\n");
        if (node.width > 0) {
            css.append("  width: ").append(node.width).append("px;\n");
        }
        if (node.height > 0) {
            css.append("  height: ").append(node.height).append("px;\n");
        }
        if (node.style.marginTop > 0) css.append("  margin-top: ").append(node.style.marginTop).append("px;\n");
        if (node.style.marginRight > 0) css.append("  margin-right: ").append(node.style.marginRight).append("px;\n");
        if (node.style.marginBottom > 0) css.append("  margin-bottom: ").append(node.style.marginBottom).append("px;\n");
        if (node.style.marginLeft > 0) css.append("  margin-left: ").append(node.style.marginLeft).append("px;\n");
        
        if (node.style.paddingTop > 0) css.append("  padding-top: ").append(node.style.paddingTop).append("px;\n");
        if (node.style.paddingRight > 0) css.append("  padding-right: ").append(node.style.paddingRight).append("px;\n");
        if (node.style.paddingBottom > 0) css.append("  padding-bottom: ").append(node.style.paddingBottom).append("px;\n");
        if (node.style.paddingLeft > 0) css.append("  padding-left: ").append(node.style.paddingLeft).append("px;\n");
        
        if (node.style.color != null && !node.style.color.isEmpty()) {
            css.append("  color: ").append(node.style.color).append(";\n");
        }
        if (node.style.backgroundColor != null && !node.style.backgroundColor.isEmpty()) {
            css.append("  background-color: ").append(node.style.backgroundColor).append(";\n");
        }
        css.append("  font-size: ").append(node.style.fontSize).append("px;\n");
        css.append("}\n");

        if (tag.equals("img")) {
            String src = node.element.attr("src");
            String alt = node.element.attr("alt");
            html.append("<img class=\"").append(classId).append("\" src=\"").append(src).append("\" alt=\"").append(alt).append("\" />\n");
        } else if (tag.equals("a")) {
            String href = node.element.attr("href");
            html.append("<a class=\"").append(classId).append("\" href=\"").append(href).append("\">");
            if (!text.isEmpty()) {
                html.append(text);
            }
            for (LayoutNode child : node.children) {
                generateReconstructedHtml(child, html, css);
            }
            html.append("</a>\n");
        } else if (tag.equals("input")) {
            String type = node.element.attr("type");
            String value = node.element.attr("value");
            html.append("<input class=\"").append(classId).append("\" type=\"").append(type).append("\" value=\"").append(value).append("\" />\n");
        } else {
            // Reconstruct containers as basic div elements
            html.append("<div class=\"").append(classId).append("\">\n");
            if (!text.isEmpty()) {
                html.append("<span style=\"font-size: ").append(node.style.fontSize).append("px;\">").append(text).append("</span>\n");
            }
            for (LayoutNode child : node.children) {
                generateReconstructedHtml(child, html, css);
            }
            html.append("</div>\n");
        }
    }
}
