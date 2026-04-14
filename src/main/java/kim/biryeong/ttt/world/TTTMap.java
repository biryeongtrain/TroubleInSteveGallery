package kim.biryeong.ttt.world;

import eu.pb4.polyfactory.block.network.NetworkComponent;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.world.gen.TemplateChunkGenerator;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.fantasy.util.GameRuleStore;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.TemplateRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TTTMap {
    private static final int PORTAL_COOLDOWN_TICKS = 10;
    private static final String PORTAL_MARKER = "portal";
    private static final String REGION_ID_KEY = "id";
    private static final String PORTAL_DEST_KEY = "dest";
    private static final Logger LOGGER = LoggerFactory.getLogger("TTT_Map");
    private final MapTemplate template;
    private final List<TemplateRegion> spawns;
    private final List<PortalRoute> portalRoutes;
    private final Identifier instanceId;
    private ServerWorld world;
    private RuntimeWorldHandle handle;

    public TTTMap(Identifier id, MapTemplate template) {
        this.template = template;
        this.instanceId = id;
        this.spawns = template.getMetadata().getRegions("spawn").toList();
        this.portalRoutes = buildPortalRoutes(template);
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

    public void repairMissingBlockEntities() {
        if (this.world == null) {
            throw new IllegalStateException("Map world is not generated yet: " + this.instanceId);
        }

        BlockBounds bounds = this.template.getBounds();
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        Set<BlockPos> rotationalNetworkPositions = new LinkedHashSet<>();
        Set<BlockPos> rotationalConnectorPositions = new LinkedHashSet<>();
        int repairedCount = 0;

        for (int chunkX = ChunkSectionPos.getSectionCoord(min.getX()); chunkX <= ChunkSectionPos.getSectionCoord(max.getX()); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(min.getZ()); chunkZ <= ChunkSectionPos.getSectionCoord(max.getZ()); chunkZ++) {
                WorldChunk chunk = this.world.getChunk(chunkX, chunkZ);
                int minX = Math.max(min.getX(), chunk.getPos().getStartX());
                int maxX = Math.min(max.getX(), chunk.getPos().getEndX());
                int minZ = Math.max(min.getZ(), chunk.getPos().getStartZ());
                int maxZ = Math.min(max.getZ(), chunk.getPos().getEndZ());

                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            mutablePos.set(x, y, z);
                            BlockState state = chunk.getBlockState(mutablePos);
                            if (state.getBlock() instanceof NetworkComponent.Rotational) {
                                rotationalNetworkPositions.add(mutablePos.toImmutable());
                            }
                            if (state.getBlock() instanceof NetworkComponent.RotationalConnector) {
                                rotationalConnectorPositions.add(mutablePos.toImmutable());
                            }
                            if (!state.hasBlockEntity()) {
                                continue;
                            }
                            if (chunk.getBlockEntity(mutablePos) != null) {
                                continue;
                            }

                            repairedCount += tryCreateMissingBlockEntity(chunk, mutablePos, state);
                        }
                    }
                }
            }
        }

        if (repairedCount > 0) {
            LOGGER.info("Repaired {} missing block entities in map '{}'.", repairedCount, this.instanceId);
        }

        if (!rotationalConnectorPositions.isEmpty()) {
            rotationalConnectorPositions.forEach(pos -> NetworkComponent.RotationalConnector.updateRotationalConnectorAt(this.world, pos));
            LOGGER.info("Refreshed {} rotational connector nodes in map '{}'.", rotationalConnectorPositions.size(), this.instanceId);
        }

        if (!rotationalNetworkPositions.isEmpty()) {
            rotationalNetworkPositions.forEach(pos -> NetworkComponent.Rotational.updateRotationalAt(this.world, pos));
            LOGGER.info("Refreshed {} rotational network nodes in map '{}'.", rotationalNetworkPositions.size(), this.instanceId);
        }
    }

    public void tickPortals() {
        if (this.world == null || this.portalRoutes.isEmpty()) {
            return;
        }

        for (PortalRoute route : this.portalRoutes) {
            Vec3d destination = route.destinationBounds().centerBottom().add(0.0, 0.5, 0.0);
            for (Entity entity : this.world.getEntitiesByClass(
                    Entity.class,
                    route.sourceBounds().asBox(),
                    TTTMap::isPortalTeleportCandidate
            )) {
                teleportThroughPortal(entity, destination);
            }
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

    private void teleportThroughPortal(Entity entity, Vec3d destination) {
        entity.teleportTo(new TeleportTarget(
                this.world,
                destination,
                entity.getVelocity(),
                entity.getYaw(),
                entity.getPitch(),
                TeleportTarget.NO_OP
        ));
        // Avoid immediate bounce loops when source/destination portals are adjacent.
        entity.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
    }

    private static boolean isPortalTeleportCandidate(Entity entity) {
        return !entity.isRemoved() && entity.isAlive() && !entity.hasPortalCooldown();
    }

    private int tryCreateMissingBlockEntity(WorldChunk chunk, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = chunk.getBlockEntity(pos, WorldChunk.CreationType.IMMEDIATE);
        if (blockEntity == null) {
            LOGGER.warn(
                    "Block '{}' in map '{}' could not recreate a missing block entity at {}.",
                    state.getBlock(),
                    this.instanceId,
                    pos
                );
            return 0;
        }

        chunk.markNeedsSaving();
        return 1;
    }

    private List<PortalRoute> buildPortalRoutes(MapTemplate mapTemplate) {
        List<TemplateRegion> portals = mapTemplate.getMetadata().getRegions(PORTAL_MARKER).toList();
        if (portals.isEmpty()) {
            return List.of();
        }

        Map<String, TemplateRegion> regionsById = new HashMap<>();
        for (TemplateRegion region : mapTemplate.getMetadata().getRegions()) {
            String regionId = readRegionString(region, REGION_ID_KEY);
            if (regionId == null) {
                continue;
            }

            TemplateRegion previous = regionsById.putIfAbsent(regionId, region);
            if (previous != null) {
                LOGGER.warn(
                        "Duplicate region id '{}' found while loading map '{}'. Using first region. first={}, duplicate={}",
                        regionId,
                        this.instanceId,
                        previous.getBounds(),
                        region.getBounds()
                );
            }
        }

        List<PortalRoute> routes = new ArrayList<>();
        for (TemplateRegion portal : portals) {
            String destinationId = readRegionString(portal, PORTAL_DEST_KEY);
            if (destinationId == null) {
                LOGGER.warn(
                        "Skipping portal in map '{}': missing '{}' key at bounds={}",
                        this.instanceId,
                        PORTAL_DEST_KEY,
                        portal.getBounds()
                );
                continue;
            }

            TemplateRegion destinationRegion = regionsById.get(destinationId);
            if (destinationRegion == null) {
                destinationRegion = mapTemplate.getMetadata().getFirstRegion(destinationId);
            }
            if (destinationRegion == null) {
                LOGGER.warn(
                        "Skipping portal in map '{}': destination region '{}' not found for portal bounds={}",
                        this.instanceId,
                        destinationId,
                        portal.getBounds()
                );
                continue;
            }

            routes.add(new PortalRoute(portal.getBounds(), destinationRegion.getBounds()));
        }

        if (!routes.isEmpty()) {
            LOGGER.info("Loaded {} portal route(s) for map '{}'.", routes.size(), this.instanceId);
        }

        return List.copyOf(routes);
    }

    private static @Nullable String readRegionString(TemplateRegion region, String key) {
        NbtCompound data = region.getData();
        if (data == null) {
            return null;
        }

        return data.getString(key)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }

    private record PortalRoute(BlockBounds sourceBounds, BlockBounds destinationBounds) {
    }
}
