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
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.*;

import baritone.BaritoneProvider;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.message.MessageType;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Mixin(ClientPlayerEntity.class)
public class StateMachine {
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
        
        try{
            Method method = this.getClass().getDeclaredMethod( actions.get(task)) ;
            method.setAccessible(true);
            try{
                if(args == null){
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
        if(condition == "$") return true;
        try{
            Method method = this.getClass().getDeclaredMethod( conditions.get(task)) ;
            method.setAccessible(true);

            try{
                Object o;
                if(args == null){
                    o = method.invoke(this);
                }else{
                    o = method.invoke(this,args);
                }
                return (boolean)o;
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
            { "get wood", "getWood" },
            { "get planks", "getPlanks"},
            { "start craft", "openCraftingTable"},
            { "craft planks", "craftWoodPlanks"}  
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));


        conditions = Stream.of(new String[][] {
            { "start", "none" }, 
            { "get wood", "$" },
            { "get planks", "$"},
            { "start craft", "$"},
            { "craft planks", "$"}  
          }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
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
        the_stack.push(new Task("$"));
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
    

    public void craftWoodPlanks(){
        craftItem(Blocks.OAK_PLANKS.asItem());
    }

    
}
