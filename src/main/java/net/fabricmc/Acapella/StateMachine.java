package net.fabricmc.Acapella;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.*;

import baritone.BaritoneProvider;
import baritone.api.BaritoneAPI;
import baritone.api.command.Command;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

@Mixin(ClientPlayerEntity.class)
public class StateMachine {
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
    public static Map<String, String> states;
    public static Map<String, String> actions;
    public static String currentState;

    static {
        states = Stream.of(new String[][] {
            { "start", "get wood" }, 
            { "get wood", "end" }, 
          }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        actions = Stream.of(new String[][] {
        { "start", "none" }, 
        { "get wood", "getWood" }, 
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

    public StateMachine(){
        currentState = "start";
    }

    public void getMaterial (Block block){
        LOGGER.info("Getting " + block.getName() );
        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        BaritoneAPI.getProvider().getBaritoneForPlayer(me).getMineProcess().mine(10, block);
    }

    public void evaluate(String nextState){
        LOGGER.info("Evaluating on state " + currentState );
        try{
            Method method = this.getClass().getDeclaredMethod( actions.get(currentState)) ;
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
        LOGGER.info("moving to next state");

        currentState = nextState;
        if(currentState != "end"){
            evaluate(states.get(currentState) );
        }
        
    }

    public void getWood(){
        getMaterial(Blocks.OAK_LOG);
    }

    public void none(){
        LOGGER.info("blank state");
        return;
    }


}
