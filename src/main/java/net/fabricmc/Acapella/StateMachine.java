package net.fabricmc.Acapella;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Stack;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.*;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.message.MessageType;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
//schematics:
import baritone.api.schematic.*;
import baritone.api.process.IBuilderProcess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import java.io.File;

//view:
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.utils.Rotation;

//jump and click:
import baritone.api.utils.input.*;

//goto:
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;

@Mixin(ClientPlayerEntity.class)
public class StateMachine {
    private BlockPos lastPortalPos;
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    public static Map<String, String> conditions;
    public static Map<String, String> actions;
    public static String currentState;

    public boolean active = false;

    private String currTaskName;

    public class Task{
        public String task;
        public List<String> args;
        public Task(String s){
            task = s;
            args = null;
        }
        public Task(String a, List<String> b){
            task = a;
            args = b;
        }
    };


    private Stack<Task> the_stack;
    
    private MinecraftClient mc;
    private ClientPlayerEntity me;
    private IBaritone baritone;
    


    public StateMachine(){

        active = false;
        currTaskName = "$";
        the_stack = new Stack<>();
        the_stack.push(new Task("$"));
    }

    public void addTask(String state, String... values){
        mc = MinecraftClient.getInstance();
        me = MinecraftClient.getInstance().player;
        baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(me);

        List<String> args = new ArrayList<>();
        for(String v : values){
            args.add(v);
        }

        the_stack.push(new Task(state,args));
        active = true;
        currTaskName = "$";
    }

    
    public void evaluate(){
        if(the_stack.peek().task == "$"){
            active = false;
            return;
        }

        LOGGER.info("Evaluating on task " + the_stack.peek().task);
        
        if(currTaskName == "$"){
            me.sendMessage(Text.literal("Starting new task: " + the_stack.peek().task));
            currTaskName = the_stack.peek().task;
            LOGGER.info("before Debug Statement");
            initiate_task(the_stack.peek());
            LOGGER.info("Debug Statement");
            return;
        }
        //CHECK CURRENT TASK IS DONE YET
        //check if baritone is active
        if(checkBaritoneActive()) return;
        if(!check_condition(the_stack.peek())) return; //returns if the condition is false
        
        the_stack.pop();
        currTaskName = "$";
        
        // currentState = nextState;
        if(the_stack.peek().task == "$"){
            me.sendMessage(Text.literal("END OF STATE MACHINE"));
        }        
    }

    private void initiate_task(Task task_arg){
        //make this work with Task<>
        String task = task_arg.task;
        List<String> args = task_arg.args;


        if(actions.get(task) == null){
            LOGGER.info("initiate_task: No mapping exists for this task!");
            me.sendMessage(Text.literal("INITIATE TASK FAILED"));
            clearStack();
            return;
        }
        try{
            Method method = this.getClass().getDeclaredMethod( actions.get(task)) ;

            method.setAccessible(true);
            try{
                if(args == null || args.size() == 0){
                    Object o = method.invoke(this);
                }else{
                    Object o = method.invoke(this,args);
                }
            } catch(IllegalAccessException e){
                LOGGER.info("initiate_task: No accessing that from here");
            } catch (InvocationTargetException e){
                LOGGER.info("initiate_task: What are you invoking from anyway?");
            } catch (Exception e){
                LOGGER.info("initiate_task: " + e.toString());
            }
        } catch(NoSuchMethodException e){
            LOGGER.info("initiate_task: No method found");
        }
    }

    private boolean check_condition(Task task_arg){
        String task = task_arg.task;
        List<String> args = task_arg.args;
        
        String condition = conditions.get(task);
        if(condition == null) return true;
        if(condition == "$") return true;
        try{
            Method method = this.getClass().getDeclaredMethod( condition) ;
            method.setAccessible(true);

            try{
                Object o;
                if(args == null || args.size() == 0){
                    o = method.invoke(this);
                }else{
                    o = method.invoke(this,args);
                }
                return (boolean)o;
            } catch(IllegalAccessException e){
                LOGGER.info("check_condition: No accessing that from here");
            } catch (InvocationTargetException e){
                LOGGER.info("check_condition: What are you invoking from anyway?");
            } catch (Exception e){
                LOGGER.info("check_condition: " + e.toString());
            }
        } catch(NoSuchMethodException e){
            LOGGER.info("check_condition: No method found");
        }

        return false;
    }


    private boolean checkBaritoneActive(){
        if(baritone.getFollowProcess().isActive()) return true;
        if(baritone.getMineProcess().isActive()) return true;
        if(baritone.getBuilderProcess().isActive()) return true;
        if(baritone.getExploreProcess().isActive()) return true;
        if(baritone.getFarmProcess().isActive()) return true;
        if(baritone.getCustomGoalProcess().isActive()) return true;
        if(baritone.getGetToBlockProcess().isActive()) return true;
        return false;
    }

    static {
        actions = Stream.of(new String[][] {
            { "start", "none" }, 
            { "defeat enderDragon", "ultimateTask"},
            { "get wood", "getWood" },
            { "get planks", "getPlanks"},
            { "craft planks", "craftWoodPlanks"},
            { "craft craft", "craftCraftingTable"},
            { "place craft", "placeCraftingTable"},
            { "start craft", "openCraftingTable"},
            { "open inventory", "openInventory"},
            { "close inventory", "closeScreen"},
            { "place furnace", "placeFurnace"},
            { "make portal", "placePortal"},
            { "light portal", "lightPortal"},
            { "clean inputs", "releaseKeyboard"},
            { "equip armor", "equipArmor"},
            { "equip all armor", "equipAllArmor"},


        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));


        conditions = Stream.of(new String[][] {
            { "start", "none" }, 
            { "defeat enderDragon", "$"},
            { "get wood", "$" },
            { "get planks", "$" },
            { "start craft", "$" },
            {"place craft", "$" },
            { "open inventory", "$" },
            { "close inventory", "$" },
            { "equip armor", "$" },
            { "craft planks", "$" },
            { "place furnace", "$" },
            { "make portal", "$" },
            { "light portal", "$" },
            { "clean inputs", "$" } 

          }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }


    public void ultimateTask(){
        the_stack.pop();
        // addTask("kill dragon");
        // addTask("enter end");
        // addTask("activate endportal");
        // addTask("find stronghold");
        // addTask("get eye of ender");
        // addTask("trade for ender pearls");
        // addTask("get blaze powder");
        // addTask("enter nether");
        // addTask("build portal");
        // addTask("get obsidian");
        // addTask("get diamonds");
        // addTask("get iron");
        // addTask("get stone pickaxe");
        // addTask("get wood pickaxe");
        addTask("place craft");
        addTask("craft craft");
        addTask("get planks");


    }




    // public void CRAFTRECIPE(List<String> args){
    //     Identifier my_item = Identifier.tryParse(args.get(0));
    //     int number;
    //     try {
    //         number = Integer.parseInt(args.get(1));
    //     } catch (NumberFormatException e) {
    //         LOGGER.info("Invalid number format: " + args.get(1));
    //         number = 5;
    //     }
    // }

    public void getPlanks(){
        the_stack.pop();
        addTask("craft planks");
        addTask("open inventory");
        addTask("get wood");
    }

    public void getWood(){
        getMaterial(Blocks.OAK_LOG);
    }

    
    public void getGrass(){
        getMaterial(Blocks.DIRT);
    }

    public void mineStone() {
        getMaterial(Blocks.STONE);
    }

    public void createPortal() {
        the_stack.pop();
        addTask("clean inputs");
        addTask("light portal");
        addTask("make portal");
    }


    public void none(){
        LOGGER.info("blank state");
        return;
    }
    
    public void getMaterial (Block block){
        LOGGER.info("Getting " + block.getName() );
        baritone.getMineProcess().mine(5, block);
        
    
    }

    public void clearStack(){
        active = false;
        currTaskName = "$";
        the_stack = new Stack<>();
        the_stack.push(new Task("$"));
    }

    public void openCraftingTable(){
        baritone.getGetToBlockProcess().getToBlock(Blocks.CRAFTING_TABLE);
    }

    public void openInventory(){
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new InventoryScreen(client.player));
    }

    public void closeScreen(){
        mc.setScreen(null);
    }

    public void craftItem(Item item){
        Identifier id = Registries.ITEM.getId(item);
        MinecraftClient client = MinecraftClient.getInstance();
        RecipeManager recipeManager = client.world.getRecipeManager();
        Optional<?> recipe = recipeManager.get(id);
        recipe.ifPresent(rec -> LOGGER.info( ((Recipe<?>)rec).toString()) );
        recipe.ifPresent(rec ->
            client.interactionManager.clickRecipe(client.player.currentScreenHandler.syncId, (Recipe<?>)rec, false)
        );
        
        InventoryHelper.scheduleCraft();
    }

    public void getIron(){
        the_stack.pop();
        addTask("smelt iron");
        addTask("start furnace");
    }

    public void openFurnace(){
        baritone.getGetToBlockProcess().getToBlock(Blocks.FURNACE);
    }

    public void smeltIron(){
        smeltItem(Items.RAW_IRON);
    }

    public void smeltItem(Item item){
        MinecraftClient client = MinecraftClient.getInstance();
        int coalSlot = findInSlots(client.player.currentScreenHandler.slots, Registries.ITEM.getId(Items.COAL));
        int smelteeSlot = findInSlots(client.player.currentScreenHandler.slots, Registries.ITEM.getId(item));

        LOGGER.info(Integer.toString(coalSlot));
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, coalSlot, 0, SlotActionType.QUICK_MOVE, client.player);        
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, smelteeSlot, 0, SlotActionType.QUICK_MOVE, client.player);        

    }

    public int findInSlots(DefaultedList<Slot> listOfSlots, Identifier id){
        for( int i = 0; i < listOfSlots.size(); i++){
            if(Registries.ITEM.getId(listOfSlots.get(i).getStack().getItem()) == id ){
                return i;
            }
        }
        return 1000;
    }

    public int findItemStack(PlayerInventory inventory, Identifier id) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (Registries.ITEM.getId(stack.getItem()) == id) {
                return i;
            }
        }
        
        return 1000; 
    }
    public List<ItemStack> getStacksOfItem(PlayerInventory inventory, Identifier id) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (Registries.ITEM.getId(stack.getItem()) == id) {
                items.add(stack);
            }
        }
        
        return items; 
    }

    public int getCountOfItem(PlayerInventory inventory, Identifier id) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (Registries.ITEM.getId(stack.getItem()) == id) {
                count += stack.getCount();
            }
        }
        
        return count; 
    }


    public void craftWoodPlanks(){
        craftItem(Blocks.OAK_PLANKS.asItem());
    }

    public void craftFurnace(){
        craftItem(Blocks.FURNACE.asItem());
    }
  
    public void craftCraftingTable(){
        craftItem(Blocks.CRAFTING_TABLE.asItem());
    }

    public void placeCraftingTable(){
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        BlockPos craftingPos = me.getBlockPos();

        BaritoneAPI.getSettings().allowInventory.value = true;
        Boolean out = BaritoneAPI.getProvider().getBaritoneForPlayer(me).getBuilderProcess().build("crafting_table.schem", craftingPos);

        if (!out) {
            me.sendMessage(Text.literal("failed to place crafting table"));
        } 

        /* //still very useful code (def use later)
        Rotation rotate = new Rotation(0, 90);
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.JUMP, true);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys(); 
        */



    }

    public void placeFurnace() {

        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        BlockPos craftingPos = me.getBlockPos();

        BaritoneAPI.getSettings().allowInventory.value = true;
        Boolean out = BaritoneAPI.getProvider().getBaritoneForPlayer(me).getBuilderProcess().build("furnace.schem", craftingPos);

        if (!out) {
            me.sendMessage(Text.literal("failed to place furnace"));
        }
    }

    public void equipAllArmor(){
        the_stack.pop();
        addTask("close inventory");
        addTask("equip armor");
        addTask("open inventory");
    }

    public void equipArmor(){
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory inventory = client.player.getInventory();

        for (int i = 0; i < inventory.size(); i++){
            if( inventory.getStack(i).getItem() instanceof ArmorItem ){
                
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.QUICK_MOVE, client.player);        
            }
        }
    }

    public void placePortal() {
        //under no circumstances should this function be constructed with less than 14 obsidian in hand.
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        BlockPos portalPos = me.getBlockPos();
        
        //me.sendMessage(Text.literal("currently at" + portalPos.getX() + "," + portalPos.getY() + "," + portalPos.getZ()));
        
        BaritoneAPI.getSettings().allowInventory.value = true;
        Boolean out = BaritoneAPI.getProvider().getBaritoneForPlayer(me).getBuilderProcess().build("portal.schem", portalPos);
        //Boolean out = BaritoneAPI.getProvider().getBaritoneForPlayer(me).getBuilderProcess().build("../../../../resources/buildSchematics/portal.schem", schemFile, portalPos);
        
        if (!out) {
            me.sendMessage(Text.literal("failed to build nether portal"));
        } 

        lastPortalPos = portalPos;

    }
    
    //NOTE: only works on a portal you've placed immeditaly before. Otherwise, lightPortal will just fail.
    public void lightPortal() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;

        //time to light the portal:

        //navigate to y + 1 z + 1 location at portalPos
        Goal newGoal = new GoalXZ(lastPortalPos.getX(), lastPortalPos.getZ() + 1);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getCustomGoalProcess().setGoalAndPath(newGoal);

        //look down on portal block
        Rotation rotate = new Rotation(0, 90);
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);
        
        // TODO: make compatible with stack and make sure that player has flint and steel in hand.
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);

        //don't forget to release keyboard input when stack is implemented
    }

    public void releaseKeyboard() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
    }

    public boolean checkHasItem(List<String> args){

        Identifier my_item = Identifier.tryParse(args.get(0));
        int number;
        try {
            number = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            LOGGER.info("Invalid number format: " + args.get(1));
            return false;
        }
        LOGGER.info(my_item.toString());
        LOGGER.info("Number: " + number);

        PlayerInventory inv = me.getInventory();
        List<ItemStack> stacks = getStacksOfItem(inv,my_item);
        int count = 0;
        for(ItemStack s: stacks){
            count += s.getCount();
        }

        if(count >= number) {
            return true;
        }else{
            return false;
        }
    }

}
