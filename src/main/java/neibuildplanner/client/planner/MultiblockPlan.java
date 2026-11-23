package neibuildplanner.client.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

import blockrenderer6343.client.world.TrackedDummyWorld;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.recipe.StackInfo;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A bill of materials for one multiblock preview, with adjustable hatch counts.
 * <p>
 * The GregTech preview places exactly one hatch of each type; every other hatch-capable position (a "hint dot" group)
 * gets the casing that the structure falls back to. Raising a hatch count by one therefore turns one casing of that
 * dot group into a hatch, and lowering it turns a hatch back into a casing. A dot group can't hold more hatches than
 * it has positions.
 */
public final class MultiblockPlan {

    /** One hatch type the preview placed in a dot group, before any override. */
    private static final class PlacedHatch {

        private final ItemStack stack;
        private final int count;
        /** {@link HatchCatalog} family, or null when the picker doesn't know this hatch. */
        private final String family;
        /** The row currently showing this hatch. */
        private HatchRow row;

        private PlacedHatch(ItemStack stack, int count, String family) {
            this.stack = stack;
            this.count = count;
            this.family = family;
        }
    }

    /**
     * One hatch item in one dot group. With an override, it stands for every placed hatch of its family in the group.
     */
    public static final class HatchRow {

        public final DotGroup group;
        public final ItemStack stack;
        /** {@link HatchCatalog} family, or null when the hatch can't be swapped. */
        public final String family;
        /** True when the item comes from a hatch override instead of the preview. */
        public final boolean overridden;
        private int placedCount;
        public int plannedCount;

        private HatchRow(DotGroup group, ItemStack stack, String family, boolean overridden) {
            this.group = group;
            this.stack = stack;
            this.family = family;
            this.overridden = overridden;
        }

        public int getPlacedCount() {
            return placedCount;
        }

        /** @return the count actually applied, after clamping to what the dot group can hold. */
        public int setPlannedCount(int requested) {
            final int othersPlanned = group.plannedHatchTotal() - plannedCount;
            plannedCount = Math.max(0, Math.min(requested, group.hatchCapacity() - othersPlanned));
            return plannedCount;
        }
    }

    /** All positions sharing one hint dot. */
    public static final class DotGroup {

        public final int dot;
        public final String hint;
        public final List<HatchRow> hatches = new ArrayList<HatchRow>();
        private final List<PlacedHatch> placed = new ArrayList<PlacedHatch>();
        /** The casing that fills positions without a hatch; null if the preview placed only hatches here. */
        public ItemStack casing;
        public int placedCasingCount;
        /** Positions holding neither a hatch nor the main casing; they stay as they are. */
        public int fixedCount;

        private DotGroup(int dot, String hint) {
            this.dot = dot;
            this.hint = hint;
        }

        public int hatchCapacity() {
            // Without a casing to swap, hatches can only be rearranged among themselves.
            return casing == null ? placedHatchTotal() : placedHatchTotal() + placedCasingCount;
        }

        public int placedHatchTotal() {
            int total = 0;

            for (int i = 0, count = hatches.size(); i < count; i++) {
                total += hatches.get(i).placedCount;
            }

            return total;
        }

        public int plannedHatchTotal() {
            int total = 0;

            for (int i = 0, count = hatches.size(); i < count; i++) {
                total += hatches.get(i).plannedCount;
            }

            return total;
        }

        public int plannedCasingCount() {
            return placedCasingCount - (plannedHatchTotal() - placedHatchTotal());
        }
    }

    public final String name;
    public final ItemStack controller;
    public final List<DotGroup> dotGroups = new ArrayList<DotGroup>();
    public final List<HatchRow> hatchRows = new ArrayList<HatchRow>();
    /** Everything outside the dot groups, controller included. */
    private final ItemStackAmount fixedBlocks = new ItemStackAmount();
    private final ItemStackAmount fixedDotBlocks = new ItemStackAmount();
    private final String controllerKey;

    private MultiblockPlan(String name, ItemStack controller) {
        this.name = name;
        this.controller = controller;
        this.controllerKey = HatchOverrides.controllerKey(controller);
    }

    /**
     * @param dotPositions positions per hint dot (empty for structures without GregTech hatches)
     * @param dotHints     the "Hint Block Number" description per dot, may be missing entries
     */
    public static MultiblockPlan fromPreview(String name, ItemStack controller, TrackedDummyWorld world,
        Map<Integer, LongSet> dotPositions, Map<Integer, String> dotHints) {
        final MultiblockPlan plan = new MultiblockPlan(name, controller);
        final Map<Long, Integer> dotForPosition = new LinkedHashMap<Long, Integer>();
        final Map<Integer, ItemStackAmount> hatchesPerDot = new LinkedHashMap<Integer, ItemStackAmount>();
        final Map<Integer, ItemStackAmount> blocksPerDot = new LinkedHashMap<Integer, ItemStackAmount>();

        for (Map.Entry<Integer, LongSet> entry : dotPositions.entrySet()) {
            for (long position : entry.getValue()) {
                dotForPosition.put(position, entry.getKey());
            }

            hatchesPerDot.put(entry.getKey(), new ItemStackAmount());
            blocksPerDot.put(entry.getKey(), new ItemStackAmount());
        }

        for (long position : world.blockMap.keySet()) {
            final int x = CoordinatePacker.unpackX(position);
            final int y = CoordinatePacker.unpackY(position);
            final int z = CoordinatePacker.unpackZ(position);
            final ItemStack stack = stackAt(world, x, y, z);

            if (stack == null) {
                continue;
            }

            final Integer dot = dotForPosition.get(position);

            if (dot == null) {
                plan.fixedBlocks.add(stack);
            } else if (GregTechBlocks.isHatch(world, x, y, z)) {
                hatchesPerDot.get(dot)
                    .add(stack);
            } else {
                blocksPerDot.get(dot)
                    .add(stack);
            }
        }

        for (Map.Entry<Integer, ItemStackAmount> entry : hatchesPerDot.entrySet()) {
            final int dot = entry.getKey();
            final DotGroup group = new DotGroup(dot, dotHints.get(dot));
            final ItemStackAmount blocks = blocksPerDot.get(dot);

            // The most common non-hatch block is the casing that hatches replace.
            long bestCount = 0;

            for (ItemStack block : blocks.values()) {
                final long count = blocks.get(block);

                if (count > bestCount) {
                    bestCount = count;
                    group.casing = block;
                }
            }

            for (ItemStack block : blocks.values()) {
                final long count = blocks.get(block);

                if (group.casing != null && StackInfo.equalItemAndNBT(block, group.casing, true)) {
                    group.placedCasingCount = (int) count;
                } else {
                    group.fixedCount += (int) count;
                    plan.fixedDotBlocks.add(block, count);
                }
            }

            final ItemStackAmount hatches = entry.getValue();

            for (ItemStack hatch : hatches.values()) {
                group.placed.add(new PlacedHatch(hatch, (int) (long) hatches.get(hatch), HatchCatalog.familyOf(hatch)));
            }

            if (!group.placed.isEmpty() || group.casing != null) {
                plan.dotGroups.add(group);
                plan.rebuildHatchRows(group);
            }
        }

        plan.collectHatchRows();
        return plan;
    }

    /**
     * Swaps every hatch of the row's family in its dot group for {@code pick}, and remembers it for this multiblock.
     */
    public void overrideHatch(HatchRow row, ItemStack pick) {
        if (row.family == null || pick == null) {
            return;
        }

        HatchOverrides.set(controllerKey, row.group.dot, row.family, pick);
        rebuildHatchRows(row.group);
        collectHatchRows();
    }

    /** Drops the row's override, so the preview's (preferred) hatches come back. */
    public void clearOverride(HatchRow row) {
        if (!row.overridden) {
            return;
        }

        HatchOverrides.set(controllerKey, row.group.dot, row.family, null);
        rebuildHatchRows(row.group);
        collectHatchRows();
    }

    /**
     * Turns the group's placed hatches into rows: overridden families collapse into one row with the picked item.
     * Planned-count changes carry over to the new rows.
     */
    private void rebuildHatchRows(DotGroup group) {
        final List<HatchRow> newRows = new ArrayList<HatchRow>();
        final List<HatchRow> carriedOldRows = new ArrayList<HatchRow>();

        for (int i = 0, count = group.placed.size(); i < count; i++) {
            final PlacedHatch placedHatch = group.placed.get(i);
            final ItemStack override = HatchOverrides.get(controllerKey, group.dot, placedHatch.family);
            final ItemStack shown = override == null ? placedHatch.stack : override;
            HatchRow row = null;

            for (int j = 0, rowCount = newRows.size(); j < rowCount; j++) {
                if (StackInfo.equalItemAndNBT(newRows.get(j).stack, shown, true)) {
                    row = newRows.get(j);
                    break;
                }
            }

            if (row == null) {
                row = new HatchRow(group, shown, placedHatch.family, override != null);
                newRows.add(row);
            }

            row.placedCount += placedHatch.count;
            row.plannedCount += placedHatch.count;
            final HatchRow oldRow = placedHatch.row;

            // Each old row's +/- adjustment moves to the first new row that inherits one of its hatches.
            if (oldRow != null && !carriedOldRows.contains(oldRow)) {
                carriedOldRows.add(oldRow);
                row.plannedCount += oldRow.plannedCount - oldRow.placedCount;
            }

            placedHatch.row = row;
        }

        for (int i = 0, count = newRows.size(); i < count; i++) {
            final HatchRow row = newRows.get(i);
            row.plannedCount = Math.max(0, row.plannedCount);
        }

        group.hatches.clear();
        group.hatches.addAll(newRows);
    }

    private void collectHatchRows() {
        hatchRows.clear();

        for (int i = 0, count = dotGroups.size(); i < count; i++) {
            hatchRows.addAll(dotGroups.get(i).hatches);
        }
    }

    /** Same block-to-item conversion as BlockRenderer6343's own ingredient list. */
    private static ItemStack stackAt(World world, int x, int y, int z) {
        final Block block = world.getBlock(x, y, z);

        if (block == null || block == Blocks.air) {
            return null;
        }

        final int meta = world.getBlockMetadata(x, y, z);
        final List<ItemStack> drops;

        if (block.quantityDropped(world.rand) != 1) {
            drops = new ArrayList<ItemStack>();
            drops.add(new ItemStack(block));
        } else {
            drops = block.getDrops(world, x, y, z, meta, 0);
        }

        if (drops == null || drops.isEmpty() || drops.get(0) == null) {
            return null;
        }

        final ItemStack stack = drops.get(0)
            .copy();
        stack.stackSize = 1;
        return stack;
    }

    public void resetHatches() {
        for (int i = 0, count = hatchRows.size(); i < count; i++) {
            hatchRows.get(i).plannedCount = hatchRows.get(i).placedCount;
        }
    }

    /** Every material with the planned hatch counts applied. */
    public ItemStackAmount plannedTotals() {
        final ItemStackAmount totals = new ItemStackAmount();
        totals.putAll(fixedBlocks);
        totals.putAll(fixedDotBlocks);

        for (int i = 0, count = dotGroups.size(); i < count; i++) {
            final DotGroup group = dotGroups.get(i);

            if (group.casing != null) {
                totals.add(group.casing, (long) group.plannedCasingCount());
            }

            for (int j = 0, hatchCount = group.hatches.size(); j < hatchCount; j++) {
                final HatchRow row = group.hatches.get(j);
                totals.add(row.stack, (long) row.plannedCount);
            }
        }

        totals.removeIf(entry -> entry.getValue() <= 0);
        return totals;
    }

    public ItemStackAmount previewTotals() {
        final ItemStackAmount totals = new ItemStackAmount();
        totals.putAll(fixedBlocks);
        totals.putAll(fixedDotBlocks);

        for (int i = 0, count = dotGroups.size(); i < count; i++) {
            final DotGroup group = dotGroups.get(i);

            if (group.casing != null) {
                totals.add(group.casing, (long) group.placedCasingCount);
            }

            // What the preview actually placed, so an override shows up as a swap in the totals.
            for (int j = 0, hatchCount = group.placed.size(); j < hatchCount; j++) {
                final PlacedHatch placedHatch = group.placed.get(j);
                totals.add(placedHatch.stack, (long) placedHatch.count);
            }
        }

        return totals;
    }

    /** The shopping list for a bookmark group: controller first, then hatches, then blocks. */
    public List<ItemStack> shoppingList() {
        final ItemStackAmount totals = plannedTotals();
        final List<ItemStack> hatches = new ArrayList<ItemStack>();
        final List<ItemStack> blocks = new ArrayList<ItemStack>();
        final List<ItemStack> result = new ArrayList<ItemStack>();
        ItemStack controllerStack = null;

        for (ItemStack stack : totals.values()) {
            final ItemStack sized = StackInfo.withAmount(stack, totals.get(stack));

            if (controller != null && StackInfo.equalItemAndNBT(stack, controller, true)) {
                controllerStack = sized;
            } else if (isPlannedHatch(stack)) {
                hatches.add(sized);
            } else {
                blocks.add(sized);
            }
        }

        if (controllerStack != null) {
            result.add(controllerStack);
        }

        result.addAll(hatches);
        result.addAll(blocks);
        return result;
    }

    private boolean isPlannedHatch(ItemStack stack) {
        for (int i = 0, count = hatchRows.size(); i < count; i++) {
            if (StackInfo.equalItemAndNBT(hatchRows.get(i).stack, stack, true)) {
                return true;
            }
        }

        return false;
    }
}
