package me.perch.QuestUtils;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import me.perch.QuestUtils.type.ChatTaskType;
import me.perch.QuestUtils.type.EntityInteractionTaskType;

import java.util.function.Function;

public enum TaskTypeRegistry {

    CHAT(
            "chat",
            ChatTaskType::new
    ),

    ENTITYINTERACTION(
            "entityinteraction",
            EntityInteractionTaskType::new
    );

    private final String taskId;
    private final Function<BukkitQuestsPlugin, BukkitTaskType> factory;

    TaskTypeRegistry(String taskId,
                     Function<BukkitQuestsPlugin, BukkitTaskType> factory) {
        this.taskId = taskId;
        this.factory = factory;
    }

    public BukkitTaskType create(BukkitQuestsPlugin plugin) {
        return factory.apply(plugin);
    }

    public String getTaskId() {
        return taskId;
    }
}
