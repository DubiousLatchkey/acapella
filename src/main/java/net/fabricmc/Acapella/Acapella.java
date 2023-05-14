package net.fabricmc.Acapella;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;

import baritone.api.IBaritoneProvider;

import static net.minecraft.server.command.CommandManager.*;

import static net.minecraft.server.command.CommandManager.literal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;

public class Acapella implements ModInitializer {

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    //public static final Item CUSTOM_ITEM = new Item(new FabricItemSettings());
	StateMachine stateMachine;
	
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Beat Mincecraft");
		stateMachine = new StateMachine();
		InventoryHelper.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("beatMinecraft")
         .executes(context -> {
             // For versions below 1.19, replace "Text.literal" with "new LiteralText".
             context.getSource().sendMessage(Text.literal("Beating Minecraft..."));
             
			 stateMachine.addTask("get planks");
			 //stateMachine.addTask("get wood");
			 
			 return 1;
        })));


		ClientTickEvents.START_CLIENT_TICK.register((MinecraftClient minecraftClient)->{
			if(minecraftClient.player == null) return;   //prevents this function from running while in game menu
			//THIS FUNCTION RUNS EVERY SINGLE TICK.
			//THIS FUNCTION HANDLES CODE THAT SHOULD ONLY BE RELEVANT DURING THE EXECUTION OF /beatMinecraft
			if(stateMachine == null) return;
			if(!stateMachine.active) return;
				
			//check for interrupts / emergency-conditions

			stateMachine.evaluate(); //
			


        });


        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("debug")
         .executes(context -> {
             // For versions below 1.19, replace "Text.literal" with "new LiteralText".
			context.getSource().sendMessage(Text.literal("Debugging Baritone"));
			debug();

			return 1;
		})));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("mine")
         .executes(context -> {
             // For versions below 1.19, replace "Text.literal" with "new LiteralText".
			context.getSource().sendMessage(Text.literal("Mining some wood"));

			ClientPlayerEntity me = MinecraftClient.getInstance().player;
			IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(me);
			baritone.getMineProcess().mine(10, Blocks.OAK_LOG);

			return 1;
		})));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("clearStack")
         .executes(context -> {
             // For versions below 1.19, replace "Text.literal" with "new LiteralText".
			clearStack();

			return 1;
		})));
		
		
    }
	
	private void debug(){
		ClientPlayerEntity me = MinecraftClient.getInstance().player;
		IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(me);
		
		//check all baritone processes
		LOGGER.info("" + baritone.getFollowProcess().isActive());
		LOGGER.info("" + baritone.getMineProcess().isActive());
		LOGGER.info("" + baritone.getBuilderProcess().isActive());
		LOGGER.info("" + baritone.getExploreProcess().isActive());
		LOGGER.info("" + baritone.getFarmProcess().isActive());
		LOGGER.info("" + baritone.getCustomGoalProcess().isActive());
		LOGGER.info("" + baritone.getGetToBlockProcess().isActive());

		LOGGER.info("Statemachine State: " + stateMachine.active);
	}

	private void clearStack(){
		stateMachine.clearStack();
	}

}