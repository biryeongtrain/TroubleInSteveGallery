package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import kim.biryeong.ttt.util.NonThrowable;
import net.minecraft.item.Items;

public class BambooDagger extends SimplePolymerItem implements NonThrowable {
    public BambooDagger(Settings settings) {
        super(settings, Items.BAMBOO, false);
    }
}
