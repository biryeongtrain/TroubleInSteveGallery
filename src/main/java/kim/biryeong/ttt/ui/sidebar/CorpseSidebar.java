package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.AvatarTextRenderer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;

public class CorpseSidebar extends Sidebar {
    private final String userName;
    private final Role role;
    private final Role userRole;
    private final ServerPlayerEntity user;
    private final DamageSource source;
    private final UUID uuid;

    public CorpseSidebar(CorpseEntity corpse, ServerPlayerEntity user) {
        super(Priority.HIGH);
        this.userName = corpse.getGameProfile().getName();
        this.role = corpse.getRole();
        this.source = corpse.getDamageSource();
        this.uuid = corpse.getUuid();
        this.user = user;
        this.userRole = ((InGamePlayerInfoProvider) user).tts$getRole();
        this.initialize();
    }

    private void initialize() {
        this.setTitle(GameManager.byMiniMessage("%s님의 시체 정보".formatted(this.userName)));
        this.addLines(AvatarTextRenderer.resolveSmallAvatar(this.uuid, userName, false));
        this.addLines(GameManager.byMiniMessage(
                "직업: <#color>%s</#color>"
                        .formatted(this.role.krRoleName)
                        .replace("color", this.role.hexColor)
        ));

        this.addLines(GameManager.byMiniMessage("사망 원인: %s".formatted(resolveKillResult(this.source))));

        boolean hasScanner = this.user.getInventory().containsAny(Set.of(ModItems.DNA_SCANNER));
        if (canRevealKillerInfo(this.userRole, hasScanner)) {
            this.addLines(this.buildKillerInfoLine());
        }

        this.show();
        this.addPlayer(this.user);
    }

    static boolean canRevealKillerInfo(Role viewerRole, boolean hasScanner) {
        return viewerRole == Role.DETECTIVE && hasScanner;
    }

    private Text buildKillerInfoLine() {
        if (this.source == null || !(this.source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return GameManager.byMiniMessage("살해자 정보를 확인할 수 없습니다.");
        }
        return GameManager.byMiniMessage("살해자: <red>%s</red>".formatted(attacker.getGameProfile().getName()));
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

    {
        this.setDefaultNumberFormat(BlankNumberFormat.INSTANCE);
    }
}
