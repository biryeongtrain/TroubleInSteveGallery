package kim.biryeong.ttt.ui.dialog.log;

import de.tomalbrc.avatarrenderer.AvatarRendererMod;
import de.tomalbrc.avatarrenderer.impl.AvatarRenderer;
import de.tomalbrc.avatarrenderer.impl.SkinLoader;
import de.tomalbrc.dialogutils.DialogUtils;
import kim.biryeong.ttt.game.data.PlayerRoundDataInstance;
import kim.biryeong.ttt.player.role.Role;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathCombatLogDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeathCombatLogDialog.class);
    private static final String DIALOG_TITLE = "사망 전투 기록";
    private static final String DAMAGE_SECTION_TITLE = "플레이어 간 피해 기록";
    private static final String KILL_SECTION_TITLE = "킬 로그";
    private static final String EMPTY_DAMAGE_LOG = "기록된 플레이어 간 피해가 없습니다.";
    private static final String EMPTY_KILL_LOG = "기록된 킬 로그가 없습니다.";
    private static final int AVATAR_OFFSET = 24;
    private static final int AVATAR_SIZE = 34;
    private static final int SMALL_AVATAR_SIZE = 17;
    private static final String DEFAULT_AVATAR_KEY = "Steve";
    private static final Map<AvatarCacheKey, Text> SMALL_AVATAR_CACHE = new ConcurrentHashMap<>();

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

    /**
     * Resolves a small (kill-log sized) avatar text component from player identity data.
     */
    public static Text resolveSmallAvatar(@Nullable UUID uuid, @Nullable String keyOrName, boolean flipped) {
        Text avatar = resolveAvatarByUuid(uuid, flipped);
        if (avatar != null) {
            return avatar;
        }

        avatar = resolveAvatarByKey(keyOrName, flipped);
        if (avatar != null) {
            return avatar;
        }
        return resolveDefaultAvatar(flipped);
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
        Text avatar = resolveAvatar(entry);
        Text line = Text.literal(formatDamageLine(entry));
        Text merged = avatar == null
                ? line
                : Text.empty().append(avatar).append(Text.literal(" ")).append(line);
        return new PlainMessageDialogBody(merged, PlainMessageDialogBody.DEFAULT_WIDTH);
    }

    private static Text resolveAvatar(DamageEntry entry) {
        boolean flipped = entry.dealtByRecorder();
        Text avatar = resolveAvatarByUuid(entry.counterpartUuid(), flipped);
        if (avatar != null) {
            return avatar;
        }
        avatar = resolveAvatarByKey(entry.counterpartName(), flipped);
        if (avatar != null) {
            return avatar;
        }
        return resolveDefaultAvatar(flipped);
    }

    private static Text resolveAvatarByUuid(UUID uuid, boolean flipped) {
        if (uuid == null) {
            return null;
        }
        return resolveAvatarByKey(uuid.toString(), flipped);
    }

    private static Text resolveAvatarByKey(String key, boolean flipped) {
        if (key == null || key.isBlank()) {
            return null;
        }

        AvatarCacheKey cacheKey = new AvatarCacheKey(key, flipped);
        Text cachedAvatar = SMALL_AVATAR_CACHE.get(cacheKey);
        if (cachedAvatar != null) {
            return cachedAvatar;
        }

        Text renderedAvatar = renderSmallAvatar(key, flipped);
        if (renderedAvatar == null) {
            return null;
        }
        SMALL_AVATAR_CACHE.put(cacheKey, renderedAvatar);
        return renderedAvatar;
    }

    private static Text resolveDefaultAvatar(boolean flipped) {
        AvatarCacheKey defaultCacheKey = new AvatarCacheKey(DEFAULT_AVATAR_KEY, flipped);
        Text cachedDefault = SMALL_AVATAR_CACHE.get(defaultCacheKey);
        if (cachedDefault != null) {
            return cachedDefault;
        }

        BufferedImage steveSkin = loadDefaultSkin();
        if (steveSkin == null) {
            AvatarRendererMod.get(DEFAULT_AVATAR_KEY, AVATAR_OFFSET, flipped, AvatarRendererMod.NOOP);
            return AvatarRendererMod.getNow(DEFAULT_AVATAR_KEY, AVATAR_OFFSET, flipped);
        }

        Text defaultAvatar = AvatarRenderer.asTextComponent(scaleDown(AvatarRenderer.render(steveSkin, flipped)), AVATAR_OFFSET);
        SMALL_AVATAR_CACHE.put(defaultCacheKey, defaultAvatar);
        return defaultAvatar;
    }

    private static Text renderSmallAvatar(String key, boolean flipped) {
        BufferedImage skin = loadSkin(key);
        if (skin == null) {
            return null;
        }
        BufferedImage avatar = AvatarRenderer.render(skin, flipped);
        return AvatarRenderer.asTextComponent(scaleDown(avatar), AVATAR_OFFSET);
    }

    private static BufferedImage loadSkin(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return SkinLoader.load(UUID.fromString(key));
        } catch (IllegalArgumentException ignored) {
            // Fallback to username lookup when the key is not a UUID string.
        }

        Optional<BufferedImage> byName = SkinLoader.load(key);
        if (byName.isPresent()) {
            return byName.get();
        }
        return null;
    }

    private static BufferedImage loadDefaultSkin() {
        try (var stream = AvatarRendererMod.class.getResourceAsStream("/steve.png")) {
            if (stream == null) {
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException exception) {
            LOGGER.warn("Failed to load default avatar skin.", exception);
            return null;
        }
    }

    private static BufferedImage scaleDown(BufferedImage source) {
        if (source.getWidth() == SMALL_AVATAR_SIZE && source.getHeight() == SMALL_AVATAR_SIZE) {
            return source;
        }
        BufferedImage resized = new BufferedImage(SMALL_AVATAR_SIZE, SMALL_AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, SMALL_AVATAR_SIZE, SMALL_AVATAR_SIZE, 0, 0, AVATAR_SIZE, AVATAR_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return resized;
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

    private record AvatarCacheKey(String key, boolean flipped) {
    }
}
