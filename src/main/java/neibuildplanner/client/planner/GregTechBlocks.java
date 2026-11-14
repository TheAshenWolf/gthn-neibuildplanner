package neibuildplanner.client.planner;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.Loader;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** GregTech checks, guarded so the planner still works for non-GregTech structures. */
final class GregTechBlocks {

    private static final boolean GREGTECH_LOADED = Loader.isModLoaded("gregtech");

    private GregTechBlocks() {}

    static boolean isHatch(World world, int x, int y, int z) {
        return GREGTECH_LOADED && Checks.isHatch(world.getTileEntity(x, y, z));
    }

    private static final class Checks {

        private static boolean isHatch(TileEntity tileEntity) {
            if (!(tileEntity instanceof IGregTechTileEntity)) {
                return false;
            }

            final IMetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            return metaTileEntity instanceof MTEHatch;
        }
    }
}
