package kim.biryeong.ttt.world;

import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.world.gen.TemplateChunkGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.TeleportTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.GameRuleStore;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.TemplateRegion;

import java.util.List;

public class TTTMap {
    private static final Logger LOGGER = LoggerFactory.getLogger("TTT_Map");
    private final MapTemplate template;
    private final List<TemplateRegion> spawns;
    private final Identifier instanceId;
    private ServerWorld world;
    private RuntimeWorldHandle handle;

    public TTTMap(Identifier id, MapTemplate template) {
        this.template = template;
        this.spawns = template.getMetadata().getRegions("spawn").toList();
        this.instanceId = id;
        if (this.spawns.isEmpty()) {
            throw new IllegalStateException("No spawn region found.");
        }

    }

    public void generateWorld(MinecraftServer server) {
        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setGenerator(new TemplateChunkGenerator(server, this.template))
                .setDifficulty(Difficulty.PEACEFUL);
        config.setShouldTickTime(false);
        var gameRules = config.getGameRules();
        gameRules.set(GameRules.RANDOM_TICK_SPEED, 0);
        setDefaultRule(gameRules, GameRules.DO_MOB_SPAWNING, false);
        setDefaultRule(gameRules, GameRules.DO_PATROL_SPAWNING, false);
        setDefaultRule(gameRules, GameRules.DO_DAYLIGHT_CYCLE, false);
        setDefaultRule(gameRules, GameRules.DO_WEATHER_CYCLE, false);
        setDefaultRule(gameRules, GameRules.ANNOUNCE_ADVANCEMENTS, false);
        setDefaultRule(gameRules, GameRules.DO_ENTITY_DROPS, false);
        setDefaultRule(gameRules, GameRules.DO_FIRE_TICK, false);
        setDefaultRule(gameRules, GameRules.DO_MOB_LOOT, false);
        setDefaultRule(gameRules, GameRules.PROJECTILES_CAN_BREAK_BLOCKS, false);
        setDefaultRule(gameRules, GameRules.DO_VINES_SPREAD, false);
        setDefaultRule(gameRules, GameRules.REDUCED_DEBUG_INFO, true);
        setDefaultRule(gameRules, GameRules.SHOW_DEATH_MESSAGES, false);
        setDefaultRule(gameRules, GameRules.DO_TRADER_SPAWNING, false);


        this.handle = Fantasy.get(server).openTemporaryWorld(config);
        this.world = handle.asWorld();

        GameManager.getInstance().sendMessage("월드 준비 완료. 로드된 맵 : <green>%s</green>".formatted(instanceId.toString()));
    }

    public ServerWorld getWorld() {
        return world;
    }

    public Identifier getId() {
        return this.instanceId;
    }

    public BlockBounds getTemplateBounds() {
        return this.template.getBounds();
    }

    public void spreadPlayers(List<ServerPlayerEntity> participants) {
        for (ServerPlayerEntity participant : participants) {
            this.spawnPlayer(participant);
        }
    }

    public void closeMap() {
        if (this.handle == null || this.world == null) {
            return;
        }

        this.handle.unload();
        this.handle = null;
        this.world = null;
    }

    private static void setDefaultRule(GameRuleStore rules, GameRules.Key<GameRules.BooleanRule> key, boolean value) {
        if (!rules.contains(key)) {
            rules.set(key, value);
        }
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        if (this.world == null) {
            throw new IllegalStateException("Map world is not generated yet: " + this.instanceId);
        }

        Xoroshiro128PlusPlusRandom random = GameManager.getInstance().getRandom();
        int index = random.nextInt(spawns.size());
        var location = this.spawns.get(index).getBounds().sampleBlock(random);
//        player.setServerWorld(this.world);
//        player.teleportTo
//        player.requestTeleportAndDismount(location.getX(), location.getY(), location.getZ());
        player.teleportTo(new TeleportTarget(this.world, location.toCenterPos().add(0, 0.5, 0), Vec3d.ZERO, player.getYaw(), player.getPitch(), TeleportTarget.NO_OP));

    }
}
