package net.fabricmc.Acapella.commands;

import net.fabricmc.Acapella.Acapella;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import org.slf4j.Logger;

public class StackCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    //this is how you make an exception type
    // private static final SimpleCommandExceptionType ILLEGAL_FORMAT_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.calias.illegalFormatException"));


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("stack")
            .then(literal("clear").
                executes(ctx -> clearStack(ctx.getSource())))
            
            .then(literal("print").
                executes(ctx -> printStack(ctx.getSource())))
            
            .then(literal("addTask")
                .then(argument("task",string())
                    .executes(ctx -> addTask(ctx.getSource(), getString(ctx, "task")))))
        );
    }

    




    private static int clearStack(FabricClientCommandSource source){
        source.sendFeedback(Text.literal("Cleared all tasks and Baritone actions"));
        Acapella.stateMachine.clearStack();
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(me);
        baritone.getPathingBehavior().cancelEverything();

        return 0;
    }

    private static int printStack(FabricClientCommandSource source){
        source.sendFeedback(Text.literal("Printing stack..."));
        Acapella.stateMachine.printStack();

        return 0;
    }

    private static int addTask(FabricClientCommandSource source, String task){
        source.sendFeedback(Text.literal("The player has run the command /stack addTask, with argument " + task));
        if(Acapella.stateMachine.addTask(task) == -1){
            source.sendFeedback(Text.literal("Error: " + task + " is not a real task"));
        }

        return 0;
    }

}

