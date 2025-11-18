package kim.biryeong.ttt.mixin;

import eu.pb4.sidebars.api.SidebarInterface;
import eu.pb4.sidebars.impl.SidebarAPIMod;
import eu.pb4.sidebars.impl.SidebarHolder;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.duck.SuicideBombInfo;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.sidebar.CorpseSidebar;
import kim.biryeong.ttt.util.ExplosionUtil;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements InGamePlayerInfoProvider, InGameEventProvider {
    @Shadow
    public ServerPlayNetworkHandler networkHandler;
    @Unique
    private int sidebarDisplayedTicks = 0;
    @Unique
    private Role tts$role = Role.SPECTATOR;
    @Unique
    private int tts$points = 0;
    @Unique
    private boolean tts$alive = false;
    @Unique
    private boolean tts$denyToPlay = false;
    @Unique
    private SuicideBombInfo tts$bombInfo = new SuicideBombInfo();

    @Override
    public Role tts$getRole() {
        return this.tts$role;
    }

    @Override
    public int tts$getPoints() {
        return this.tts$points;
    }

    @Override
    public void tts$setRole(Role role) {
        this.tts$role = role;
    }

    @Override
    public void tts$addPoints(int points, PointReason reason) {
        this.tts$points += points;
    }

    @Override
    public void tts$clearPoints() {
        this.tts$points = 0;
    }

    @Override
    public boolean tts$isAlive() {
        return this.tts$alive;
    }

    @Override
    public boolean tts$denyToPlay() {
        return this.tts$denyToPlay;
    }

    @Override
    public void tts$fuse(int ticks) {
        this.tts$bombInfo.setTick(ticks);
    }

    @Override
    public boolean tts$isBombTriggered() {
        return this.tts$bombInfo.isTriggered();
    }

    @Override
    public void tts$clearFuse() {
        this.tts$bombInfo.clearFuse();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tts$tick(CallbackInfo ci) {
        var holder = SidebarHolder.of(this.networkHandler);
        Optional<SidebarInterface> hasSidebar = holder.sidebarApi$getAll().stream().filter(sidebar -> sidebar instanceof CorpseSidebar).findAny();
        if (hasSidebar.isPresent()) {
            if (this.sidebarDisplayedTicks == 100) {
                holder.sidebarApi$remove(hasSidebar.get());
                this.sidebarDisplayedTicks = 0;
            }
            this.sidebarDisplayedTicks++;
        }
        if (!GameManager.getInstance().isGameStarted()) {
            return;
        }

        if (!GameManager.getInstance().isAlive((ServerPlayerEntity) (Object) this)) {
            return;
        }

        if (this.tts$bombInfo.tick((ServerPlayerEntity) (Object) this)) {
            this.tts$explode();
        }
    }

    @Unique
    private void tts$explode() {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Vec3d pos = player.getSyncedPos();
        ServerWorld world = player.getEntityWorld().toServerWorld();

        ExplosionImpl explosion = ExplosionUtil.createExplosion(
                null,
                pos,
                world,
                5f
        );
        int i = explosion.explode();
        for (ServerPlayerEntity serverPlayerEntity : world.getPlayers()) {
            if (!(serverPlayerEntity.squaredDistanceTo(pos) < 4096.0)) continue;
            Optional<Vec3d> optional = Optional.ofNullable(explosion.getKnockbackByPlayer().get(serverPlayerEntity));
            serverPlayerEntity.networkHandler.sendPacket(new ExplosionS2CPacket(pos, 5f, i, optional, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.ENTITY_GENERIC_EXPLODE, Pool.empty()));
        }
    }

}
