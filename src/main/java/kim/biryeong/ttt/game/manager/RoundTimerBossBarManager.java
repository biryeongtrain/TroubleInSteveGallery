package kim.biryeong.ttt.game.manager;

import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RoundTimerBossBarManager {
    private static final int TICKS_PER_SECOND = 20;

    private final Map<UUID, ServerBossBar> barsByPlayer = new HashMap<>();

    void tick(MinecraftServer server, GameManager.Phase phase, int leftTicks) {
        Set<UUID> onlinePlayerUuids = new HashSet<>();
        boolean shouldShow = shouldShow(phase, leftTicks);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();
            onlinePlayerUuids.add(playerUuid);

            if (!shouldShow) {
                removePlayer(player);
                continue;
            }

            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            Role role = info.tts$getRole();

            ServerBossBar bossBar = this.barsByPlayer.computeIfAbsent(playerUuid, ignored -> createBossBar());
            bossBar.setColor(resolveColor(phase));
            bossBar.setName(Text.literal(resolveTitle(phase, leftTicks, role)));
            bossBar.setPercent(calculateProgress(phase, leftTicks, resolveTotalTicks(phase, Config.getInstance()), role));
            bossBar.addPlayer(player);
            bossBar.setVisible(true);
        }

        this.barsByPlayer.keySet().removeIf(uuid -> !onlinePlayerUuids.contains(uuid));
    }

    void removePlayer(ServerPlayerEntity player) {
        ServerBossBar bossBar = this.barsByPlayer.remove(player.getUuid());
        if (bossBar == null) {
            return;
        }

        bossBar.removePlayer(player);
        bossBar.setVisible(false);
    }

    private static ServerBossBar createBossBar() {
        return new ServerBossBar(Text.empty(), BossBar.Color.WHITE, BossBar.Style.PROGRESS);
    }

    static boolean shouldShow(GameManager.Phase phase, int leftTicks) {
        if (leftTicks == Integer.MIN_VALUE) {
            return false;
        }

        return phase == GameManager.Phase.POST_GAME
                || phase == GameManager.Phase.MIDDLE_GAME
                || phase == GameManager.Phase.OVER_TIME;
    }

    static String resolveTitle(GameManager.Phase phase, int leftTicks, Role role) {
        int seconds = toSeconds(leftTicks);

        return switch (phase) {
            case POST_GAME -> "라운드 시작까지 " + seconds + "초";
            case MIDDLE_GAME -> "남은 시간 " + seconds + "초";
            case OVER_TIME -> role == Role.TRAITOR
                    ? "오버타임 " + seconds + "초"
                    : "오버타임 진행 중";
            default -> "";
        };
    }

    static int toSeconds(int ticks) {
        return Math.max(0, ticks) / TICKS_PER_SECOND;
    }

    static float calculateProgress(GameManager.Phase phase, int leftTicks, int totalTicks, Role role) {
        if (phase == GameManager.Phase.OVER_TIME && role != Role.TRAITOR) {
            return 1.0f;
        }

        if (totalTicks <= 0) {
            return 0.0f;
        }

        int clampedTicks = Math.max(0, leftTicks);
        float progress = clampedTicks / (float) totalTicks;
        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    private static int resolveTotalTicks(GameManager.Phase phase, Config config) {
        return switch (phase) {
            case POST_GAME -> config.gameStartCountdownSeconds * TICKS_PER_SECOND;
            case MIDDLE_GAME -> config.playTimeSeconds * TICKS_PER_SECOND;
            case OVER_TIME -> config.maxOverTimeSeconds * TICKS_PER_SECOND;
            default -> 0;
        };
    }

    private static BossBar.Color resolveColor(GameManager.Phase phase) {
        return switch (phase) {
            case POST_GAME -> BossBar.Color.YELLOW;
            case OVER_TIME -> BossBar.Color.RED;
            case MIDDLE_GAME -> BossBar.Color.GREEN;
            default -> BossBar.Color.WHITE;
        };
    }
}
