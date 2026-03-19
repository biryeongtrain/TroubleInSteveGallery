package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import eu.pb4.sidebars.api.lines.SimpleSidebarLine;
import eu.pb4.sidebars.api.lines.SuppliedSidebarLine;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class GameDefaultSidebar extends Sidebar {
    public GameDefaultSidebar() {
        super(Priority.MEDIUM);
    }

    {

        this.setTitle(GameManager.byMiniMessage("<red>T</red><green>T</green><blue>T</blue>"));
        this.addLines(new SimpleSidebarLine(0, Text.empty(), BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, player -> {
            GameManager.Phase phase = GameManager.getInstance().getCurrentPhase();
            String phaseString = phase.displayName;
            String color = switch (phase) {
                case NOT_STARTED, END_GAME, INITIALIZE -> "white";
                case OVER_TIME -> "red";
                case POST_GAME, MIDDLE_GAME ->  "green";
            };

            return GameManager.byMiniMessage(("게임 단계 : <color>" + phaseString + "</color>").replace("color", color));
        }, (players) -> BlankNumberFormat.INSTANCE));

        this.addLines(new SimpleSidebarLine(0, Text.empty(), BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, (player) -> {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            GameManager.Phase phase = GameManager.getInstance().getCurrentPhase();
            String str = "직업 : <#color>%s</#color>"
                    .formatted(phase.canShowRole() ? info.tts$getRole().krRoleName : "???")
                    .replace("color", phase.canShowRole() ? info.tts$getRole().hexColor : "36454F");

            return GameManager.byMiniMessage(str);
        }, (players) -> BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, (player) -> {
            GameManager manager = GameManager.getInstance();
            GameManager.Phase phase = manager.getCurrentPhase();
            if (!phase.canShowRole()) {
                return Text.empty();
            }

            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            String value = formatRemainingParticipantCount(
                    info.tts$getRole(),
                    player.getGameMode(),
                    manager.getAliveParticipantCount(),
                    manager.getConfirmedRemainingParticipantCount()
            );
            String color = resolveRemainingParticipantCountColor(info.tts$getRole(), player.getGameMode());
            return GameManager.byMiniMessage(("남은 인원 : <color>" + value + "</color>").replace("color", color));
        }, (players) -> BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, (player) -> {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            return GameManager.byMiniMessage("포인트 : <yellow>%d</yellow>".formatted(info.tts$getPoints()));
        }, (players) -> BlankNumberFormat.INSTANCE));
        this.addLines(new SimpleSidebarLine(0, Text.empty(), BlankNumberFormat.INSTANCE));

        this.setDefaultNumberFormat(BlankNumberFormat.INSTANCE);
        this.show();
    }

    static boolean shouldShowConfirmedRemainingCount(Role role, GameMode gameMode) {
        return gameMode != GameMode.SPECTATOR && (role == Role.INNOCENT || role == Role.DETECTIVE);
    }

    static String formatRemainingParticipantCount(Role role, GameMode gameMode, int aliveCount, int confirmedCount) {
        int displayedCount = shouldShowConfirmedRemainingCount(role, gameMode) ? confirmedCount : aliveCount;
        return displayedCount + "명";
    }

    static String resolveRemainingParticipantCountColor(Role role, GameMode gameMode) {
        return shouldShowConfirmedRemainingCount(role, gameMode) ? "gray" : "yellow";
    }
}
