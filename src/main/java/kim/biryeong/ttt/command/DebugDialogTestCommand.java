package kim.biryeong.ttt.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import kim.biryeong.ttt.ui.dialog.test.DialogBodyTestDialogs;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class DebugDialogTestCommand {
    private static final Identifier DEFAULT_GUIDE_IMAGE_TEST_ID = Identifier.of("ttt", "conveyor_example");
    private static final Identifier DEFAULT_ALIGNED_ITEM_TEST_ID = Identifier.ofVanilla("iron_sword");

    private DebugDialogTestCommand() {
        throw new IllegalStateException("Utility class");
    }

    public static LiteralCommandNode<ServerCommandSource> createNode() {
        SuggestionProvider<ServerCommandSource> alignSuggestion = (context, builder) ->
                CommandSource.suggestMatching(
                        Arrays.stream(AlignedMessage.Align.values())
                                .map(AlignedMessage.Align::asString)
                                .toList(),
                        builder
                );

        return CommandManager.literal("dialog_test")
                .then(CommandManager.literal("image")
                        .executes(context -> openImageBodyTest(context.getSource(), DEFAULT_GUIDE_IMAGE_TEST_ID))
                        .then(CommandManager.argument("image", IdentifierArgumentType.identifier())
                                .executes(context -> openImageBodyTest(
                                        context.getSource(),
                                        IdentifierArgumentType.getIdentifier(context, "image")
                                ))))
                .then(CommandManager.literal("aligned_message")
                        .executes(context -> openAlignedMessageBodyTest(context.getSource(), AlignedMessage.Align.LEFT))
                        .then(CommandManager.argument("align", StringArgumentType.word())
                                .suggests(alignSuggestion)
                                .executes(context -> {
                                    Optional<AlignedMessage.Align> align = parseAlign(StringArgumentType.getString(context, "align"));
                                    if (align.isEmpty()) {
                                        context.getSource().sendError(Text.literal("align 값은 left, center, right 중 하나여야 합니다."));
                                        return 0;
                                    }
                                    return openAlignedMessageBodyTest(context.getSource(), align.get());
                                })))
                .then(CommandManager.literal("header_message")
                        .executes(context -> openHeaderMessageBodyTest(context.getSource())))
                .then(CommandManager.literal("aligned_item")
                        .executes(context -> openAlignedItemBodyTest(context.getSource(), DEFAULT_ALIGNED_ITEM_TEST_ID))
                        .then(CommandManager.argument("item", IdentifierArgumentType.identifier())
                                .executes(context -> openAlignedItemBodyTest(
                                        context.getSource(),
                                        IdentifierArgumentType.getIdentifier(context, "item")
                                ))))
                .build();
    }

    public static LiteralCommandNode<ServerCommandSource> createLegacyGuideImageTestNode() {
        return CommandManager.literal("guide_image_test")
                .executes(context -> openImageBodyTest(context.getSource(), DEFAULT_GUIDE_IMAGE_TEST_ID))
                .then(CommandManager.argument("image", IdentifierArgumentType.identifier())
                        .executes(context -> openImageBodyTest(
                                context.getSource(),
                                IdentifierArgumentType.getIdentifier(context, "image")
                        )))
                .build();
    }

    private static int openImageBodyTest(ServerCommandSource source, Identifier imageId) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        return DialogBodyTestDialogs.showImageBodyTest(player, imageId);
    }

    private static int openAlignedMessageBodyTest(ServerCommandSource source, AlignedMessage.Align align) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        return DialogBodyTestDialogs.showAlignedMessageBodyTest(player, align);
    }

    private static int openHeaderMessageBodyTest(ServerCommandSource source) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        return DialogBodyTestDialogs.showHeaderMessageBodyTest(player);
    }

    private static int openAlignedItemBodyTest(ServerCommandSource source, Identifier itemId) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        if (!Registries.ITEM.containsId(itemId)) {
            source.sendError(Text.literal("존재하지 않는 아이템 ID입니다: " + itemId));
            return 0;
        }
        Item item = Registries.ITEM.get(itemId);

        return DialogBodyTestDialogs.showAlignedItemBodyTest(player, item);
    }

    private static Optional<AlignedMessage.Align> parseAlign(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(AlignedMessage.Align.values())
                .filter(align -> align.asString().equals(normalized))
                .findFirst();
    }

    private static ServerPlayerEntity requirePlayer(ServerCommandSource source) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return null;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return null;
        }

        return player;
    }
}
