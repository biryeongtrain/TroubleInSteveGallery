package kim.biryeong.ttt.item.traitor;

import eu.pb4.polymer.core.api.item.PolymerItem;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.NonThrowable;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public class TraitorDisruptorItem extends Item implements PolymerItem, NonThrowable {
    private static final double RANGE = 16.0;
    private static final int DISRUPT_TICKS = 200;

    public TraitorDisruptorItem(Settings settings) {
        super(settings);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.ECHO_SHARD;
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return null;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) {
            return ActionResult.FAIL;
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return ActionResult.FAIL;
        }

        GameManager manager = GameManager.getInstance();
        InGamePlayerInfoProvider userInfo = (InGamePlayerInfoProvider) player;
        if (!manager.getCurrentPhase().canShowRole() || !manager.isAlive(player) || userInfo.tts$getRole() != Role.TRAITOR) {
            player.sendMessage(Text.literal("이 아이템은 생존한 트레이터만 라운드 중 사용할 수 있습니다."), false);
            return ActionResult.FAIL;
        }

        int affectedPlayers = 0;
        ServerWorld serverWorld = (ServerWorld) world;
        for (ServerPlayerEntity target : serverWorld.getPlayers(this::canDisrupt)) {
            if (!player.getBlockPos().isWithinDistance(target.getBlockPos(), RANGE)) {
                continue;
            }

            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, DISRUPT_TICKS, 0, false, true, true));
            target.sendMessage(Text.literal("주변 신호가 교란되었습니다."), true);
            affectedPlayers++;
        }

        if (affectedPlayers <= 0) {
            player.sendMessage(Text.literal("교란할 대상이 주변에 없습니다."), false);
            return ActionResult.FAIL;
        }

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 0.55f);
        player.sendMessage(Text.literal("주변 비-트레이터 " + affectedPlayers + "명의 시야를 교란했습니다."), false);
        ItemStack stack = user.getStackInHand(hand);
        UseCooldownComponent cooldown = stack.get(DataComponentTypes.USE_COOLDOWN);
        if (cooldown != null) {
            cooldown.set(stack, user);
        }
        stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    private boolean canDisrupt(ServerPlayerEntity target) {
        GameManager manager = GameManager.getInstance();
        if (!manager.isAlive(target)) {
            return false;
        }

        InGamePlayerInfoProvider targetInfo = (InGamePlayerInfoProvider) target;
        return targetInfo.tts$getRole() != Role.TRAITOR && targetInfo.tts$getRole() != Role.SPECTATOR;
    }
}
