package kim.biryeong.ttt.ui.sidebar;

import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public final class GameDefaultSidebarGameTest {
    @GameTest
    public void confirmedRemainingCountIsShownOnlyToAliveInnocentTeam(TestContext context) {
        context.assertTrue(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.INNOCENT, GameMode.ADVENTURE),
                Text.literal("alive innocent should see confirmed remaining count")
        );
        context.assertTrue(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.DETECTIVE, GameMode.ADVENTURE),
                Text.literal("alive detective should see confirmed remaining count")
        );
        context.assertFalse(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.INNOCENT, GameMode.SPECTATOR),
                Text.literal("dead innocent spectator should not see confirmed remaining count")
        );
        context.assertFalse(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.DETECTIVE, GameMode.SPECTATOR),
                Text.literal("dead detective spectator should not see confirmed remaining count")
        );
        context.complete();
    }

    @GameTest
    public void traitorAndSpectatorViewsKeepAliveParticipantCount(TestContext context) {
        context.assertFalse(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.TRAITOR, GameMode.ADVENTURE),
                Text.literal("traitor should keep seeing actual alive count")
        );
        context.assertFalse(
                GameDefaultSidebar.shouldShowConfirmedRemainingCount(Role.SPECTATOR, GameMode.SPECTATOR),
                Text.literal("spectator should keep seeing actual alive count")
        );
        context.assertEquals(
                "5명",
                GameDefaultSidebar.formatRemainingParticipantCount(Role.TRAITOR, GameMode.ADVENTURE, 5, 8),
                Text.literal("traitor display should use alive participant count")
        );
        context.assertEquals(
                "8명",
                GameDefaultSidebar.formatRemainingParticipantCount(Role.INNOCENT, GameMode.ADVENTURE, 5, 8),
                Text.literal("innocent display should use confirmed participant count")
        );
        context.assertEquals(
                "5명",
                GameDefaultSidebar.formatRemainingParticipantCount(Role.DETECTIVE, GameMode.SPECTATOR, 5, 8),
                Text.literal("spectating detective should fall back to alive participant count")
        );
        context.complete();
    }
}
