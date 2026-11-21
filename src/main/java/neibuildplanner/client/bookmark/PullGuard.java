package neibuildplanner.client.bookmark;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.BookmarkContainerInfo;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.recipe.chain.RecipeChainMath;
import neibuildplanner.client.ChatMessages;
import neibuildplanner.client.PlannerConfig;

/**
 * All-or-nothing bookmark pulls. Runs before NEI's own pull logic; if storage can't cover the whole request, the pull
 * is cancelled and nothing moves.
 */
public final class PullGuard {

    private PullGuard() {}

    /**
     * @return true when the pull must be cancelled. Holding Alt (Alt+V / Alt+Shift+V) skips the check for that one
     *         pull: NEI's pull key ignores modifiers, so the pull itself still runs.
     */
    public static boolean shouldCancel(RecipeChainMath math, boolean missingOnly) {
        if (!PlannerConfig.pullAllOrNothing || math == null || NEIClientUtils.altKey()) {
            return false;
        }

        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final IBookmarkContainerHandler handler = gui == null ? null
            : BookmarkContainerInfo.getBookmarkContainerHandler(gui);

        if (handler == null) {
            return false;
        }

        final ItemStackAmount required = GroupRequirements.of(math);
        final ItemStackAmount available = StorageContents.of(gui, handler);

        // Shift+V ("pull missing") counts what you already carry; plain V pulls the full amount.
        if (missingOnly) {
            available.putAll(StorageContents.playerInventory(gui));
        }

        final ItemStackAmount missing = StorageContents.shortfall(required, available);

        if (missing.isEmpty()) {
            return false;
        }

        if (PlannerConfig.pullMissingChatMessage) {
            ChatMessages.send(
                "neibuildplanner.chat.pull_cancelled",
                missing.size(),
                KeyManager.getKeyName("bookmark.pull_items", NEIClientUtils.ALT_HASH));
            ChatMessages.sendStackList(missing);
        }

        return true;
    }
}
