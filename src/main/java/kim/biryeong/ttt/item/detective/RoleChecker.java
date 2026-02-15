package kim.biryeong.ttt.item.detective;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.NonThrowable;
import kim.biryeong.ttt.util.Sounds;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class RoleChecker extends Item implements PolymerItem, NonThrowable {
    public RoleChecker(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.END_CRYSTAL;
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return null;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, @Nullable EquipmentSlot slot) {
        if (slot == null && entity instanceof ServerPlayerEntity player) {
            stack.get(DataComponentTypes.USE_COOLDOWN).set(stack, player);
        }
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity.getWorld().isClient()) {
            return ActionResult.FAIL;
        }

        if (!(entity instanceof ServerPlayerEntity player)) {
            return ActionResult.FAIL;
        }

        InGamePlayerInfoProvider provider = (InGamePlayerInfoProvider) player;
        Role role = provider.tts$getRole();

        Text message = GameManager.byMiniMessage("%s님의 직업은 <#color>%s</#color>입니다."
                .formatted(player.getGameProfile().getName(), role.krRoleName)
                .replace("color", role.hexColor)
        );
        user.getWorld().playSound(null, user.getBlockPos(), Sounds.TESTER_ITEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        user.sendMessage(message, false);
        stack.decrement(1);

        return ActionResult.SUCCESS;
    }

    @Override
    public void modifyClientTooltip(List<Text> tooltip, ItemStack stack, PacketContext context) {
        tooltip.add(GameManager.byMiniMessage("<blue> 탐정용 아이템입니다. 아이템을 들고 우클릭 하면"));
        tooltip.add(GameManager.byMiniMessage("<blue> 적중한 플레이어의 정보가 나옵니다."));
    }
}

