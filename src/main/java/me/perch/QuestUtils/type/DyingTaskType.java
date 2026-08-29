package me.perch.QuestUtils.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.util.NumberConversions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DyingTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public DyingTaskType(BukkitQuestsPlugin plugin) {
        super("dying", TaskUtils.TASK_ATTRIBUTION_STRING, "Die in a specific way or location.");
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));

        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "x"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "y"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "z"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "distance-padding"));

        super.addConfigValidator(
                TaskUtils.useAcceptedValuesConfigValidator(
                        this,
                        DAMAGE_CAUSES.keySet(),
                        "death-reason",
                        null,
                        false
                )
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        Location deathLocation = player.getLocation();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        EntityDamageEvent.DamageCause actualCause = lastDamage == null ? null : lastDamage.getCause();

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player died", quest.getId(), task.getId(), player.getUniqueId());

            String requiredCauseString = (String) task.getConfigValue("death-reason");
            if (requiredCauseString != null) {
                EntityDamageEvent.DamageCause requiredCause =
                        DAMAGE_CAUSES.get(requiredCauseString.toLowerCase(Locale.ROOT));

                if (requiredCause == null || actualCause != requiredCause) {
                    super.debug("Death reason does not match, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }
            }

            String worldString = (String) task.getConfigValue("world");
            if (worldString != null) {
                World world = Bukkit.getWorld(worldString);

                if (world == null) {
                    super.debug("World " + worldString + " does not exist, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }

                if (!deathLocation.getWorld().equals(world)) {
                    super.debug("Death world does not match, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }
            }

            Integer x = (Integer) task.getConfigValue("x");
            Integer y = (Integer) task.getConfigValue("y");
            Integer z = (Integer) task.getConfigValue("z");

            if (x != null || y != null || z != null) {
                if (x == null || y == null || z == null) {
                    super.debug("Incomplete death co-ordinates, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }

                Location requiredLocation = new Location(null, x, y, z);
                double distanceSquared = distanceSquared(requiredLocation, deathLocation);
                Integer padding = (Integer) task.getConfigValue("distance-padding");

                if (padding != null) {
                    if (distanceSquared > padding * padding) {
                        super.debug("Death location outside padding, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                        continue;
                    }
                } else if (blockLocationsDiffer(requiredLocation, deathLocation)) {
                    super.debug("Death location does not match exactly, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }
            }

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress, 1);
            int amountNeeded = (int) task.getConfigValue("amount");

            if (progress >= amountNeeded) {
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amountNeeded);
        }
    }

    private boolean blockLocationsDiffer(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private double distanceSquared(Location from, Location to) {
        return NumberConversions.square(from.getX() - to.getX())
                + NumberConversions.square(from.getY() - to.getY())
                + NumberConversions.square(from.getZ() - to.getZ());
    }

    private static final Map<String, EntityDamageEvent.DamageCause> DAMAGE_CAUSES = new HashMap<>() {{
        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            put(cause.name().toLowerCase(Locale.ROOT), cause);
        }
    }};
}