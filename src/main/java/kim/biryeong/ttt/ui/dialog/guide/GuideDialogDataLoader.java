package kim.biryeong.ttt.ui.dialog.guide;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GuideDialogDataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideDialogDataLoader.class);
    private static final String BASIC_PAGE_PATH = "guide/basic";
    private static final String INNOCENT_PAGE_PATH = "guide/innocent";
    private static final String TRAITOR_PAGE_PATH = "guide/traitor";
    private static final String DETECTIVE_PAGE_PATH = "guide/detective";
    private static final String UPDATE_PAGE_PATH = "guide/update";
    private static final String TEXT_EXTENSION = ".txt";
    private static final String LEGACY_JSON_EXTENSION = ".json";
    private static final String PAGE_INFO_SECTION = "pageinfo";
    private static final String NEWLINE_MARKER = "<nl>";
    private static final String DOUBLE_NEWLINE_MARKER = "<nl2>";
    private static volatile List<BasicPage> basicPages = List.of();
    private static volatile List<BasicPage> innocentPages = List.of();
    private static volatile List<BasicPage> traitorPages = List.of();
    private static volatile List<BasicPage> detectivePages = List.of();
    private static volatile List<BasicPage> updatePages = List.of();
    private static boolean initialized;

    private GuideDialogDataLoader() {
        throw new IllegalStateException("Utility class");
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerLifecycleEvents.SERVER_STARTED.register(GuideDialogDataLoader::reload);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> reload(server));
    }

    public static List<BasicPage> getBasicPages() {
        return basicPages;
    }

    public static List<BasicPage> getInnocentPages() {
        return innocentPages;
    }

    public static List<BasicPage> getTraitorPages() {
        return traitorPages;
    }

    public static List<BasicPage> getDetectivePages() {
        return detectivePages;
    }

    public static List<BasicPage> getUpdatePages() {
        return updatePages;
    }

    private static void reload(MinecraftServer server) {
        basicPages = loadPages(server, BASIC_PAGE_PATH);
        innocentPages = loadPages(server, INNOCENT_PAGE_PATH);
        traitorPages = loadPages(server, TRAITOR_PAGE_PATH);
        detectivePages = loadPages(server, DETECTIVE_PAGE_PATH);
        updatePages = loadPages(server, UPDATE_PAGE_PATH);

        LOGGER.info(
                "Loaded guide pages from datapacks - basic: {}, innocent: {}, traitor: {}, detective: {}, update: {}.",
                basicPages.size(),
                innocentPages.size(),
                traitorPages.size(),
                detectivePages.size(),
                updatePages.size()
        );
    }

    private static List<BasicPage> loadPages(MinecraftServer server, String path) {
        Map<Identifier, Resource> resources = server.getResourceManager().findResources(path, identifier -> {
            String resourcePath = identifier.getPath();
            return resourcePath.endsWith(TEXT_EXTENSION) || resourcePath.endsWith(LEGACY_JSON_EXTENSION);
        });

        List<BasicPage> loadedPages = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Identifier, Resource> entry) -> entry.getKey().toString()))
                .forEach(entry -> parseBasicPage(entry.getKey(), entry.getValue(), loadedPages));
        return List.copyOf(loadedPages);
    }

    private static void parseBasicPage(
            Identifier resourceId,
            Resource resource,
            List<BasicPage> output
    ) {
        String path = resourceId.getPath();
        if (path.endsWith(TEXT_EXTENSION)) {
            parseTextBasicPage(resourceId, resource, output);
            return;
        }

        parseLegacyJsonBasicPage(resourceId, resource, output);
    }

    private static void parseTextBasicPage(
            Identifier resourceId,
            Resource resource,
            List<BasicPage> output
    ) {
        try (var input = resource.getInputStream()) {
            String page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            parseTextPage(resourceId, page).ifPresent(output::add);
        } catch (Exception exception) {
            LOGGER.warn("Failed to read txt guide page resource '{}'.", resourceId, exception);
        }
    }

    private static Optional<BasicPage> parseTextPage(Identifier resourceId, String page) {
        PageDraft draft = new PageDraft();

        String normalizedPage = normalizeTextPage(page);
        StringBuilder body = new StringBuilder();
        String previousLine = "";
        SectionType sectionType = SectionType.BODY;

        for (String line : normalizedPage.lines().toList()) {
            String compactLower = compactLower(line);

            if (compactLower.startsWith("###section:")) {
                String sectionName = compactLower.substring("###section:".length());
                if (PAGE_INFO_SECTION.equals(sectionName)) {
                    sectionType = SectionType.PAGE_INFO;
                }
                continue;
            }
            if (compactLower.startsWith("###endsection")) {
                sectionType = SectionType.BODY;
                continue;
            }

            if (sectionType == SectionType.PAGE_INFO) {
                parsePageInfoLine(resourceId, line, draft);
                continue;
            }

            if (compactLower.startsWith("###image:")) {
                Optional<ImageDirective> imageDirective = parseImageDirective(resourceId, directiveValue(line));
                if (imageDirective.isPresent()) {
                    ImageDirective directive = imageDirective.orElseThrow();
                    appendImageLine(body, directive.image(), directive.description());
                    previousLine = NEWLINE_MARKER;
                }
                continue;
            }
            if (compactLower.startsWith("###itemalign:")) {
                parseAlignValue(directiveValue(line)).ifPresent(parsed -> draft.itemAlign = parsed);
                continue;
            }
            if (compactLower.startsWith("###item:")) {
                Optional<ItemDirective> itemDirective = parseItemDirective(resourceId, directiveValue(line), draft.itemAlign);
                if (itemDirective.isPresent()) {
                    ItemDirective directive = itemDirective.orElseThrow();
                    appendItemLine(body, directive.item(), directive.description(), directive.align());
                    previousLine = NEWLINE_MARKER;
                }
                continue;
            }
            if (compactLower.startsWith("###align:")) {
                parseAlignValue(directiveValue(line)).ifPresent(parsed -> draft.align = parsed);
                continue;
            }
            if (compactLower.startsWith("###header:")) {
                String header = directiveValue(line);
                if (!header.isBlank()) {
                    if (draft.title.isBlank()) {
                        draft.title = header.strip();
                    } else {
                        appendHeaderLine(body, header.strip());
                        previousLine = NEWLINE_MARKER;
                    }
                }
                continue;
            }
            if (compactLower.startsWith("###")) {
                // Keep parser-compatible behavior by safely ignoring unsupported directives.
                continue;
            }

            appendBodyLine(body, line, previousLine);
            previousLine = line;
        }

        return Optional.of(new BasicPage(
                draft.title.strip(),
                convertBodyToLines(body.toString()),
                draft.image,
                draft.imageDescription,
                draft.align
        ));
    }

    private static void parsePageInfoLine(
            Identifier resourceId,
            String line,
            PageDraft draft
    ) {
        String[] split = line.split("=", 2);
        if (split.length != 2) {
            return;
        }

        String key = split[0].strip().toLowerCase(Locale.ROOT);
        String value = split[1].strip();
        if (value.isEmpty()) {
            return;
        }

        switch (key) {
            case "title" -> draft.title = value;
            case "image" -> parseImageIdentifier(resourceId, value).ifPresent(parsed -> draft.image = Optional.of(parsed));
            case "image_description", "imagedescription" -> draft.imageDescription = Optional.of(value);
            case "align" -> parseAlignValue(value).ifPresent(parsed -> draft.align = parsed);
            case "item_align", "itemalign" -> parseAlignValue(value).ifPresent(parsed -> draft.itemAlign = parsed);
            default -> {
                // Ignore unsupported page metadata keys for compatibility.
            }
        }
    }

    private static Optional<ImageDirective> parseImageDirective(
            Identifier resourceId,
            String args
    ) {
        if (args.isEmpty()) {
            return Optional.empty();
        }

        int splitIndex = firstWhitespaceIndex(args);
        String imageValue = splitIndex == -1 ? args : args.substring(0, splitIndex).strip();
        Optional<Identifier> image = parseImageIdentifier(resourceId, imageValue);
        if (image.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> description = Optional.empty();
        if (splitIndex != -1) {
            String parsedDescription = args.substring(splitIndex + 1).trim();
            if (!parsedDescription.isEmpty()) {
                description = Optional.of(parsedDescription);
            }
        }
        return Optional.of(new ImageDirective(image.orElseThrow(), description));
    }

    private static Optional<Identifier> parseImageIdentifier(Identifier resourceId, String value) {
        Identifier parsed = Identifier.tryParse(value);
        if (parsed == null) {
            LOGGER.warn("Invalid guide page image id '{}' in resource '{}'.", value, resourceId);
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    private static Optional<ItemDirective> parseItemDirective(
            Identifier resourceId,
            String args,
            AlignedMessage.Align align
    ) {
        if (args.isEmpty()) {
            return Optional.empty();
        }

        int splitIndex = firstWhitespaceIndex(args);
        String itemValue = splitIndex == -1 ? args : args.substring(0, splitIndex).strip();
        Optional<Identifier> itemId = parseItemIdentifier(resourceId, itemValue);
        if (itemId.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> description = Optional.empty();
        if (splitIndex != -1) {
            String parsedDescription = args.substring(splitIndex + 1).trim();
            if (!parsedDescription.isEmpty()) {
                description = Optional.of(parsedDescription);
            }
        }
        return Optional.of(new ItemDirective(itemId.orElseThrow(), description, align));
    }

    private static Optional<Identifier> parseItemIdentifier(Identifier resourceId, String value) {
        Identifier parsed = Identifier.tryParse(value);
        if (parsed == null) {
            LOGGER.warn("Invalid guide page item id '{}' in resource '{}'.", value, resourceId);
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    private static Optional<AlignedMessage.Align> parseAlignValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "left" -> Optional.of(AlignedMessage.Align.LEFT);
            case "center" -> Optional.of(AlignedMessage.Align.CENTER);
            case "right" -> Optional.of(AlignedMessage.Align.RIGHT);
            default -> Optional.empty();
        };
    }

    private static void appendBodyLine(StringBuilder body, String line, String previousLine) {
        if (!body.isEmpty() || !isNewlineMarker(line)) {
            String compactLower = compactLower(line);
            if (!compactLower.isEmpty() && !body.isEmpty() && compactLower.charAt(0) == '-') {
                body.append('\n');
            } else if (!body.isEmpty() && !isNewlineMarker(previousLine)) {
                body.append(' ');
            }
            body.append(line);
        }
    }

    private static void appendHeaderLine(StringBuilder body, String header) {
        if (!body.isEmpty() && body.charAt(body.length() - 1) != '\n') {
            body.append('\n');
        }
        body.append(GuideDialogUiSupport.HEADER_LINE_PREFIX).append(header).append('\n');
    }

    private static void appendImageLine(StringBuilder body, Identifier imageId, Optional<String> description) {
        if (!body.isEmpty() && body.charAt(body.length() - 1) != '\n') {
            body.append('\n');
        }
        body.append(GuideDialogUiSupport.createImageLineToken(imageId, description)).append('\n');
    }

    private static void appendItemLine(
            StringBuilder body,
            Identifier itemId,
            Optional<String> description,
            AlignedMessage.Align align
    ) {
        if (!body.isEmpty() && body.charAt(body.length() - 1) != '\n') {
            body.append('\n');
        }
        body.append(GuideDialogUiSupport.createItemLineToken(itemId, description, align)).append('\n');
    }

    private static List<String> convertBodyToLines(String bodyText) {
        String normalized = stripNewlineMarkers(bodyText);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String line : normalized.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return List.copyOf(lines);
    }

    private static String stripNewlineMarkers(String bodyText) {
        String normalized = bodyText;
        while (normalized.startsWith(NEWLINE_MARKER)) {
            normalized = normalized.substring(NEWLINE_MARKER.length());
        }
        while (normalized.endsWith(NEWLINE_MARKER)) {
            normalized = normalized.substring(0, normalized.length() - NEWLINE_MARKER.length());
        }
        return normalized.replace(DOUBLE_NEWLINE_MARKER, "\n\n").replace(NEWLINE_MARKER, "\n");
    }

    private static boolean isNewlineMarker(String line) {
        String stripped = line.strip();
        return stripped.equalsIgnoreCase(NEWLINE_MARKER) || stripped.equalsIgnoreCase(DOUBLE_NEWLINE_MARKER);
    }

    private static String normalizeTextPage(String page) {
        return page.replace("\r", "")
                .replace(' ', ' ')
                .replace('\u00A0', ' ')
                .replace("\n\n", "\n" + NEWLINE_MARKER + "\n");
    }

    private static String compactLower(String line) {
        return line.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String directiveValue(String line) {
        int separatorIndex = line.indexOf(':');
        if (separatorIndex < 0) {
            return "";
        }
        return line.substring(separatorIndex + 1).strip();
    }

    private static int firstWhitespaceIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void parseLegacyJsonBasicPage(
            Identifier resourceId,
            Resource resource,
            List<BasicPage> output
    ) {
        try (var reader = new java.io.InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            var jsonElement = JsonParser.parseReader(reader);
            BasicPage.CODEC.parse(JsonOps.INSTANCE, jsonElement).resultOrPartial(error ->
                    LOGGER.warn("Failed to parse legacy json guide page '{}': {}", resourceId, error)
            ).ifPresent(output::add);
        } catch (Exception exception) {
            LOGGER.warn("Failed to read legacy json guide page resource '{}'.", resourceId, exception);
        }
    }

    private enum SectionType {
        BODY,
        PAGE_INFO
    }

    private static final class PageDraft {
        private String title = "";
        private Optional<Identifier> image = Optional.empty();
        private Optional<String> imageDescription = Optional.empty();
        private AlignedMessage.Align align = AlignedMessage.Align.LEFT;
        private AlignedMessage.Align itemAlign = AlignedMessage.Align.LEFT;
    }

    private record ImageDirective(Identifier image, Optional<String> description) {
    }

    private record ItemDirective(Identifier item, Optional<String> description, AlignedMessage.Align align) {
    }

    public record BasicPage(
            String title,
            List<String> lines,
            Optional<Identifier> image,
            Optional<String> imageDescription,
            AlignedMessage.Align align
    ) {
        public static final Codec<BasicPage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("title").forGetter(BasicPage::title),
                Codec.list(Codec.STRING).optionalFieldOf("lines", List.of()).forGetter(BasicPage::lines),
                Identifier.CODEC.optionalFieldOf("image").forGetter(BasicPage::image),
                Codec.STRING.optionalFieldOf("image_description").forGetter(BasicPage::imageDescription),
                StringIdentifiable.createCodec(AlignedMessage.Align::values)
                        .optionalFieldOf("align", AlignedMessage.Align.LEFT)
                        .forGetter(BasicPage::align)
        ).apply(instance, BasicPage::new));
    }
}
