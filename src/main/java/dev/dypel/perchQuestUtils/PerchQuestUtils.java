package dev.dypel.perchQuestUtils;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import dev.dypel.perchQuestUtils.type.EntityInteractionTaskType;
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
        // Register the entity interaction task type
        EntityInteractionTaskType entityInteractionTaskType = new EntityInteractionTaskType(questsPlugin);
        questsPlugin.getTaskTypeManager().registerTaskType(entityInteractionTaskType);

        getLogger().info("Registered entityinteraction task type!");
    }
}