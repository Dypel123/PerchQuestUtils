package me.perch.QuestUtils;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskTypeManager;
import com.leonardobishop.quests.common.plugin.Quests;
import me.perch.QuestUtils.type.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PerchQuestUtils extends JavaPlugin {

    @Override
    public void onEnable() {
        Quests questsPlugin = (Quests) Bukkit.getPluginManager().getPlugin("Quests");
        BukkitTaskTypeManager taskTypeManager = (BukkitTaskTypeManager) questsPlugin.getTaskTypeManager();

        if (questsPlugin == null) {
            getLogger().severe("Quests plugin not found! Disabling EvergreenTasktypes...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register custom task types
        taskTypeManager.registerTaskType(new ChatTaskType((BukkitQuestsPlugin) questsPlugin));
        taskTypeManager.registerTaskType(new EntityInteractionTaskType((BukkitQuestsPlugin) questsPlugin));
        taskTypeManager.registerTaskType(new HoneyBottlingTaskType((BukkitQuestsPlugin) questsPlugin));
        taskTypeManager.registerTaskType(new DisguiseKillTaskType((BukkitQuestsPlugin) questsPlugin));
        taskTypeManager.registerTaskType(new DyingTaskType((BukkitQuestsPlugin) questsPlugin));
        taskTypeManager.registerTaskType(new PistonPushingTaskType((BukkitQuestsPlugin) questsPlugin));

        getLogger().info("EvergreenTasktypes has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EvergreenTasktypes has been disabled!");
    }

}