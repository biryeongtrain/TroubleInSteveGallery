package kim.biryeong.ttt;

import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.role.Role;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

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

    @GameTest
    public void spectatorChatDisplayUsesGrayFormatting(TestContext context) {
        Text rendered = Events.buildSpectatorChatText(Text.literal("Observer"), Text.literal("hello"));
        TextColor gray = TextColor.fromFormatting(Formatting.GRAY);

        context.assertEquals(
                "[관전자] Observer: hello",
                rendered.getString(),
                Text.literal("spectator chat text includes prefix, sender, and message")
        );

        for (Text sibling : rendered.getSiblings()) {
            context.assertEquals(
                    gray,
                    sibling.getStyle().getColor(),
                    Text.literal("every spectator chat segment should be gray")
            );
        }
        context.complete();
    }

    @GameTest
    public void detectiveBlueNameChatRoutingMatchesRoleAndAliveState(TestContext context) {
        context.assertTrue(
                Events.shouldRouteToDetectiveBlueNameChat(GameManager.Phase.MIDDLE_GAME, true, Role.DETECTIVE),
                Text.literal("alive detective in middle game should use blue-name chat")
        );
        context.assertTrue(
                Events.shouldRouteToDetectiveBlueNameChat(GameManager.Phase.OVER_TIME, true, Role.DETECTIVE),
                Text.literal("alive detective in overtime should use blue-name chat")
        );
        context.assertFalse(
                Events.shouldRouteToDetectiveBlueNameChat(GameManager.Phase.POST_GAME, true, Role.DETECTIVE),
                Text.literal("warmup should not use blue-name chat")
        );
        context.assertFalse(
                Events.shouldRouteToDetectiveBlueNameChat(GameManager.Phase.MIDDLE_GAME, false, Role.DETECTIVE),
                Text.literal("dead detective should not use blue-name chat")
        );
        context.assertFalse(
                Events.shouldRouteToDetectiveBlueNameChat(GameManager.Phase.MIDDLE_GAME, true, Role.INNOCENT),
                Text.literal("innocent should not use blue-name chat")
        );
        context.complete();
    }

    @GameTest
    public void detectiveBlueNameChatTextColorsOnlyName(TestContext context) {
        Text rendered = Events.buildDetectiveBlueNameChatText(Text.literal("Detective"), Text.literal("sus"));
        TextColor blue = TextColor.fromFormatting(Formatting.BLUE);

        context.assertEquals(
                "Detective: sus",
                rendered.getString(),
                Text.literal("detective chat text should contain name and message")
        );

        context.assertEquals(3, rendered.getSiblings().size(), Text.literal("detective chat should have 3 segments"));
        context.assertEquals(
                blue,
                rendered.getSiblings().get(0).getStyle().getColor(),
                Text.literal("name segment should be blue")
        );
        context.assertTrue(
                rendered.getSiblings().get(2).getStyle().getColor() == null,
                Text.literal("message segment should keep default color")
        );
        context.complete();
    }

    @GameTest
    public void inGameChannelChatConversionUsesKoreanKeyboardConverter(TestContext context) {
        Text converted = Events.convertInGameChannelChatContent(Text.literal("dkssud"));
        context.assertEquals(
                "안녕",
                converted.getString(),
                Text.literal("english 2-beolsik input should convert to korean in in-game channel")
        );

        Text mixed = Events.convertInGameChannelChatContent(Text.literal("한글 english"));
        context.assertEquals(
                "한글 english",
                mixed.getString(),
                Text.literal("mixed korean and english input should remain unchanged")
        );
        context.complete();
    }
}
