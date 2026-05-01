package kim.biryeong.ttt.ui.hud;

import kim.biryeong.ttt.player.role.Role;
import net.minecraft.world.GameMode;

public final class RoundHudFormatter {
    private RoundHudFormatter() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean shouldShowConfirmedRemainingCount(Role role, GameMode gameMode) {
        return gameMode != GameMode.SPECTATOR && (role == Role.INNOCENT || role == Role.DETECTIVE);
    }

    public static String formatRemainingParticipantCount(Role role, GameMode gameMode, int aliveCount, int confirmedCount) {
        int displayedCount = shouldShowConfirmedRemainingCount(role, gameMode) ? confirmedCount : aliveCount;
        return displayedCount + "명";
    }

    public static String resolveRemainingParticipantCountColor(Role role, GameMode gameMode) {
        return shouldShowConfirmedRemainingCount(role, gameMode) ? "gray" : "yellow";
    }
}
