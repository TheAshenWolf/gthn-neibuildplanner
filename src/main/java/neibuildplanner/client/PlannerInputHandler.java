package neibuildplanner.client;

import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.nei.BookmarkContainerInfo;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import neibuildplanner.client.bookmark.BookmarkActions;
import neibuildplanner.client.planner.PreviewAccess;

/**
 * Alt + an existing NEI key. Runs in NEI's first key pass, before NEI's own hotkeys, so the plain key keeps its normal
 * NEI behaviour and the Alt variant never reaches it.
 * <ul>
 * <li>Alt + bookmark key (A): add to bookmarks, never remove.</li>
 * <li>Alt + favorite key (F): toggle "preferred in multiblock previews".</li>
 * <li>Alt + craft key (C) over a bookmark group, in an AE2 terminal: request autocrafts for the group.</li>
 * </ul>
 * Alt + pull key (V) is not handled here: NEI's pull ignores modifiers and {@code PullGuard} lets it through when Alt
 * is held. This class only adds its hint.
 */
public class PlannerInputHandler implements IContainerInputHandler, IContainerTooltipHandler {

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        if (!NEIClientUtils.altKey()) {
            return false;
        }

        if (matches("bookmark.add", keyCode)) {
            return BookmarkActions.addAlways(GuiContainerManager.getStackMouseOver(gui));
        }

        if (matches("bookmark.favorite", keyCode)) {
            return togglePreferred(GuiContainerManager.getStackMouseOver(gui));
        }

        if (matches("gui.craft_items", keyCode)) {
            return BookmarkActions.requestGroupCrafts(gui);
        }

        return false;
    }

    /** Lines for NEI's hold-Alt hotkey list in the tooltip. */
    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        if (BookmarkActions.canRequestGroupCrafts(gui)) {
            hotkeys.put(altKeyName("gui.craft_items"), translate("neibuildplanner.hotkey.craft_group"));
        }

        final ItemStack hovered = GuiContainerManager.getStackMouseOver(gui);

        if (PlannerConfig.pullAllOrNothing && BookmarkContainerInfo.getBookmarkContainerHandler(gui) != null
            && (hovered != null || BookmarkActions.hoveredGroupId() >= 0)) {
            hotkeys.put(altKeyName("bookmark.pull_items"), translate("neibuildplanner.hotkey.pull_partial"));
            hotkeys.put(
                KeyManager.getKeyName("bookmark.pull_items", NEIClientUtils.ALT_HASH | NEIClientUtils.SHIFT_HASH),
                translate("neibuildplanner.hotkey.pull_missing_partial"));
        }

        if (hovered != null) {
            hotkeys.put(altKeyName("bookmark.add"), translate("neibuildplanner.hotkey.add_bookmark"));
            hotkeys.put(
                altKeyName("bookmark.favorite"),
                translate(
                    PreferredBlocks.contains(hovered) ? "neibuildplanner.hotkey.unprefer"
                        : "neibuildplanner.hotkey.prefer"));
        }

        return hotkeys;
    }

    /** "Alt + C" style name for an NEI binding, or null when it is unbound (NEI drops null entries). */
    private static String altKeyName(String neiKey) {
        return KeyManager.getKeyName(neiKey, NEIClientUtils.ALT_HASH);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static boolean matches(String neiKey, int keyCode) {
        final int boundKey = KeyManager.getKeyCode(neiKey);
        return boundKey > 0 && boundKey == keyCode;
    }

    private static boolean togglePreferred(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        final boolean nowPreferred = PreferredBlocks.toggle(stack);
        ChatMessages.send(
            nowPreferred ? "neibuildplanner.chat.preferred_added" : "neibuildplanner.chat.preferred_removed",
            stack.getDisplayName());
        PreviewAccess.rebuildOpenPreview();
        return true;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
