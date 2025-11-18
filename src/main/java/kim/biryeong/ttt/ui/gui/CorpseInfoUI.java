package kim.biryeong.ttt.ui.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.ttt.entity.CorpseEntity;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.role.Role;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 들어가야 할 것 : ID / 직업 / 갑옷 여부 / 사망원인
 */
public class CorpseInfoUI extends SimpleGui {
    private final Role role;
    private final String userName;

    public CorpseInfoUI(ServerPlayerEntity player, CorpseEntity corpse) {
        super(ScreenHandlerType.GENERIC_9X1, player, true);
        this.role = corpse.getRole();
        this.userName = corpse.getGameProfile().name();
    }

    private void initialize() {
        this.setTitle(GameManager.byMiniMessage("%s 님의 시체".formatted(this.userName)));
        for (int i = 0; i <= this.size; i++) {
            if (i / 3 == 0) {
                this.setSlot(i, new GuiElementBuilder().setItem(getRoleItem()));
            } else if (i / 3 == 1) {
                this.setSlot(i, new GuiElementBuilder());
            }
        }
    }

    {

    }

    private Item getRoleItem() {
        return switch  (role) {
            case INNOCENT -> Items.GREEN_STAINED_GLASS_PANE;
            case TRAITOR -> Items.RED_STAINED_GLASS_PANE;
            case DETECTIVE -> Items.BLUE_STAINED_GLASS_PANE;
            default -> Items.GRAY_STAINED_GLASS_PANE;
        };
    }
}
