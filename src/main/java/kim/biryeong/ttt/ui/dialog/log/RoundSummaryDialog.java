package kim.biryeong.ttt.ui.dialog.log;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.dialog.TTTDialogs;
import kim.biryeong.ttt.util.AvatarTextRenderer;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class RoundSummaryDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoundSummaryDialog.class);
    private static final String DIALOG_TITLE = "라운드 기록";
    private static final String TRAITOR_SECTION_TITLE = "트레이터 목록";
    private static final String ROUND_KILL_SECTION_TITLE = "전체 킬 로그";
    private static final String EMPTY_TRAITOR_LIST = "트레이터가 없습니다.";
    private static final String EMPTY_ROUND_KILL_LOG = "기록된 전체 킬 로그가 없습니다.";

    private RoundSummaryDialog() {
        throw new IllegalStateException("Utility class");
    }

    public static void showRoundSummary(
            ServerPlayerEntity player,
            List<RoundSummaryKillEntry> killEntries,
            List<RoundSummaryTraitorEntry> traitorEntries
    ) {
        Identifier dialogId = TTTDialogs.roundSummaryLogId(player);
        NoticeDialog dialog = buildDialog(killEntries, traitorEntries);
        DialogUtils.registerDialog(dialogId, dialog);
        sendDialog(player, dialogId, dialog);
    }

    private static NoticeDialog buildDialog(
            List<RoundSummaryKillEntry> killEntries,
            List<RoundSummaryTraitorEntry> traitorEntries
    ) {
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(DIALOG_TITLE),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                buildBodies(killEntries, traitorEntries),
                List.of()
        );

        DialogActionButtonData closeButton = new DialogActionButtonData(
                new DialogButtonData(Text.literal("닫기"), DialogButtonData.DEFAULT_WIDTH),
                Optional.empty()
        );

        return new NoticeDialog(commonData, closeButton);
    }

    private static void sendDialog(ServerPlayerEntity player, Identifier dialogId, NoticeDialog dialog) {
        var server = player.getServer();
        if (server == null) {
            LOGGER.warn(
                    "Cannot send round summary dialog for {} ({}): server is null.",
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

    private static List<DialogBody> buildBodies(
            List<RoundSummaryKillEntry> killEntries,
            List<RoundSummaryTraitorEntry> traitorEntries
    ) {
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new PlainMessageDialogBody(Text.literal(TRAITOR_SECTION_TITLE).withColor(0xe02f58), PlainMessageDialogBody.DEFAULT_WIDTH));
        if (traitorEntries.isEmpty()) {
            bodies.add(new PlainMessageDialogBody(Text.literal("- " + EMPTY_TRAITOR_LIST), PlainMessageDialogBody.DEFAULT_WIDTH));
        } else {
            for (RoundSummaryTraitorEntry traitorEntry : traitorEntries) {
                bodies.add(buildTraitorAvatarBody(traitorEntry));
            }
        }

        bodies.add(new PlainMessageDialogBody(Text.literal(""), PlainMessageDialogBody.DEFAULT_WIDTH));
        bodies.add(new PlainMessageDialogBody(Text.literal(ROUND_KILL_SECTION_TITLE), PlainMessageDialogBody.DEFAULT_WIDTH));
        if (killEntries.isEmpty()) {
            bodies.add(new PlainMessageDialogBody(Text.literal("- " + EMPTY_ROUND_KILL_LOG), PlainMessageDialogBody.DEFAULT_WIDTH));
        } else {
            for (RoundSummaryKillEntry killEntry : killEntries) {
                MutableText killLine = Text.literal("- " + formatRoundSummaryKillLine(killEntry));
                if (isSameTeamKill(killEntry.killerRole(), killEntry.victimRole())) {
                    killLine = killLine.formatted(Formatting.RED);
                }
                bodies.add(new PlainMessageDialogBody(
                        killLine,
                        PlainMessageDialogBody.DEFAULT_WIDTH
                ));
            }
        }

        return List.copyOf(bodies);
    }

    private static PlainMessageDialogBody buildTraitorAvatarBody(RoundSummaryTraitorEntry traitorEntry) {
        Text avatar = AvatarTextRenderer.resolveSmallAvatar(
                traitorEntry.uuid(),
                traitorEntry.name(),
                false
        );
        Text line = Text.empty()
                .append(Text.literal("- "))
                .append(avatar)
                .append(Text.literal(" "))
                .append(Text.literal(traitorEntry.name()));
        return new PlainMessageDialogBody(line, PlainMessageDialogBody.DEFAULT_WIDTH);
    }

    private static String formatRoundSummaryKillLine(RoundSummaryKillEntry killEntry) {
        String killerName = "Unknown".equalsIgnoreCase(killEntry.killerName())
                ? "환경/미상"
                : killEntry.killerName();
        return "[%s] %s -> %s (피해자 역할: %s)".formatted(
                formatElapsedSeconds(killEntry.elapsedSeconds()),
                killerName,
                killEntry.victimName(),
                toKoreanRole(killEntry.victimRole())
        );
    }

    public static boolean isSameTeamKill(Role killerRole, Role victimRole) {
        if (killerRole == Role.SPECTATOR || victimRole == Role.SPECTATOR) {
            return false;
        }

        if (killerRole == Role.TRAITOR || victimRole == Role.TRAITOR) {
            return killerRole == Role.TRAITOR && victimRole == Role.TRAITOR;
        }

        return true;
    }

    private static String toKoreanRole(Role role) {
        return switch (role) {
            case INNOCENT -> "시민";
            case TRAITOR -> "배신자";
            case DETECTIVE -> "탐정";
            case SPECTATOR -> "관전자";
        };
    }

    private static String formatElapsedSeconds(int elapsedSeconds) {
        int clampedSeconds = Math.max(0, elapsedSeconds);
        int minutes = clampedSeconds / 60;
        int seconds = clampedSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    public record RoundSummaryKillEntry(
            int elapsedSeconds,
            String killerName,
            Role killerRole,
            String victimName,
            Role victimRole
    ) {
    }

    public record RoundSummaryTraitorEntry(
            UUID uuid,
            String name
    ) {
    }
}
