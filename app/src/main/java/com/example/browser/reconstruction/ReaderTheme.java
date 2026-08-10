package com.example.browser.reconstruction;

import android.graphics.Color;

public enum ReaderTheme {
    LIGHT("Light", Color.WHITE, Color.rgb(33, 33, 33), Color.rgb(25, 118, 210), Color.rgb(245, 247, 250), Color.rgb(218, 220, 224)),
    SEPIA("Sepia", Color.rgb(251, 240, 217), Color.rgb(59, 52, 40), Color.rgb(180, 80, 20), Color.rgb(243, 230, 205), Color.rgb(225, 210, 183)),
    OLED_DARK("OLED Dark", Color.BLACK, Color.rgb(230, 225, 229), Color.rgb(144, 202, 249), Color.rgb(24, 24, 24), Color.rgb(45, 45, 45)),
    MATERIAL_DARK("M3 Dark", Color.rgb(28, 27, 31), Color.rgb(230, 225, 229), Color.rgb(208, 188, 255), Color.rgb(43, 41, 48), Color.rgb(73, 69, 79));
    // MATERIAL_YOU("Material You Dynamic", Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT);

    public final String displayName;
    public final int backgroundColor;
    public final int textColor;
    public final int linkColor;
    public final int cardBackgroundColor;
    public final int borderColor;

    ReaderTheme(String displayName, int backgroundColor, int textColor, int linkColor, int cardBackgroundColor, int borderColor) {
        this.displayName = displayName;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.linkColor = linkColor;
        this.cardBackgroundColor = cardBackgroundColor;
        this.borderColor = borderColor;
    }
}
