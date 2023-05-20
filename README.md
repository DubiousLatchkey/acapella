# Fabric Example Mod

Beating Minecraft Autonomously

## What it does

Basically it uses a stack machine to push tasks to Baritone and our own functions that do all the tasks required to beat minecraft.  Currently, there is code for all of the tasks with 3 that have not been completed: fighting the dragon, pathing to the stronghold, and entering the end portal.

## Commands
Here are some commands that can allow you to interact with the stack. Whatever is at the top of the stack, is the Task that is currently being executed.
- /stack clear | Clears the stack
- /stack print | Prints the current contents of the stack
- /stack addTask "task name" | Adds a valid task name to the top of the stack 


## Releases

Once we figure it out.

## Build it yourself

For setup instructions please see the [fabric wiki page](https://fabricmc.net/wiki/tutorial:setup) that relates to the IDE that you are using.

For vscode users: Try ```./gradlew vscode``` and then ```code .```.  Download fabrics recommended vscode plugins.
