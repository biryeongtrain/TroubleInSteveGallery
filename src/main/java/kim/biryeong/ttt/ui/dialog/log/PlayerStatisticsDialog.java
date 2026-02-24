package kim.biryeong.ttt.ui.dialog.log;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import net.minecraft.dialog.AfterAction;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.NoticeDialog;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class PlayerStatisticsDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStatisticsDialog.class);
    private static final String DIALOG_TITLE = "TTT 통계";

    private PlayerStatisticsDialog() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Shows the rendered stats lines as a player dialog.
     */
    public static void show(ServerPlayerEntity viewer, List<Text> lines) {
        Identifier dialogId = TTTDialogs.playerStatsDialogId(viewer);
        NoticeDialog dialog = buildDialog(lines);
        DialogUtils.registerDialog(dialogId, dialog);
        sendDialog(viewer, dialogId, dialog);
    }

    private static NoticeDialog buildDialog(List<Text> lines) {
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(DIALOG_TITLE),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                buildBodies(lines),
                List.of()
        );

        DialogActionButtonData closeButton = new DialogActionButtonData(
                new DialogButtonData(Text.literal("닫기"), DialogButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );

        return new NoticeDialog(commonData, closeButton);
    }

    private static List<DialogBody> buildBodies(List<Text> lines) {
        return lines.stream()
                .map(line -> (DialogBody) new PlainMessageDialogBody(line, PlainMessageDialogBody.DEFAULT_WIDTH))
                .toList();
    }

    private static void sendDialog(ServerPlayerEntity player, Identifier dialogId, NoticeDialog dialog) {
        var server = player.getServer();
        if (server == null) {
            LOGGER.warn(
                    "Cannot send player statistics dialog for {} ({}): server is null.",
                    player.getGameProfile().getName(),
                    player.getUuid()
            );
            return;
        }

        var dialogRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIALOG);
        RegistryEntry<Dialog> dialogEntry = dialogRegistry.getEntry(dialogId)
                .<RegistryEntry<Dialog>>map(entry -> entry)
                .orElseGet(() -> dialogRegistry.getEntry(dialog));
        player.openDialog(dialogEntry);
    }
}
