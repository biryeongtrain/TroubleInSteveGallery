package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.util.NonThrowable;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.util.Sounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * 자폭폭탄.
 */
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
        if (!this.fusePlayer(user)) {
            return ActionResult.FAIL;
        }

        user.getStackInHand(hand).decrement(1);
        return ActionResult.SUCCESS;
    }

    private boolean fusePlayer(PlayerEntity player) {
        if (player.getWorld().isClient()) {
            return false;
        }

        if (!GameManager.getInstance().getCurrentPhase().canShowRole()) {
            return false;
        }

        InGameEventProvider provider = (InGameEventProvider) player;
        player.getWorld().playSound(null, player.getBlockPos(), Sounds.JIHAD_BOMB_ACTIVE, SoundCategory.PLAYERS, 0.5f, 1.0f);
        provider.tts$fuse(35);
        return true;
    }
}
