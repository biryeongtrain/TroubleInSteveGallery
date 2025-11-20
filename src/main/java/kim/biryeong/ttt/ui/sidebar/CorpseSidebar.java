package kim.biryeong.ttt.ui.sidebar;

import eu.pb4.sidebars.api.Sidebar;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.ModItems;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;

public class CorpseSidebar extends Sidebar {
    private final String userName;
    private final Role role;
    private final Role userRole;
    private final ServerPlayerEntity user;
    private DamageSource source;

    public CorpseSidebar(CorpseEntity corpse, ServerPlayerEntity user) {
        super(Priority.HIGH);
        this.userName = corpse.getGameProfile().name();
        this.role = corpse.getRole();
        this.source = corpse.getDamageSource();
        this.user = user;
        this.userRole = ((InGamePlayerInfoProvider) user).tts$getRole();
        this.initialize();
    }

    private void initialize() {
        this.setTitle(GameManager.byMiniMessage("%s 님의 시체 정보".formatted(userName)));
        this.addLines(GameManager.byMiniMessage(
                        "직업 : <#color>%s</#color>"
                                .formatted(this.role.krRoleName)
                                .replace("color", String.valueOf(this.role.hexColor))
                )
        );

        String killResult = this.source == null ?
                "알 수 없음" :
                switch (this.source.getType().msgId()) {
                    case ("arrow") -> "원거리 피해";
                    case "player" -> "근접 피해";
                    default -> "환경 변수";
                };
        this.addLines(GameManager.byMiniMessage("사망 원인 : %s".formatted(killResult)));

        if (this.role == Role.DETECTIVE) {
            boolean hasScanner = this.user.getInventory().containsAny(Set.of(ModItems.DNA_SCANNER));
            if (hasScanner) {
                boolean killerRecorded = this.source != null && this.source.getAttacker() != null && this.source.getAttacker() instanceof ServerPlayerEntity;

                Text text = killerRecorded ?
                        GameManager.byMiniMessage("살인자 정보가 없습니다.") :
                        GameManager.byMiniMessage("살인자 : <red>%s<red>".formatted(GameManager.getInstance().getPlayer(this.source.getAttacker().getUuid()).getStringifiedName()))
                ;

                this.addLines(text);
            }
        }

        this.show();
        this.addPlayer(this.user);
    }

    {
        this.setDefaultNumberFormat(BlankNumberFormat.INSTANCE);
    }
}
