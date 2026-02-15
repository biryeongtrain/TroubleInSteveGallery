package kim.biryeong.ttt;

import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

public final class EventsGameTest {
    @GameTest
    public void spectatorChatRoutingMatchesRoundAndAliveState(TestContext context) {
        context.assertFalse(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.NOT_STARTED, false),
                Text.literal("not started does not route spectator chat")
        );
        context.assertFalse(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.INITIALIZE, false),
                Text.literal("initialize does not route spectator chat")
        );
        context.assertTrue(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.POST_GAME, false),
                Text.literal("post game routes dead sender to spectator chat")
        );
        context.assertTrue(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.MIDDLE_GAME, false),
                Text.literal("middle game routes dead sender to spectator chat")
        );
        context.assertTrue(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.OVER_TIME, false),
                Text.literal("overtime routes dead sender to spectator chat")
        );
        context.assertFalse(
                Events.shouldRouteToSpectatorChat(GameManager.Phase.MIDDLE_GAME, true),
                Text.literal("alive sender keeps normal chat")
        );
        context.complete();
    }

    @GameTest
    public void spectatorChatRecipientsMatchRoleAndAliveState(TestContext context) {
        context.assertTrue(
                Events.canReceiveSpectatorChat(false, Role.INNOCENT),
                Text.literal("dead non-spectator receives spectator chat")
        );
        context.assertTrue(
                Events.canReceiveSpectatorChat(true, Role.SPECTATOR),
                Text.literal("alive spectator role receives spectator chat")
        );
        context.assertFalse(
                Events.canReceiveSpectatorChat(true, Role.INNOCENT),
                Text.literal("alive innocent does not receive spectator chat")
        );
        context.assertFalse(
                Events.canReceiveSpectatorChat(true, Role.DETECTIVE),
                Text.literal("alive detective does not receive spectator chat")
        );
        context.assertFalse(
                Events.canReceiveSpectatorChat(true, Role.TRAITOR),
                Text.literal("alive traitor does not receive spectator chat")
        );
        context.complete();
    }
}
