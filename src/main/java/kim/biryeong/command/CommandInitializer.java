package kim.biryeong.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import kim.biryeong.game.manager.GameManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class CommandInitializer {
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> root = CommandManager.literal("tts")
                .requires(x -> x.hasPermissionLevel(3))
                .build();

        LiteralCommandNode<ServerCommandSource> startGame = CommandManager.literal("start")
                .then(CommandManager.argument("resetPoints", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean reset = BoolArgumentType.getBool(ctx, "resetPoints");
                            GameManager.getInstance().startGame(reset);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();


        LiteralCommandNode<ServerCommandSource> stopGame = CommandManager.literal("stop")
                        .executes(ctx -> {
                            GameManager.getInstance().stopGame();
                            return Command.SINGLE_SUCCESS;
                        }).build();
        root.addChild(startGame);
        root.addChild(stopGame);
        dispatcher.getRoot().addChild(root);
    }
}
