package kim.biryeong.ttt.ui.hud;

import kim.biryeong.dantashader.displayhud.DisplayHud;
import kim.biryeong.dantashader.displayhud.TextDisplayHud;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.AvatarTextRenderer;
import kim.biryeong.ttt.util.Scheduler;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;

public final class CorpseHud {
    private static final String CORPSE_INFO_ID = "ttt:corpse_info";
    private static final int DISPLAY_TICKS = 100;
    private static final int CORPSE_LINE_WIDTH = 310;
    private static final float CORPSE_TEXT_SCALE = 138;
    private static final float CORPSE_X = 1500;
    private static final float CORPSE_Y = 450;

    private CorpseHud() {
        throw new IllegalStateException("Utility class");
    }

    public static void show(CorpseEntity corpse, ServerPlayerEntity viewer) {
        DisplayHud.removeHud(viewer, CORPSE_INFO_ID);

        String deadPlayerName = corpse.getGameProfile().getName();
        UUID deadPlayerUuid = corpse.getGameProfile().getId();
        Role deadPlayerRole = corpse.getRole();
        DamageSource damageSource = corpse.getDamageSource();
        int secondsSinceDeath = Math.max(0, GameManager.getInstance().getRoundElapsedSeconds() - corpse.getDeathElapsedSeconds());
        Role viewerRole = ((InGamePlayerInfoProvider) viewer).tts$getRole();
        boolean hasScanner = viewer.getInventory().containsAny(Set.of(ModItems.DNA_SCANNER));

        TextDisplayHud hud = new TextDisplayHud();
        hud.setLineWidth(CORPSE_LINE_WIDTH);
        hud.setShadowToggle(true);
        hud.setScale(CORPSE_TEXT_SCALE, CORPSE_TEXT_SCALE, 1);
        hud.setViewRange(1000);
        hud.setLocation(CORPSE_X, CORPSE_Y, 0);
        hud.setText(buildCorpseInfo(deadPlayerUuid, deadPlayerName, deadPlayerRole, viewerRole, hasScanner, damageSource, secondsSinceDeath));
        hud.spawn(viewer, CORPSE_INFO_ID);

        Scheduler.INSTANCE.submit(server -> {
            if (DisplayHud.getHud(viewer, CORPSE_INFO_ID) == hud) {
                DisplayHud.removeHud(viewer, CORPSE_INFO_ID);
            }
        }, DISPLAY_TICKS);
    }

    public static boolean canRevealKillerInfo(Role viewerRole, boolean hasScanner) {
        return viewerRole == Role.DETECTIVE && hasScanner;
    }

    private static Text buildCorpseInfo(
            UUID deadPlayerUuid,
            String deadPlayerName,
            Role deadPlayerRole,
            Role viewerRole,
            boolean hasScanner,
            DamageSource damageSource,
            int secondsSinceDeath
    ) {
        Text text = Text.empty()
                .append(AvatarTextRenderer.resolveSmallAvatar(deadPlayerUuid, deadPlayerName, false))
                .append(GameManager.byMiniMessage(" <white>" + deadPlayerName + "님의 시체</white>\n"))
                .append(GameManager.byMiniMessage(
                        ("<gray>직업</gray> : <#color>" + deadPlayerRole.krRoleName + "</#color>\n")
                                .replace("color", deadPlayerRole.hexColor)
                ))
                .append(GameManager.byMiniMessage("<gray>사망 시점</gray> <yellow>약 " + secondsSinceDeath + "초 전</yellow>\n"))
                .append(GameManager.byMiniMessage("<gray>사망 원인</gray> <yellow>" + resolveKillResult(damageSource) + "</yellow>"));

        if (canRevealKillerInfo(viewerRole, hasScanner)) {
            text = text.copy()
                    .append(Text.literal("\n"))
                    .append(buildKillerInfoLine(damageSource));
        }

        return text;
    }

    private static Text buildKillerInfoLine(DamageSource source) {
        if (source == null || !(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return GameManager.byMiniMessage("<gray>살해자</gray> <dark_gray>확인 불가</dark_gray>");
        }
        return GameManager.byMiniMessage("<gray>살해자</gray> <red>" + attacker.getGameProfile().getName() + "</red>");
    }

    private static String resolveKillResult(DamageSource source) {
        if (source == null) {
            return "알 수 없음";
        }

        return switch (source.getType().msgId()) {
            case "arrow" -> "원거리";
            case "player" -> "근접";
            default -> "환경/기타";
        };
    }
}
