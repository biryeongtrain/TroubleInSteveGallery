package kim.biryeong.ttt.util;

import eu.pb4.mapcanvas.api.font.DefaultFonts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public final class TextUncenterer {
    private static final Identifier DEFAULT_FONT_ID = Identifier.ofVanilla("default");
    private static final int FONT_SIZE = 8;

    private TextUncenterer() {
        throw new IllegalStateException("Utility class");
    }

    public static List<Text> getLeftAligned(Text text, int width, String language) {
        return getAligned(text, width, language, MutableText::append);
    }

    public static List<Text> getRightAligned(Text text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> Text.empty().append(filler).append(value));
    }

    public static List<Text> splitLines(Text text, int width, String language) {
        return getAligned(text, width, language, (value, filler) -> value);
    }

    private static List<Text> getAligned(
            Text text,
            int width,
            String language,
            BiFunction<MutableText, Text, Text> merger
    ) {
        // Kept for API parity with upstream body signature; 1.21.8 backport does not apply locale shaping here.
        if (language == null || language.isEmpty()) {
            language = "en_us";
        }

        int normalizedWidth = Math.max(0, width);
        List<MutableText> splitLines = splitStyledLines(text);
        List<Text> result = new ArrayList<>(splitLines.size());

        for (MutableText line : splitLines) {
            int lineWidth = getWidth(line);
            int fillerWidth = Math.max(0, normalizedWidth - lineWidth);
            result.add(merger.apply(line, filler(fillerWidth)));
        }

        return List.copyOf(result);
    }

    private static List<MutableText> splitStyledLines(Text text) {
        List<MutableText> lines = new ArrayList<>();
        lines.add(Text.empty());

        text.visit((style, string) -> {
            int startIndex = 0;
            while (true) {
                int newLineIndex = string.indexOf('\n', startIndex);
                String part = newLineIndex == -1
                        ? string.substring(startIndex)
                        : string.substring(startIndex, newLineIndex);

                if (!part.isEmpty()) {
                    lines.getLast().append(Text.literal(part).setStyle(style));
                }

                if (newLineIndex == -1) {
                    break;
                }

                lines.add(Text.empty());
                startIndex = newLineIndex + 1;
            }
            return Optional.empty();
        }, Style.EMPTY);

        return lines;
    }

    private static int getWidth(Text text) {
        final int[] totalWidth = {0};
        text.visit((style, string) -> {
            if (!string.isEmpty()) {
                totalWidth[0] += getTextWidth(style, string);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return totalWidth[0];
    }

    private static int getTextWidth(Style style, String string) {
        Identifier fontId = style.getFont() == null ? DEFAULT_FONT_ID : style.getFont();
        return DefaultFonts.REGISTRY.getDefaultedFont(fontId).getTextWidth(string, FONT_SIZE);
    }

    public static Text filler(int width) {
        if (width <= 0) {
            return Text.empty();
        }

        int spaceWidth = Math.max(1, DefaultFonts.REGISTRY.getDefaultedFont(DEFAULT_FONT_ID).getTextWidth(" ", FONT_SIZE));
        int spaces = (int) Math.ceil((double) width / spaceWidth);
        return Text.literal(" ".repeat(spaces));
    }
}
