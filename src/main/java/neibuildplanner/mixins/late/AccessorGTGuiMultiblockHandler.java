package neibuildplanner.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import blockrenderer6343.integration.gregtech.GTGuiMultiblockHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;

/** The GregTech preview's hint-dot bookkeeping: which positions accept hatches, and the hint text per dot. */
@Mixin(value = GTGuiMultiblockHandler.class, remap = false)
public interface AccessorGTGuiMultiblockHandler {

    @Accessor("hatchGroupPositions")
    Int2ObjectMap<LongSet> neibuildplanner$getHatchGroupPositions();

    @Accessor("hintForDot")
    Int2ObjectMap<String> neibuildplanner$getHintForDot();
}
