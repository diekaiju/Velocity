package com.example.browser.reconstruction;

import org.jsoup.nodes.Element;
import java.util.ArrayList;
import java.util.List;

public class LayoutNode {
    public final Element element;
    public final ComputedStyle style;
    public int x = 0;
    public int y = 0;
    public int width = 0;
    public int height = 0;
    public final List<LayoutNode> children = new ArrayList<>();

    public LayoutNode(Element element, ComputedStyle style) {
        this.element = element;
        this.style = style;
    }
}
