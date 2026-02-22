package kim.biryeong.ttt.ui.dialog.guide;

import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import kim.biryeong.ttt.ui.dialog.body.AlignedItemBody;
import kim.biryeong.ttt.ui.dialog.body.HeaderMessage;
import kim.biryeong.ttt.ui.dialog.body.ImageBody;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.action.SimpleDialogAction;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.NoticeDialog;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

final class GuideDialogUiSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideDialogUiSupport.class);
    static final String BACK_TO_MENU_LABEL = "목록으로";
    static final String HEADER_LINE_PREFIX = "<tts:header>";
    static final String IMAGE_LINE_PREFIX = "<tts:image>";
    static final String ITEM_LINE_PREFIX = "<tts:item>";
    static final int NAV_BUTTON_WIDTH = 30;
    static final int MENU_BUTTON_WIDTH = 88;
    static final int GUIDE_BODY_WIDTH = 300;

    private GuideDialogUiSupport() {
        throw new IllegalStateException("Utility class");
    }

    static int openDialog(ServerPlayerEntity player, Identifier dialogId, Dialog fallbackDialog, Logger logger) {
        var server = player.getServer();
        if (server == null) {
            logger.warn(
                    "Cannot open guide dialog '{}' for {} ({}): server is null.",
                    dialogId,
                    player.getGameProfile().getName(),
                    player.getUuid()
            );
            return 0;
        }

        var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        RegistryEntry<Dialog> dialogEntry = dialogRegistry.getEntry(dialogId)
                .<RegistryEntry<Dialog>>map(entry -> entry)
                .orElseGet(() -> dialogRegistry.getEntry(fallbackDialog));
        player.openDialog(dialogEntry);
        return 1;
    }

    static NoticeDialog buildCategoryNoticeDialog(String title, List<DialogBody> bodies) {
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(title),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                List.copyOf(bodies),
                List.of()
        );

        DialogActionButtonData backButton = new DialogActionButtonData(
                new DialogButtonData(Text.literal(BACK_TO_MENU_LABEL), DialogButtonData.DEFAULT_WIDTH),
                Optional.of(new SimpleDialogAction(new ClickEvent.RunCommand("/tts guide")))
        );

        return new NoticeDialog(commonData, backButton);
    }

    static DialogActionButtonData createCommandButton(String label, String command) {
        return createCommandButton(label, command, DialogButtonData.DEFAULT_WIDTH);
    }

    static DialogActionButtonData createCommandButton(String label, String command, int width) {
        return new DialogActionButtonData(
                new DialogButtonData(Text.literal(label), width),
                Optional.of(new SimpleDialogAction(new ClickEvent.RunCommand(command)))
        );
    }

    static DialogActionButtonData createCloseButton(String label) {
        return new DialogActionButtonData(
                new DialogButtonData(Text.literal(label), DialogButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );
    }

    static void addEmptyLine(List<DialogBody> bodies) {
        bodies.add(new PlainMessageDialogBody(Text.empty(), PlainMessageDialogBody.DEFAULT_WIDTH));
    }

    static void addBodyLine(List<DialogBody> bodies, String text) {
        bodies.add(new PlainMessageDialogBody(Text.literal(text), PlainMessageDialogBody.DEFAULT_WIDTH));
    }

    static void addBodyLine(List<DialogBody> bodies, Text text) {
        bodies.add(new PlainMessageDialogBody(text, PlainMessageDialogBody.DEFAULT_WIDTH));
    }

    static void addAlignedBodyLine(List<DialogBody> bodies, String text, AlignedMessage.Align align) {
        bodies.add(new AlignedMessage(parseMiniMessage(text), GUIDE_BODY_WIDTH, align));
    }

    static void addAlignedBodyLine(List<DialogBody> bodies, Text text, AlignedMessage.Align align) {
        bodies.add(new AlignedMessage(text, GUIDE_BODY_WIDTH, align));
    }

    static Text parseMiniMessage(String value) {
        if (value == null || value.isEmpty()) {
            return Text.empty();
        }
        try {
            return byMiniMessage(value);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse minimessage guide text '{}'. Falling back to literal text.", value, exception);
            return Text.literal(value);
        }
    }

    static boolean isHeaderLineToken(String line) {
        return line != null && line.startsWith(HEADER_LINE_PREFIX);
    }

    static String extractHeaderTokenValue(String line) {
        if (!isHeaderLineToken(line)) {
            return "";
        }
        return line.substring(HEADER_LINE_PREFIX.length()).trim();
    }

    static String createImageLineToken(Identifier imageId, Optional<String> description) {
        StringBuilder token = new StringBuilder(IMAGE_LINE_PREFIX).append(imageId);
        description.map(String::trim).filter(value -> !value.isEmpty()).ifPresent(value ->
                token.append(' ').append(value)
        );
        return token.toString();
    }

    static boolean isImageLineToken(String line) {
        return line != null && line.startsWith(IMAGE_LINE_PREFIX);
    }

    static Optional<ImageLineToken> extractImageLineToken(String line) {
        if (!isImageLineToken(line)) {
            return Optional.empty();
        }

        String args = line.substring(IMAGE_LINE_PREFIX.length()).trim();
        if (args.isEmpty()) {
            return Optional.empty();
        }

        int splitIndex = firstWhitespaceIndex(args);
        String imageValue = splitIndex == -1 ? args : args.substring(0, splitIndex).strip();
        Identifier imageId = Identifier.tryParse(imageValue);
        if (imageId == null) {
            LOGGER.warn("Invalid guide image token id '{}'.", imageValue);
            return Optional.empty();
        }

        Optional<String> description = Optional.empty();
        if (splitIndex != -1) {
            String value = args.substring(splitIndex + 1).trim();
            if (!value.isEmpty()) {
                description = Optional.of(value);
            }
        }

        return Optional.of(new ImageLineToken(imageId, description));
    }

    static String createItemLineToken(Identifier itemId, Optional<String> description, AlignedMessage.Align align) {
        StringBuilder token = new StringBuilder(ITEM_LINE_PREFIX)
                .append(itemId)
                .append(' ')
                .append(align.asString());
        description.map(String::trim).filter(value -> !value.isEmpty()).ifPresent(value ->
                token.append(' ').append(value)
        );
        return token.toString();
    }

    static boolean isItemLineToken(String line) {
        return line != null && line.startsWith(ITEM_LINE_PREFIX);
    }

    static Optional<ItemLineToken> extractItemLineToken(String line) {
        if (!isItemLineToken(line)) {
            return Optional.empty();
        }

        String args = line.substring(ITEM_LINE_PREFIX.length()).trim();
        if (args.isEmpty()) {
            return Optional.empty();
        }

        int firstSplitIndex = firstWhitespaceIndex(args);
        String itemValue = firstSplitIndex == -1 ? args : args.substring(0, firstSplitIndex).strip();
        Identifier itemId = Identifier.tryParse(itemValue);
        if (itemId == null) {
            LOGGER.warn("Invalid guide item token id '{}'.", itemValue);
            return Optional.empty();
        }

        AlignedMessage.Align align = AlignedMessage.Align.LEFT;
        String remaining = firstSplitIndex == -1 ? "" : args.substring(firstSplitIndex + 1).trim();
        if (!remaining.isEmpty()) {
            int secondSplitIndex = firstWhitespaceIndex(remaining);
            String alignCandidate = secondSplitIndex == -1
                    ? remaining
                    : remaining.substring(0, secondSplitIndex).strip();
            Optional<AlignedMessage.Align> parsedAlign = parseAlignValue(alignCandidate);
            if (parsedAlign.isPresent()) {
                align = parsedAlign.orElseThrow();
                remaining = secondSplitIndex == -1 ? "" : remaining.substring(secondSplitIndex + 1).trim();
            }
        }

        Optional<String> description = remaining.isEmpty()
                ? Optional.empty()
                : Optional.of(remaining);
        return Optional.of(new ItemLineToken(itemId, description, align));
    }

    static boolean addDirectiveBodyFromToken(List<DialogBody> bodies, String line) {
        if (isHeaderLineToken(line)) {
            String headerValue = extractHeaderTokenValue(line);
            if (!headerValue.isEmpty()) {
                bodies.add(new HeaderMessage(parseMiniMessage(headerValue), GUIDE_BODY_WIDTH));
            }
            return true;
        }

        if (isImageLineToken(line)) {
            extractImageLineToken(line).ifPresent(imageLineToken -> {
                Optional<Text> description = imageLineToken.description()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(GuideDialogUiSupport::parseMiniMessage);
                bodies.add(new ImageBody(imageLineToken.image(), description));
                addEmptyLine(bodies);
            });
            return true;
        }

        if (isItemLineToken(line)) {
            extractItemLineToken(line).ifPresent(itemLineToken -> {
                if (!Registries.ITEM.containsId(itemLineToken.item())) {
                    LOGGER.warn("Guide item '{}' is not registered. Skipping item body token.", itemLineToken.item());
                    return;
                }

                Optional<Text> description = itemLineToken.description()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(GuideDialogUiSupport::parseMiniMessage);
                AlignedMessage alignedDescription = new AlignedMessage(
                        description.orElse(Text.empty()),
                        GUIDE_BODY_WIDTH,
                        itemLineToken.align()
                );
                bodies.add(new AlignedItemBody(
                        new ItemStack(Registries.ITEM.get(itemLineToken.item())),
                        alignedDescription,
                        true,
                        true,
                        16,
                        16
                ));
                addEmptyLine(bodies);
            });
            return true;
        }

        return false;
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

    private static int firstWhitespaceIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    record ImageLineToken(Identifier image, Optional<String> description) {
    }

    record ItemLineToken(Identifier item, Optional<String> description, AlignedMessage.Align align) {
    }
}
