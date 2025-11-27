package kim.biryeong.ttt.player;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public record ItemLoadout(Text displayName, List<Text> description, Identifier loadoutId, List<ItemStack> items) {
    public void giveToPlayer(ServerPlayerEntity player) {
        this.items.forEach(player::giveItemStack);
    }
}
