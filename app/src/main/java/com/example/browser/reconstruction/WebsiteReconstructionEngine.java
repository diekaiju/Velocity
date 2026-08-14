package com.example.browser.reconstruction;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WebsiteReconstructionEngine {

    public static class ReconstructedPage {
        public final LayoutNode rootLayout;
        public final String html;
        public final String css;
        public final Map<String, Integer> anchorMap;

        public ReconstructedPage(LayoutNode rootLayout, String html, String css, Map<String, Integer> anchorMap) {
            this.rootLayout = rootLayout;
            this.html = html;
            this.css = css;
            this.anchorMap = anchorMap;
        }
    }

    public static ReconstructedPage reconstruct(String rawHtml, String baseUrl) {
        if (rawHtml == null || rawHtml.trim().isEmpty()) {
            return new ReconstructedPage(null, "", "", new HashMap<>());
        }

        Document document = Jsoup.parse(rawHtml, baseUrl == null ? "" : baseUrl);

        // 1. Analyze and Clean DOM structure
        cleanDom(document);

        // Collect anchor target IDs and heading IDs
        Set<String> anchorIds = new HashSet<>();
        for (Element link : document.select("a[href^='#']")) {
            String href = link.attr("href");
            if (href.length() > 1) {
                anchorIds.add(href.substring(1));
            }
        }

        int headingIdx = 0;
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
            String id = heading.attr("id");
            if (id.isEmpty()) {
                id = heading.attr("name");
            }
            if (id.isEmpty()) {
                id = "toc_heading_" + (headingIdx++);
                heading.attr("id", id);
            }
            anchorIds.add(id);
        }

        // 2. Build Layout Tree and Compute styles/layout coordinates
        LayoutNode rootLayout = buildLayoutTree(document.body());
        
        // Map anchors to their Y coordinates during layout
        Map<String, Integer> anchorMap = new HashMap<>();

        // Compute layout bounds (assume viewport width is 1080px for layout planning)
        calculateLayout(rootLayout, 0, 0, 1080, anchorIds, anchorMap);

        // 3. Generate simplified HTML and inline/stylesheet CSS
        StringBuilder htmlBuilder = new StringBuilder();
        StringBuilder cssBuilder = new StringBuilder();
        
        htmlBuilder.append("<div class=\"page\">\n");
        generateReconstructedHtml(rootLayout, htmlBuilder, cssBuilder, anchorIds);
        htmlBuilder.append("</div>\n");

        return new ReconstructedPage(rootLayout, htmlBuilder.toString(), cssBuilder.toString(), anchorMap);
    }

    private static void cleanDom(Document document) {
        // Strip scripts, CSS styles, canvas, svg
        document.select("script, style, iframe, canvas, svg, noscript, link[rel=stylesheet]").remove();
        
        // Strip sidebars, ads, social lists
        document.select("aside, [id*=sidebar], [id*=ad], [class*=sidebar], [class*=ad], [class*=social]").remove();
    }

    private static LayoutNode buildLayoutTree(Element element) {
        if (element == null) return null;

        ComputedStyle style = ComputedStyle.fromInlineStyle(element.attr("style"));
        
        // Simple CSS display mapping from tag name if not specified
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if (style.display.equals("block")) {
            if (tag.equals("span") || tag.equals("a") || tag.equals("strong") || tag.equals("b") || tag.equals("em") || tag.equals("i") || tag.equals("code")) {
                style.display = "inline";
            }
        }

        if (tag.equals("img") || tag.equals("image")) {
            if (style.width <= 0 && element.hasAttr("width")) {
                try {
                    String wAttr = element.attr("width").replaceAll("[^0-9]", "");
                    if (!wAttr.isEmpty()) {
                        style.width = Integer.parseInt(wAttr);
                    }
                } catch (Exception ignored) {}
            }
            if (style.height <= 0 && element.hasAttr("height")) {
                try {
                    String hAttr = element.attr("height").replaceAll("[^0-9]", "");
                    if (!hAttr.isEmpty()) {
                        style.height = Integer.parseInt(hAttr);
                    }
                } catch (Exception ignored) {}
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

    private static List<LayoutNode> getTableRows(LayoutNode tableNode) {
        List<LayoutNode> rows = new ArrayList<>();
        findRowsRecursive(tableNode, rows);
        return rows;
    }

    private static void findRowsRecursive(LayoutNode node, List<LayoutNode> rows) {
        String tag = node.element.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("tr")) {
            rows.add(node);
            return;
        }
        for (LayoutNode child : node.children) {
            findRowsRecursive(child, rows);
        }
    }

    private static List<LayoutNode> getRowCells(LayoutNode rowNode) {
        List<LayoutNode> cells = new ArrayList<>();
        for (LayoutNode child : rowNode.children) {
            String tag = child.element.tagName().toLowerCase(Locale.ROOT);
            if (tag.equals("td") || tag.equals("th")) {
                cells.add(child);
            }
        }
        return cells;
    }

    private static void layoutTable(LayoutNode tableNode, int parentWidth, Set<String> anchorIds, Map<String, Integer> anchorMap) {
        List<LayoutNode> rows = getTableRows(tableNode);
        int maxCols = 0;
        for (LayoutNode row : rows) {
            maxCols = Math.max(maxCols, getRowCells(row).size());
        }
        
        int availableWidth = parentWidth - tableNode.style.marginLeft - tableNode.style.marginRight - tableNode.style.paddingLeft - tableNode.style.paddingRight;
        int colWidth = maxCols > 0 ? availableWidth / maxCols : availableWidth;

        // Register anchor if the table itself has one
        registerAnchor(tableNode, anchorIds, anchorMap);

        int rowY = tableNode.y;
        for (LayoutNode row : rows) {
            registerAnchor(row, anchorIds, anchorMap);
            List<LayoutNode> cells = getRowCells(row);
            int columnX = 0;
            int rowHeight = 0;

            for (int col = 0; col < cells.size(); col++) {
                LayoutNode cell = cells.get(col);
                registerAnchor(cell, anchorIds, anchorMap);
                cell.x = tableNode.x + columnX;
                cell.y = rowY;
                cell.width = colWidth;

                calculateLayout(cell, cell.x, cell.y, colWidth, anchorIds, anchorMap);
                rowHeight = Math.max(rowHeight, cell.height);
                columnX += colWidth;
            }

            for (LayoutNode cell : cells) {
                cell.height = rowHeight;
            }

            row.x = tableNode.x;
            row.y = rowY;
            row.width = availableWidth;
            row.height = rowHeight;

            rowY += rowHeight;
        }

        tableNode.width = availableWidth;
        tableNode.height = Math.max(50, rowY - tableNode.y);
    }

    private static void registerAnchor(LayoutNode node, Set<String> anchorIds, Map<String, Integer> anchorMap) {
        String elementId = "";
        if (node.element.hasAttr("id")) {
            elementId = node.element.attr("id");
        } else if (node.element.hasAttr("name")) {
            elementId = node.element.attr("name");
        }
        if (!elementId.isEmpty() && anchorIds.contains(elementId)) {
            anchorMap.put(elementId, node.y);
        }
    }

    private static void calculateLayout(LayoutNode node, int currentX, int currentY, int parentWidth, Set<String> anchorIds, Map<String, Integer> anchorMap) {
        node.x = currentX + node.style.marginLeft + node.style.paddingLeft;
        node.y = currentY + node.style.marginTop + node.style.paddingTop;

        registerAnchor(node, anchorIds, anchorMap);

        String tag = node.element.tagName().toLowerCase(Locale.ROOT);
        if (tag.equals("table")) {
            layoutTable(node, parentWidth, anchorIds, anchorMap);
            return;
        }
        
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
            if (child.style.display.equals("block") || child.element.tagName().toLowerCase(Locale.ROOT).equals("tr") || child.element.tagName().toLowerCase(Locale.ROOT).equals("li")) {
                // Return to next line for block elements
                if (inlineX > node.x) {
                    childY += maxChildHeight;
                    inlineX = node.x;
                    maxChildHeight = 0;
                }
                calculateLayout(child, node.x, childY, node.width, anchorIds, anchorMap);
                childY += child.height + child.style.marginTop + child.style.marginBottom;
            } else {
                // Inline layout flow
                if (inlineX + child.style.width > node.x + node.width) {
                    // Wrap inline elements
                    childY += maxChildHeight;
                    inlineX = node.x;
                    maxChildHeight = 0;
                }
                calculateLayout(child, inlineX, childY, node.width, anchorIds, anchorMap);
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
            node.height = Math.max(tag.equals("hr") ? 2 : 20, totalChildrenHeight + node.style.paddingTop + node.style.paddingBottom);
        }
    }

    private static final Set<String> STRUCTURAL_TAGS = new HashSet<>(Arrays.asList(
        "table", "thead", "tbody", "tfoot", "tr", "th", "td",
        "ul", "ol", "li", "blockquote", "pre", "code", "hr",
        "details", "summary", "form", "button", "label", "textarea", "select", "option",
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "span", "section", "a", "img", "input",
        "header", "nav", "footer"
    ));

    private static void generateReconstructedHtml(LayoutNode node, StringBuilder html, StringBuilder css, Set<String> anchorIds) {
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
        css.append(".").append(classId).append(" {\n");
        if (tag.equals("table")) {
            css.append("  display: table;\n");
            css.append("  border-collapse: collapse;\n");
        } else if (tag.equals("tr")) {
            css.append("  display: table-row;\n");
        } else if (tag.equals("td") || tag.equals("th")) {
            css.append("  display: table-cell;\n");
            css.append("  border: 1px solid #ccc;\n");
            css.append("  padding: 6px;\n");
            if (tag.equals("th")) {
                css.append("  font-weight: bold;\n");
                css.append("  background-color: #f2f2f2;\n");
            }
        } else if (tag.equals("ul")) {
            css.append("  display: block;\n");
            css.append("  list-style-type: disc;\n");
            css.append("  padding-left: 24px;\n");
        } else if (tag.equals("ol")) {
            css.append("  display: block;\n");
            css.append("  list-style-type: decimal;\n");
            css.append("  padding-left: 24px;\n");
        } else if (tag.equals("li")) {
            css.append("  display: list-item;\n");
        } else if (tag.equals("blockquote")) {
            css.append("  display: block;\n");
            css.append("  border-left: 4px solid #ccc;\n");
            css.append("  margin-left: 20px;\n");
            css.append("  padding-left: 10px;\n");
            css.append("  color: #666;\n");
        } else if (tag.equals("pre")) {
            css.append("  display: block;\n");
            css.append("  background-color: #f4f4f4;\n");
            css.append("  padding: 8px;\n");
            css.append("  font-family: monospace;\n");
            css.append("  white-space: pre-wrap;\n");
        } else if (tag.equals("code")) {
            css.append("  display: inline;\n");
            css.append("  background-color: #f4f4f4;\n");
            css.append("  font-family: monospace;\n");
        } else {
            css.append("  display: ").append(node.style.display).append(";\n");
        }

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

        boolean isStructural = STRUCTURAL_TAGS.contains(tag);
        String outputTag = isStructural ? tag : "div";

        html.append("<").append(outputTag).append(" class=\"").append(classId).append("\"");

        String elementId = "";
        if (node.element.hasAttr("id")) {
            elementId = node.element.attr("id");
        } else if (node.element.hasAttr("name")) {
            elementId = node.element.attr("name");
        }
        if (!elementId.isEmpty() && anchorIds.contains(elementId)) {
            html.append(" id=\"").append(elementId).append("\"");
        }

        if (outputTag.equals("img")) {
            String src = node.element.attr("src");
            String alt = node.element.attr("alt");
            html.append(" src=\"").append(src).append("\" alt=\"").append(alt).append("\" />\n");
        } else if (outputTag.equals("a")) {
            String href = node.element.attr("href");
            html.append(" href=\"").append(href).append("\">");
            if (!text.isEmpty()) {
                html.append(text);
            }
            for (LayoutNode child : node.children) {
                generateReconstructedHtml(child, html, css, anchorIds);
            }
            html.append("</a>\n");
        } else if (outputTag.equals("input")) {
            String type = node.element.attr("type");
            String value = node.element.attr("value");
            html.append(" type=\"").append(type).append("\" value=\"").append(value).append("\" />\n");
        } else if (outputTag.equals("hr")) {
            html.append(" />\n");
        } else {
            html.append(">\n");
            if (!text.isEmpty()) {
                if (outputTag.equals("span") || outputTag.equals("code") || outputTag.equals("pre")) {
                    html.append(text);
                } else {
                    html.append("<span style=\"font-size: ").append(node.style.fontSize).append("px;\">").append(text).append("</span>\n");
                }
            }
            for (LayoutNode child : node.children) {
                generateReconstructedHtml(child, html, css, anchorIds);
            }
            html.append("</").append(outputTag).append(">\n");
        }
    }
}
