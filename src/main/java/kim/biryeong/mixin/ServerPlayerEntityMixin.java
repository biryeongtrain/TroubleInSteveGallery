package kim.biryeong.mixin;

import kim.biryeong.player.role.InGamePlayerInfoProvider;
import kim.biryeong.player.role.Role;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements InGamePlayerInfoProvider {
    @Unique
    private Role tts$role = Role.SPECTATOR;
    @Unique
    private int tts$points = 0;
    @Unique
    private boolean tts$alive = false;
    @Unique
    private boolean tts$denyToPlay = false;

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
}
