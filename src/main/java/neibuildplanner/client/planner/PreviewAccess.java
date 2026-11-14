package neibuildplanner.client.planner;

import net.minecraft.item.ItemStack;

import blockrenderer6343.client.renderer.ImmediateWorldSceneRenderer;
import blockrenderer6343.integration.nei.GuiMultiblockHandler;
import blockrenderer6343.integration.nei.MultiblockHandler;

/**
 * Added to BlockRenderer6343's GuiMultiblockHandler by a mixin, to reach its protected preview state.
 */
public interface PreviewAccess {

    ImmediateWorldSceneRenderer neibuildplanner$getRenderer();

    ItemStack neibuildplanner$getControllerStack();

    String neibuildplanner$getMultiblockName();

    void neibuildplanner$rebuildPreview();

    /** Rebuilds the multiblock preview on screen, if any, so a preferred-block change shows at once. */
    static void rebuildOpenPreview() {
        final GuiMultiblockHandler handler;

        try {
            handler = MultiblockHandler.getCurrentGuiHandler();
        } catch (NoClassDefFoundError error) {
            return; // BlockRenderer6343 isn't installed.
        }

        if (handler instanceof PreviewAccess) {
            ((PreviewAccess) handler).neibuildplanner$rebuildPreview();
        }
    }
}
