package com.example.browser.reconstruction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ComputedStyle {
    public String display = "block";
    public String position = "static";
    public int width = -1; // -1 means auto/wrap_content
    public int height = -1;
    public int marginTop = 0;
    public int marginRight = 0;
    public int marginBottom = 0;
    public int marginLeft = 0;
    public int paddingTop = 0;
    public int paddingRight = 0;
    public int paddingBottom = 0;
    public int paddingLeft = 0;
    public String color = "#000000";
    public String backgroundColor = "transparent";
    public float fontSize = 14f;
    public int fontWeight = 400; // normal

    public static ComputedStyle fromInlineStyle(String styleAttr) {
        ComputedStyle style = new ComputedStyle();
        if (styleAttr == null || styleAttr.trim().isEmpty()) {
            return style;
        }
        Map<String, String> properties = parseInlineStyle(styleAttr);
        
        if (properties.containsKey("display")) {
            style.display = properties.get("display").toLowerCase(Locale.ROOT);
        }
        if (properties.containsKey("position")) {
            style.position = properties.get("position").toLowerCase(Locale.ROOT);
        }
        if (properties.containsKey("width")) {
            style.width = parseDimension(properties.get("width"));
        }
        if (properties.containsKey("height")) {
            style.height = parseDimension(properties.get("height"));
        }
        if (properties.containsKey("margin")) {
            parseQuad(properties.get("margin"), style, true);
        }
        if (properties.containsKey("margin-top")) style.marginTop = parseDimension(properties.get("margin-top"));
        if (properties.containsKey("margin-right")) style.marginRight = parseDimension(properties.get("margin-right"));
        if (properties.containsKey("margin-bottom")) style.marginBottom = parseDimension(properties.get("margin-bottom"));
        if (properties.containsKey("margin-left")) style.marginLeft = parseDimension(properties.get("margin-left"));
        
        if (properties.containsKey("padding")) {
            parseQuad(properties.get("padding"), style, false);
        }
        if (properties.containsKey("padding-top")) style.paddingTop = parseDimension(properties.get("padding-top"));
        if (properties.containsKey("padding-right")) style.paddingRight = parseDimension(properties.get("padding-right"));
        if (properties.containsKey("padding-bottom")) style.paddingBottom = parseDimension(properties.get("padding-bottom"));
        if (properties.containsKey("padding-left")) style.paddingLeft = parseDimension(properties.get("padding-left"));
        
        if (properties.containsKey("color")) {
            style.color = properties.get("color");
        }
        if (properties.containsKey("background-color")) {
            style.backgroundColor = properties.get("background-color");
        }
        if (properties.containsKey("font-size")) {
            style.fontSize = parseFontSize(properties.get("font-size"));
        }
        if (properties.containsKey("font-weight")) {
            style.fontWeight = parseFontWeight(properties.get("font-weight"));
        }
        
        return style;
    }

    private static Map<String, String> parseInlineStyle(String styleAttr) {
        Map<String, String> map = new HashMap<>();
        String[] declarations = styleAttr.split(";");
        for (String dec : declarations) {
            int colon = dec.indexOf(':');
            if (colon != -1) {
                String key = dec.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String val = dec.substring(colon + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    private static int parseDimension(String value) {
        if (value == null || value.isEmpty()) return 0;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.equals("auto")) return -1;
        if (clean.endsWith("px")) {
            try {
                return (int) Float.parseFloat(clean.substring(0, clean.length() - 2));
            } catch (Exception ignored) {}
        }
        try {
            return (int) Float.parseFloat(clean.replaceAll("[^0-9.-]", ""));
        } catch (Exception ignored) {}
        return 0;
    }

    private static float parseFontSize(String value) {
        if (value == null || value.isEmpty()) return 14f;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.endsWith("px")) {
            try {
                return Float.parseFloat(clean.substring(0, clean.length() - 2));
            } catch (Exception ignored) {}
        }
        if (clean.endsWith("em") || clean.endsWith("rem")) {
            try {
                return Float.parseFloat(clean.substring(0, clean.length() - 2)) * 14f;
            } catch (Exception ignored) {}
        }
        try {
            return Float.parseFloat(clean.replaceAll("[^0-9.-]", ""));
        } catch (Exception ignored) {}
        return 14f;
    }

    private static int parseFontWeight(String value) {
        if (value == null || value.isEmpty()) return 400;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.equals("bold")) return 700;
        if (clean.equals("normal")) return 400;
        try {
            return Integer.parseInt(clean.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {}
        return 400;
    }

    private static void parseQuad(String value, ComputedStyle style, boolean isMargin) {
        if (value == null || value.isEmpty()) return;
        String[] tokens = value.trim().split("\\s+");
        int t = 0, r = 0, b = 0, l = 0;
        try {
            if (tokens.length == 1) {
                t = r = b = l = parseDimension(tokens[0]);
            } else if (tokens.length == 2) {
                t = b = parseDimension(tokens[0]);
                r = l = parseDimension(tokens[1]);
            } else if (tokens.length == 3) {
                t = parseDimension(tokens[0]);
                r = l = parseDimension(tokens[1]);
                b = parseDimension(tokens[2]);
            } else if (tokens.length >= 4) {
                t = parseDimension(tokens[0]);
                r = parseDimension(tokens[1]);
                b = parseDimension(tokens[2]);
                l = parseDimension(tokens[3]);
            }
        } catch (Exception ignored) {}
        
        if (isMargin) {
            style.marginTop = t;
            style.marginRight = r;
            style.marginBottom = b;
            style.marginLeft = l;
        } else {
            style.paddingTop = t;
            style.paddingRight = r;
            style.paddingBottom = b;
            style.paddingLeft = l;
        }
    }
}
