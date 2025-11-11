package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.item.detective.NonThrowable;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

public class SuicideBomb extends Item implements PolymerItem, NonThrowable {

    public SuicideBomb(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext packetContext) {
        return Items.STICK;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return super.use(world, user, hand);
    }

    private void fusePlayer(PlayerEntity player) {
        if (player.getEntityWorld().isClient()) {
            return;
        }

        if (!GameManager.getInstance().isGameStarted()) {
            return;
        }

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        InGameEventProvider provider = (InGameEventProvider) player;
        provider.tts$fuse(60);

    }
}
