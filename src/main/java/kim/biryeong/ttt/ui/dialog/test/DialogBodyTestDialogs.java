package kim.biryeong.ttt.ui.dialog.test;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.ui.dialog.body.AlignedItemBody;
import kim.biryeong.ttt.ui.dialog.body.AlignedMessage;
import kim.biryeong.ttt.ui.dialog.body.HeaderMessage;
import kim.biryeong.ttt.ui.dialog.body.ImageBody;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.NoticeDialog;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public final class DialogBodyTestDialogs {
    private static final Logger LOGGER = LoggerFactory.getLogger(DialogBodyTestDialogs.class);
    private static final int DIALOG_WIDTH = 300;

    private DialogBodyTestDialogs() {
        throw new IllegalStateException("Utility class");
    }

    public static int showImageBodyTest(ServerPlayerEntity player, Identifier imageId) {
        Identifier dialogId = createPerPlayerDialogId(player, "image");
        NoticeDialog dialog = buildImageBodyDialog(imageId);
        DialogUtils.registerDialog(dialogId, dialog);
        return openDialog(player, dialogId, dialog, "image_body_test");
    }

    public static int showAlignedMessageBodyTest(ServerPlayerEntity player, AlignedMessage.Align align) {
        Identifier dialogId = createPerPlayerDialogId(player, "aligned_message");
        NoticeDialog dialog = buildAlignedMessageBodyDialog(align);
        DialogUtils.registerDialog(dialogId, dialog);
        return openDialog(player, dialogId, dialog, "aligned_message_test");
    }

    public static int showHeaderMessageBodyTest(ServerPlayerEntity player) {
        Identifier dialogId = createPerPlayerDialogId(player, "header_message");
        NoticeDialog dialog = buildHeaderMessageBodyDialog();
        DialogUtils.registerDialog(dialogId, dialog);
        return openDialog(player, dialogId, dialog, "header_message_test");
    }

    public static int showAlignedItemBodyTest(ServerPlayerEntity player, Item item) {
        Identifier dialogId = createPerPlayerDialogId(player, "aligned_item");
        NoticeDialog dialog = buildAlignedItemBodyDialog(item);
        DialogUtils.registerDialog(dialogId, dialog);
        return openDialog(player, dialogId, dialog, "aligned_item_test");
    }

    private static int openDialog(ServerPlayerEntity player, Identifier dialogId, NoticeDialog dialog, String testName) {
        var server = player.getServer();
        if (server == null) {
            LOGGER.warn(
                    "Cannot open dialog body test '{}' for {} ({}): server is null.",
                    testName,
                    player.getGameProfile().getName(),
                    player.getUuid()
            );
            return 0;
        }

        var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        RegistryEntry<Dialog> dialogEntry = dialogRegistry.getEntry(dialogId)
                .<RegistryEntry<Dialog>>map(entry -> entry)
                .orElseGet(() -> dialogRegistry.getEntry(dialog));
        player.openDialog(dialogEntry);
        return 1;
    }

    private static Identifier createPerPlayerDialogId(ServerPlayerEntity player, String suffix) {
        String playerKey = player.getUuidAsString().replace("-", "");
        return Identifier.of(
                TroubleInTerroristTownMod.MOD_ID,
                "dialog_body_test_" + suffix + "_" + playerKey
        );
    }

    private static NoticeDialog buildImageBodyDialog(Identifier imageId) {
        return createNoticeDialog(
                "Dialog Test: ImageBody",
                Optional.of(Text.literal(imageId.toString())),
                List.of(new ImageBody(imageId, Optional.empty()))
        );
    }

    private static NoticeDialog buildAlignedMessageBodyDialog(AlignedMessage.Align align) {
        AlignedMessage body = new AlignedMessage(
                Text.literal("정렬 테스트 본문입니다.\nAlignedMessage body example"),
                DIALOG_WIDTH,
                align
        );

        List<DialogBody> bodies = List.of(
                new HeaderMessage(Text.literal("AlignedMessage (" + align.asString() + ")"), DIALOG_WIDTH),
                body
        );
        return createNoticeDialog("Dialog Test: AlignedMessage", Optional.empty(), bodies);
    }

    private static NoticeDialog buildHeaderMessageBodyDialog() {
        List<DialogBody> bodies = List.of(
                new HeaderMessage(Text.literal("HeaderMessage Example"), DIALOG_WIDTH),
                new PlainMessageDialogBody(
                        Text.literal("헤더 바디가 좌우 라인과 함께 출력되는지 확인합니다."),
                        DIALOG_WIDTH
                )
        );
        return createNoticeDialog("Dialog Test: HeaderMessage", Optional.empty(), bodies);
    }

    private static NoticeDialog buildAlignedItemBodyDialog(Item item) {
        ItemStack stack = new ItemStack(item);
        AlignedMessage description = new AlignedMessage(
                Text.literal("아이템 설명 정렬 테스트\n- 아이템 바디 + 정렬 본문"),
                DIALOG_WIDTH - 56,
                AlignedMessage.Align.LEFT
        );

        List<DialogBody> bodies = List.of(
                new HeaderMessage(Text.literal("AlignedItemBody Example"), DIALOG_WIDTH),
                new AlignedItemBody(stack, description, true, true, 32, 32)
        );
        return createNoticeDialog(
                "Dialog Test: AlignedItemBody",
                Optional.of(Text.literal(item.getName().getString())),
                bodies
        );
    }

    private static NoticeDialog createNoticeDialog(String title, Optional<Text> externalTitle, List<DialogBody> bodies) {
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(title),
                externalTitle,
                true,
                false,
                AfterAction.CLOSE,
                List.copyOf(bodies),
                List.of()
        );

        DialogActionButtonData closeButton = new DialogActionButtonData(
                new DialogButtonData(Text.literal("닫기"), DialogButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );

        return new NoticeDialog(commonData, closeButton);
    }
}
