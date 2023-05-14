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


import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Mixin(ClientPlayerEntity.class)
public class StateMachine {
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    public static Map<String, String> states;
    public static Map<String, String> actions;
    public static String currentState;
    public boolean active = false;

    private String currTaskName;

    public class Task<A, B>{
        public A task;
        public B args;
        public Task(A s){
            task = s;
            args = null;
        }
        public Task(A a, B b){
            task = a;
            args = b;
        }
    };


    private Stack<Task<String,List<String>>> the_stack;
    
    private MinecraftClient mc;
    private ClientPlayerEntity me;
    private IBaritone baritone;
    
    
    static {
        states = Stream.of(new String[][] {
            { "start", "get wood" }, 
            { "get wood", "end" }, 
          }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        actions = Stream.of(new String[][] {
        { "start", "none" }, 
        { "get wood", "getWood" },
        { "get planks", "getPlanks"},
        { "start craft", "openCraftingTable"},
        { "craft planks", "craftWoodPlanks"},
        { "get iron", "getIron"},
        { "smelt iron", "smeltIron"},
        { "start furnace", "openFurnace"},  
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

    public StateMachine(){
        active = false;
        currTaskName = "$";
        the_stack = new Stack<>();
        the_stack.push(new Task<String, List<String>>("$"));
    }

    public void addTask(String state, String... values){
        mc = MinecraftClient.getInstance();
        me = MinecraftClient.getInstance().player;
        baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(me);

        List<String> args = new ArrayList<>();
        for(String v : values){
            args.add(v);
        }

        the_stack.push(new Task<String, List<String>>(state,args));
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
            initiate_task(the_stack.peek());
            return;
        }
        //CHECK CURRENT TASK IS DONE YET
        //check if baritone is active
        if(checkBaritoneActive()) return;
        
        the_stack.pop();
        currTaskName = "$";
        
        // currentState = nextState;
        if(the_stack.peek().task == "$"){
            me.sendMessage(Text.literal("END OF STATE MACHINE"));
        }        
    }

    private void initiate_task(Task<String,List<String>> task_arg){
        //make this work with Task<>
        String task = task_arg.task;
        List<String> args = task_arg.args;
        
        try{
            Method method = this.getClass().getDeclaredMethod( actions.get(task)) ;
            method.setAccessible(true);
            try{
                Object o = method.invoke(this);
            } catch(IllegalAccessException e){
                LOGGER.info("No accessing that from here");
            } catch (InvocationTargetException e){
                LOGGER.info("What are you invoking from anyway?");
            } catch (Exception e){
                LOGGER.info(e.toString());
            }
        } catch(NoSuchMethodException e){
            LOGGER.info("No method found");
        }

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
    
    public void getPlanks(){
        the_stack.pop();
        addTask("craft planks");
        addTask("start craft");
        addTask("get wood");
    }

    public void getWood(){
        getMaterial(Blocks.OAK_LOG);
    }
    
    public void getGrass(){
        getMaterial(Blocks.DIRT);
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
        the_stack.push(new Task<String, List<String>>("$"));
    }

    public void openCraftingTable(){
        baritone.getGetToBlockProcess().getToBlock(Blocks.CRAFTING_TABLE);
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
        smeltItem(Items.IRON_ORE);
    }

    public void smeltItem(Item item){
        Identifier id = Registries.ITEM.getId(item);
        MinecraftClient client = MinecraftClient.getInstance();
        int coalSlot = findItemStack(client.player.getInventory(), Registries.ITEM.getId(Items.COAL));
        int smelteeSlot = findItemStack(client.player.getInventory(), Registries.ITEM.getId(item));

        LOGGER.info(Integer.toString(coalSlot));
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, coalSlot, 0, SlotActionType.QUICK_MOVE, client.player);        
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
    

    public void craftWoodPlanks(){
        craftItem(Blocks.OAK_PLANKS.asItem());
    }

    
}
