package neibuildplanner.mixins.late;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import blockrenderer6343.api.utils.CreativeItemSource;
import neibuildplanner.client.PreferredBlocks;

/**
 * The multiblock preview builds the structure in survival-build mode and asks this source for every block and hatch.
 * Normally it hands out the first item in NEI's item list that fits. Preferred items are offered first.
 */
@Mixin(value = CreativeItemSource.class, remap = false)
public abstract class MixinCreativeItemSource {

    @Inject(method = "take", at = @At("HEAD"), cancellable = true)
    private void neibuildplanner$offerPreferredFirst(Predicate<ItemStack> predicate, boolean simulate, int count,
        CallbackInfoReturnable<Map<ItemStack, Integer>> callback) {
        final ItemStack preferred = PreferredBlocks.firstMatch(predicate);

        if (preferred == null) {
            return;
        }

        final Map<ItemStack, Integer> result = new HashMap<ItemStack, Integer>();
        result.put(preferred, Integer.MAX_VALUE);
        callback.setReturnValue(result);
    }
}
