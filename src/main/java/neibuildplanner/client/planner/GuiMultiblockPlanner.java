package neibuildplanner.client.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import blockrenderer6343.client.renderer.ImmediateWorldSceneRenderer;
import blockrenderer6343.integration.nei.GuiMultiblockHandler;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.recipe.StackInfo;
import it.unimi.dsi.fastutil.longs.LongSet;
import neibuildplanner.client.bookmark.BookmarkActions;
import neibuildplanner.mixins.late.AccessorGTGuiMultiblockHandler;

/**
 * Hatch calculator for one multiblock preview. Scroll over a hatch (or click - / +) to change how many you will build;
 * the casing of that hint-dot group moves the other way. "Bookmark" adds the resulting material list as a new
 * bookmark group.
 */
public class GuiMultiblockPlanner extends GuiScreen {

    private static final int ROW_HEIGHT = 18;
    private static final int PANEL_MAX_WIDTH = 300;
    private static final int PADDING = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COUNT_BOX_WIDTH = 12;
    private static final int BUTTON_BOOKMARK = 0;
    private static final int BUTTON_RESET = 1;
    private static final int BUTTON_BACK = 2;

    private enum RowKind {
        HEADER,
        HATCH,
        CASING,
        TOTAL
    }

    private static final class Row {

        private final RowKind kind;
        private final String text;
        private final ItemStack stack;
        private final MultiblockPlan.HatchRow hatch;
        private final MultiblockPlan.DotGroup group;

        private Row(RowKind kind, String text, ItemStack stack, MultiblockPlan.HatchRow hatch,
            MultiblockPlan.DotGroup group) {
            this.kind = kind;
            this.text = text;
            this.stack = stack;
            this.hatch = hatch;
            this.group = group;
        }
    }

    private static final int CELL_SIZE = 18;
    private static final int PICKER_MAX_COLUMNS = 12;
    private static final int PICKER_TITLE_HEIGHT = 14;

    private final GuiScreen parent;
    private final MultiblockPlan plan;
    private final List<Row> rows = new ArrayList<Row>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int scrollRows;
    /** The hatch row being swapped, or null when the picker is closed. */
    private MultiblockPlan.HatchRow pickerRow;
    private List<ItemStack> pickerCandidates = Collections.emptyList();
    private int pickerScrollRows;

    public GuiMultiblockPlanner(GuiScreen parent, MultiblockPlan plan) {
        this.parent = parent;
        this.plan = plan;
    }

    /** Builds a plan from the preview on screen and opens the planner over it. */
    public static void open(GuiMultiblockHandler handler) {
        if (!(handler instanceof PreviewAccess)) {
            return;
        }

        final PreviewAccess preview = (PreviewAccess) handler;
        final ImmediateWorldSceneRenderer renderer = preview.neibuildplanner$getRenderer();

        if (renderer == null) {
            return;
        }

        Map<Integer, LongSet> dotPositions = Collections.emptyMap();
        Map<Integer, String> dotHints = Collections.emptyMap();

        if (handler instanceof AccessorGTGuiMultiblockHandler) {
            final AccessorGTGuiMultiblockHandler gregTech = (AccessorGTGuiMultiblockHandler) handler;
            dotPositions = gregTech.neibuildplanner$getHatchGroupPositions();
            dotHints = gregTech.neibuildplanner$getHintForDot();
        }

        final MultiblockPlan plan = MultiblockPlan.fromPreview(
            preview.neibuildplanner$getMultiblockName(),
            preview.neibuildplanner$getControllerStack(),
            renderer.world,
            dotPositions,
            dotHints);
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new GuiMultiblockPlanner(minecraft.currentScreen, plan));
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, width - 20);
        panelLeft = (width - panelWidth) / 2;
        panelTop = 10;
        listTop = panelTop + PADDING + 14;
        listBottom = height - 10 - PADDING - BUTTON_HEIGHT - 6;

        final int buttonWidth = (panelWidth - PADDING * 2 - 8) / 3;
        final int buttonY = listBottom + 6;
        buttonList.clear();
        buttonList.add(
            new GuiButton(
                BUTTON_BOOKMARK,
                panelLeft + PADDING,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                I18n.format("neibuildplanner.planner.bookmark")));
        buttonList.add(
            new GuiButton(
                BUTTON_RESET,
                panelLeft + PADDING + buttonWidth + 4,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                I18n.format("neibuildplanner.planner.reset")));
        buttonList.add(
            new GuiButton(
                BUTTON_BACK,
                panelLeft + PADDING + (buttonWidth + 4) * 2,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                I18n.format("gui.back")));
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();

        if (!plan.dotGroups.isEmpty()) {
            rows.add(new Row(RowKind.HEADER, I18n.format("neibuildplanner.planner.hatches"), null, null, null));

            for (int i = 0, groupCount = plan.dotGroups.size(); i < groupCount; i++) {
                final MultiblockPlan.DotGroup group = plan.dotGroups.get(i);
                final String hint = group.hint == null ? "" : ": " + group.hint;
                rows.add(
                    new Row(
                        RowKind.HEADER,
                        EnumChatFormatting.GRAY + I18n.format("neibuildplanner.planner.dot", group.dot) + hint,
                        null,
                        null,
                        group));

                for (int j = 0, hatchCount = group.hatches.size(); j < hatchCount; j++) {
                    final MultiblockPlan.HatchRow hatch = group.hatches.get(j);
                    rows.add(new Row(RowKind.HATCH, null, hatch.stack, hatch, group));
                }

                if (group.casing != null) {
                    rows.add(new Row(RowKind.CASING, null, group.casing, null, group));
                }
            }
        }

        rows.add(new Row(RowKind.HEADER, I18n.format("neibuildplanner.planner.materials"), null, null, null));
        final ItemStackAmount planned = plan.plannedTotals();

        for (ItemStack stack : planned.values()) {
            rows.add(new Row(RowKind.TOTAL, null, stack, null, null));
        }

        scrollRows = Math.max(0, Math.min(scrollRows, rows.size() - visibleRowCount()));
    }

    private int visibleRowCount() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_BOOKMARK) {
            BookmarkActions.addGroup(plan.shoppingList(), plan.name);
            mc.displayGuiScreen(parent);
        } else if (button.id == BUTTON_RESET) {
            plan.resetHatches();
            rebuildRows();
        } else if (button.id == BUTTON_BACK) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            if (pickerRow != null) {
                closePicker();
            } else {
                mc.displayGuiScreen(parent);
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();

        if (wheel == 0) {
            return;
        }

        final int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        final int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        final int direction = wheel > 0 ? 1 : -1;

        if (pickerRow != null) {
            final int hiddenRows = pickerTotalRows() - pickerVisibleRows();
            pickerScrollRows = Math.max(0, Math.min(pickerScrollRows - direction, hiddenRows));
            return;
        }

        final Row row = rowAt(mouseX, mouseY);

        if (row != null && row.kind == RowKind.HATCH) {
            changeHatch(row.hatch, direction);
        } else {
            scrollRows = Math.max(0, Math.min(scrollRows - direction, rows.size() - visibleRowCount()));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (pickerRow != null) {
            // The picker sits over the list and buttons, so clicks never reach them while it is open.
            pickerClicked(mouseX, mouseY, button);
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
        final Row row = rowAt(mouseX, mouseY);

        if (row == null || row.kind != RowKind.HATCH) {
            return;
        }

        final int left = panelLeft + PADDING;

        if (mouseX >= left && mouseX < left + CELL_SIZE) {
            if (button == 0) {
                openPicker(row.hatch);
            } else if (button == 1) {
                plan.clearOverride(row.hatch);
                rebuildRows();
            }

            return;
        }

        if (button != 0) {
            return;
        }

        final int countRight = panelLeft + panelWidth - PADDING;

        if (mouseX >= countRight - COUNT_BOX_WIDTH) {
            changeHatch(row.hatch, 1);
        } else if (mouseX >= countRight - COUNT_BOX_WIDTH * 2 - 30 && mouseX < countRight - COUNT_BOX_WIDTH - 30) {
            changeHatch(row.hatch, -1);
        }
    }

    private void openPicker(MultiblockPlan.HatchRow hatch) {
        final List<ItemStack> candidates = HatchCatalog.candidates(hatch.family);

        if (candidates.size() < 2) {
            return; // Nothing to swap to (or not a hatch the picker knows).
        }

        pickerRow = hatch;
        pickerCandidates = candidates;
        pickerScrollRows = 0;

        // Start scrolled to the current hatch.
        for (int i = 0, count = candidates.size(); i < count; i++) {
            if (StackInfo.equalItemAndNBT(candidates.get(i), hatch.stack, true)) {
                pickerScrollRows = Math.max(0, Math.min(i / pickerColumns(), pickerTotalRows() - pickerVisibleRows()));
                break;
            }
        }
    }

    private void closePicker() {
        pickerRow = null;
        pickerCandidates = Collections.emptyList();
    }

    private void pickerClicked(int mouseX, int mouseY, int button) {
        final int index = pickerIndexAt(mouseX, mouseY);

        if (button == 0 && index >= 0) {
            plan.overrideHatch(pickerRow, pickerCandidates.get(index));
            closePicker();
            rebuildRows();
        } else if (!insidePicker(mouseX, mouseY)) {
            closePicker();
        }
    }

    private int pickerColumns() {
        return Math.max(1, Math.min(PICKER_MAX_COLUMNS, (panelWidth - PADDING * 4) / CELL_SIZE));
    }

    private int pickerTotalRows() {
        final int columns = pickerColumns();
        return (pickerCandidates.size() + columns - 1) / columns;
    }

    private int pickerVisibleRows() {
        final int available = (listBottom - listTop - PICKER_TITLE_HEIGHT - PADDING * 2) / CELL_SIZE;
        return Math.max(1, Math.min(pickerTotalRows(), available));
    }

    private int pickerWidth() {
        return pickerColumns() * CELL_SIZE + PADDING * 2;
    }

    private int pickerHeight() {
        return PICKER_TITLE_HEIGHT + pickerVisibleRows() * CELL_SIZE + PADDING * 2;
    }

    private int pickerLeft() {
        return (width - pickerWidth()) / 2;
    }

    private int pickerTop() {
        return listTop + (listBottom - listTop - pickerHeight()) / 2;
    }

    private boolean insidePicker(int mouseX, int mouseY) {
        return mouseX >= pickerLeft() && mouseX < pickerLeft() + pickerWidth()
            && mouseY >= pickerTop()
            && mouseY < pickerTop() + pickerHeight();
    }

    /** @return the candidate index under the mouse, or -1. */
    private int pickerIndexAt(int mouseX, int mouseY) {
        final int gridLeft = pickerLeft() + PADDING;
        final int gridTop = pickerTop() + PADDING + PICKER_TITLE_HEIGHT;

        if (mouseX < gridLeft || mouseY < gridTop) {
            return -1;
        }

        final int column = (mouseX - gridLeft) / CELL_SIZE;
        final int visibleRow = (mouseY - gridTop) / CELL_SIZE;

        if (column >= pickerColumns() || visibleRow >= pickerVisibleRows()) {
            return -1;
        }

        final int index = (pickerScrollRows + visibleRow) * pickerColumns() + column;
        return index < pickerCandidates.size() ? index : -1;
    }

    private void changeHatch(MultiblockPlan.HatchRow hatch, int direction) {
        final int step = isShiftKeyDown() ? 10 : 1;
        hatch.setPlannedCount(hatch.plannedCount + direction * step);
        rebuildRows();
    }

    private Row rowAt(int mouseX, int mouseY) {
        if (mouseX < panelLeft || mouseX > panelLeft + panelWidth || mouseY < listTop || mouseY >= listBottom) {
            return null;
        }

        final int index = scrollRows + (mouseY - listTop) / ROW_HEIGHT;
        return index < rows.size() ? rows.get(index) : null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, height - 10, 0xE0101010);
        drawCenteredString(fontRendererObj, plan.name, width / 2, panelTop + PADDING, 0xFFFFFF);

        final ItemStackAmount preview = plan.previewTotals();
        final ItemStackAmount planned = plan.plannedTotals();
        final int visible = visibleRowCount();
        Row hoveredRow = null;

        for (int i = 0; i < visible; i++) {
            final int index = scrollRows + i;

            if (index >= rows.size()) {
                break;
            }

            final Row row = rows.get(index);
            final int y = listTop + i * ROW_HEIGHT;

            if (drawRow(row, y, planned, preview) && mouseY >= y
                && mouseY < y + ROW_HEIGHT
                && mouseX >= panelLeft + PADDING
                && mouseX < panelLeft + panelWidth - PADDING) {
                hoveredRow = row;
            }
        }

        if (scrollRows > 0) {
            drawCenteredString(fontRendererObj, "▲", panelLeft + panelWidth - 4, listTop, 0xAAAAAA);
        }

        if (scrollRows + visible < rows.size()) {
            drawCenteredString(fontRendererObj, "▼", panelLeft + panelWidth - 4, listBottom - 8, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (pickerRow != null) {
            drawPicker(mouseX, mouseY);
        } else if (hoveredRow != null) {
            drawRowTooltip(hoveredRow, mouseX, mouseY);
        }
    }

    private void drawRowTooltip(Row row, int mouseX, int mouseY) {
        final List<String> lines = itemTooltip(row.stack);

        if (row.kind == RowKind.HATCH && row.hatch.family != null) {
            if (row.hatch.overridden) {
                lines.add(EnumChatFormatting.AQUA + I18n.format("neibuildplanner.planner.overridden"));
            }

            if (HatchCatalog.candidates(row.hatch.family)
                .size() > 1) {
                lines.add(EnumChatFormatting.DARK_GRAY + I18n.format("neibuildplanner.planner.pick_hint"));
            }
        }

        drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
    }

    /** The stack's normal tooltip, coloured the way vanilla's renderToolTip does. */
    private List<String> itemTooltip(ItemStack stack) {
        final List<String> tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        final List<String> lines = new ArrayList<String>();

        for (int i = 0, count = tooltip.size(); i < count; i++) {
            lines.add(
                i == 0 ? stack.getRarity().rarityColor + tooltip.get(i) : EnumChatFormatting.GRAY + tooltip.get(i));
        }

        return lines;
    }

    private void drawPicker(int mouseX, int mouseY) {
        final int left = pickerLeft();
        final int top = pickerTop();
        final int columns = pickerColumns();
        final int visibleRows = pickerVisibleRows();
        final int gridLeft = left + PADDING;
        final int gridTop = top + PADDING + PICKER_TITLE_HEIGHT;

        // Push the overlay above the item icons of the list underneath.
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 300);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, height - 10, 0xA0000000);
        drawRect(left - 1, top - 1, left + pickerWidth() + 1, top + pickerHeight() + 1, 0xFF808080);
        drawRect(left, top, left + pickerWidth(), top + pickerHeight(), 0xF0181818);
        final String title = fontRendererObj
            .trimStringToWidth(I18n.format("neibuildplanner.planner.pick_title"), pickerWidth() - PADDING * 2);
        fontRendererObj.drawStringWithShadow(title, gridLeft, top + PADDING, 0xFFD700);
        GL11.glPopMatrix();

        final int hoveredIndex = pickerIndexAt(mouseX, mouseY);

        for (int visibleRow = 0; visibleRow < visibleRows; visibleRow++) {
            for (int column = 0; column < columns; column++) {
                final int index = (pickerScrollRows + visibleRow) * columns + column;

                if (index >= pickerCandidates.size()) {
                    break;
                }

                final ItemStack candidate = pickerCandidates.get(index);
                final int x = gridLeft + column * CELL_SIZE;
                final int y = gridTop + visibleRow * CELL_SIZE;
                final boolean current = StackInfo.equalItemAndNBT(candidate, pickerRow.stack, true);

                if (current || index == hoveredIndex) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(0, 0, 300);
                    drawRect(x, y, x + CELL_SIZE, y + CELL_SIZE, current ? 0x8055FF55 : 0x60FFFFFF);
                    GL11.glPopMatrix();
                }

                zLevel = 300;
                itemRender.zLevel = 300;
                drawItem(candidate, x + 1, y + 1);
                itemRender.zLevel = 0;
                zLevel = 0;
            }
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);

        if (pickerScrollRows > 0) {
            drawCenteredString(fontRendererObj, "▲", left + pickerWidth() - 5, top + PADDING, 0xAAAAAA);
        }

        if (pickerScrollRows + visibleRows < pickerTotalRows()) {
            drawCenteredString(fontRendererObj, "▼", left + pickerWidth() - 5, top + pickerHeight() - 10, 0xAAAAAA);
        }

        GL11.glPopMatrix();

        if (hoveredIndex >= 0) {
            drawHoveringText(itemTooltip(pickerCandidates.get(hoveredIndex)), mouseX, mouseY, fontRendererObj);
        }
    }

    /** @return true if the row shows an item (and so has a tooltip). */
    private boolean drawRow(Row row, int y, ItemStackAmount plannedTotals, ItemStackAmount preview) {
        final int left = panelLeft + PADDING;
        final int right = panelLeft + panelWidth - PADDING;

        if (row.kind == RowKind.HEADER) {
            final String text = fontRendererObj.trimStringToWidth(row.text, right - left);
            fontRendererObj.drawStringWithShadow(text, left, y + 6, 0xFFD700);
            return false;
        }

        drawItem(row.stack, left, y + 1);

        if (row.kind == RowKind.HATCH) {
            final int planned = row.hatch.plannedCount;
            drawRect(right - COUNT_BOX_WIDTH, y + 2, right, y + 16, 0x60FFFFFF);
            drawRect(right - COUNT_BOX_WIDTH * 2 - 30, y + 2, right - COUNT_BOX_WIDTH - 30, y + 16, 0x60FFFFFF);
            drawCenteredString(fontRendererObj, "+", right - COUNT_BOX_WIDTH / 2, y + 5, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "-", right - COUNT_BOX_WIDTH * 3 / 2 - 30, y + 5, 0xFFFFFF);
            drawCenteredString(
                fontRendererObj,
                String.valueOf(planned),
                right - COUNT_BOX_WIDTH - 15,
                y + 5,
                planned == row.hatch.getPlacedCount() ? 0xFFFFFF : 0x55FF55);
            drawName(
                row.stack,
                left + 20,
                y + 5,
                right - COUNT_BOX_WIDTH * 2 - 34 - (left + 20),
                row.hatch.overridden ? 0x55FFFF : 0xDDDDDD);
            return true;
        }

        final long planned;
        final long previewCount;

        if (row.kind == RowKind.CASING) {
            planned = row.group.plannedCasingCount();
            previewCount = row.group.placedCasingCount;
        } else {
            planned = plannedTotals.getOrDefault(row.stack, 0L);
            previewCount = preview.getOrDefault(row.stack, 0L);
        }

        final long delta = planned - previewCount;
        final String countText = delta == 0 ? "x" + planned : "x" + planned + (delta > 0 ? " (+" : " (") + delta + ")";
        final int countColor = delta == 0 ? 0xFFFFFF : delta > 0 ? 0x55FF55 : 0xFF5555;
        final int countWidth = fontRendererObj.getStringWidth(countText);
        fontRendererObj.drawStringWithShadow(countText, right - countWidth, y + 5, countColor);
        drawName(row.stack, left + 20, y + 5, right - countWidth - 4 - (left + 20), 0xDDDDDD);
        return true;
    }

    private void drawName(ItemStack stack, int x, int y, int maxWidth, int color) {
        final String name = fontRendererObj.trimStringToWidth(stack.getDisplayName(), Math.max(0, maxWidth));
        fontRendererObj.drawStringWithShadow(name, x, y, color);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
