package kim.biryeong.ttt.ui.dialog.log;

import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.game.data.PlayerRoundDataInstance;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class DeathCombatLogDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeathCombatLogDialog.class);
    private static final String DIALOG_TITLE = "사망 전투 기록";
    private static final String DAMAGE_SECTION_TITLE = "플레이어 간 피해 기록";
    private static final String KILL_SECTION_TITLE = "킬 로그";
    private static final String EMPTY_DAMAGE_LOG = "기록된 플레이어 간 피해가 없습니다.";
    private static final String EMPTY_KILL_LOG = "기록된 킬 로그가 없습니다.";

    private DeathCombatLogDialog() {
        throw new IllegalStateException("Utility class");
    }

    public static void showKillLog(
            ServerPlayerEntity player,
            PlayerRoundDataInstance roundData,
            List<DamageEntry> damageEntries
    ) {
        Identifier dialogId = TTTDialogs.deathCombatLogId(player);
        NoticeDialog dialog = buildDialog(roundData, damageEntries);
        DialogUtils.registerDialog(dialogId, dialog);
        sendDialog(player, dialogId, dialog);
    }

    private static NoticeDialog buildDialog(PlayerRoundDataInstance roundData, List<DamageEntry> damageEntries) {
        DialogCommonData commonData = new DialogCommonData(
                Text.literal(DIALOG_TITLE),
                Optional.empty(),
                true,
                false,
                AfterAction.CLOSE,
                buildDialogBodies(roundData, damageEntries),
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
                    "Cannot send death combat dialog for {} ({}): server is null.",
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

    private static List<DialogBody> buildDialogBodies(
            PlayerRoundDataInstance roundData,
            List<DamageEntry> damageEntries
    ) {
        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(new PlainMessageDialogBody(Text.literal(DAMAGE_SECTION_TITLE), PlainMessageDialogBody.DEFAULT_WIDTH));
        if (damageEntries.isEmpty()) {
            bodies.add(new PlainMessageDialogBody(Text.literal("- " + EMPTY_DAMAGE_LOG), PlainMessageDialogBody.DEFAULT_WIDTH));
        } else {
            for (DamageEntry entry : damageEntries) {
                bodies.add(buildDamageAvatarBody(entry));
            }
        }

        bodies.add(new PlainMessageDialogBody(Text.literal(""), PlainMessageDialogBody.DEFAULT_WIDTH));
        bodies.add(new PlainMessageDialogBody(Text.literal(buildKillLogSection(roundData)), PlainMessageDialogBody.DEFAULT_WIDTH));
        return List.copyOf(bodies);
    }

    private static PlainMessageDialogBody buildDamageAvatarBody(DamageEntry entry) {
        Text avatar = AvatarTextRenderer.resolveSmallAvatar(
                entry.counterpartUuid(),
                entry.counterpartName(),
                entry.dealtByRecorder()
        );
        Text line = Text.literal(formatDamageLine(entry));
        Text merged = avatar == null
                ? line
                : Text.empty().append(avatar).append(Text.literal(" ")).append(line);
        return new PlainMessageDialogBody(merged, PlainMessageDialogBody.DEFAULT_WIDTH);
    }

    private static String buildKillLogSection(PlayerRoundDataInstance roundData) {
        List<String> lines = new ArrayList<>();
        lines.add(KILL_SECTION_TITLE);
        List<PlayerRoundDataInstance.RoundKillData> killData = roundData.roundKillData();
        if (killData.isEmpty()) {
            lines.add("- " + EMPTY_KILL_LOG);
        } else {
            for (PlayerRoundDataInstance.RoundKillData data : killData) {
                lines.add("- " + formatKillLine(data));
            }
        }
        return String.join("\n", lines);
    }

    private static String formatDamageLine(DamageEntry entry) {
        String action = entry.dealtByRecorder() ? "가한 피해" : "받은 피해";
        String counterpartLabel = entry.dealtByRecorder() ? "대상" : "공격자";
        String role = toKoreanRole(entry.counterpartRole());
        String amount = String.format(Locale.ROOT, "%.1f", entry.amount());
        return "[%s] %s %s (%s: %s, 역할: %s)".formatted(
                formatElapsedSeconds(entry.elapsedSeconds()),
                action,
                amount,
                counterpartLabel,
                entry.counterpartName(),
                role
        );
    }

    private static String formatKillLine(PlayerRoundDataInstance.RoundKillData killData) {
        String role = toKoreanRole(killData.victimRole());
        if (killData.slainByVictim()) {
            return "[%s] %s 에게 사망 (역할: %s)".formatted(
                    formatElapsedSeconds(killData.elapsedSeconds()),
                    killData.victimName(),
                    role
            );
        }
        return "[%s] %s 처치 (역할: %s)".formatted(
                formatElapsedSeconds(killData.elapsedSeconds()),
                killData.victimName(),
                role
        );
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
        return "%02d:%02d".formatted(minutes, seconds);
    }

    public record DamageEntry(
            int elapsedSeconds,
            String counterpartName,
            UUID counterpartUuid,
            Role counterpartRole,
            float amount,
            boolean dealtByRecorder
    ) {
    }
}
