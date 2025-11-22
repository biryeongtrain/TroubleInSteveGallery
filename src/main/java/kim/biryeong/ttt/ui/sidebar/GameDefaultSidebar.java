package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import eu.pb4.sidebars.api.lines.SimpleSidebarLine;
import eu.pb4.sidebars.api.lines.SuppliedSidebarLine;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.Text;

public class GameDefaultSidebar extends Sidebar {
    public GameDefaultSidebar() {
        super(Priority.MEDIUM);
    }

    {

        this.setTitle(GameManager.byMiniMessage("<red>T</red><green>T</green><blue>T</blue>"));
        this.addLines(new SimpleSidebarLine(0, Text.empty(), BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, (player -> {
            GameManager.Phase phase = GameManager.getInstance().getCurrentPhase();
            String phaseString = phase.displayName;
            String color = switch (phase) {
                case NOT_STARTED, END_GAME, INITIALIZE -> "white";
                case OVER_TIME -> "red";
                case POST_GAME, MIDDLE_GAME ->  "green";
            };

            return GameManager.byMiniMessage(("게임 단계 : <color>" + phaseString + "</color>").replace("color", color));
        }), (players) -> BlankNumberFormat.INSTANCE));
        this.addLines(new SuppliedSidebarLine(0, (player) -> {
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            GameManager.Phase phase = GameManager.getInstance().getCurrentPhase();
            int time = GameManager.getInstance().getLeftTicks();
            StringBuilder builder = new StringBuilder();
            builder.append("남은 시간 : ");

            if (time == Integer.MIN_VALUE) {
                return Text.empty();
            }

            if (phase == GameManager.Phase.OVER_TIME) {
                if (info.tts$getRole() == Role.TRAITOR) {
                    builder.append("<red>%s초</red>".formatted(String.valueOf(time / 20)));
                } else {
                    builder.append("<red>연장 시간</red>");
                }
            } else {
                builder.append("<green>%s초</green>".formatted(String.valueOf(time / 20)));
            }

            return GameManager.byMiniMessage(builder.toString());
        }, (p) -> BlankNumberFormat.INSTANCE));

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
            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
            return GameManager.byMiniMessage("포인트 : <yellow>%d</yellow>".formatted(info.tts$getPoints()));
        }, (players) -> BlankNumberFormat.INSTANCE));
        this.setDefaultNumberFormat(BlankNumberFormat.INSTANCE);
        this.show();
    }
}
