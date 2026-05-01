package kim.biryeong.ttt.mixin;

import com.mojang.serialization.Codec;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.ItemLoadout;
import kim.biryeong.ttt.player.ItemLoadouts;
import kim.biryeong.ttt.player.duck.InGameEventProvider;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.duck.SuicideBombInfo;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.explosion.ExplosionUtil;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements InGamePlayerInfoProvider, InGameEventProvider {
    @Shadow
    public ServerPlayNetworkHandler networkHandler;
    @Unique
    private Role tts$role = Role.SPECTATOR;
    @Unique
    private boolean tts$alive = false;
    @Unique
    private boolean tts$denyToPlay = false;
    @Unique
    private boolean tts$tipsEnabled = true;
    @Unique
    private boolean tts$bgmEnabled = true;
    @Unique
    private  SuicideBombInfo tts$bombInfo = new SuicideBombInfo();
    @Unique
    private boolean ttt$isInCombat = false;
    @Unique
    private ItemLoadout tts$loadout = ItemLoadouts.get(ItemLoadouts.DEFAULT_LOADOUT_KEY);

    @Override
    public Role tts$getRole() {
        return this.tts$role;
    }

    @Override
    public int tts$getPoints() {
        return GameManager.getInstance().getPlayerPoint(((ServerPlayerEntity) (Object) this).getUuid());
    }

    @Override
    public void tts$setRole(Role role) {
        this.tts$role = role;
    }

    @Override
    public void tts$setDenyToPlay(boolean denyToPlay) {
        this.tts$denyToPlay = denyToPlay;
    }

    @Override
    public void tts$setTipsEnabled(boolean enabled) {
        this.tts$tipsEnabled = enabled;
    }

    @Override
    public void tts$setBgmEnabled(boolean enabled) {
        this.tts$bgmEnabled = enabled;
    }

    @Override
    public void tts$setItemLoadout(ItemLoadout loadout) {
        if (loadout == null) {
            this.tts$loadout = ItemLoadouts.get(ItemLoadouts.DEFAULT_LOADOUT_KEY);
            return;
        }
        this.tts$loadout = ItemLoadouts.get(loadout.loadoutId());
    }

    @Override
    public void tts$addPoints(int points, PointReason reason) {
        GameManager.getInstance().addPoint(((ServerPlayerEntity) (Object) this).getUuid(), points);
    }

    @Override
    public void tts$clearPoints() {
        GameManager.getInstance().clearPoints(((ServerPlayerEntity) (Object) this).getUuid());
    }

    @Override
    public boolean tts$isAlive() {
        return this.tts$alive || this.tts$role != Role.SPECTATOR;
    }

    @Override
    public boolean tts$denyToPlay() {
        return this.tts$denyToPlay;
    }

    @Override
    public boolean tts$tipsEnabled() {
        return this.tts$tipsEnabled;
    }

    @Override
    public boolean tts$bgmEnabled() {
        return this.tts$bgmEnabled;
    }

    @Override
    public void tts$clearSidebarTime() {
        // Kept for InGamePlayerInfoProvider compatibility after corpse UI moved to display-hud.
    }

    @Override
    public ItemLoadout tts$getItemLoadout() {
        return this.tts$loadout;
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
        if (this.tts$bombInfo == null) {
            this.tts$bombInfo = new SuicideBombInfo();
        }
        this.tts$bombInfo.clearFuse();
    }

    @Override
    public boolean ttt$isInCombat() {
        return this.ttt$isInCombat;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tts$tick(CallbackInfo ci) {
        if (!GameManager.getInstance().isGameStarted()) {
            return;
        }

        if (!GameManager.getInstance().isAlive((ServerPlayerEntity) (Object) this)) {
            return;
        }
        if (this.tts$bombInfo == null) {
            this.tts$bombInfo = new SuicideBombInfo();
        }
        if (this.tts$bombInfo.tick((ServerPlayerEntity) (Object) this)) {
            this.tts$explode();
        }
    }

    @Unique
    private void tts$explode() {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Vec3d pos = player.getSyncedPos().add(0, 1.5, 0);
        ExplosionUtil.explodeWithFallback(
                player,
                pos,
                player.getWorld().toServerWorld(),
                5f
        );
    }

    @Inject(method = "enterCombat", at = @At("HEAD"))
    private void ttt$enterCombat(CallbackInfo ci) {
        this.ttt$isInCombat = true;
    }

    @Inject(method = "endCombat", at = @At("HEAD"))
    private void ttt$leaveCombat(CallbackInfo ci) {
        this.ttt$isInCombat = false;
    }


    @Inject(method = "writeCustomData", at = @At("HEAD"))
    private void ttt$writeCustomData(WriteView view, CallbackInfo ci) {
        view.put("tts$loadout", Identifier.CODEC, this.tts$loadout.loadoutId());
        view.put("tts$tips_enabled", Codec.BOOL, this.tts$tipsEnabled);
        view.put("tts$bgm_enabled", Codec.BOOL, this.tts$bgmEnabled);
    }

    @Inject(method = "readCustomData", at = @At("HEAD"))
    private void ttt$readCustomData(ReadView view, CallbackInfo ci) {
        this.tts$loadout = ItemLoadouts.get(view.read("tts$loadout", Identifier.CODEC).orElse(ItemLoadouts.DEFAULT_LOADOUT_KEY));
        this.tts$tipsEnabled = view.read("tts$tips_enabled", Codec.BOOL).orElse(true);
        this.tts$bgmEnabled = view.read("tts$bgm_enabled", Codec.BOOL).orElse(true);
    }
}
