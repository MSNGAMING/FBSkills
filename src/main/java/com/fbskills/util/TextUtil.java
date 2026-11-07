package com.fbskills.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Text and Adventure component utilities for modern Paper/Purpur/Folia servers.
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.#");
    private static final DecimalFormat INT_FORMAT = new DecimalFormat("#,##0");

    private TextUtil() {}

    /**
     * Parses a MiniMessage formatted string into an Adventure Component.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(input);
    }

    /**
     * Parses a list of strings into a list of Components.
     */
    public static List<Component> parseList(List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(TextUtil::parse).toList();
    }

    /**
     * Converts a Component to plain text.
     */
    public static String toPlainText(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Formats decimal numbers cleanly.
     */
    public static String formatDouble(double value) {
        return NUMBER_FORMAT.format(value);
    }

    /**
     * Formats integer numbers with commas.
     */
    public static String formatInt(long value) {
        return INT_FORMAT.format(value);
    }
}
