package com.velocity.browser.reconstruction;

import android.graphics.Color;

public enum ReaderTheme {
    LIGHT("Light", Color.WHITE, Color.rgb(33, 33, 33), Color.rgb(11, 87, 208), Color.rgb(240, 244, 249), Color.rgb(196, 199, 197), Color.rgb(227, 235, 248), Color.rgb(11, 87, 208), Color.rgb(11, 87, 208), Color.WHITE),
    SEPIA("Sepia", Color.rgb(251, 240, 217), Color.rgb(59, 52, 40), Color.rgb(180, 80, 20), Color.rgb(243, 230, 205), Color.rgb(225, 210, 183), Color.rgb(236, 222, 196), Color.rgb(180, 80, 20), Color.rgb(180, 80, 20), Color.WHITE),
    OLED_DARK("OLED Dark", Color.BLACK, Color.rgb(226, 226, 230), Color.rgb(168, 199, 250), Color.rgb(24, 25, 28), Color.rgb(65, 71, 77), Color.rgb(32, 34, 38), Color.rgb(168, 199, 250), Color.rgb(168, 199, 250), Color.BLACK),
    MATERIAL_DARK("Material You Dark", Color.rgb(17, 19, 22), Color.rgb(226, 226, 230), Color.rgb(168, 199, 250), Color.rgb(33, 35, 39), Color.rgb(65, 71, 77), Color.rgb(42, 45, 50), Color.rgb(168, 199, 250), Color.rgb(168, 199, 250), Color.rgb(6, 46, 111));

    public final String displayName;
    public final int backgroundColor;
    public final int textColor;
    public final int linkColor;
    public final int cardBackgroundColor;
    public final int borderColor;
    public final int codeBackgroundColor;
    public final int accentBarColor;
    public final int buttonBackgroundColor;
    public final int buttonTextColor;

    ReaderTheme(String displayName, int backgroundColor, int textColor, int linkColor, int cardBackgroundColor, int borderColor, int codeBackgroundColor, int accentBarColor, int buttonBackgroundColor, int buttonTextColor) {
        this.displayName = displayName;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.linkColor = linkColor;
        this.cardBackgroundColor = cardBackgroundColor;
        this.borderColor = borderColor;
        this.codeBackgroundColor = codeBackgroundColor;
        this.accentBarColor = accentBarColor;
        this.buttonBackgroundColor = buttonBackgroundColor;
        this.buttonTextColor = buttonTextColor;
    }
}
