package kim.biryeong.game.data;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import kim.biryeong.player.role.InGamePlayerInfoProvider;
import kim.biryeong.player.role.Role;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class PlayerRoundDataInstance {
    public static final Codec<PlayerRoundDataInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Date.CODEC.fieldOf("date").forGetter(PlayerRoundDataInstance::date),
            Role.CODEC.fieldOf("role").forGetter(PlayerRoundDataInstance::role),
            RoundKillData.CODEC.listOf().fieldOf("roundKillData").forGetter(PlayerRoundDataInstance::roundKillData)
    ).apply(instance, PlayerRoundDataInstance::new));

    private final Date date;
    private final Role role;
    private final List<RoundKillData> roundKillData;

    private PlayerRoundDataInstance(Date date, Role role, List<RoundKillData> roundKillData) {
        this.date = date;
        this.role = role;
        this.roundKillData = roundKillData;
    }

    public Date date() {
        return date;
    }

    public Role role() {
        return role;
    }

    public List<RoundKillData> roundKillData() {
        return ImmutableList.copyOf(roundKillData);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlayerRoundDataInstance that = (PlayerRoundDataInstance) o;
        return Objects.equal(date, that.date) && role == that.role && Objects.equal(roundKillData, that.roundKillData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(date, role, roundKillData);
    }

    public static PlayerRoundDataInstance create(ServerPlayerEntity player) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        Date date = Date.fromNow();
        Role role = info.tts$getRole();
        return new PlayerRoundDataInstance(date, role, new ArrayList<>());
    }

    public void recordKillData(int elapsedSeconds, ServerPlayerEntity player, DamageSource source) {
        if (player == null) {
            this.roundKillData.add(new RoundKillData(elapsedSeconds, "Unknown", Role.SPECTATOR, true));
        }
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        String victimName = player.getStringifiedName();
        Role victimRole = info.tts$getRole();
        this.roundKillData.add(new RoundKillData(elapsedSeconds, victimName, victimRole, source.getAttacker() == player));
    }

    public record RoundKillData(int elapsedSeconds, String victimName, Role victimRole, boolean slainByVictim) {
        public static final Codec<RoundKillData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("elapsedSeconds").forGetter(RoundKillData::elapsedSeconds),
                    Codec.STRING.fieldOf("victimName").forGetter(RoundKillData::victimName),
                    Role.CODEC.fieldOf("victimRole").forGetter(RoundKillData::victimRole),
                    Codec.BOOL.fieldOf("slainByVictim").forGetter(RoundKillData::slainByVictim)
            ).apply(instance, RoundKillData::new)
        );
    }
}
