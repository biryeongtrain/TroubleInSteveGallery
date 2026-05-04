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
import kim.biryeong.ttt.TroubleInTerroristTownMod;
import kim.biryeong.ttt.config.Config;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.ItemLoadout;
import kim.biryeong.ttt.player.ItemLoadouts;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.ui.dialog.guide.GuideListDialog;
import kim.biryeong.ttt.ui.dialog.loadout.ItemLoadoutDialog;
import kim.biryeong.ttt.ui.dialog.log.PlayerStatisticsDialog;
import kim.biryeong.ttt.util.AvatarTextRenderer;
import kim.biryeong.ttt.util.DebugFakePlayerRegistry;
import kim.biryeong.ttt.util.KoreanKeyboardConverter;
import kim.biryeong.ttt.util.Scheduler;
import kim.biryeong.ttt.util.explosion.ExplosionUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static kim.biryeong.ttt.game.manager.GameManager.byMiniMessage;

@SuppressWarnings("unused")
public class CommandInitializer {
    private static final int MAX_FAKE_PLAYER_NAME_LENGTH = 16;
    private static final int MAX_FAKE_PLAYER_SPAWN_COUNT = 32;
    private static final int ACCUSE_COOLDOWN_TICKS = 100;
    private static final int START_COUNTDOWN_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final String FAKE_PLAYER_FALLBACK_NAME = "FakePlayer";
    private static final Text DEBUG_FAKE_PLAYER_DISCONNECT_REASON = Text.literal("디버그 가짜 플레이어가 제거되었습니다.");
    private static final Map<UUID, Integer> ACCUSE_LAST_USED_TICKS = new HashMap<>();
    private static int startCountdownToken = 0;
    private static @Nullable PendingStartCountdown pendingStartCountdown;

    private record PendingStartCountdown(
            int token,
            boolean resetPoints,
            Identifier mapId,
            boolean recordReplay
    ) {
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> adminRoot = CommandManager.literal("tts_admin")
                .requires(source -> source.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> startGame = CommandManager.literal("start")
                .then(CommandManager.argument("resetPoints", BoolArgumentType.bool())
                        .executes(ctx -> executeStartGame(
                                ctx.getSource(),
                                BoolArgumentType.getBool(ctx, "resetPoints"),
                                null,
                                true
                        ))
                        .then(CommandManager.argument("recordReplay", BoolArgumentType.bool())
                                .executes(ctx -> executeStartGame(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "resetPoints"),
                                        null,
                                        BoolArgumentType.getBool(ctx, "recordReplay")
                                )))
                        .then(CommandManager.argument("map", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) ->
                                        CommandSource.suggestIdentifiers(
                                                GameManager.getInstance().getRegisteredRoundMapIds(),
                                                builder
                                        ))
                                .executes(ctx -> executeStartGame(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "resetPoints"),
                                        IdentifierArgumentType.getIdentifier(ctx, "map"),
                                        true
                                ))
                                .then(CommandManager.argument("recordReplay", BoolArgumentType.bool())
                                        .executes(ctx -> executeStartGame(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "resetPoints"),
                                                IdentifierArgumentType.getIdentifier(ctx, "map"),
                                                BoolArgumentType.getBool(ctx, "recordReplay")
                                        )))))
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
                    if (cancelStartCountdown(ctx.getSource(), true)) {
                        return Command.SINGLE_SUCCESS;
                    }
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
        LiteralCommandNode<ServerCommandSource> spectatorModeAlias = CommandManager.literal("spectator")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setSpectatorMode(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();

        LiteralCommandNode<ServerCommandSource> tips = CommandManager.literal("tips")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setTipsEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();
        LiteralCommandNode<ServerCommandSource> bgm = CommandManager.literal("bgm")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setBgmEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();
        LiteralCommandNode<ServerCommandSource> sidebar = CommandManager.literal("sidebar")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setSidebarEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();
        LiteralCommandNode<ServerCommandSource> displayHud = CommandManager.literal("display_hud")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setDisplayHudEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();
        LiteralCommandNode<ServerCommandSource> displayHudAlias = CommandManager.literal("display-hud")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> setDisplayHudEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))
                .build();
        LiteralCommandNode<ServerCommandSource> ui = CommandManager.literal("ui")
                .then(CommandManager.literal("legacy")
                        .executes(ctx -> setUiMode(ctx.getSource(), true)))
                .then(CommandManager.literal("display_hud")
                        .executes(ctx -> setUiMode(ctx.getSource(), false)))
                .then(CommandManager.literal("display-hud")
                        .executes(ctx -> setUiMode(ctx.getSource(), false)))
                .build();

        LiteralCommandNode<ServerCommandSource> guide = CommandManager.literal("guide")
                .executes(ctx -> openGuideMenu(ctx.getSource()))
                .then(CommandManager.literal("basic")
                        .executes(ctx -> openBasicGuidePage(ctx.getSource(), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openBasicGuidePage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))))
                .then(CommandManager.literal("innocent")
                        .executes(ctx -> openInnocentGuidePage(ctx.getSource(), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openInnocentGuidePage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))))
                .then(CommandManager.literal("traitor")
                        .executes(ctx -> openTraitorGuidePage(ctx.getSource(), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openTraitorGuidePage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))))
                .then(CommandManager.literal("detective")
                        .executes(ctx -> openDetectiveGuidePage(ctx.getSource(), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openDetectiveGuidePage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))))
                .then(CommandManager.literal("update")
                        .executes(ctx -> openUpdateGuidePage(ctx.getSource(), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openUpdateGuidePage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))))
                .build();

        LiteralCommandNode<ServerCommandSource> accuse = CommandManager.literal("accuse")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeAccuse(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target")
                        )))
                .build();
        LiteralCommandNode<ServerCommandSource> quickAccuse = CommandManager.literal("t")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> executeAccuse(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target")
                        )))
                .build();
        LiteralCommandNode<ServerCommandSource> stats = CommandManager.literal("stats")
                .executes(ctx -> showStats(ctx.getSource(), null))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> showStats(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "player")
                        )))
                .build();
        SuggestionProvider<ServerCommandSource> loadoutIdSuggester = (ctx, builder) ->
                CommandSource.suggestIdentifiers(
                        ItemLoadouts.getLoadouts().stream()
                                .map(ItemLoadout::loadoutId)
                                .sorted(Comparator.comparing(Identifier::toString))
                                .toList(),
                        builder
                );
        LiteralCommandNode<ServerCommandSource> loadout = CommandManager.literal("loadout")
                .executes(ctx -> openLoadoutDialog(ctx.getSource()))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                                .suggests(loadoutIdSuggester)
                                .executes(ctx -> setLoadout(
                                        ctx.getSource(),
                                        IdentifierArgumentType.getIdentifier(ctx, "id")
                                ))))
                .build();
        LiteralCommandNode<ServerCommandSource> traitorChat = CommandManager.literal("tc")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> executeTraitorChat(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "message")
                        )))
                .build();

        LiteralCommandNode<ServerCommandSource> reload = CommandManager.literal("reload")
                .executes(ctx -> {
                    Config.reload();
                    GameManager manager = GameManager.getInstance();
                    manager.reloadMapData(manager.getAllMapIds());
                    return Command.SINGLE_SUCCESS;
                })
                .build();

        LiteralCommandNode<ServerCommandSource> resetEntropy = CommandManager.literal("reset_entropy")
                .executes(ctx -> resetEntropyForPlayers(
                        ctx.getSource(),
                        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                ))
                .then(CommandManager.literal("all")
                        .executes(ctx -> resetEntropyForPlayers(
                                ctx.getSource(),
                                ctx.getSource().getServer().getPlayerManager().getPlayerList()
                        )))
                .then(CommandManager.argument("players", EntityArgumentType.players())
                        .executes(ctx -> resetEntropyForPlayers(
                                ctx.getSource(),
                                EntityArgumentType.getPlayers(ctx, "players")
                        )))
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

        LiteralCommandNode<ServerCommandSource> dialogTest = DebugDialogTestCommand.createNode();
        LiteralCommandNode<ServerCommandSource> legacyGuideImageTest = DebugDialogTestCommand.createLegacyGuideImageTestNode();

        LiteralCommandNode<ServerCommandSource> fakePlayerCommand = createFakePlayerCommand();

        adminRoot.addChild(startGame);
        adminRoot.addChild(stopGame);
        adminRoot.addChild(setRole);
        adminRoot.addChild(reload);
        adminRoot.addChild(resetEntropy);

        userRoot.addChild(spectatorMode);
        userRoot.addChild(spectatorModeAlias);
        userRoot.addChild(tips);
        userRoot.addChild(bgm);
        userRoot.addChild(sidebar);
        userRoot.addChild(displayHud);
        userRoot.addChild(displayHudAlias);
        userRoot.addChild(ui);
        userRoot.addChild(guide);
        userRoot.addChild(accuse);
        userRoot.addChild(stats);
        userRoot.addChild(loadout);

        debugNode.addChild(createExplosion);
        debugNode.addChild(enableDebugMode);
        debugNode.addChild(dialogTest);
        debugNode.addChild(legacyGuideImageTest);
        debugNode.addChild(fakePlayerCommand);

        dispatcher.getRoot().addChild(adminRoot);
        dispatcher.getRoot().addChild(userRoot);
        dispatcher.getRoot().addChild(quickAccuse);
        dispatcher.getRoot().addChild(traitorChat);
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

    private static int executeStartGame(
            ServerCommandSource source,
            boolean resetPoints,
            @Nullable Identifier mapId,
            boolean recordReplay
    ) {
        GameManager manager = GameManager.getInstance();
        if (manager.isGameInitializing() || manager.isGameStarted()) {
            source.sendError(Text.literal("게임이 이미 시작되었거나 초기화 중입니다."));
            return 0;
        }
        if (pendingStartCountdown != null) {
            source.sendError(Text.literal("이미 게임 시작 카운트다운이 진행 중입니다."));
            return 0;
        }

        if (mapId != null && !manager.hasRoundMapTemplate(mapId)) {
            List<String> registeredMaps = manager.getRegisteredRoundMapIds().stream()
                    .map(Identifier::toString)
                    .toList();

            if (registeredMaps.isEmpty()) {
                source.sendError(Text.literal("사용 가능한 라운드 맵이 없습니다. datapack의 map_template를 확인하세요."));
                return 0;
            }

            source.sendError(Text.literal(
                    "등록되지 않은 맵입니다: " + mapId + " (사용 가능: " + String.join(", ", registeredMaps) + ")"
            ));
            return 0;
        }

        Identifier targetMapId;
        try {
            targetMapId = manager.resolveNextRoundMapId(mapId);
        } catch (IllegalStateException exception) {
            source.sendError(Text.literal("사용 가능한 라운드 맵이 없습니다. datapack의 map_template를 확인하세요."));
            return 0;
        }

        scheduleStartCountdown(source, resetPoints, targetMapId, recordReplay);
        source.sendFeedback(
                () -> Text.literal("게임 시작 카운트다운을 시작했습니다. (10초)"),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void scheduleStartCountdown(
            ServerCommandSource source,
            boolean resetPoints,
            Identifier targetMapId,
            boolean recordReplay
    ) {
        int token = ++startCountdownToken;
        pendingStartCountdown = new PendingStartCountdown(token, resetPoints, targetMapId, recordReplay);
        source.getServer().getPlayerManager().broadcast(
                buildStartCountdownAnnouncementText(targetMapId, START_COUNTDOWN_SECONDS),
                false
        );

        for (int secondsLeft = START_COUNTDOWN_SECONDS; secondsLeft >= 1; secondsLeft--) {
            final int remaining = secondsLeft;
            int delayTicks = (START_COUNTDOWN_SECONDS - remaining) * TICKS_PER_SECOND;
            Scheduler.INSTANCE.submit(
                    (Consumer<MinecraftServer>) server -> runStartCountdownTick(server, token, remaining),
                    delayTicks
            );
        }
        Scheduler.INSTANCE.submit(
                (Consumer<MinecraftServer>) server -> runStartCountdownComplete(server, token),
                START_COUNTDOWN_SECONDS * TICKS_PER_SECOND
        );
    }

    private static void runStartCountdownTick(MinecraftServer server, int token, int secondsLeft) {
        if (!isStartCountdownActive(token)) {
            return;
        }

        server.getPlayerManager().broadcast(buildStartCountdownTickText(secondsLeft), false);
        playStartCountdownSound(server);
    }

    private static void runStartCountdownComplete(MinecraftServer server, int token) {
        PendingStartCountdown countdown = takeStartCountdown(token);
        if (countdown == null) {
            return;
        }

        GameManager manager = GameManager.getInstance();
        if (manager.isGameInitializing() || manager.isGameStarted()) {
            return;
        }
        manager.startGame(countdown.resetPoints(), countdown.mapId(), countdown.recordReplay());
    }

    private static boolean isStartCountdownActive(int token) {
        PendingStartCountdown countdown = pendingStartCountdown;
        if (countdown == null || countdown.token() != token) {
            return false;
        }

        GameManager manager = GameManager.getInstance();
        if (manager.isGameInitializing() || manager.isGameStarted()) {
            pendingStartCountdown = null;
            return false;
        }
        return true;
    }

    private static @Nullable PendingStartCountdown takeStartCountdown(int token) {
        PendingStartCountdown countdown = pendingStartCountdown;
        if (countdown == null || countdown.token() != token) {
            return null;
        }
        pendingStartCountdown = null;
        return countdown;
    }

    private static boolean cancelStartCountdown(ServerCommandSource source, boolean broadcastToPlayers) {
        if (pendingStartCountdown == null) {
            return false;
        }

        pendingStartCountdown = null;
        source.sendFeedback(() -> Text.literal("게임 시작 카운트다운을 취소했습니다."), true);
        if (broadcastToPlayers) {
            source.getServer().getPlayerManager().broadcast(Text.literal("[TTT] 게임 시작 카운트다운이 취소되었습니다."), false);
        }
        return true;
    }

    private static void playStartCountdownSound(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_DISPENSE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    static Text buildStartCountdownAnnouncementText(Identifier mapId, int seconds) {
        return byMiniMessage("<yellow>[TTT] " + seconds + "</yellow><green>초 후 게임이 시작됩니다. 맵: <red>" + mapId);
    }

    static Text buildStartCountdownTickText(int secondsLeft) {
        return byMiniMessage("<yellow>[TTT]<yellow> <green>게임 시작까지</green> <yellow>" + secondsLeft + "</yellow><green>초</green>");
    }

    private static int setTipsEnabled(ServerCommandSource source, boolean enabled) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        info.tts$setTipsEnabled(enabled);
        source.sendFeedback(
                () -> Text.literal("Game tips are now " + (enabled ? "enabled." : "disabled.")),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int resetEntropyForPlayers(ServerCommandSource source, Collection<ServerPlayerEntity> players) {
        if (players.isEmpty()) {
            source.sendError(Text.literal("초기화할 플레이어가 없습니다."));
            return 0;
        }

        GameManager manager = GameManager.getInstance();
        int resetCount = 0;
        for (ServerPlayerEntity player : players) {
            int before = manager.getRoleEntropy(player);
            manager.resetRoleEntropy(player);
            int after = manager.getRoleEntropy(player);
            resetCount++;
            TroubleInTerroristTownMod.LOGGER.info(
                    "Role entropy reset by {} for {} ({}) {} -> {}",
                    source.getName(),
                    player.getGameProfile().getName(),
                    player.getUuid(),
                    before,
                    after
            );
        }

        int finalResetCount = resetCount;
        source.sendFeedback(
                () -> Text.literal("역할 엔트로피를 " + finalResetCount + "명 초기화했습니다."),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int setBgmEnabled(ServerCommandSource source, boolean enabled) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        info.tts$setBgmEnabled(enabled);
        source.sendFeedback(
                () -> Text.literal("Lobby BGM is now " + (enabled ? "enabled." : "disabled.")),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int setSidebarEnabled(ServerCommandSource source, boolean enabled) {
        return setUiMode(source, enabled);
    }

    private static int setDisplayHudEnabled(ServerCommandSource source, boolean enabled) {
        return setUiMode(source, !enabled);
    }

    private static int setUiMode(ServerCommandSource source, boolean useLegacySidebar) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command can only be used by players."));
            return 0;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        info.tts$setSidebarEnabled(useLegacySidebar);
        GameManager manager = GameManager.getInstance();
        if (useLegacySidebar) {
            manager.removeRoundHudPlayer(player);
            manager.addDefaultSidebarPlayer(player);
        } else {
            manager.removeDefaultSidebarPlayer(player);
            manager.addRoundHudPlayer(player);
        }
        source.sendFeedback(
                () -> Text.literal("UI mode is now " + (useLegacySidebar ? "legacy sidebar." : "display HUD.")),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int openLoadoutDialog(ServerCommandSource source) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        return ItemLoadoutDialog.show(player);
    }

    private static int setLoadout(ServerCommandSource source, Identifier loadoutId) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("콘솔에서는 /tts loadout set <id> 를 사용할 수 없습니다."));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        if (!ItemLoadouts.LOADOUTS.containsKey(loadoutId)) {
            source.sendError(Text.literal("알 수 없는 로드아웃입니다: " + loadoutId));
            return 0;
        }

        InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
        ItemLoadout selectedLoadout = ItemLoadouts.get(loadoutId);
        info.tts$setItemLoadout(selectedLoadout);
        source.sendFeedback(
                () -> Text.empty()
                        .append(Text.literal("로드아웃을 "))
                        .append(selectedLoadout.displayName().copy().formatted(Formatting.GOLD))
                        .append(Text.literal(" 으로 설정했습니다.")),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int showStats(ServerCommandSource source, @Nullable ServerPlayerEntity targetArgument) {
        ServerPlayerEntity target = targetArgument;
        if (target == null) {
            if (!source.isExecutedByPlayer()) {
                source.sendError(Text.literal("콘솔에서는 /tts stats <player> 형식으로 사용하세요."));
                return 0;
            }
            target = source.getPlayer();
        }

        if (target == null) {
            source.sendError(Text.literal("통계 대상을 찾을 수 없습니다."));
            return 0;
        }

        GameManager.PlayerStatisticsSnapshot stats;
        try {
            stats = GameManager.getInstance().getPlayerStatisticsSnapshot(target);
        } catch (IllegalStateException exception) {
            source.sendError(Text.literal("통계 데이터를 불러올 수 없습니다: " + exception.getMessage()));
            return 0;
        }

        List<Text> lines = buildStatsLines(target.getGameProfile().getName(), stats);
        if (source.isExecutedByPlayer()) {
            ServerPlayerEntity viewer = source.getPlayer();
            if (viewer != null) {
                PlayerStatisticsDialog.show(viewer, lines);
                return Command.SINGLE_SUCCESS;
            }
        }

        for (Text line : lines) {
            source.sendFeedback(() -> line, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static List<Text> buildStatsLines(String playerName, GameManager.PlayerStatisticsSnapshot stats) {
        return List.of(
                Text.literal(""),
                Text.literal("[TTT 통계] " + playerName).formatted(Formatting.GOLD),
                Text.literal("킬 / 데스: " + stats.kills() + " / " + stats.deaths()
                        + " (K/D " + formatKillDeathRatio(stats.kills(), stats.deaths()) + ")"),
                Text.literal("지목 적중률: " + formatRateSummary(stats.accuseHits(), stats.accuseAttempts())),
                Text.literal("팀 킬 확률: " + formatRateSummary(stats.teamKills(), stats.kills())),
                Text.literal("[플레이 횟수]"),
                Text.literal("플레이 횟수: " + stats.playCount()),
                Text.literal("이노센트 횟수: " + stats.innocentCount()),
                Text.literal("트레이터 횟수: " + stats.traitorCount()),
                Text.literal("탐정 횟수: " + stats.detectiveCount())
        );
    }

    static String formatKillDeathRatio(int kills, int deaths) {
        int normalizedKills = Math.max(0, kills);
        int normalizedDeaths = Math.max(0, deaths);
        if (normalizedDeaths == 0) {
            return normalizedKills == 0 ? "0.00" : "INF";
        }
        return String.format(Locale.ROOT, "%.2f", (double) normalizedKills / normalizedDeaths);
    }

    static String formatRateSummary(int numerator, int denominator) {
        int normalizedDenominator = Math.max(0, denominator);
        int normalizedNumerator = Math.max(0, Math.min(numerator, normalizedDenominator));
        if (normalizedDenominator == 0) {
            return "기록 없음";
        }

        double percent = (double) normalizedNumerator * 100.0 / normalizedDenominator;
        return normalizedNumerator + "/" + normalizedDenominator
                + " (" + String.format(Locale.ROOT, "%.1f%%", percent) + ")";
    }

    private static int executeAccuse(ServerCommandSource source, ServerPlayerEntity target) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        GameManager manager = GameManager.getInstance();
        InGamePlayerInfoProvider senderInfo = (InGamePlayerInfoProvider) sender;
        if (!canUseAccuseCommand(manager.getCurrentPhase(), manager.isAlive(sender), senderInfo.tts$getRole())) {
            source.sendError(Text.literal("이 명령어는 생존한 플레이어가 라운드 진행 중일 때만 사용할 수 있습니다."));
            return 0;
        }
        int serverTick = source.getServer().getTicks();
        int remainingCooldownTicks = getAccuseCooldownRemainingTicks(sender.getUuid(), serverTick);
        if (remainingCooldownTicks > 0) {
            source.sendError(buildAccuseCooldownText(remainingCooldownTicks));
            return 0;
        }

        Text senderAvatar = AvatarTextRenderer.resolveSmallAvatar(
                sender.getUuid(),
                sender.getGameProfile().getName(),
                false
        );
        Text targetAvatar = AvatarTextRenderer.resolveSmallAvatar(
                target.getUuid(),
                target.getGameProfile().getName(),
                false
        );
        Text broadcastMessage = buildAccuseBroadcastText(
                senderAvatar,
                sender.getDisplayName(),
                targetAvatar,
                target.getDisplayName()
        );
        source.getServer().getPlayerManager().broadcast(broadcastMessage, false);
        manager.recordAccuseResult(sender, target);
        ACCUSE_LAST_USED_TICKS.put(sender.getUuid(), serverTick);
        return Command.SINGLE_SUCCESS;
    }

    static boolean canUseAccuseCommand(GameManager.Phase phase, boolean senderAlive, Role senderRole) {
        return phase.canShowRole() && senderAlive && senderRole != Role.SPECTATOR;
    }

    static int accuseCooldownTicks() {
        return ACCUSE_COOLDOWN_TICKS;
    }

    static int calculateAccuseCooldownRemainingTicks(int lastUseTick, int currentTick, int cooldownTicks) {
        int normalizedCooldownTicks = Math.max(0, cooldownTicks);
        int elapsedTicks = currentTick - lastUseTick;
        if (elapsedTicks < 0) {
            return 0;
        }
        return Math.max(0, normalizedCooldownTicks - elapsedTicks);
    }

    static int toCooldownDisplaySeconds(int remainingTicks) {
        int ticks = Math.max(0, remainingTicks);
        if (ticks == 0) {
            return 0;
        }
        return (ticks + 19) / 20;
    }

    static Text buildAccuseCooldownText(int remainingTicks) {
        return Text.literal("지목 채팅 쿨타임입니다. " + toCooldownDisplaySeconds(remainingTicks) + "초 후 다시 시도하세요.");
    }

    public static int getAccuseCooldownRemainingTicks(UUID playerUuid, int currentTick) {
        Integer lastUseTick = ACCUSE_LAST_USED_TICKS.get(playerUuid);
        if (lastUseTick == null) {
            return 0;
        }

        return calculateAccuseCooldownRemainingTicks(lastUseTick, currentTick, ACCUSE_COOLDOWN_TICKS);
    }

    public static Text buildAccuseBroadcastText(
            @Nullable Text senderAvatar,
            Text senderName,
            @Nullable Text targetAvatar,
            Text targetName
    ) {
        Text senderWithAvatar = prependAvatar(senderAvatar, senderName.copy().formatted(Formatting.YELLOW));
        Text targetWithAvatar = prependAvatar(targetAvatar, targetName.copy().formatted(Formatting.RED));

        TroubleInTerroristTownMod.LOGGER.info("{} 이 트레지목 -> {}", senderName.getString(), targetName.getString());
        return Text.empty()
                .append(Text.literal("\n[지목] ").formatted(Formatting.RED))
                .append(senderWithAvatar)
                .append(Text.literal(" 님이 ").formatted(Formatting.GRAY))
                .append(targetWithAvatar)
                .append(Text.literal(" 님을 트레이터로 지목했습니다.\n").formatted(Formatting.GRAY));


    }

    private static Text prependAvatar(@Nullable Text avatar, Text name) {
        if (avatar == null) {
            return name;
        }

        return Text.empty()
                .append(avatar.copy())
                .append(Text.literal(" "))
                .append(name);
    }

    private static int executeTraitorChat(ServerCommandSource source, String message) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        GameManager manager = GameManager.getInstance();
        InGamePlayerInfoProvider senderInfo = (InGamePlayerInfoProvider) sender;
        if (!canUseTraitorChat(manager.getCurrentPhase(), manager.isAlive(sender), senderInfo.tts$getRole())) {
            source.sendError(Text.literal("이 명령어는 생존한 트레이터가 라운드 진행 중일 때만 사용할 수 있습니다."));
            return 0;
        }

        String convertedMessage = KoreanKeyboardConverter.convertChat(message);
        Text traitorChatText = buildTraitorChatText(sender.getDisplayName(), convertedMessage);
        for (ServerPlayerEntity candidate : source.getServer().getPlayerManager().getPlayerList()) {
            InGamePlayerInfoProvider candidateInfo = (InGamePlayerInfoProvider) candidate;
            if (canReceiveTraitorChat(manager.isAlive(candidate), candidateInfo.tts$getRole())) {
                candidate.sendMessage(traitorChatText.copy(), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    static boolean canUseTraitorChat(GameManager.Phase phase, boolean senderAlive, Role senderRole) {
        return phase.canShowRole() && senderAlive && senderRole == Role.TRAITOR;
    }

    static boolean canReceiveTraitorChat(boolean candidateAlive, Role candidateRole) {
        return candidateAlive && candidateRole == Role.TRAITOR;
    }

    static Text buildTraitorChatText(Text senderName, String convertedMessage) {
        return Text.empty()
                .append(Text.literal("[트레이터] ").formatted(Formatting.DARK_RED))
                .append(senderName.copy().formatted(Formatting.RED))
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(Text.literal(convertedMessage));
    }

    private static int openGuideMenu(ServerCommandSource source) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showMenu(player);
    }

    private static int openBasicGuidePage(ServerCommandSource source, int page) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showBasicGuide(player, page);
    }

    private static int openTraitorGuidePage(ServerCommandSource source, int page) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showTraitorGuide(player, page);
    }

    private static int openInnocentGuidePage(ServerCommandSource source, int page) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showInnocentGuide(player, page);
    }

    private static int openDetectiveGuidePage(ServerCommandSource source, int page) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showDetectiveGuide(player, page);
    }

    private static int openUpdateGuidePage(ServerCommandSource source, int page) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        return GuideListDialog.showUpdateGuide(player, page);
    }

    private static int openGuideCategory(ServerCommandSource source, GuideCategory category) {
        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("이 명령어는 플레이어만 실행할 수 있습니다."));
            return 0;
        }

        return switch (category) {
            case BASIC -> GuideListDialog.showBasicGuide(player);
            case INNOCENT -> GuideListDialog.showInnocentGuide(player);
            case TRAITOR -> GuideListDialog.showTraitorGuide(player);
            case DETECTIVE -> GuideListDialog.showDetectiveGuide(player);
            case UPDATE -> GuideListDialog.showUpdateGuide(player);
        };
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

    private enum GuideCategory {
        BASIC,
        INNOCENT,
        TRAITOR,
        DETECTIVE,
        UPDATE
    }
}
