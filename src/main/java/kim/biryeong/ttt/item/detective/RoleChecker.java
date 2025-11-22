package kim.biryeong.ttt.item.detective;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.NonThrowable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public class RoleChecker extends Item implements PolymerItem, NonThrowable {
    public RoleChecker(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.PAPER;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, @Nullable EquipmentSlot slot) {
        if (slot == null && entity instanceof ServerPlayerEntity player) {
            stack.get(DataComponentTypes.USE_COOLDOWN).set(stack, player);
        }
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity.getEntityWorld().isClient()) {
            return ActionResult.FAIL;
        }

        if (!(entity instanceof ServerPlayerEntity player)) {
            return ActionResult.FAIL;
        }

        InGamePlayerInfoProvider provider = (InGamePlayerInfoProvider) player;
        Role role = provider.tts$getRole();

        Text message = GameManager.byMiniMessage("%s님의 직업은 <#color>%s</#color>입니다."
                .formatted(player.getStringifiedName(), role.krRoleName)
                .replace("color", role.hexColor)
        );
        user.sendMessage(message, false);
        stack.decrement(1);

        return ActionResult.SUCCESS;
    }
}

