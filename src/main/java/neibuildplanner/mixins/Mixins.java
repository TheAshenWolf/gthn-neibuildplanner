package neibuildplanner.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    PULL_ALL_OR_NOTHING(new MixinBuilder().setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.NEI)
        .addClientMixins("MixinBookmarkPanel")),
    PREFERRED_PREVIEW_BLOCKS(new MixinBuilder().setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.NEI)
        .addRequiredMod(TargetMods.BLOCK_RENDERER)
        .addClientMixins("MixinCreativeItemSource")),
    PLANNER_BUTTON(new MixinBuilder().setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.NEI)
        .addRequiredMod(TargetMods.BLOCK_RENDERER)
        .addClientMixins("MixinGuiMultiblockHandler")),
    PLANNER_HATCH_GROUPS(new MixinBuilder().setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.NEI)
        .addRequiredMod(TargetMods.BLOCK_RENDERER)
        .addRequiredMod(TargetMods.GREGTECH)
        .addClientMixins("AccessorGTGuiMultiblockHandler"));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
