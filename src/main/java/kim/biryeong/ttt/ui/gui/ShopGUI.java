package kim.biryeong.ttt.ui.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.util.ShopUtil;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class ShopGUI extends SimpleGui {
    private final InGamePlayerInfoProvider provider;

    public ShopGUI(ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, player, true);
    }

    {
        this.provider = (InGamePlayerInfoProvider) this.player;
        this.setTitle(GameManager.byMiniMessage("<gold>상점</gold>"));
        List<ShopElement> entries = ShopUtil.getShopElements(provider.tts$getRole());
        entries.forEach(shopElement -> {
            var builder = new GuiElementBuilder().setItem(shopElement.item());
            if (shopElement.name() != null) {
                builder.setItemName(shopElement.name());
            }
            shopElement.descriptions().forEach(builder::addLoreLine);
            builder.setCallback((index, clickType, type) -> {
                int playerPoints = provider.tts$getPoints();
                if (playerPoints >= shopElement.point()) {
                    provider.tts$addPoints(-shopElement.point(), InGamePlayerInfoProvider.PointReason.ROLE_PLAYING);
                    shopElement.handler().accept(shopElement.item(), this.player);
                    this.close();
                } else {
                    this.player.sendMessage(GameManager.byMiniMessage("<red>포인트가 부족합니다!</red>"), false);
                }
            });

            this.addSlot(builder.build());
        });
    }
}
