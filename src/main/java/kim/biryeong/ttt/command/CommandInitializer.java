package kim.biryeong.ttt.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import kim.biryeong.ttt.game.data.PlayerDataInstance;
import kim.biryeong.ttt.game.manager.GameManager;
import kim.biryeong.ttt.player.duck.InGamePlayerInfoProvider;
import kim.biryeong.ttt.player.role.Role;
import kim.biryeong.ttt.util.explosion.ExplosionUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.Locale;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class CommandInitializer {
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> adminRoot = CommandManager.literal("tts_admin")
                .requires(x -> x.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> startGame = CommandManager.literal("start")
                .then(CommandManager.argument("resetPoints", BoolArgumentType.bool())
                        .executes(ctx -> {
                            if (GameManager.getInstance().isGameInitializing() || GameManager.getInstance().isGameStarted()) {
                                ctx.getSource().sendError(Text.literal("Game is already started or initializing."));
                                return 0;
                            }
                            boolean reset = BoolArgumentType.getBool(ctx, "resetPoints");
                            GameManager.getInstance().startGame(reset);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();


        final SuggestionProvider<ServerCommandSource> ROLE_SUGGESTER = (ctx, builder) ->
                CommandSource.suggestMatching(Stream.of(Role.values()).map(Role::asString).toList(), builder);

        LiteralCommandNode<ServerCommandSource> setRole = CommandManager.literal("set_role")
                .then(CommandManager.argument("role", StringArgumentType.string()).suggests(ROLE_SUGGESTER)
                        .executes(ctx -> {
                            var role = Role.valueOf(StringArgumentType.getString(ctx, "role").toUpperCase(Locale.ENGLISH));
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            InGamePlayerInfoProvider info = (InGamePlayerInfoProvider) player;
                            info.tts$setRole(role);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        LiteralCommandNode<ServerCommandSource> stopGame = CommandManager.literal("stop")
                .executes(ctx -> {
                    if (!GameManager.getInstance().isGameStarted()) {
                        ctx.getSource().sendError(Text.literal("No game is currently running."));
                        return 0;
                    }
                    GameManager.getInstance().stopGame(PlayerDataInstance.Result.CANCELED);
                    return Command.SINGLE_SUCCESS;
                }).build();

        LiteralCommandNode<ServerCommandSource> userRoot = CommandManager.literal("tts")
                .build();

        LiteralCommandNode<ServerCommandSource> spectatorMode = CommandManager.literal("spectator_mode")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();


        LiteralCommandNode<ServerCommandSource> debugNode = CommandManager.literal("tts_debug")
                .requires(ctx -> ctx.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> createExplosion = CommandManager.literal("explode")
                .executes(ctx -> {
                    if (!ctx.getSource().isExecutedByPlayer()) {
                        ctx.getSource().sendError(Text.literal("This command can only be executed by a player."));
                        return 0;
                    }

                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ExplosionImpl explosion = ExplosionUtil.createExplosion(null, player.getSyncedPos(), player.getEntityWorld(), 7);

                    explosion.explode();

                    return Command.SINGLE_SUCCESS;
                })
                .build();

        LiteralCommandNode<ServerCommandSource> enableDebugMode = CommandManager.literal("debugmode")
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            GameManager.getInstance().setDebugMode(BoolArgumentType.getBool(ctx, "value"));
                            return Command.SINGLE_SUCCESS;
                        })
                ).build();

        adminRoot.addChild(startGame);
        adminRoot.addChild(stopGame);
        adminRoot.addChild(setRole);

        userRoot.addChild(spectatorMode);

        debugNode.addChild(createExplosion);
        debugNode.addChild(enableDebugMode);

        dispatcher.getRoot().addChild(adminRoot);
        dispatcher.getRoot().addChild(userRoot);
        dispatcher.getRoot().addChild(debugNode);
    }
}
