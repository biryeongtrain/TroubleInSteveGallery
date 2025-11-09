package kim.biryeong.entity;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import kim.biryeong.game.manager.GameManager;
import kim.biryeong.mixin.MannequinEntityAccessor;
import kim.biryeong.mixin.PlayerLikeEntityAccessor;
import kim.biryeong.player.role.InGamePlayerInfoProvider;
import kim.biryeong.player.role.Role;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CorpseEntity extends LivingEntity implements PolymerEntity, Leashable {
    private boolean isRevealed = false;
    private GameProfile gameProfile;
    private Role role;
    private LeashData leashData;
    private boolean isBurning = false;
    private int burnTicks = 0;
    private final InteractionElement interactionElement;
    private final ElementHolder holder;
    private final EntityAttachment attachment;
    public CorpseEntity(EntityType<CorpseEntity> type, World world) {
        super(type, world);
        this.role = Role.SPECTATOR;
        var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures",
                "ewogICJ0aW1lc3RhbXAiIDogMTYxNDk0NDg4ODg4OSwKICAicHJvZmlsZUlkIiA6ICI1N2IzZGZiNWY4YTY0OWUyOGI1NDRlNGZmYzYzMjU2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJYaWthcm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdmYzc1ZTBlYzAwNDAyMjMyOTZhYTRkMDhiZDI2YmU0ZDE3MmU4ZGUwNzE4NTU4ODgyMmZhZTM2M2QyMjMxOSIKICAgIH0KICB9Cn0=",
                "R/dm6ic4CYbsr66Iz859K5r1MVd7y08FUvOmJgKTE5KRcPdDNe71Vv61jzh0jQ9QeZJXsHe4+58RY2LiXn7LdPKWpNd+ljK2K4n00Yjp/MM9s6ppNOAQj32LY5UuwcXPUkTSQfr2GROM9zvY93lAuILr6xodvUoIrPcbBDHgxuN6FDiE1jKfFF5z2yZIHOVZXqJPJ+0ri1sw3mjMhbO3dPdpzTW24olgR3wqbXgfEwIeiMk1En+wBtce6ZnNHNXIaMj4fFDAsMmFKqvFcPY8SjfjW/jWBDYNFUCMpxTS2XduQGhSoSlNXG+OrI93Ya/iObGeqAp9WCqFvkV8azyG1VTFfegZCFrUwKV+819B8Q3H3JzJOzES9zvhX5CDKYaE4QvWAqGzTOVw7h0NxtOh9alFkbRR2lWFiBhUMT8EqRjkb+OyBVe9vGRJOU448aLQFyuEWLICje9FAmOHRH0JFpMDEKCLvAAZKZAOx9jceQKrcrcAS0f9nnqjWLLrWMK8lWh0CNcPN1P51rQsxMUlWddNEig+RyjOLHIz/fsv3EQ7yycWkeFfkxq0NAVZGajp4T3NhtWG+WlYywafy5Gtys0Mmv4CXu6xzoUdeLhtMjwgmqfdatQlAJGiZCuSMc1KwWis2inI1YDg5jIy8BTViFBGn76mks21iUEpL4JP8FU="
        )));
        this.setPose(EntityPose.SLEEPING);
        this.gameProfile = new GameProfile(this.getUuid(), "", properties);
        this.holder = new ElementHolder();
        this.interactionElement = InteractionElement.redirect(this);
        interactionElement.setSize(0.9f, 0.9f);
        this.holder.addElement(interactionElement);
        this.attachment = new EntityAttachment(holder, this,false);
    }

    public static DefaultAttributeContainer.Builder createCorpseEntityAttributes() {
        return PlayerLikeEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.1D);
    }

    private void onCreated(CorpseEntity corpse) {
        var x = VirtualElement.InteractionHandler.redirect(this);
    }

    public static CorpseEntity createCorpse(World world, ServerPlayerEntity player) {
        var entity = new CorpseEntity(TTSEntityType.CORPSE, world);
        entity.gameProfile = new GameProfile(entity.getUuid(), "Corpse of " + player.getStringifiedName(), player.getGameProfile().properties());
        entity.role = ((InGamePlayerInfoProvider) player).tts$getRole();
        return entity;
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext packetContext) {
        return EntityType.PLAYER;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }


    @Override
    public void onBeforeSpawnPacket(ServerPlayerEntity player, Consumer<Packet<?>> packetConsumer) {
        var packet = PolymerEntityUtils.createMutablePlayerListPacket(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER));
        packet.getEntries().add(new PlayerListS2CPacket.Entry(this.gameProfile.id(), this.gameProfile, false, 0, GameMode.ADVENTURE, Text.empty(), true, 0, null));
        packetConsumer.accept(packet);
    }

    @Override
    public void onEntityPacketSent(Consumer<Packet<?>> consumer, Packet<?> packet) {
        PolymerEntity.super.onEntityPacketSent(consumer, packet);
        if (packet instanceof EntitySetHeadYawS2CPacket headYawS2CPacket) {
            var ent = (Entity) this;
//            consumer.accept(new EntityS2CPacket.Rotate(ent.getId(), MathHelper.packDegrees(headYawS2CPacket.getHeadYaw()), (byte) (ent.getPitch() * 256.0F / 360.0F), ent.isOnGround()));
        }
    }



    @Override
    public void modifyRawTrackedData(List<DataTracker.SerializedEntry<?>> data, ServerPlayerEntity player, boolean initial) {
        data.removeIf(x -> x.id() >= PlayerLikeEntityAccessor.getMAIN_ARM_ID().id());
        data.removeIf(x -> x.id() == POSE.id());
        if (initial) {
            data.add(DataTracker.SerializedEntry.of(PlayerLikeEntityAccessor.getPLAYER_MODE_CUSTOMIZATION_ID(), (byte) 0xFE));
            data.add(DataTracker.SerializedEntry.of(MannequinEntityAccessor.getPROFILE(), ProfileComponent.ofStatic(
                    this.gameProfile
            )));
            data.add(DataTracker.SerializedEntry.of(MannequinEntityAccessor.getDESCRIPTION(), Optional.empty()));
        }
        data.add(DataTracker.SerializedEntry.of(POSE, EntityPose.SLEEPING));
    }

    public void onTrackingStopped(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(this.getUuid())));
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        this.onStartedTrackingBy(player);
        this.onTrackingStopped(player);
    }

    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
//        var result = super.interact(player, hand);
         {
            // TODO SGUI Execute
            if (!isRevealed) {
                this.setCustomName(GameManager.getInstance().byMiniMessage("%s's Corpse</color>".formatted(this.gameProfile.name())));
                this.setCustomNameVisible(true);
                this.isRevealed = true;
            }
        }

        if (player.getMainHandStack().getItem() == Items.BLAZE_ROD) {
            this.setFireTicks(1000);
            this.isBurning = true;
        }

        if (player.getMainHandStack().getItem() == Items.STICK) {
        }

        return ActionResult.PASS;
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.isBurning && burnTicks++ > 200) {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    public boolean canBeLeashedTo(Entity entity) {
        return true;
    }

    @Override
    public double getElasticLeashDistance() {
        return 2.0;
    }

    @Override
    public @Nullable LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }
}
