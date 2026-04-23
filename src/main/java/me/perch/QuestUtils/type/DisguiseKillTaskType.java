package me.perch.QuestUtils.type;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.item.QuestItem;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class DisguiseKillTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;
    private final Table<String, String, QuestItem> fixedQuestItemCache = HashBasedTable.create();

    public DisguiseKillTaskType(BukkitQuestsPlugin plugin) {
        super("disguisekill", TaskUtils.TASK_ATTRIBUTION_STRING,
                "Kill mobs while wearing their head.");

        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useEntityListConfigValidator(this, "mob", "mobs"));
        super.addConfigValidator(TaskUtils.useSpawnReasonListConfigValidator(this, "spawn-reason", "spawn-reasons"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "hostile"));
        super.addConfigValidator(TaskUtils.useItemStackConfigValidator(this, "item"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "data"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "exact-match"));
        super.addConfigValidator(TaskUtils.useEnumConfigValidator(this, TaskUtils.StringMatchMode.class, "name-match-mode"));

        plugin.getServer().getPluginManager().registerEvents(new EntityDeathListener(), plugin);
    }

    @Override
    public void onReady() {
        fixedQuestItemCache.clear();
    }

    private final class EntityDeathListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEntityDeath(EntityDeathEvent event) {
            LivingEntity entity = event.getEntity();
            Player killer = entity.getKiller();
            Player player;

            if (killer != null) {
                player = killer;
            } else {
                EntityDamageEvent damageEvent = entity.getLastDamageCause();
                player = plugin.getVersionSpecificHandler().getDamager(damageEvent);
            }

            handle(player, entity, 1);
        }
    }

    private void handle(Player player, LivingEntity entity, int eventAmount) {
        if (player == null || player.hasMetadata("NPC")) return;

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) return;

        if (entity instanceof Player) return;

        ItemStack item = plugin.getVersionSpecificHandler().getItemInMainHand(player);

        String customName = entity.getCustomName();

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player killed " + entity.getType(), quest.getId(), task.getId(), player.getUniqueId());

            if (task.hasConfigKey("hostile")) {
                boolean hostile = TaskUtils.getConfigBoolean(task, "hostile");

                if (!hostile && !(entity instanceof Animals)) continue;
                if (hostile && !(entity instanceof Monster)) continue;
            }

            if (!TaskUtils.matchEntity(this, pendingTask, entity, player.getUniqueId())) continue;
            if (!TaskUtils.matchSpawnReason(this, pendingTask, entity, player.getUniqueId())) continue;

            if (!TaskUtils.matchString(this, pendingTask, customName, player.getUniqueId(),
                    "name", "names", true, "name-match-mode", false)) continue;

            ItemStack helmet = player.getInventory().getHelmet();
            if (!matchesDisguise(entity, helmet)) {
                super.debug("Player not wearing correct mob head", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            if (task.hasConfigKey("item")) {
                if (item == null) continue;

                QuestItem qi = fixedQuestItemCache.get(quest.getId(), task.getId());
                if (qi == null) {
                    qi = TaskUtils.getConfigQuestItem(task, "item", "data");
                    fixedQuestItemCache.put(quest.getId(), task.getId(), qi);
                }

                boolean exactMatch = TaskUtils.getConfigBoolean(task, "exact-match", true);
                if (!qi.compareItemStack(item, exactMatch)) continue;
            }

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress, eventAmount);

            int amount = (int) task.getConfigValue("amount");

            if (progress >= amount) {
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amount);
        }
    }

    private boolean matchesDisguise(LivingEntity entity, ItemStack helmet) {
        if (helmet == null) return false;

        Material type = helmet.getType();

        switch (entity.getType()) {
            case CREEPER:
                return type == Material.CREEPER_HEAD;

            case ZOMBIE:
                return type == Material.ZOMBIE_HEAD;

            case SKELETON:
                return type == Material.SKELETON_SKULL;

            case WITHER_SKELETON, WITHER:
                return type == Material.WITHER_SKELETON_SKULL;

            case ENDER_DRAGON:
                return type == Material.DRAGON_HEAD;

            case PLAYER:
                return type == Material.PLAYER_HEAD;

            default:
                return false;
        }
    }
}