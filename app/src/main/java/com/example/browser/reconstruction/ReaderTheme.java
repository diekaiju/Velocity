package com.example.browser.reconstruction;

import android.graphics.Color;

public enum ReaderTheme {
    LIGHT("Light", Color.WHITE, Color.rgb(33, 33, 33), Color.rgb(103, 80, 164), Color.rgb(247, 243, 249), Color.rgb(202, 196, 206), Color.rgb(234, 221, 255), Color.rgb(103, 80, 164), Color.rgb(103, 80, 164), Color.WHITE),
    SEPIA("Sepia", Color.rgb(251, 240, 217), Color.rgb(59, 52, 40), Color.rgb(180, 80, 20), Color.rgb(243, 230, 205), Color.rgb(225, 210, 183), Color.rgb(236, 222, 196), Color.rgb(180, 80, 20), Color.rgb(180, 80, 20), Color.WHITE),
    OLED_DARK("OLED Dark", Color.BLACK, Color.rgb(230, 225, 229), Color.rgb(208, 188, 255), Color.rgb(28, 27, 31), Color.rgb(73, 69, 79), Color.rgb(36, 34, 40), Color.rgb(208, 188, 255), Color.rgb(208, 188, 255), Color.BLACK),
    MATERIAL_DARK("M3 Dark", Color.rgb(28, 27, 31), Color.rgb(230, 225, 229), Color.rgb(208, 188, 255), Color.rgb(43, 41, 48), Color.rgb(73, 69, 79), Color.rgb(54, 52, 59), Color.rgb(208, 188, 255), Color.rgb(208, 188, 255), Color.rgb(56, 30, 114));

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
