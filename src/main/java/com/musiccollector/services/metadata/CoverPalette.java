package com.musiccollector.services.metadata;

/**
 * The result of sampling a sleeve: its dominant tone, an accent drawn from the same
 * artwork, and the dominant tone's WCAG relative luminance.
 */
public record CoverPalette(String dominantColor, String accentColor, double luminance) {

    /**
     * Turn 3 of the design deck: a sleeve darker than this gets the dark chrome, anything
     * lighter gets the light one. Kept here rather than in the clients so web and mobile
     * cannot drift apart on where the line sits.
     */
    public static final double DARK_CHROME_THRESHOLD = 0.55;

    public boolean dark() {
        return luminance < DARK_CHROME_THRESHOLD;
    }
}
