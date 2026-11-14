package neibuildplanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Client-only NEI addon. acceptableRemoteVersions lets the client join servers that don't have it.
 */
@Mod(
    modid = NEIBuildPlanner.MODID,
    name = "NEI Build Planner",
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    dependencies = "after:NotEnoughItems;after:blockrenderer6343")
public class NEIBuildPlanner {

    public static final String MODID = "neibuildplanner";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }

        neibuildplanner.client.ClientSetup.preInit(event.getModConfigurationDirectory());
    }
}
