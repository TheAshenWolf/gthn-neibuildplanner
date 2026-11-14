package neibuildplanner.mixins.late;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import blockrenderer6343.client.renderer.ImmediateWorldSceneRenderer;
import blockrenderer6343.client.utils.BRButton;
import blockrenderer6343.integration.nei.GuiMultiblockHandler;
import neibuildplanner.client.planner.GuiMultiblockPlanner;
import neibuildplanner.client.planner.PreviewAccess;

/** Adds the "B" (build planner) button under the preview and exposes the preview state the planner reads. */
@Mixin(value = GuiMultiblockHandler.class, remap = false)
public abstract class MixinGuiMultiblockHandler implements PreviewAccess {

    @Shadow
    protected ImmediateWorldSceneRenderer renderer;

    @Shadow
    protected ItemStack stackForm;

    @Shadow
    protected abstract BRButton addButtonInRow(String displayString);

    @Shadow
    protected abstract void initializeSceneRenderer(boolean resetCamera);

    @Shadow
    protected abstract String getMultiblockName();

    @Inject(method = "initGui", at = @At("TAIL"))
    private void neibuildplanner$addPlannerButton(CallbackInfo callback) {
        addButtonInRow("B").setTooltip(I18n.format("neibuildplanner.planner.button"))
            .setClickAction(() -> GuiMultiblockPlanner.open((GuiMultiblockHandler) (Object) this));
    }

    @Override
    public ImmediateWorldSceneRenderer neibuildplanner$getRenderer() {
        return renderer;
    }

    @Override
    public ItemStack neibuildplanner$getControllerStack() {
        return stackForm == null ? null : stackForm.copy();
    }

    @Override
    public String neibuildplanner$getMultiblockName() {
        return getMultiblockName();
    }

    @Override
    public void neibuildplanner$rebuildPreview() {
        if (renderer != null) {
            initializeSceneRenderer(false);
        }
    }
}
