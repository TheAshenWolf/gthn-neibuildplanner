package neibuildplanner.client.bookmark;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemQuantityField;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.chain.RecipeChainMath;
import neibuildplanner.client.ChatMessages;
import neibuildplanner.client.ae2.GroupCraftRequester;

public final class BookmarkActions {

    private BookmarkActions() {}

    /**
     * Adds the stack to the default bookmark group even when it is already there. NEI's own bookmark key is a toggle
     * and would remove the existing entry instead.
     */
    public static boolean addAlways(ItemStack hovered) {
        if (hovered == null) {
            return false;
        }

        final java.awt.Point mouse = GuiDraw.getMousePosition();
        ItemStack stack = hovered.copy();

        if (ItemPanels.itemPanel.containsWithSubpanels(mouse.x, mouse.y)) {
            // Item list: use the amount typed into NEI's quantity field, like NEI's own bookmark key does.
            stack = ItemQuantityField.prepareStackWithQuantity(stack, 0);
        }

        ItemPanels.bookmarkPanel.getGrid()
            .addItem(BookmarkItem.of(BookmarkGrid.DEFAULT_GROUP_ID, stack), true);
        return true;
    }

    /** Adds the stacks as a new bookmark group, so the same item can sit in many groups at once. */
    public static void addGroup(List<ItemStack> stacks, String name) {
        if (ItemPanels.bookmarkPanel.addGroup(stacks, (BookmarkViewMode) null, false)) {
            ChatMessages.send("neibuildplanner.chat.group_added", name);
        }
    }

    /** @return the group under the mouse in the bookmark panel, or -1. */
    public static int hoveredGroupId() {
        final java.awt.Point mouse = GuiDraw.getMousePosition();

        if (!ItemPanels.bookmarkPanel.contains(mouse.x, mouse.y)) {
            return -1;
        }

        final BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouse.x, mouse.y);

        if (slot != null) {
            return slot.getGroupId();
        }

        return ItemPanels.bookmarkPanel.getHoveredGroupId(true);
    }

    public static boolean requestGroupCrafts(GuiContainer gui) {
        final int groupId = hoveredGroupId();

        if (groupId < 0) {
            return false;
        }

        final RecipeChainMath math = ItemPanels.bookmarkPanel.getGrid()
            .createRecipeChainMath(groupId);

        if (math == null) {
            return false;
        }

        final ItemStackAmount required = GroupRequirements.of(math);
        GuiContainer terminal = gui;

        if (gui instanceof GuiRecipe) {
            // A recipe page opened from the terminal: go back to the terminal first, as NEE does for Ctrl+click.
            terminal = ((GuiRecipe<?>) gui).firstGui;
            gui.mc.displayGuiScreen(terminal);
        }

        GroupCraftRequester.request(terminal, required);
        return true;
    }
}
