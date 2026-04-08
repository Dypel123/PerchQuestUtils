package me.perch.QuestUtils.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PerchWalkingTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public PerchWalkingTaskType(BukkitQuestsPlugin plugin) {
        super("perchwalking", TaskUtils.TASK_ATTRIBUTION_STRING, "Walk using any horse.");
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "distance"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "distance"));
        super.addConfigValidator(TaskUtils.useAcceptedValuesConfigValidator(this, Mode.STRING_MODE_MAP.keySet(), "mode"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        int distance = Math.abs(to.getBlockX() - from.getBlockX()) +
                Math.abs(to.getBlockZ() - from.getBlockZ());

        if (distance == 0) return;

        Player player = event.getPlayer();
        if (player.getVehicle() instanceof RideableMinecart) return;

        handle(player, distance);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        int distance = Math.abs(to.getBlockX() - from.getBlockX()) +
                Math.abs(to.getBlockZ() - from.getBlockZ());

        if (distance == 0) return;

        List<Entity> passengers = plugin.getVersionSpecificHandler().getPassengers(event.getVehicle());
        for (Entity passenger : passengers) {
            if (passenger instanceof Player player) {
                handle(player, distance);
            }
        }
    }

    private void handle(Player player, int distance) {
        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player moved", quest.getId(), task.getId(), player.getUniqueId());

            Mode mode = Mode.STRING_MODE_MAP.get(task.getConfigValue("mode"));

            if (mode != null && !validateMode(player, mode)) {
                super.debug("Player mode does not match required mode, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress, distance);
            super.debug("Incrementing task progress (now " + progress + ")", quest.getId(), task.getId(), player.getUniqueId());

            int distanceNeeded = (int) task.getConfigValue("distance");

            if (progress >= distanceNeeded) {
                super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, distanceNeeded);
        }
    }

    private boolean validateMode(final @NotNull Player player, final @NotNull Mode mode) {
        return switch (mode) {

            case ANY_HORSE -> {
                boolean result =
                        plugin.getVersionSpecificHandler().isPlayerOnHorse(player)
                                || plugin.getVersionSpecificHandler().isPlayerOnSkeletonHorse(player)
                                || plugin.getVersionSpecificHandler().isPlayerOnZombieHorse(player)
                                || plugin.getVersionSpecificHandler().isPlayerOnDonkey(player)
                                || plugin.getVersionSpecificHandler().isPlayerOnMule(player);

                if (!result) {
                    super.debug("Player is not on any horse type", null, null, player.getUniqueId());
                }

                yield result;
            }

            case VEHICLE -> player.isInsideVehicle();
        };
    }

    private enum Mode {
        ANY_HORSE,
        VEHICLE;

        private static final Map<String, Mode> STRING_MODE_MAP = new HashMap<>() {{
            for (final Mode mode : Mode.values()) {
                put(mode.name().toLowerCase(Locale.ROOT), mode);
            }
        }};
    }
}