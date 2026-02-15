package kim.biryeong.ttt.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.DebugFakePlayerRegistry;
import kim.biryeong.ttt.util.Scheduler;
import kim.biryeong.ttt.util.explosion.ExplosionUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class CommandInitializer {
    private static final int MAX_FAKE_PLAYER_NAME_LENGTH = 16;
    private static final int MAX_FAKE_PLAYER_SPAWN_COUNT = 32;
    private static final String FAKE_PLAYER_FALLBACK_NAME = "FakePlayer";
    private static final Text DEBUG_FAKE_PLAYER_DISCONNECT_REASON = Text.literal("디버그 가짜 플레이어가 제거되었습니다.");

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> adminRoot = CommandManager.literal("tts_admin")
                .requires(source -> source.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> startGame = CommandManager.literal("start")
                .then(CommandManager.argument("resetPoints", BoolArgumentType.bool())
                        .executes(ctx -> {
                            if (GameManager.getInstance().isGameInitializing() || GameManager.getInstance().isGameStarted()) {
                                ctx.getSource().sendError(Text.literal("게임이 이미 시작되었거나 초기화 중입니다."));
                                return 0;
                            }
                            boolean reset = BoolArgumentType.getBool(ctx, "resetPoints");
                            GameManager.getInstance().startGame(reset);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        final SuggestionProvider<ServerCommandSource> roleSuggester = (ctx, builder) ->
                CommandSource.suggestMatching(Stream.of(Role.values()).map(Role::asString).toList(), builder);

        LiteralCommandNode<ServerCommandSource> setRole = CommandManager.literal("set_role")
                .then(CommandManager.argument("role", StringArgumentType.string())
                        .suggests(roleSuggester)
                        .executes(ctx -> {
                            Role role = Role.valueOf(StringArgumentType.getString(ctx, "role").toUpperCase(Locale.ENGLISH));
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                            info.tts$setRole(role);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        LiteralCommandNode<ServerCommandSource> stopGame = CommandManager.literal("stop")
                .executes(ctx -> {
                    if (!GameManager.getInstance().isGameStarted()) {
                        ctx.getSource().sendError(Text.literal("현재 진행 중인 게임이 없습니다."));
                        return 0;
                    }
                    GameManager.getInstance().stopGame(PlayerDataInstance.Result.CANCELED);
                    return Command.SINGLE_SUCCESS;
                })
                .build();

        LiteralCommandNode<ServerCommandSource> givePoints = CommandManager.literal("give_points")
                .then(CommandManager.argument("player", EntityArgumentType.players())
                        .then(CommandManager.argument("points", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "player");
                                    int points = IntegerArgumentType.getInteger(ctx, "points");
                                    for (ServerPlayerEntity player : players) {
                                        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                                        info.tts$addPoints(points, InGamePlayerInfoProvider.PointReason.ROLE_PLAYING);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();

        adminRoot.addChild(givePoints);

        LiteralCommandNode<ServerCommandSource> userRoot = CommandManager.literal("tts").build();

        LiteralCommandNode<ServerCommandSource> spectatorMode = CommandManager.literal("spectator_mode")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setSpectatorMode(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();

        LiteralCommandNode<ServerCommandSource> reload = CommandManager.literal("reload")
                .executes(ctx -> {
                    Config.reload();
                    return Command.SINGLE_SUCCESS;
                })
                .build();

        LiteralCommandNode<ServerCommandSource> debugNode = CommandManager.literal("tts_debug")
                .requires(source -> source.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> createExplosion = CommandManager.literal("explode")
                .executes(ctx -> {
                    if (!ctx.getSource().isExecutedByPlayer()) {
                        ctx.getSource().sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
                        return 0;
                    }

                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ExplosionImpl explosion = ExplosionUtil.createExplosion(null, player.getSyncedPos(), player.getWorld(), 7);
                    explosion.explode();
                    return Command.SINGLE_SUCCESS;
                })
                .build();

        LiteralCommandNode<ServerCommandSource> enableDebugMode = CommandManager.literal("debugmode")
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            GameManager.getInstance().setDebugMode(BoolArgumentType.getBool(ctx, "value"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        LiteralCommandNode<ServerCommandSource> fakePlayerCommand = createFakePlayerCommand();

        adminRoot.addChild(startGame);
        adminRoot.addChild(stopGame);
        adminRoot.addChild(setRole);
        adminRoot.addChild(reload);

        userRoot.addChild(spectatorMode);

        debugNode.addChild(createExplosion);
        debugNode.addChild(enableDebugMode);
        debugNode.addChild(fakePlayerCommand);

        dispatcher.getRoot().addChild(adminRoot);
        dispatcher.getRoot().addChild(userRoot);
        dispatcher.getRoot().addChild(debugNode);
    }

    private static LiteralCommandNode<ServerCommandSource> createFakePlayerCommand() {
        SuggestionProvider<ServerCommandSource> fakePlayerNameSuggester = (ctx, builder) ->
                CommandSource.suggestMatching(getOnlineDebugFakePlayerNames(ctx.getSource()), builder);

        LiteralCommandNode<ServerCommandSource> fakePlayerRoot = CommandManager.literal("fake_player").build();

        LiteralCommandNode<ServerCommandSource> spawnNode = CommandManager.literal("spawn")
                .executes(ctx -> spawnAutoNamedFakePlayer(ctx.getSource()))
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> spawnFakePlayers(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                1
                        ))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_FAKE_PLAYER_SPAWN_COUNT))
                                .executes(ctx -> spawnFakePlayers(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        IntegerArgumentType.getInteger(ctx, "count")
                                ))))
                .build();

        LiteralCommandNode<ServerCommandSource> removeNode = CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(fakePlayerNameSuggester)
                        .executes(ctx -> removeFakePlayer(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")
                        )))
                .build();

        LiteralCommandNode<ServerCommandSource> clearNode = CommandManager.literal("clear")
                .executes(ctx -> clearFakePlayers(ctx.getSource()))
                .build();

        LiteralCommandNode<ServerCommandSource> listNode = CommandManager.literal("list")
                .executes(ctx -> listFakePlayers(ctx.getSource()))
                .build();

        fakePlayerRoot.addChild(spawnNode);
        fakePlayerRoot.addChild(removeNode);
        fakePlayerRoot.addChild(clearNode);
        fakePlayerRoot.addChild(listNode);
        return fakePlayerRoot;
    }

    private static int spawnFakePlayers(ServerCommandSource source, String baseNameArgument, int count) {
        MinecraftServer server = source.getServer();
        String sanitizedBaseName = sanitizeFakePlayerBaseName(baseNameArgument);
        int spawnedCount = 0;

        for (int i = 1; i <= count; i++) {
            String fakePlayerName = buildFakePlayerName(sanitizedBaseName, i, count);
            if (server.getPlayerManager().getPlayer(fakePlayerName) != null) {
                source.sendError(Text.literal("가짜 플레이어 '" + fakePlayerName + "'를 생성할 수 없습니다: 이미 사용 중인 이름입니다."));
                continue;
            }

            ServerPlayerEntity fakePlayer = createAndConnectFakePlayer(source, fakePlayerName);
            DebugFakePlayerRegistry.register(fakePlayer.getUuid());
            spawnedCount++;
        }

        if (spawnedCount == 0) {
            source.sendError(Text.literal("생성된 가짜 플레이어가 없습니다."));
            return 0;
        }

        int skippedCount = count - spawnedCount;
        final int finalSpawnedCount = spawnedCount;
        final int finalSkippedCount = skippedCount;
        source.sendFeedback(
                () -> Text.literal(
                        finalSpawnedCount + "명의 가짜 플레이어를 생성했습니다"
                                + (finalSkippedCount > 0 ? " (" + finalSkippedCount + "명 건너뜀)." : ".")
                ),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnAutoNamedFakePlayer(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        String autoName = findNextAutoFakePlayerName(server);

        ServerPlayerEntity fakePlayer = createAndConnectFakePlayer(source, autoName);
        DebugFakePlayerRegistry.register(fakePlayer.getUuid());
        source.sendFeedback(() -> Text.literal("가짜 플레이어 '" + autoName + "'를 생성했습니다."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static String findNextAutoFakePlayerName(MinecraftServer server) {
        for (int index = 1; index <= 9999; index++) {
            String candidate = index == 1
                    ? FAKE_PLAYER_FALLBACK_NAME
                    : trimToNameLimit(FAKE_PLAYER_FALLBACK_NAME + "_" + index);
            if (server.getPlayerManager().getPlayer(candidate) == null) {
                return candidate;
            }
        }

        return trimToNameLimit(FAKE_PLAYER_FALLBACK_NAME + "_" + UUID.randomUUID().toString().substring(0, 4));
    }

    private static ServerPlayerEntity createAndConnectFakePlayer(ServerCommandSource source, String fakePlayerName) {
        MinecraftServer server = source.getServer();
        ServerWorld world = source.getWorld();
        Vec3d spawnPos = source.getPosition();
        Vec2f rotation = source.getRotation();

        ConnectedClientData clientData = ConnectedClientData.createDefault(
                new GameProfile(UUID.randomUUID(), fakePlayerName),
                false
        );

        ServerPlayerEntity fakePlayer = new ServerPlayerEntity(
                server,
                world,
                clientData.gameProfile(),
                clientData.syncedOptions()
        );

        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(new ChannelHandler[]{connection});
        server.getPlayerManager().onPlayerConnect(connection, fakePlayer, clientData);

        fakePlayer.teleport(world, spawnPos.x, spawnPos.y, spawnPos.z, Set.of(), rotation.y, rotation.x, false);
        stabilizeDebugFakePlayerState(fakePlayer);

        // Connection lifecycle handlers may re-teleport or reset load state; re-apply once after join side effects.
        Scheduler.INSTANCE.submit(ignored -> {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(fakePlayer.getUuid());
            if (online != null && DebugFakePlayerRegistry.contains(online.getUuid())) {
                stabilizeDebugFakePlayerState(online);
            }
        }, 2);

        return fakePlayer;
    }

    private static void stabilizeDebugFakePlayerState(ServerPlayerEntity fakePlayer) {
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) fakePlayer;
        info.tts$setRole(Role.INNOCENT);
        fakePlayer.changeGameMode(GameMode.ADVENTURE);

        // Fake players do not acknowledge teleport/player-loaded packets, so clear guard states manually.
        fakePlayer.onTeleportationDone();
        fakePlayer.setLoaded(true);

        var abilities = fakePlayer.getAbilities();
        abilities.invulnerable = false;
        abilities.allowFlying = false;
        abilities.flying = false;
        fakePlayer.sendAbilitiesUpdate();
    }

    private static int removeFakePlayer(ServerCommandSource source, String name) {
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(name);
        if (player == null) {
            source.sendError(Text.literal("이름이 '" + name + "'인 온라인 플레이어를 찾을 수 없습니다."));
            return 0;
        }
        if (!DebugFakePlayerRegistry.unregister(player.getUuid())) {
            source.sendError(Text.literal("플레이어 '" + name + "'는 디버그 가짜 플레이어가 아닙니다."));
            return 0;
        }

        player.networkHandler.disconnect(DEBUG_FAKE_PLAYER_DISCONNECT_REASON);
        source.sendFeedback(() -> Text.literal("가짜 플레이어 '" + name + "'를 제거했습니다."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearFakePlayers(ServerCommandSource source) {
        int removedCount = 0;
        List<UUID> uuids = new ArrayList<>(DebugFakePlayerRegistry.snapshot());
        for (UUID uuid : uuids) {
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null) {
                DebugFakePlayerRegistry.unregister(uuid);
                continue;
            }

            player.networkHandler.disconnect(DEBUG_FAKE_PLAYER_DISCONNECT_REASON);
            DebugFakePlayerRegistry.unregister(uuid);
            removedCount++;
        }

        final int finalRemovedCount = removedCount;
        source.sendFeedback(() -> Text.literal(finalRemovedCount + "명의 가짜 플레이어를 제거했습니다."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listFakePlayers(ServerCommandSource source) {
        List<String> names = getOnlineDebugFakePlayerNames(source);
        if (names.isEmpty()) {
            source.sendFeedback(() -> Text.literal("현재 온라인인 디버그 가짜 플레이어가 없습니다."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(
                () -> Text.literal("온라인 디버그 가짜 플레이어 (" + names.size() + "명): " + String.join(", ", names)),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static List<String> getOnlineDebugFakePlayerNames(ServerCommandSource source) {
        return DebugFakePlayerRegistry.snapshot().stream()
                .map(uuid -> source.getServer().getPlayerManager().getPlayer(uuid))
                .filter(Objects::nonNull)
                .map(player -> player.getGameProfile().getName())
                .sorted()
                .toList();
    }

    private static String sanitizeFakePlayerBaseName(String input) {
        String sanitized = input.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isBlank()) {
            sanitized = FAKE_PLAYER_FALLBACK_NAME;
        }
        if (sanitized.length() > MAX_FAKE_PLAYER_NAME_LENGTH) {
            return sanitized.substring(0, MAX_FAKE_PLAYER_NAME_LENGTH);
        }
        return sanitized;
    }

    private static String buildFakePlayerName(String baseName, int index, int totalCount) {
        if (totalCount <= 1) {
            return trimToNameLimit(baseName);
        }

        String suffix = "_" + index;
        int maxBaseLength = Math.max(1, MAX_FAKE_PLAYER_NAME_LENGTH - suffix.length());
        String trimmedBase = baseName.length() > maxBaseLength ? baseName.substring(0, maxBaseLength) : baseName;
        return trimmedBase + suffix;
    }

    private static String trimToNameLimit(String value) {
        return value.length() <= MAX_FAKE_PLAYER_NAME_LENGTH
                ? value
                : value.substring(0, MAX_FAKE_PLAYER_NAME_LENGTH);
    }

    private static int setSpectatorMode(ServerCommandSource source, boolean enabled) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        info.tts$setDenyToPlay(enabled);

        source.sendFeedback(
                () -> Text.literal("관전자 모드가 " + (enabled ? "활성화" : "비활성화") + "되었습니다."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }
}
