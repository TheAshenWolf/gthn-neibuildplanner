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

    /** One hatch type placed in one dot group. */
    public static final class HatchRow {

        public final DotGroup group;
        public final ItemStack stack;
        public final int placedCount;
        public int plannedCount;

        private HatchRow(DotGroup group, ItemStack stack, int placedCount) {
            this.group = group;
            this.stack = stack;
            this.placedCount = placedCount;
            this.plannedCount = placedCount;
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

    private MultiblockPlan(String name, ItemStack controller) {
        this.name = name;
        this.controller = controller;
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
                final HatchRow row = new HatchRow(group, hatch, (int) (long) hatches.get(hatch));
                group.hatches.add(row);
                plan.hatchRows.add(row);
            }

            if (!group.hatches.isEmpty() || group.casing != null) {
                plan.dotGroups.add(group);
            }
        }

        return plan;
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

            for (int j = 0, hatchCount = group.hatches.size(); j < hatchCount; j++) {
                final HatchRow row = group.hatches.get(j);
                totals.add(row.stack, (long) row.placedCount);
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
