package me.perch.QuestUtils.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HoneyBottlingTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public HoneyBottlingTaskType(BukkitQuestsPlugin plugin) {
        super(
                "honeybottling",
                TaskUtils.TASK_ATTRIBUTION_STRING,
                "Collect honey from beehives or bee nests using bottles."
        );

        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        if (player.hasMetadata("NPC")) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();

        if (type != Material.BEEHIVE && type != Material.BEE_NEST) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.GLASS_BOTTLE) return;

        if (!(block.getBlockData() instanceof Beehive beehive)) return;
        if (beehive.getHoneyLevel() < beehive.getMaximumHoneyLevel()) return;

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) return;

        for (TaskUtils.PendingTask pendingTask :
                TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {

            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug(
                    "Player harvested honey",
                    quest.getId(),
                    task.getId(),
                    player.getUniqueId()
            );

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress, 1);

            int amount = (int) task.getConfigValue("amount");

            if (progress >= amount) {
                super.debug(
                        "Marking task as complete",
                        quest.getId(),
                        task.getId(),
                        player.getUniqueId()
                );
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amount);
        }
    }
}