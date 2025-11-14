package kim.biryeong.ttt.ui.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 들어가야 할 것 : ID / 직업 / 갑옷 여부 / 환경 사망 여부
 */
public class CorpseInfoUI extends SimpleGui {
    public CorpseInfoUI(ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, player, true);

    }




}
