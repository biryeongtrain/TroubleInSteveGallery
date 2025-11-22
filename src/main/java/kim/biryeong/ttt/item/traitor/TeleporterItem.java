package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.item.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

public class TeleporterItem extends Item implements PolymerItem {

    public TeleporterItem(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.PAPER;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        var result = super.use(world, user, hand);
        if (user.getWorld().isClient()) {
            return ActionResult.FAIL;
        }

        ItemStack stack = user.getStackInHand(hand);
        if (stack.contains(ModComponents.TELEPORTER_POS)) {
            BlockPos pos = stack.get(ModComponents.TELEPORTER_POS);
            user.requestTeleportAndDismount(pos.getX(), pos.getY(), pos.getZ());
            ((ServerWorld) world).getPlayers((player) -> pos.isWithinDistance(player.getBlockPos(), 10.0))
                    .forEach(player -> player.networkHandler.sendPacket(
                            new PlaySoundS2CPacket(
                                    RegistryEntry.of(SoundEvents.ENTITY_PLAYER_TELEPORT),
                                    player.getSoundCategory(),
                                    pos.getX(),
                                    pos.getY(),
                                    pos.getZ(),
                                    1.0f,
                                    1.0f,
                                    0L)
                            )
                    );

            stack.decrement(1);
        } else {
            BlockPos pos = user.getBlockPos();
            stack.set(ModComponents.TELEPORTER_POS, pos);
            user.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.MASTER, 1.0f, 1.0f);
            UseCooldownComponent cooldown = stack.get(DataComponentTypes.USE_COOLDOWN);
            cooldown.set(stack, user);
        }

        return result;
    }
}
