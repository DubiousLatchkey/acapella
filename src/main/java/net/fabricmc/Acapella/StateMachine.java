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
import net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
//schematics:
import baritone.api.schematic.*;
import baritone.api.process.IBuilderProcess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import java.io.File;

//view:
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.Rotation;
import baritone.api.pathing.goals.*;

//jump and click:
import baritone.api.utils.input.*;

//goto:
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

@Mixin(ClientPlayerEntity.class)
public class StateMachine {
    private BlockPos lastPortalPos;
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    public static Map<String, String> conditions;
    public static Map<String, String> actions;
    public static String currentState;

    public ArrayList<Vec3d> end_frames = new ArrayList<Vec3d>();

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
    private Vec3d lastEyePos;
    
    public long ticksToIdle = 0;


    public StateMachine(){

        active = false;
        currTaskName = "$";
        the_stack = new Stack<>();
        the_stack.push(new Task("$"));
    }

    public int addTask(String state, String... values){
        
        if(!actions.containsKey(state)){
            return -1;
        }

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
        return 0;
    }

    public void printStack(){
        int count = 0;
        for(Task x : the_stack){
            me.sendMessage(Text.literal("" + count + ": " + x.task));
            count++;
        }
    }

    
    public void evaluate(){
        if(the_stack.peek().task == "$"){
            active = false;
            return;
        }
        LOGGER.info("Evaluating on task " + the_stack.peek().task);
        
        if(currTaskName == "$"){
            me.sendMessage(Text.literal("Starting new task: " + the_stack.peek().task + ", args: " + the_stack.peek().args.toString()));
            currTaskName = the_stack.peek().task;
            initiate_task(the_stack.peek());
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
            Method method;
            if(args == null || args.size() == 0){
                method = this.getClass().getDeclaredMethod( actions.get(task)) ;
            }else{
                method = this.getClass().getDeclaredMethod( actions.get(task), List.class) ;
            }
            
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

    public void idle1(){
        ticksToIdle = 20;
    }

    public void idleCustom(List<String> args){
        int number;
        try {
            number = Integer.parseInt(args.get(0));
        } catch (NumberFormatException e) {
            LOGGER.info("Invalid number format: " + args.get(1));
            number = 100;
        }
        ticksToIdle = number;
    }

    private boolean check_condition(Task task_arg){
        String task = task_arg.task;
        List<String> args = task_arg.args;
        LOGGER.info("check condition " + args.size());
        
        String conditionFuncName = conditions.get(task);
        if(conditionFuncName == null) return true;
        if(conditionFuncName == "$") return true;
        try{
            Method method;
            if(args == null || args.size() == 0){
                method = this.getClass().getDeclaredMethod( conditionFuncName) ;
            }else{
                method = this.getClass().getDeclaredMethod( conditionFuncName, List.class) ;
            }
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
            { "get planks", "getPlanks"},
            { "craft craft", "craftCraftingTable"},
            { "place craft", "placeCraftingTable"},
            { "start craft", "openCraftingTable"},
            { "open inventory", "openInventory"},
            { "close inventory", "closeScreen"},
            { "place furnace", "placeFurnace"},
            { "smelt iron", "smeltIron"},
            { "get obsidian", "getObsidian"},
            { "find obsidian", "findObsidian"},
            { "mine obsidian", "mineObsidian"},
            { "make portal", "placePortal"},
            { "goin portal", "goinPortal"},
            { "light portal", "lightPortal"},
            { "create portal", "createPortal"},
            { "clean inputs", "releaseKeyboard"},
            { "goto water", "gotoWater"},
            { "grab water", "grabWater"},
            { "get water", "getWater"},
            { "place water", "placeWater"},
            { "CRAFTGENERIC", "CRAFTRECIPE"},
            { "GETGENERIC", "GETITEM"},
            { "equip armor", "equipArmor"},
            { "equip all armor", "equipAllArmor"},
            { "farm blazes", "farmBlazes"},
            { "kill blazes", "killBlazes"},
            { "goto nether fence", "gotoNetherBrickFence"},
            { "farm endermen", "farmEndermen"},
            { "kill endermen", "killEndermen"},
            { "goto warped forest", "gotoWarpedForest"},
            { "goto spawner", "gotoSpawner"},
            { "prepare flint and steel", "prepareFlintAndSteel"},
            { "move flint and steel", "moveFlintAndSteelToPosition4"},
            { "break underneath", "breakBlockUnderneath"},
            { "set radius small", "setRadiusSmall"},
            { "set radius large", "setRadiusLarge"},
            { "retrieve slot3", "retrieveSlot3"},
            { "retrieve furnace", "retrieveFurnaceItems"},
            { "move north", "moveNorthOne"},
            { "move furnace", "moveFurnaceToPosition5"},
            { "prepare furnace", "prepareFurnace"},
            { "look angled", "lookAngled"},
            { "try goto stronghold", "tryGotoStronghold"},
            { "look at eye", "lookAtEye"},
            { "use eye", "useEye"},
            { "move eye", "moveEyeToPosition5"},
            { "goto stronghold", "throwEye"},
            { "release keys", "releaseKeyboard"},
            { "idle 1", "idle1"},
            { "use furnace with iron", "furnaceIron"},
            { "start furnace", "openFurnace"},
            { "idle custom", "idleCustom"},
            {"fill frames", "fillFrames"},
            {"fill frame data", "fillFrameData"},
            {"goto center of frames", "gotoCenterOfFrames"},
            {"goto stone brick stairs", "gotoStoneBrickStairs"},
            {"goto portal room", "gotoPortalRoom"},
            {"goto bedrock", "gotoBedrock"},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));



        conditions = Stream.of(new String[][] {
            { "start", "none" }, 
            { "defeat enderDragon", "$"},
            { "get wood", "$" },
            { "get planks", "$" },
            { "start craft", "$" },
            { "place craft", "$" },
            { "open inventory", "$" },
            { "close inventory", "$" },
            { "equip armor", "$" },
            { "craft planks", "$" },
            { "place furnace", "$" },
            { "get obsidian", "$"},
            { "find obsidian", "$" },
            { "mine obsidian", "$" },
            { "make portal", "$" },
            { "goin portal", "$" },
            { "light portal", "$" },
            { "prepare flint and steel", "$" },
            { "clean inputs", "$" },
            { "goto water", "$"},
            { "grab water", "$"},
            { "get water", "$" }, 
            { "kill blazes", "checkHasItem" },
            { "kill endermen", "checkHasItem" },
            { "CRAFTGENERIC", "checkHasItem"},
            { "try goto stronghold", "foundStronghold"},

          }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }


    public void ultimateTask(){
        the_stack.pop();


        
        addTask("farm endermen");

        addTask("farm blazes");


        addTask("idle custom","200");

        addTask("create portal");
        addTask("GETGENERIC","obsidian","14");

        addTask("equip all armor");

        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","iron_chestplate","1");
        addTask("CRAFTGENERIC","iron_helmet","1");
        addTask("CRAFTGENERIC","iron_leggings","1");
        addTask("CRAFTGENERIC","iron_boots","1");
        addTask("CRAFTGENERIC","flint_and_steel","1");
        placeAndOpenCraftingTable();


        addTask("use furnace with iron");

        addTask("GETGENERIC","gravel","20");
        addTask("GETGENERIC","iron_ore","24");
        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","diamond_pickaxe","1");
        addTask("CRAFTGENERIC","diamond_sword","1");
        placeAndOpenCraftingTable();

        addTask("GETGENERIC","diamond_ore","5");
        
        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","iron_pickaxe","1");
        addTask("CRAFTGENERIC","stick","4");
        addTask("CRAFTGENERIC","oak_planks","20");
        placeAndOpenCraftingTable();

        addTask("GETGENERIC", "furnace", "1");
        addTask("use furnace with iron");
        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","furnace","1");
        placeAndOpenCraftingTable();

        
        addTask("GETGENERIC", "iron_ore", "7");
        addTask("GETGENERIC", "coal_ore", "7");

        
        addTask("GETGENERIC", "oak_log","10");

        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","stone_pickaxe","1");
        // addTask("CRAFTGENERIC","stone_sword","1");
        addTask("CRAFTGENERIC","stone_axe","1");
        // addTask("CRAFTGENERIC","stone_shovel","1");
        placeAndOpenCraftingTable();

        addTask("GETGENERIC", "cobblestone","15","stone");
        addTask("break underneath");
        addTask("break underneath");

        closeAndGrabCraftingTable();
        addTask("CRAFTGENERIC","wooden_pickaxe","1");
        addTask("CRAFTGENERIC","wooden_shovel","1");
        addTask("CRAFTGENERIC","stick","4");
        placeAndOpenCraftingTable();
        addTask("close inventory");
        addTask("CRAFTGENERIC", "crafting_table", "1");
        addTask("open inventory");

        
        addTask("get planks");
        
        
        
    }

    public void closeAndGrabCraftingTable(){
        // addTask("set radius large");
        addTask("GETGENERIC", "crafting_table", "1");
        // addTask("set radius small");
        addTask("close inventory");

    }

    public void placeAndOpenCraftingTable(){
        addTask("start craft");
        addTask("place craft");

        
    }

    public void furnaceIron(){
        the_stack.pop();
        addTask("smelt iron");
        addTask("start furnace");
        addTask("place furnace");
        addTask("prepare furnace");
        addTask("look angled");
        addTask("move north");
        
    }

    public void getPlanks(){
        the_stack.pop();
        addTask("close inventory");
        addTask("CRAFTGENERIC", "oak_planks", "7");
        addTask("open inventory");
        addTask("GETGENERIC","oak_log", "7");
    }

    public void setRadiusSmall(){
        BaritoneAPI.getSettings().blockReachDistance.value = 10f;
    }

    public void setRadiusLarge(){
        BaritoneAPI.getSettings().blockReachDistance.value = 200f;

    }

    public void breakBlockUnderneath(){
        BlockPos playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeet();

        // Calculate the position of the block to break (crafting table in this case)
        BlockPos blockPos = playerPos.down(); // Set the position of the crafting table

        // Create a custom IMineProcess to break the crafting table
        IMineProcess mineProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess();

        // Set the mining target to the crafting table
        
        mineProcess.mine(1, new BlockOptionalMeta(mc.world.getBlockState(blockPos).getBlock()));

        // // Force revalidate the goal and path
        // PathingCommand pathingCommand = new PathingCommand(null, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        // BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().execute(pathingCommand);

    }


    public void GETITEM(List<String> args){
        int number;
        Block[] newArray = new Block[args.size()-1];

        newArray[0] = Registries.BLOCK.get(Identifier.tryParse(args.get(0)));
        if(args.size() > 2){
            for(int i = 0; i < args.size() - 2; i++){
                newArray[i+1] = Registries.BLOCK.get(Identifier.tryParse(args.get(i + 2)));
            }
        }
        

        try {
            number = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            LOGGER.info("Invalid number format: " + args.get(1));
            number = 5;
        }

        getMaterial(number, newArray);


        LOGGER.info(BaritoneAPI.getSettings().blocksToAvoidBreaking.value.get(0).toString());
        if(BaritoneAPI.getSettings().blocksToAvoidBreaking.value.get(0) == Blocks.CRAFTING_TABLE){
            BaritoneAPI.getSettings().blocksToAvoidBreaking.value.remove(Blocks.CRAFTING_TABLE);
            BaritoneAPI.getSettings().blocksToAvoidBreaking.value.remove(Blocks.FURNACE);
        }
    }



    public void CRAFTRECIPE(List<String> args){
        Identifier my_item = Identifier.tryParse(args.get(0));
        int number;
        
        try {
            number = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            LOGGER.info("Invalid number format: " + args.get(1));
            number = 5;
        }
        Item actual_item = Registries.ITEM.get(my_item);
        for(int i = 0; i < number; i++){
            craftItem(actual_item);
        }
    }




    public void getWater() {
        the_stack.pop();
        addTask("clean inputs");
        addTask("grab water");
        addTask("goto water");
    }


    public void createPortal() {
        the_stack.pop();
        addTask("clean inputs");
        addTask("light portal");
        addTask("idle custom","50");
        addTask("prepare flint and steel");
        addTask("goin portal");
        addTask("make portal");
    }


    public void none(){
        LOGGER.info("blank state");
        return;
    }
    
    public void getMaterial (int num, Block... my_blocks){
        // LOGGER.info("Getting " + block.getName() );
        // my_blocks.add(block);
        baritone.getMineProcess().mine(num, my_blocks);
        
    
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
        // recipe.ifPresent(rec -> LOGGER.info( ((Recipe<?>)rec).toString()) );
        recipe.ifPresent(rec ->
            client.interactionManager.clickRecipe(client.player.currentScreenHandler.syncId, (Recipe<?>)rec, false)
        );
        
        InventoryHelper.scheduleCraft();
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
        
        
        int count = getCountOfItem(client.player.getInventory(), Registries.ITEM.getId(item));
        
        me.sendMessage(Text.literal("We have " + count + " iron"));
        
        LOGGER.info(Integer.toString(coalSlot));
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, coalSlot, 0, SlotActionType.QUICK_MOVE, client.player);        
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, smelteeSlot, 0, SlotActionType.QUICK_MOVE, client.player);        


        the_stack.pop();
        addTask("retrieve furnace");
        addTask("idle custom", "" + (count*200));

    }

    public int findInSlots(DefaultedList<Slot> listOfSlots, Identifier id){
        for( int i = 0; i < listOfSlots.size(); i++){
            if(Registries.ITEM.getId(listOfSlots.get(i).getStack().getItem()) == id ){
                return i;
            }
        }
        return 1000;
    }

    public void retrieveFurnaceItems(){
        the_stack.pop();
        addTask("close inventory");
        addTask("retrieve slot3");
        // addTask("start furnace");
    }

    public void retrieveSlot3(){
        MinecraftClient client = MinecraftClient.getInstance();
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 2, 0, SlotActionType.QUICK_MOVE, client.player);        
    }

    public void prepareFlintAndSteel(){
        the_stack.pop();
        addTask("close inventory");
        addTask("move flint and steel");
        addTask("open inventory");
    }

    public void prepareFurnace(){
        the_stack.pop();
        addTask("close inventory");
        addTask("move furnace");
        addTask("open inventory");
    }

    public void moveFlintAndSteelToPosition4(){
        MinecraftClient client = MinecraftClient.getInstance();
        int slot = findInSlots(client.player.currentScreenHandler.slots, Registries.ITEM.getId(Items.FLINT_AND_STEEL));
        swapSlots(slot, 39);
        me.getInventory().selectedSlot = 3;

    }

    public void moveFurnaceToPosition5(){
        MinecraftClient client = MinecraftClient.getInstance();
        int slot = findInSlots(client.player.currentScreenHandler.slots, Registries.ITEM.getId(Items.FURNACE));
        swapSlots(slot, 41);
        me.getInventory().selectedSlot = 5;
    }

    public void swapSlots (int slot1, int slot2){
        MinecraftClient client = MinecraftClient.getInstance();
        if(slot1 == slot2) return;
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot1, 0, SlotActionType.PICKUP, me);
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot2, 0, SlotActionType.PICKUP, me);
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot1, 0, SlotActionType.PICKUP, me);
    }

    public int findItemStack(PlayerInventory inventory, Identifier id) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (stack.getItem() == Registries.ITEM.get(id)) {
                return i;
            }
        }
        
        return 1000; 
    }

    public void farmBlazes(){
        the_stack.pop();
        addTask("kill blazes", "blaze_rod", "7");
        addTask("goto spawner");
        addTask("GETGENERIC", "nether_brick_fence","1");

    }

    public void farmEndermen(){
        the_stack.pop();
        addTask("kill endermen", "ender_pearl", "12");
        // addTask("goto spawner");
        addTask("GETGENERIC", "warped_nylium","1");

    }


    // public void gotoNetherBrickFence(){
    //     baritone.getGetToBlockProcess().getToBlock(Blocks.NETHER_BRICK_FENCE);
    // }

    // public void gotoWarpedForest(){
    //     baritone.getGetToBlockProcess().getToBlock(Blocks.WARPED_NYLIUM);
    // }


    public void gotoSpawner(){
        baritone.getGetToBlockProcess().getToBlock(Blocks.SPAWNER);
    }

    public void killBlazes(List<String> args){
        baritone.getFollowProcess().follow(i -> i.getType() ==  EntityType.BLAZE);
    }

    public void killEndermen(List<String> args){
        baritone.getFollowProcess().follow(i -> i.getType() ==  EntityType.ENDERMAN);
    }


    public List<ItemStack> getStacksOfItem(PlayerInventory inventory, Identifier id) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Registries.ITEM.get(id)) {
                items.add(stack);
            }
        }
        
        return items; 
    }

    public int getCountOfItem(PlayerInventory inventory, Identifier id) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            
            if (stack.getItem() == Registries.ITEM.get(id)) {
                count += stack.getCount();
            }
        }
        
        return count; 
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
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys(); 
        */

    }

    public void moveNorthOne(){
        BlockPos my_pos = me.getBlockPos();
        Goal newt = new GoalBlock(my_pos.getX(), my_pos.getY(), my_pos.getZ() - 2);
        //Goal newGoal = new GoalXZ(lastPortalPos.getX(), lastPortalPos.getZ() + 1);
        BaritoneAPI.getSettings().allowWaterBucketFall.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getCustomGoalProcess().setGoalAndPath(newt);
        
    }
    
    public void lookAngled(){
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        Rotation rotate = new Rotation(0, 40);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);

    }

    public void placeFurnace() {
 
        

        // Check if the hit result is a block
        
        
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult.getType() == BlockHitResult.Type.BLOCK) {
            // Get the block position from the hit result
            Vec3d targetPos = hitResult.getPos();
            Vec3d my_pos = me.getPos();
            double dist = targetPos.distanceTo(my_pos);

            me.sendMessage(Text.literal("the distance that was calculated is: " + dist));
            if(dist > 0.6 && dist < 4)
            if (checkAllHeldItem(Items.FURNACE)) {
                BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
                BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }

        }

        // BaritoneAPI.getSettings().buildIgnoreDirection.value = true;
        // BaritoneAPI.getSettings().buildIgnoreProperties.value.add("facing=north");
        // BaritoneAPI.getSettings().buildIgnoreProperties.value.add("lit");
        // BaritoneAPI.getSettings().buildIgnoreProperties.value.add("facing");
        // BaritoneAPI.getSettings().buildIgnoreProperties.value.add("list=false");
        // BaritoneAPI.getSettings().buildIgnoreProperties.value.add("[facing=north,lit=false]");
        
        // LOGGER.info(BaritoneAPI.getSettings().blocksToAvoidBreaking.toString());
        // LOGGER.info(BaritoneAPI.getSettings().buildIgnoreProperties.toString());


        // ClientPlayerEntity me = MinecraftClient.getInstance().player;
        // BlockPos craftingPos = me.getBlockPos();

        // BaritoneAPI.getSettings().allowInventory.value = true;
        // Boolean out = BaritoneAPI.getProvider().getBaritoneForPlayer(me).getBuilderProcess().build("furnace.schem", craftingPos);

        // if (!out) {
        //     me.sendMessage(Text.literal("failed to place furnace"));
        // }
    }

    public void gotoWater() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;

        //goto water
        baritone.getGetToBlockProcess().getToBlock(Blocks.WATER);

        //look at water
        Rotation rotate = new Rotation(0, 90);
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);

    }

    public void grabWater() {
        //right click with bucket
        if (checkAllHeldItem(Items.BUCKET)) {

            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            
        }
        //maybe make this better later, maybe toss back onto stack or something?
    
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
    public void winTheGame(){
        the_stack.pop();
        addTask("goto bedrock");
    }

    public void gotoBedrock(){
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getGetToBlockProcess().getToBlock(Blocks.BEDROCK);
        MinecraftClient client = MinecraftClient.getInstance();
        client.getServer().getCommandManager().execute(client.getServer().getCommandManager().getDispatcher().parse("kill @e[type=ender_dragon]", client.getServer().getCommandSource()), "kill @e[type=ender_dragon]");
    }

    public void gotoPortalRoom(){
        the_stack.pop();
        //addTask("break underneath");
        addTask("fill frames");
        addTask("goto center of frames");
        addTask("fill frame data");
        addTask("goto stone brick stairs");
    }

    public void fillFrames(){
        mc.player.getInventory().selectedSlot = 4;
        mc.player.getInventory().markDirty();
        for (int i = 0; i < end_frames.size(); i ++){
            mc.interactionManager.interactBlock(me, me.getActiveHand(), (BlockHitResult) new BlockHitResult(end_frames.get(i), Direction.DOWN, new BlockPos((int)end_frames.get(i).x, (int)end_frames.get(i).y, (int)end_frames.get(i).z), true));

        }

        end_frames.clear();
    }

    public void gotoCenterOfFrames(){
        int averageX = 0;
        int averageY = 0;
        int averageZ = 0;
        for (int i = 0; i < end_frames.size(); i ++){
            averageX += (int)end_frames.get(i).x;
            averageY += (int)end_frames.get(i).y;
            averageZ += (int)end_frames.get(i).z;
        }        
        averageX = averageX / end_frames.size();
        averageY = averageY / end_frames.size();
        averageZ = averageZ / end_frames.size();
        LOGGER.info(averageX + " " + averageY + " " + averageZ);
        Goal newt = new GoalNear( new BlockPos(averageX, averageY + 1, averageZ), 2);
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getCustomGoalProcess().setGoalAndPath(newt);

        
    }

    public void fillFrameData(){
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity me = mc.player;
        int minX = (int)mc.player.getX() - 10;
        int maxX = (int)mc.player.getX() + 10;
        int minY = (int)mc.player.getY() - 10;
        int maxY = (int)mc.player.getY() + 10;
        int minZ = (int)mc.player.getZ() - 10;
        int maxZ = (int)mc.player.getZ() + 10;


        for (int x = minX; x < maxX; x ++){
            for(int y = minY; y < maxY; y++){
                for(int z = minZ; z < maxZ; z++){
                    //LOGGER.info( x + " " + y + " " + z);
                    BlockPos pos =  new BlockPos(x, y, z);
                    if(mc.world.getBlockState(pos).getBlock().equals(Blocks.END_PORTAL_FRAME)){
                        LOGGER.info("found a frame");
                        end_frames.add(new Vec3d(x, y, z));
                    }   
                    
                }
            }
        }
    }

    public void gotoStoneBrickStairs(){
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getGetToBlockProcess().getToBlock(Blocks.STONE_BRICK_STAIRS);
    }


    public void throwEye(){
        the_stack.pop();
        addTask("try goto stronghold");
        addTask("look at eye");
        addTask("idle 1");
        addTask("release keys");
        addTask("use eye");
        addTask("close inventory");
        addTask("move eye");
        addTask("open inventory");
    }

    public void tryGotoStronghold(){
        Box nearby = new Box(mc.player.getBlockPos().add(-20,-20,-20),mc.player.getBlockPos().add(20,20,20));
        for (Entity entity : mc.world.getEntitiesByType(EntityType.EYE_OF_ENDER, nearby, i->true)){
            lastEyePos = entity.getEyePos();
        }

        GoalXZ goal = GoalXZ.fromDirection(
                mc.player.getPos(),
                mc.player.getRotationClient().y,
                200
        );

        baritone.getCustomGoalProcess().setGoalAndPath(goal);
    }

    public void lookAtEye(){
        Box nearby = new Box(mc.player.getBlockPos().add(-20,-20,-20),mc.player.getBlockPos().add(20,20,20));
        Vec3d lookHere;
        for (Entity entity : mc.world.getEntitiesByType(EntityType.EYE_OF_ENDER, nearby, i->true)){
            lookHere = entity.getEyePos();
            mc.player.lookAt(EntityAnchor.EYES, lookHere);
            break;
        }
    }

    public void useEye(){
        MinecraftClient client = MinecraftClient.getInstance();
        client.player.getInventory().selectedSlot = 4;
        client.player.getInventory().markDirty();

        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
    }

    public void moveEyeToPosition5(){
        MinecraftClient client = MinecraftClient.getInstance();
        int slot = findInSlots(client.player.currentScreenHandler.slots, Registries.ITEM.getId(Items.ENDER_EYE));
        if(slot != 40){
            swapSlots(slot, 40);
        }
    }

    public void findObsidian() {
        //doing a really weird obsidian generator
        ClientPlayerEntity me = MinecraftClient.getInstance().player;

        //goto ruined portal (hopefully)
        baritone.getGetToBlockProcess().getToBlock(Blocks.OBSIDIAN);
    }

    //will follow findObsidian return
    public void placeWater() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        
        //look down to place water
        Rotation rotate = new Rotation(0, 90);
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);

        //place water:
        if (checkAllHeldItem(Items.WATER_BUCKET)) {

            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            
        }
    }

    public void mineObsidian() {
        baritone.getMineProcess().mine(28, Blocks.OBSIDIAN);
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
    public void goinPortal() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        //navigate to y + 1 z + 1 location at portalPos
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value.add(Blocks.OBSIDIAN);
        Goal newt = new GoalBlock(lastPortalPos.getX(), lastPortalPos.getY() + 1, lastPortalPos.getZ() + 1);
        //Goal newGoal = new GoalXZ(lastPortalPos.getX(), lastPortalPos.getZ() + 1);
        BaritoneAPI.getSettings().allowWaterBucketFall.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getCustomGoalProcess().setGoalAndPath(newt);
    }
    
    
    public void lightPortal() {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;

        //time to light the portal:

        //look down on portal block
        Rotation rotate = new Rotation(0, 90);
        BaritoneAPI.getSettings().antiCheatCompatibility.value = false;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getLookBehavior().updateTarget(rotate, true);
        
        //reset setting
        BaritoneAPI.getSettings().allowWaterBucketFall.value = true;
        BaritoneAPI.getSettings().blocksToDisallowBreaking.value.remove(Blocks.OBSIDIAN);
        
        // make sure flint and steel is in hand:
        if (checkAllHeldItem(Items.FLINT_AND_STEEL)) {
            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().clearAllKeys();
            BaritoneAPI.getProvider().getBaritoneForPlayer(me).getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        }
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
        // LOGGER.info(my_item.toString());
        // LOGGER.info("Number: " + number);

        PlayerInventory inv = me.getInventory();
        List<ItemStack> stacks = getStacksOfItem(inv,my_item);
        int count = 0;
        for(ItemStack s: stacks){
            count += s.getCount();
        }
        // LOGGER.info(stacks.toString());
        // LOGGER.info("The number of items of TYPE that we have is: " + count);

        if(count >= number) {
            return true;
        }else{
            if(args.get(0) == "blaze_rod"){
                addTask("goto spawner");
                addTask("GETGENERIC","blaze_rod",""+ number);
            }
            if(args.get(0) == "ender_pearl"){
                addTask("GETGENERIC","ender_pearl",""+ number);
            }
            return false;
        }
    }

    //used ONLY for right clicks, as it checks both main hand and off-hand
    private boolean checkAllHeldItem(Item item) {
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        Iterable<ItemStack> items = me.getHandItems();
        for (ItemStack stack : items ) {
            if (stack.isOf(item)) {
                return true;
            }
        }
        return false;

    }

    private boolean foundStronghold(){
        LOGGER.info(Double.toString(lastEyePos.x + lastEyePos.z)  + " " + Double.toString(me.getX() + me.getZ()));
        if(lastEyePos.x + lastEyePos.z < me.getX() + me.getZ()) {
            return true;
        }
        else{
            the_stack.pop();
            addTask("goto stronghold");
            return false;
        }
    }

}
