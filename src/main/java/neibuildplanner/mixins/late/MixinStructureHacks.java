package neibuildplanner.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.structure.IStructureElement;

import blockrenderer6343.integration.nei.StructureHacks;
import neibuildplanner.client.planner.ChannelWrappers;

/**
 * BlockRenderer finds hatch positions by searching an element for GregTech's hatch adder, looking inside lazy elements
 * and chains but not inside {@code withChannel} wrappers. Multiblocks that wrap the hatch adder itself in a channel,
 * like the Precise Assembler's unit casing, end up with no hint dots, so the planner has no hatch rows.
 * <p>
 * Runs only after BlockRenderer found nothing, so it stays out of the way if BlockRenderer learns to unwrap channels
 * itself; {@code require = 0} turns it off if the method changes.
 */
@Mixin(value = StructureHacks.class, remap = false)
public abstract class MixinStructureHacks {

    @Inject(method = "getFirstMatchingElement", at = @At("RETURN"), cancellable = true, require = 0)
    private static void neibuildplanner$lookInsideChannels(String elementToFind, Object multi,
        IStructureElement<Object> element, CallbackInfoReturnable<IStructureElement<Object>> callback) {
        if (callback.getReturnValue() != null) {
            return;
        }

        final IStructureElement<Object> backing = ChannelWrappers.unwrap(element);

        if (backing != null) {
            callback.setReturnValue(StructureHacks.getFirstMatchingElement(elementToFind, multi, backing));
        }
    }
}
