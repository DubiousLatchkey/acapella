package net.fabricmc.Acapella;

import net.fabricmc.api.ModInitializer;

import org.apache.http.entity.EntityTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;

import baritone.api.IBaritoneProvider;

import static net.minecraft.server.command.CommandManager.*;

import static net.minecraft.server.command.CommandManager.literal;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.block.Blocks;


import net.fabricmc.Acapella.commands.*;


public class Acapella implements ModInitializer {

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    //public static final Item CUSTOM_ITEM = new Item(new FabricItemSettings());
	public static StateMachine stateMachine;
	boolean wasEating = false;
	
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		//copy relavent schematic files into right place
		//path is ../../../../resources/buildSchematics to
		// ../../../../../../run/schematics
		//also need to make folder schematics if it doesn't exist
		
		try {

			Path path = Paths.get("schematics");
		
			//java.nio.file.Files;
			Files.createDirectories(path);
		
			System.out.println("Directory is created!");
		
		} catch (IOException e) {
		
			System.err.println("Failed to create directory!" + e.getMessage());
		
		  }

		  				//THIS WONT WORK IF YOU WERE TO DOWNLOAD THIS MOD. NEED ANOTHER IMPLEMENTATION
		  //get path of each file to copy from:
		// Path craftingPath = FileSystems.getDefault().getPath("../src/main/resources/buildSchematics/", "crafting_table.schem");
		// Path furnacePath = FileSystems.getDefault().getPath("../src/main/resources/buildSchematics/", "furnace.schem");
		// Path portalPath = FileSystems.getDefault().getPath("../src/main/resources/buildSchematics/", "portal.schem");

		// Path craftingTo = FileSystems.getDefault().getPath("schematics", "crafting_table.schem");
		// Path furnaceTo = FileSystems.getDefault().getPath("schematics", "furnace.schem");
		// Path portalTo = FileSystems.getDefault().getPath("schematics", "portal.schem");

		// if (!Files.exists(craftingTo)) {
		// 	try {
		// 		if (!Files.exists(craftingPath)) {
		// 			System.err.println("Failed to copy from crafting table schematic");
		// 		}
		// 		Files.createFile(craftingTo);
		// 		Files.copy(craftingPath, craftingTo, StandardCopyOption.REPLACE_EXISTING);
		// 	} catch (IOException e) {
		// 		System.err.println("Failed to copy file to " + e.getMessage());
		// 	  }
		// }
		// if (!Files.exists(furnaceTo)) {
		// 	try {
		// 		if (!Files.exists(furnacePath)) {
		// 			System.err.println("Failed to copy from furnace schematic");
		// 		}
		// 		Files.createFile(furnaceTo);
		// 		Files.copy(furnacePath, furnaceTo, StandardCopyOption.REPLACE_EXISTING);
		// 	} catch (IOException e) {
		// 		System.err.println("Failed to copy file to " + e.getMessage());
		// 	  }
		// }
		// if (!Files.exists(portalTo)) {
		// 	try {
		// 		if (!Files.exists(portalPath)) {
		// 			System.err.println("Failed to copy from portal schematic");
		// 		}
		// 		Files.createFile(portalTo);
		// 		Files.copy(portalPath, portalTo, StandardCopyOption.REPLACE_EXISTING);
		// 	} catch (IOException e) {
			// 		System.err.println("Failed to copy file to" + e.getMessage());
			// 	  }
			// }
			
			LOGGER.info("Beat Mincecraft");
			stateMachine = new StateMachine();
			InventoryHelper.init();
			
			
			//this registers /stack commands
			ClientCommandRegistrationCallback.EVENT.register(Acapella::registerCommands);



			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("beatMinecraft")
			.executes(context -> {
				// For versions below 1.19, replace "Text.literal" with "new LiteralText".
				context.getSource().sendMessage(Text.literal("Beating Minecraft..."));
				
				
				//stateMachine.addTask("defeat enderDragon");
				
				stateMachine.addTask("defeat enderDragon");
				
				
				return 1;
			})));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("interact")
         .executes(context -> {
             // For versions below 1.19, replace "Text.literal" with "new LiteralText".
            context.getSource().sendMessage(Text.literal("Attempting Interaction..."));

			//stateMachine.addTask("win the game");		 
			MinecraftClient client = MinecraftClient.getInstance();
			client.getServer().getCommandManager().execute(client.getServer().getCommandManager().getDispatcher().parse("kill @e[type=ender_dragon]", client.getServer().getCommandSource()), "kill @e[type=ender_dragon]");

			
			return 1;
        })));


		ClientTickEvents.START_CLIENT_TICK.register((MinecraftClient minecraftClient)->{
			if(minecraftClient.player == null) return;   //prevents this function from running while in game menu
			//THIS FUNCTION RUNS EVERY SINGLE TICK.
			//THIS FUNCTION HANDLES CODE THAT SHOULD ONLY BE RELEVANT DURING THE EXECUTION OF /beatMinecraft
			if(stateMachine.ticksToIdle > 0){ 
				LOGGER.info("Ticks left: " + stateMachine.ticksToIdle);
				stateMachine.ticksToIdle--; return;}
			if(stateMachine == null) return;
			if(!stateMachine.active) return;
			//check for interrupts / emergency-conditions
				

			//tick statemachine
			stateMachine.evaluate();
			


        });




        // CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("stack")
        //  .executes(context -> {

		// 	stateMachine.clearStack();

		// 	return 1;
		// })));

		// Stay Alive Behaviors
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			// if (mc.player != null
			// 				&& mc.player.getAttackCooldownProgress(0) >= 1) {
			// 	if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
			// 		Entity entity = ((EntityHitResult)mc.crosshairTarget).getEntity();
			// 		if (entity.isAlive() && entity.isAttackable()) {
			// 			mc.interactionManager.attackEntity(mc.player, entity);
			// 		}
			// 	}
			// }
			if(mc.world != null && mc.player.getHungerManager().isNotFull()){
				//Eat
				PlayerInventory inventory = mc.player.getInventory();
				for (int i = 0; i < 9;i++){
					ItemStack stack = inventory.getStack(i);
					if(stack.isFood()){
						if(inventory.selectedSlot != i){
						inventory.selectedSlot = i;
						inventory.markDirty();
						//stack.finishUsing(mc.world, mc.player);
						//mc.player.eatFood(mc.world, stack);
						// mc.player.setCurrentHand(Hand.MAIN_HAND);
						// mc.player.emitGameEvent(GameEvent.ITEM_INTERACT_START);
						// ServerWorld world = mc.getServer().getWorld((RegistryKey<World>)mc.getServer().getWorldRegistryKeys().toArray()[0]);
						// ServerPlayerEntity playerServer = world.getPlayers().get(0);
						// LOGGER.info(Boolean.toString(world.isClient));
						// for (int j = 0; j < 35; j++){
						// 	//LOGGER.info(Integer.toString(mc.player.getItemUseTimeLeft()));
						// 	((LivingEntity)mc.player).tick();
						// 	((LivingEntity)playerServer).tick();
						// }
						
						}
						//mc.player.handleStatus(EntityStatuses.CONSUME_ITEM);
						mc.options.useKey.setPressed(true);
						wasEating = true;
						break;
					}
				}
			}
			else{
				if(wasEating){
					mc.options.useKey.setPressed(false);
				}
				
				if (mc.world != null && mc.world.getTime() % 15 == 0) {
					Box nearby = new Box(mc.player.getBlockPos().add(-6,-6,-6),mc.player.getBlockPos().add(6,6,6));
					List<Entity> entities = new ArrayList<Entity>();


					for (Entity entity : mc.world.getEntitiesByType(EntityType.ZOMBIE, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.SKELETON, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.SPIDER, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.SILVERFISH, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					if(mc.world.getDimensionKey() != DimensionTypes.THE_END){
						for (Entity entity : mc.world.getEntitiesByType(EntityType.ENDERMAN, nearby, i->true)){
							LOGGER.info(entity.getEntityName());
							entities.add( entity);
						}
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.CAVE_SPIDER, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.PIG, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.COW, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.BLAZE, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.HOGLIN, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.WITHER_SKELETON, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}
					for (Entity entity : mc.world.getEntitiesByType(EntityType.MAGMA_CUBE, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}

					for (Entity entity : mc.world.getEntitiesByType(EntityType.PIGLIN, nearby, i->true)){
						LOGGER.info(entity.getEntityName());
						entities.add( entity);
					}

					if(entities.size() > 0){
						PlayerInventory inventory = mc.player.getInventory();
						inventory.selectedSlot = 0;
						inventory.markDirty();
					}

					entities.forEach(entity -> {
						if (entity.isAttackable()){
							mc.interactionManager.attackEntity(mc.player, entity);
						}
					});
				}
			}
		});
		
		
    }

	public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess){
		StackCommand.register(dispatcher);
	}

}