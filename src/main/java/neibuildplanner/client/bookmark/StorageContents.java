package neibuildplanner.client.bookmark;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.api.IBookmarkContainerHandler;
import neibuildplanner.client.ae2.TerminalStorage;

/**
 * What the open container can supply. AE2 terminals keep their contents in a client-side item list rather than in
 * slots, so NEI's default slot scan reports nothing useful there; those are read through NEE instead.
 */
public final class StorageContents {

    private StorageContents() {}

    public static ItemStackAmount of(GuiContainer gui, IBookmarkContainerHandler handler) {
        if (TerminalStorage.isAvailable() && TerminalStorage.isTerminal(gui)) {
            return TerminalStorage.read(gui);
        }

        final ItemStackAmount contents = new ItemStackAmount();
        final List<ItemStack> stacks = handler.getStorageStacks(gui);

        for (int i = 0, count = stacks.size(); i < count; i++) {
            contents.add(stacks.get(i));
        }

        return contents;
    }

    public static ItemStackAmount playerInventory(GuiContainer gui) {
        final ItemStackAmount contents = new ItemStackAmount();
        final ItemStack[] inventory = gui.mc.thePlayer.inventory.mainInventory;

        for (int i = 0, count = inventory.length; i < count; i++) {
            if (inventory[i] != null) {
                contents.add(inventory[i]);
            }
        }

        return contents;
    }

    /** @return required minus available, only the positive remainders. */
    public static ItemStackAmount shortfall(ItemStackAmount required, ItemStackAmount available) {
        final ItemStackAmount missing = new ItemStackAmount();

        for (ItemStack stack : required.values()) {
            final long remainder = required.get(stack) - available.getOrDefault(stack, 0L);

            if (remainder > 0) {
                missing.add(stack, remainder);
            }
        }

        return missing;
    }
}
