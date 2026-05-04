package kim.biryeong.ttt.ui.hud;

import kim.biryeong.dantashader.displayhud.DisplayHud;
import kim.biryeong.dantashader.displayhud.TextDisplayHud;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.AvatarTextRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.List;

public final class RoundHudManager {
    private static final String ROUND_STATUS_ID = "ttt:round_status";
    private static final String ACCUSATION_TITLE_ID = "ttt:accusation_title";
    private static final String ACCUSATION_LOG_ID = "ttt:accusation_log";
    private static final String LAST_TEXT_KEY = "lastText";
    private static final String ACCUSATION_LAST_TEXT_KEY = "lastAccusationText";
    private static final int STATUS_LINE_WIDTH = 230;
    private static final float STATUS_TEXT_SCALE = 146;
    private static final float STATUS_X = 1860;
    private static final float STATUS_Y = 235;
    private static final int ACCUSATION_VISIBLE_SECONDS = 120;
    private static final int ACCUSATION_LINE_WIDTH = 390;
    private static final int MAX_VISIBLE_ACCUSATIONS = 8;
    private static final int ACCUSATION_TITLE_LINE_WIDTH = 160;
    private static final float ACCUSATION_TITLE_TEXT_SCALE = 146;
    private static final float ACCUSATION_TEXT_SCALE = 112;
    private static final float ACCUSATION_X = 80;
    private static final float ACCUSATION_TITLE_Y = 130;
    private static final float ACCUSATION_Y = 235;

    public void addPlayer(ServerPlayerEntity player) {
        getOrCreateStatusHud(player);
    }

    public void removePlayer(ServerPlayerEntity player) {
        DisplayHud.removeHud(player, ROUND_STATUS_ID);
        DisplayHud.removeHud(player, ACCUSATION_TITLE_ID);
        DisplayHud.removeHud(player, ACCUSATION_LOG_ID);
    }

    public void tick(MinecraftServer server, GameManager manager) {
        GameManager.RoundAccusationSnapshot accusationSnapshot = manager.getRoundAccusationSnapshot();
        List<GameManager.RoundAccusationEvent> recentAccusations = collectRecentAccusations(manager, accusationSnapshot);
        String accusationTextKey = buildAccusationTextKey(recentAccusations);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            if (!info.tts$displayHudEnabled()) {
                removePlayer(player);
                continue;
            }

            TextDisplayHud hud = getOrCreateStatusHud(player);
            String text = buildStatusText(manager, player);
            if (text.equals(hud.getExtraData(LAST_TEXT_KEY))) {
                updateAccusationLog(player, recentAccusations, accusationTextKey);
                continue;
            }

            hud.setText(GameManager.byMiniMessage(text));
            hud.setExtraData(LAST_TEXT_KEY, text);
            updateAccusationLog(player, recentAccusations, accusationTextKey);
        }
    }

    private static TextDisplayHud getOrCreateStatusHud(ServerPlayerEntity player) {
        DisplayHud existing = DisplayHud.getHud(player, ROUND_STATUS_ID);
        if (existing instanceof TextDisplayHud textHud) {
            return textHud;
        }

        if (existing != null) {
            existing.remove();
        }

        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(STATUS_LINE_WIDTH);
        hud.setTextAlignment(DisplayEntity.TextDisplayEntity.TextAlignment.RIGHT);
        hud.setShadowToggle(true);
        hud.setScale(STATUS_TEXT_SCALE, STATUS_TEXT_SCALE, 1);
        hud.setViewRange(1000);
        hud.setLocation(STATUS_X, STATUS_Y, 0);
        hud.spawn(player, ROUND_STATUS_ID);
        return hud;
    }

    private static void updateAccusationLog(
            ServerPlayerEntity player,
            List<GameManager.RoundAccusationEvent> recentAccusations,
            String textKey
    ) {
        if (recentAccusations.isEmpty()) {
            DisplayHud.removeHud(player, ACCUSATION_TITLE_ID);
            DisplayHud.removeHud(player, ACCUSATION_LOG_ID);
            return;
        }

        getOrCreateAccusationTitleHud(player);
        updateRecentAccusationLog(player, recentAccusations, textKey);
    }

    private static void updateRecentAccusationLog(
            ServerPlayerEntity player,
            List<GameManager.RoundAccusationEvent> recentAccusations,
            String textKey
    ) {
        if (recentAccusations.isEmpty()) {
            DisplayHud.removeHud(player, ACCUSATION_LOG_ID);
            return;
        }

        TextDisplayHud hud = getOrCreateAccusationHud(player);
        if (textKey.equals(hud.getExtraData(ACCUSATION_LAST_TEXT_KEY))) {
            return;
        }

        hud.setText(buildAccusationLogText(recentAccusations));
        hud.setExtraData(ACCUSATION_LAST_TEXT_KEY, textKey);
    }

    private static TextDisplayHud getOrCreateAccusationTitleHud(ServerPlayerEntity player) {
        DisplayHud existing = DisplayHud.getHud(player, ACCUSATION_TITLE_ID);
        if (existing instanceof TextDisplayHud textHud) {
            return textHud;
        }

        if (existing != null) {
            existing.remove();
        }

        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(ACCUSATION_TITLE_LINE_WIDTH);
        hud.setTextAlignment(DisplayEntity.TextDisplayEntity.TextAlignment.LEFT);
        hud.setShadowToggle(true);
        hud.setScale(ACCUSATION_TITLE_TEXT_SCALE, ACCUSATION_TITLE_TEXT_SCALE, 1);
        hud.setViewRange(1000);
        hud.setLocation(ACCUSATION_X, ACCUSATION_TITLE_Y, 0);
        hud.setText(GameManager.byMiniMessage("<red>지목 기록</red>"));
        hud.spawn(player, ACCUSATION_TITLE_ID);
        return hud;
    }

    private static TextDisplayHud getOrCreateAccusationHud(ServerPlayerEntity player) {
        DisplayHud existing = DisplayHud.getHud(player, ACCUSATION_LOG_ID);
        if (existing instanceof TextDisplayHud textHud) {
            return textHud;
        }

        if (existing != null) {
            existing.remove();
        }

        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(ACCUSATION_LINE_WIDTH);
        hud.setTextAlignment(DisplayEntity.TextDisplayEntity.TextAlignment.LEFT);
        hud.setShadowToggle(true);
        hud.setScale(ACCUSATION_TEXT_SCALE, ACCUSATION_TEXT_SCALE, 1);
        hud.setViewRange(1000);
        hud.setLocation(ACCUSATION_X, ACCUSATION_Y, 0);
        hud.spawn(player, ACCUSATION_LOG_ID);
        return hud;
    }

    private static String buildStatusText(GameManager manager, ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        GameManager.Phase phase = manager.getCurrentPhase();
        Role role = info.tts$getRole();

        StringBuilder builder = new StringBuilder()
                .append("<red>T</red><green>T</green><blue>T</blue>\n")
                .append("<gray>단계</gray> ")
                .append(formatPhase(phase))
                .append("\n")
                .append("<gray>직업</gray> ")
                .append(formatRole(phase, role));

        if (phase.canShowRole()) {
            builder.append("\n")
                    .append("<gray>인원</gray> ")
                    .append(formatRemainingParticipantCount(
                            role,
                            player.getGameMode(),
                            manager.getAliveParticipantCount(),
                            manager.getConfirmedRemainingParticipantCount()
                    ));
        }

        return builder.append("\n")
                .append("<gray>포인트</gray> <yellow>")
                .append(info.tts$getPoints())
                .append("P</yellow>")
                .toString();
    }

    private static String formatPhase(GameManager.Phase phase) {
        String color = switch (phase) {
            case NOT_STARTED, END_GAME, INITIALIZE -> "white";
            case OVER_TIME -> "red";
            case POST_GAME, MIDDLE_GAME -> "green";
        };
        return "<" + color + ">" + phase.displayName + "</" + color + ">";
    }

    private static String formatRole(GameManager.Phase phase, Role role) {
        if (!phase.canShowRole()) {
            return "<dark_gray>???</dark_gray>";
        }
        return "<#" + role.hexColor + ">" + role.krRoleName + "</#" + role.hexColor + ">";
    }

    private static String formatRemainingParticipantCount(Role role, GameMode gameMode, int aliveCount, int confirmedCount) {
        String color = RoundHudFormatter.resolveRemainingParticipantCountColor(role, gameMode);
        String value = RoundHudFormatter.formatRemainingParticipantCount(role, gameMode, aliveCount, confirmedCount);
        return "<" + color + ">" + value + "</" + color + ">";
    }

    private static List<GameManager.RoundAccusationEvent> collectRecentAccusations(
            GameManager manager,
            GameManager.RoundAccusationSnapshot accusationSnapshot
    ) {
        if (!manager.getCurrentPhase().canShowRole()) {
            return List.of();
        }

        int currentElapsedSeconds = manager.getRoundElapsedSeconds();
        List<GameManager.RoundAccusationEvent> recentEvents = accusationSnapshot.events().stream()
                .filter(event -> currentElapsedSeconds - event.elapsedSeconds() < ACCUSATION_VISIBLE_SECONDS)
                .toList();
        int fromIndex = Math.max(0, recentEvents.size() - MAX_VISIBLE_ACCUSATIONS);
        return recentEvents.subList(fromIndex, recentEvents.size());
    }

    private static Text buildAccusationLogText(List<GameManager.RoundAccusationEvent> events) {
        Text text = Text.empty();

        for (int index = events.size() - 1; index >= 0; index--) {
            GameManager.RoundAccusationEvent event = events.get(index);
            if (text.getString().isEmpty()) {
                text = buildAccusationLine(event);
                continue;
            }

            text = text.copy()
                    .append(Text.literal("\n"))
                    .append(buildAccusationLine(event));
        }

        return text;
    }

    private static Text buildAccusationLine(GameManager.RoundAccusationEvent event) {
        return Text.empty()
                .append(buildAccuserText(event))
                .append(Text.literal(" -> ").formatted(Formatting.GRAY))
                .append(buildTargetText(event));
    }

    private static Text buildAccuserText(GameManager.RoundAccusationEvent event) {
        return prependAvatar(
                AvatarTextRenderer.resolveSmallAvatar(event.accuserUuid(), event.accuserName(), false),
                Text.literal(event.accuserName()).formatted(Formatting.YELLOW)
        );
    }

    private static Text buildTargetText(GameManager.RoundAccusationEvent event) {
        return prependAvatar(
                AvatarTextRenderer.resolveSmallAvatar(event.targetUuid(), event.targetName(), false),
                Text.literal(event.targetName()).formatted(Formatting.RED)
        );
    }

    private static Text prependAvatar(Text avatar, Text name) {
        return Text.empty()
                .append(avatar.copy())
                .append(Text.literal(" "))
                .append(name);
    }

    private static String buildAccusationTextKey(List<GameManager.RoundAccusationEvent> events) {
        StringBuilder builder = new StringBuilder();
        for (GameManager.RoundAccusationEvent event : events) {
            builder.append(event.elapsedSeconds())
                    .append('|')
                    .append(event.accuserUuid())
                    .append('|')
                    .append(event.targetUuid())
                    .append(';');
        }
        return builder.toString();
    }
}
