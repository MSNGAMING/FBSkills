package com.fbskills.util;

/**
 * Builds visually appealing, MiniMessage-styled text progress bars.
 */
public final class ProgressBarUtil {

    private static final String DEFAULT_FILLED_CHAR = "■";
    private static final String DEFAULT_EMPTY_CHAR = "□";

    private ProgressBarUtil() {}

    /**
     * Constructs a progress bar string formatted with MiniMessage color tags.
     */
    public static String buildBar(double ratio, int totalBars, String completedColor, String remainingColor) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int filledCount = (int) Math.round(clamped * totalBars);
        int emptyCount = totalBars - filledCount;

        StringBuilder sb = new StringBuilder("<dark_gray>[</dark_gray><")
                .append(completedColor)
                .append(">");

        sb.append(DEFAULT_FILLED_CHAR.repeat(Math.max(0, filledCount)));

        sb.append("</").append(completedColor).append("><").append(remainingColor).append(">");
        sb.append(DEFAULT_EMPTY_CHAR.repeat(Math.max(0, emptyCount)));

        sb.append("</").append(remainingColor).append("><dark_gray>]</dark_gray> <gray>")
                .append(String.format("%.1f%%", clamped * 100.0))
                .append("</gray>");

        return sb.toString();
    }

    /**
     * Default progress bar with 10 bars and emerald gradient style.
     */
    public static String buildDefaultBar(double ratio) {
        return buildBar(ratio, 10, "green", "dark_gray");
    }
}
