package me.perch.QuestUtils.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public ChatTaskType(BukkitQuestsPlugin plugin) {
        super(
                "chat",
                TaskUtils.TASK_ATTRIBUTION_STRING,
                "Send a specific message in chat."
        );

        this.plugin = plugin;

        // Config validators
        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "message"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "ignore-case"));
        super.addConfigValidator(
                TaskUtils.useEnumConfigValidator(
                        this,
                        TaskUtils.StringMatchMode.class,
                        "message-match-mode"
                )
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Ignore NPCs
        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        String message = event.getMessage();

        for (TaskUtils.PendingTask pendingTask :
                TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {

            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug(
                    "Player sent chat message '" + message + "'",
                    quest.getId(),
                    task.getId(),
                    player.getUniqueId()
            );

            boolean ignoreCase = TaskUtils.getConfigBoolean(task, "ignore-case");

            if (!TaskUtils.matchString(
                    this,
                    pendingTask,
                    message,
                    player.getUniqueId(),
                    "message",
                    "messages",
                    false,
                    "message-match-mode",
                    ignoreCase
            )) {
                super.debug(
                        "Message does not match requirements, continuing...",
                        quest.getId(),
                        task.getId(),
                        player.getUniqueId()
                );
                continue;
            }

            super.debug(
                    "Marking chat task as complete",
                    quest.getId(),
                    task.getId(),
                    player.getUniqueId()
            );

            taskProgress.setCompleted(true);
        }
    }
}
