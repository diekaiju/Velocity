package com.example.browser.reconstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ArticleOutlineExtractor {

    public static class OutlineItem {
        public final String id;
        public final String title;
        public final int level;

        public OutlineItem(String id, String title, int level) {
            this.id = id;
            this.title = title;
            this.level = level;
        }
    }

    public static List<OutlineItem> extractOutline(LayoutNode rootNode) {
        List<OutlineItem> items = new ArrayList<>();
        if (rootNode == null) return items;
        findHeadings(rootNode, items);
        return items;
    }

    private static void findHeadings(LayoutNode node, List<OutlineItem> items) {
        if (node == null) return;
        if (node.element != null) {
            String tag = node.element.tagName().toLowerCase(Locale.ROOT);
            if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
                int level = tag.charAt(1) - '0';
                String title = node.element.text().trim();
                String id = node.element.hasAttr("id") ? node.element.attr("id") : (node.element.hasAttr("name") ? node.element.attr("name") : "");
                if (!title.isEmpty()) {
                    items.add(new OutlineItem(id, title, level));
                }
            }
        }
        for (LayoutNode child : node.children) {
            findHeadings(child, items);
        }
    }
}
