package kim.biryeong.ttt.item.detective;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.util.NonThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

/**
 * 가지고 있을 때 살인자 정보같은거 볼 수 있는 아이템.
 */
public class DNAScanner extends SimplePolymerItem implements NonThrowable {
    public DNAScanner(Settings settings) {
        super(settings, Items.TRIAL_KEY, false);
    }

    @Override
    public void modifyClientTooltip(List<Text> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(GameManager.byMiniMessage("<blue> 탐정용 아이템입니다. 소지 시 시체를 조사하면"));
        tooltip.add(GameManager.byMiniMessage("<blue> 추가 정보가 나타납니다."));
    }
}
