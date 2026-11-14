package neibuildplanner.client;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

public final class ClientSetup {

    private ClientSetup() {}

    public static void preInit(File configDirectory) {
        PlannerConfig.load(configDirectory);
        PreferredBlocks.load(configDirectory);
        MinecraftForge.EVENT_BUS.register(new PreferredTooltipHandler());
    }
}
