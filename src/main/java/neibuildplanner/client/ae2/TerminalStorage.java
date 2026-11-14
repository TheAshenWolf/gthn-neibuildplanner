package neibuildplanner.client.ae2;

import java.lang.ref.WeakReference;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import codechicken.nei.ItemStackAmount;

/**
 * Reads an AE2 terminal's contents through NotEnoughEnergistics, which already knows the field layout of every
 * terminal flavour (AE2, fluid crafting, wireless, Thaumic). Every NEE class is touched only after
 * {@link #isAvailable()} said NEE is installed.
 */
public final class TerminalStorage {

    private static final String NEE_GUI_UTILS = "com.github.vfyjxf.nee.utils.GuiUtils";
    private static Boolean available;
    private static WeakReference<GuiContainer> lastCheckedGui = new WeakReference<GuiContainer>(null);
    private static boolean lastCheckedIsTerminal;

    private TerminalStorage() {}

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName(NEE_GUI_UTILS, false, TerminalStorage.class.getClassLoader());
                available = Boolean.TRUE;
            } catch (ClassNotFoundException exception) {
                available = Boolean.FALSE;
            }
        }

        return available;
    }

    public static boolean isTerminal(GuiContainer gui) {
        if (gui == null) {
            return false;
        }

        // Hotkey hints ask every frame while Alt is held, and the lookup walks the class hierarchy by reflection.
        if (lastCheckedGui.get() != gui) {
            lastCheckedGui = new WeakReference<GuiContainer>(gui);
            lastCheckedIsTerminal = NeeBridge.getItemRepo(gui) != null;
        }

        return lastCheckedIsTerminal;
    }

    public static ItemStackAmount read(GuiContainer gui) {
        final ItemStackAmount contents = new ItemStackAmount();
        final List<IAEStack<?>> stacks = NeeBridge.getStorageStacks(gui);

        for (int i = 0, count = stacks.size(); i < count; i++) {
            final IAEStack<?> stack = stacks.get(i);

            if (stack instanceof IAEItemStack && stack.getStackSize() > 0) {
                final ItemStack itemStack = ((IAEItemStack) stack).getItemStack();

                if (itemStack != null) {
                    contents.add(itemStack, stack.getStackSize());
                }
            }
        }

        return contents;
    }

    /** Separate class so the NEE references are only resolved once NEE is known to be present. */
    private static final class NeeBridge {

        private static Object getItemRepo(GuiContainer gui) {
            return com.github.vfyjxf.nee.utils.GuiUtils.getItemRepo(gui);
        }

        private static List<IAEStack<?>> getStorageStacks(GuiContainer gui) {
            return com.github.vfyjxf.nee.utils.GuiUtils.getStorageStacks(gui, stack -> true);
        }
    }
}
