package kim.biryeong.ttt.util;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

public class ShopUtil {
    public static final BiConsumer<Item, ServerPlayerEntity> DEFAULT = (item, player) -> {
        player.getInventory().insertStack(item.getDefaultStack());
    };
}
