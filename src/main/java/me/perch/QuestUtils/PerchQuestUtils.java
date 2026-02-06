package me.perch.QuestUtils;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import me.perch.QuestUtils.type.EntityInteractionTaskType;
import me.perch.QuestUtils.type.ChatTaskType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PerchQuestUtils extends JavaPlugin {

    private BukkitQuestsPlugin questsPlugin;

    @Override
    public void onEnable() {
        // Get Quests plugin instance
        questsPlugin = (BukkitQuestsPlugin) getServer().getPluginManager().getPlugin("Quests");

        if (questsPlugin == null) {
            getLogger().severe("Quests plugin not found! Disabling PerchTasktypes...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register custom task types
        registerTaskTypes();

        getLogger().info("PerchTasktypes has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PerchTasktypes has been disabled!");
    }

    private void registerTaskTypes() {
        for (TaskTypeRegistry type : TaskTypeRegistry.values()) {
            questsPlugin.getTaskTypeManager()
                    .registerTaskType(type.create(questsPlugin));

            getLogger().info("Registered task type: " + type.getTaskId());
        }
    }
}