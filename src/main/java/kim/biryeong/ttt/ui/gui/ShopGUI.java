package kim.biryeong.ttt.ui.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

public class ShopGUI extends SimpleGui {
    private final InGamePlayerInfoProvider provider;

    public ShopGUI(ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, player, false);
    }

    {
        this.provider = (InGamePlayerInfoProvider) this.player;
        this.setTitle(GameManager.byMiniMessage("<gold>상점</gold>"));

    }
}
