package neibuildplanner.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.BookmarkPanel;
import codechicken.nei.recipe.chain.RecipeChainMath;
import neibuildplanner.client.bookmark.PullGuard;

/** All-or-nothing bookmark pulls: every pull path (V, Shift+V, group, single item) ends in this method. */
@Mixin(value = BookmarkPanel.class, remap = false)
public abstract class MixinBookmarkPanel {

    @Inject(
        method = "pullBookmarkItems(Lcodechicken/nei/recipe/chain/RecipeChainMath;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void neibuildplanner$cancelIncompletePull(RecipeChainMath math, boolean shift,
        CallbackInfoReturnable<Boolean> callback) {
        if (PullGuard.shouldCancel(math, shift)) {
            // true = key handled, so nothing else reacts to it.
            callback.setReturnValue(true);
        }
    }
}
