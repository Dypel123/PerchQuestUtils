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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quests task type which counts piston activations. Configured values from 1
 * through 12 require that exact number of moved blocks. Any configured value of
 * 13 or more counts any failed, straight-line attempt containing at least 13
 * blocks.
 *
 * <p>Bukkit piston events do not contain a player. To avoid awarding a nearby
 * bystander, this class correlates the piston with a recent player action on a
 * redstone control or component.</p>
 */
public final class PistonPushingTaskType extends BukkitTaskType {

    private static final long ACTION_WINDOW_MILLIS = 2_000L;
    private static final int DEFAULT_TRIGGER_RADIUS = 32;
    private static final int VANILLA_PUSH_LIMIT = 12;
    private static final int MAX_FAILED_ATTEMPT_SCAN = 128;

    private final BukkitQuestsPlugin plugin;
    private final Map<UUID, RecentAction> recentActions = new HashMap<>();
    private final Set<PistonKey> activePowerCycles = new HashSet<>();

    public PistonPushingTaskType(BukkitQuestsPlugin plugin) {
        super(
                "pistonpushing",
                TaskUtils.TASK_ATTRIBUTION_STRING,
                "Activate a piston which pushes a configured number of blocks."
        );
        this.plugin = plugin;

        // Optional; defaults to 1 when omitted.
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "blocks"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "blocks"));

        // Optional. The default is 32 blocks between the player's redstone
        // action and the piston. Increase this for longer redstone lines.
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "trigger-radius"));
    }

    /**
     * Remember direct player actions which can cause a redstone state change.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }

        // PlayerInteractEvent can be emitted once per hand.
        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getHand() != null
                && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (isInteractiveRedstoneControl(clicked.getType())) {
            rememberAction(event.getPlayer(), clicked.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isRedstoneComponent(block.getType()) || isPiston(block.getType())) {
            rememberAction(event.getPlayer(), block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (isRedstoneComponent(type) || isPiston(type)) {
            rememberAction(event.getPlayer(), block.getLocation());
        }

        if (isPiston(type)) {
            activePowerCycles.remove(PistonKey.of(block));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        recentActions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Handles normal, successful extensions. getBlocks() includes blocks moved
     * through slime/honey attachments, unlike the deprecated getLength().
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        PistonKey key = PistonKey.of(event.getBlock());
        activePowerCycles.add(key);

        // Mark cancelled extensions as handled so the physics fallback does not
        // mistake another plugin's cancellation for an over-limit attempt.
        if (event.isCancelled()) {
            return;
        }

        processActivation(event.getBlock(), event.getBlocks().size(), false);
    }

    /**
     * Minecraft normally rejects a 13+ block line before a useful successful
     * extension can be observed. A physics update lets us inspect a piston that
     * became powered but remained unextended.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonPhysics(BlockPhysicsEvent event) {
        Block changedBlock = event.getBlock();
        if (!isPiston(changedBlock.getType())) {
            return;
        }

        Location location = changedBlock.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> inspectPowerState(location));
    }

    private void inspectPowerState(Location location) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block pistonBlock = world.getBlockAt(location);
        PistonKey key = PistonKey.of(pistonBlock);

        if (!isPiston(pistonBlock.getType())) {
            activePowerCycles.remove(key);
            return;
        }

        boolean powered = pistonBlock.isBlockPowered() || pistonBlock.isBlockIndirectlyPowered();
        if (!powered) {
            activePowerCycles.remove(key);
            return;
        }

        // Only count one activation per off -> on power cycle.
        if (!activePowerCycles.add(key)) {
            return;
        }

        BlockData blockData = pistonBlock.getBlockData();
        if (!(blockData instanceof Piston) || ((Piston) blockData).isExtended()) {
            return;
        }

        BlockFace direction = ((Directional) blockData).getFacing();
        int attemptedBlocks = countStraightLine(pistonBlock, direction);

        if (attemptedBlocks >= VANILLA_PUSH_LIMIT + 1) {
            processActivation(pistonBlock, attemptedBlocks, true);
        }
    }

    private int countStraightLine(Block piston, BlockFace direction) {
        int count = 0;

        for (int distance = 1; distance <= MAX_FAILED_ATTEMPT_SCAN; distance++) {
            Block block = piston.getRelative(direction, distance);
            Material type = block.getType();

            if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
                break;
            }

            count++;
        }

        return count;
    }

    private void processActivation(Block piston, int affectedBlocks, boolean failedOverLimitAttempt) {
        Player player = findMostLikelyActor(piston);
        if (player == null || player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        RecentAction action = recentActions.get(player.getUniqueId());
        if (action == null) {
            return;
        }

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug(
                    failedOverLimitAttempt
                            ? "Player attempted to push " + affectedBlocks + " blocks with a piston"
                            : "Player pushed " + affectedBlocks + " blocks with a piston",
                    quest.getId(), task.getId(), player.getUniqueId()
            );

            Integer requiredBlocks = (Integer) task.getConfigValue("blocks");
            Integer configuredAmount = (Integer) task.getConfigValue("amount");
            Integer configuredRadius = (Integer) task.getConfigValue("trigger-radius");

            int amount = configuredAmount == null ? 1 : configuredAmount;
            int triggerRadius = configuredRadius == null ? DEFAULT_TRIGGER_RADIUS : configuredRadius;

            if (requiredBlocks == null
                    || requiredBlocks <= 0 || amount <= 0 || triggerRadius <= 0) {
                super.debug("Invalid piston task values, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            if (action.distanceSquared(piston.getLocation()) > (double) triggerRadius * triggerRadius) {
                super.debug("Player action was too far from the piston, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            // 1-12 requires an exact successful push. Any configured value of
            // 13+ enables over-limit mode, where every 13+ attempt matches. For
            // example, blocks: 20 still matches an actual 13-block attempt.
            boolean blocksMatch = requiredBlocks <= VANILLA_PUSH_LIMIT
                    ? !failedOverLimitAttempt && affectedBlocks == requiredBlocks
                    : failedOverLimitAttempt && affectedBlocks >= VANILLA_PUSH_LIMIT + 1;

            if (!blocksMatch) {
                super.debug("Piston block count does not match, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress);
            super.debug("Incrementing task progress (now " + progress + ")",
                    quest.getId(), task.getId(), player.getUniqueId());

            if (progress >= amount) {
                super.debug("Marking task as complete",
                        quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amount);
        }
    }

    private Player findMostLikelyActor(Block piston) {
        long cutoff = System.currentTimeMillis() - ACTION_WINDOW_MILLIS;
        UUID worldId = piston.getWorld().getUID();
        Player bestPlayer = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        recentActions.entrySet().removeIf(entry -> entry.getValue().timestampMillis < cutoff);

        for (Map.Entry<UUID, RecentAction> entry : recentActions.entrySet()) {
            RecentAction action = entry.getValue();
            if (!action.worldId.equals(worldId)) {
                continue;
            }

            Player candidate = Bukkit.getPlayer(entry.getKey());
            if (candidate == null || !candidate.isOnline()) {
                continue;
            }

            double distanceSquared = action.distanceSquared(piston.getLocation());
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestPlayer = candidate;
            }
        }

        return bestPlayer;
    }

    private void rememberAction(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        recentActions.put(
                player.getUniqueId(),
                new RecentAction(world.getUID(), location.getX(), location.getY(), location.getZ(), System.currentTimeMillis())
        );
    }

    private boolean isPiston(Material material) {
        return material == Material.PISTON || material == Material.STICKY_PISTON;
    }

    private boolean isInteractiveRedstoneControl(Material material) {
        String name = material.name();
        return name.equals("LEVER")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.equals("TRIPWIRE");
    }

    private boolean isRedstoneComponent(Material material) {
        String name = material.name();
        return isInteractiveRedstoneControl(material)
                || name.equals("REDSTONE")
                || name.equals("REDSTONE_WIRE")
                || name.equals("REDSTONE_BLOCK")
                || name.contains("REDSTONE_TORCH")
                || name.contains("REPEATER")
                || name.contains("COMPARATOR")
                || name.equals("OBSERVER")
                || name.equals("TRIPWIRE_HOOK")
                || name.equals("DAYLIGHT_DETECTOR")
                || name.equals("TARGET");
    }

    private static final class RecentAction {
        private final UUID worldId;
        private final double x;
        private final double y;
        private final double z;
        private final long timestampMillis;

        private RecentAction(UUID worldId, double x, double y, double z, long timestampMillis) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestampMillis = timestampMillis;
        }

        private double distanceSquared(Location other) {
            double dx = x - other.getX();
            double dy = y - other.getY();
            double dz = z - other.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class PistonKey {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private PistonKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static PistonKey of(Block block) {
            return new PistonKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof PistonKey)) {
                return false;
            }

            PistonKey other = (PistonKey) object;
            return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}