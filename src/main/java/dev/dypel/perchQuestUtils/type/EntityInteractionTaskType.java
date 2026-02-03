package dev.dypel.perchQuestUtils.type;

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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class EntityInteractionTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;
    private final Table<String, String, QuestItem> fixedQuestItemCache = HashBasedTable.create();

    public EntityInteractionTaskType(BukkitQuestsPlugin plugin) {
        super("entityinteraction", TaskUtils.TASK_ATTRIBUTION_STRING, "Right click an entity with a specific item.");
        this.plugin = plugin;

        // Add config validators
        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useItemStackConfigValidator(this, "item"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "data"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "exact-match"));
        super.addConfigValidator(TaskUtils.useEnumConfigValidator(this, EntityType.class, "entity", "entities"));
        super.addConfigValidator(TaskUtils.useEnumConfigValidator(this, EquipmentSlot.class, "hand", "hands"));
    }

    @Override
    public void onReady() {
        fixedQuestItemCache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only process main hand to avoid double counting
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            // Check if any task specifically requires off-hand before returning
            Player player = event.getPlayer();
            QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
            if (qPlayer == null) {
                return;
            }

            boolean hasOffHandRequirement = false;
            for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
                Task task = pendingTask.task();
                if (task.hasConfigKey("hand") || task.hasConfigKey("hands")) {
                    // Only continue if this specific task requires off-hand
                    Object handValue = task.getConfigValue("hand");
                    if (handValue == null) {
                        handValue = task.getConfigValue("hands");
                    }
                    if (handValue != null) {
                        String handStr = handValue.toString().toUpperCase();
                        if (handStr.contains("OFF_HAND")) {
                            hasOffHandRequirement = true;
                            break;
                        }
                    }
                }
            }

            if (!hasOffHandRequirement) {
                return;
            }
        }

        Player player = event.getPlayer();

        // Ignore NPCs
        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        Entity entity = event.getRightClicked();
        EntityType entityType = entity.getType();
        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player interacted with entity " + entityType + " using " + hand,
                    quest.getId(), task.getId(), player.getUniqueId());

            // Check hand requirement
            if (!TaskUtils.matchEnum(EquipmentSlot.class, this, pendingTask, hand,
                    player.getUniqueId(), "hand", "hands")) {
                super.debug("Hand does not match requirement, continuing...",
                        quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            // Check entity type requirement
            if (!TaskUtils.matchEnum(EntityType.class, this, pendingTask, entityType,
                    player.getUniqueId(), "entity", "entities")) {
                super.debug("Entity type does not match requirement, continuing...",
                        quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            // Check item requirement
            if (task.hasConfigKey("item")) {
                if (item == null || item.getType().isAir()) {
                    super.debug("Specific item is required, player has no item in hand; continuing...",
                            quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }

                super.debug("Specific item is required; player held item is of type '" + item.getType() + "'",
                        quest.getId(), task.getId(), player.getUniqueId());

                QuestItem qi;
                if ((qi = fixedQuestItemCache.get(quest.getId(), task.getId())) == null) {
                    QuestItem fetchedItem = TaskUtils.getConfigQuestItem(task, "item", "data");
                    fixedQuestItemCache.put(quest.getId(), task.getId(), fetchedItem);
                    qi = fetchedItem;
                }

                boolean exactMatch = TaskUtils.getConfigBoolean(task, "exact-match", true);
                if (!qi.compareItemStack(item, exactMatch)) {
                    super.debug("Item does not match required item, continuing...",
                            quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                } else {
                    super.debug("Item matches required item",
                            quest.getId(), task.getId(), player.getUniqueId());
                }
            }

            // Increment progress
            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress);
            super.debug("Incrementing task progress (now " + progress + ")",
                    quest.getId(), task.getId(), player.getUniqueId());

            int amount = (int) task.getConfigValue("amount");
            if (progress >= amount) {
                super.debug("Marking task as complete",
                        quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amount);
        }
    }
}