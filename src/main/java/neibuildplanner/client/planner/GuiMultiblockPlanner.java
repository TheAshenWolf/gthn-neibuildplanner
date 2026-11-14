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

    private final GuiScreen parent;
    private final MultiblockPlan plan;
    private final List<Row> rows = new ArrayList<Row>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int scrollRows;

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
            mc.displayGuiScreen(parent);
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
        final Row row = rowAt(mouseX, mouseY);
        final int direction = wheel > 0 ? 1 : -1;

        if (row != null && row.kind == RowKind.HATCH) {
            changeHatch(row.hatch, direction);
        } else {
            scrollRows = Math.max(0, Math.min(scrollRows - direction, rows.size() - visibleRowCount()));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        final Row row = rowAt(mouseX, mouseY);

        if (button != 0 || row == null || row.kind != RowKind.HATCH) {
            return;
        }

        final int countRight = panelLeft + panelWidth - PADDING;

        if (mouseX >= countRight - COUNT_BOX_WIDTH) {
            changeHatch(row.hatch, 1);
        } else if (mouseX >= countRight - COUNT_BOX_WIDTH * 2 - 30 && mouseX < countRight - COUNT_BOX_WIDTH - 30) {
            changeHatch(row.hatch, -1);
        }
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
        ItemStack hoveredStack = null;

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
                hoveredStack = row.stack;
            }
        }

        if (scrollRows > 0) {
            drawCenteredString(fontRendererObj, "▲", panelLeft + panelWidth - 4, listTop, 0xAAAAAA);
        }

        if (scrollRows + visible < rows.size()) {
            drawCenteredString(fontRendererObj, "▼", panelLeft + panelWidth - 4, listBottom - 8, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (hoveredStack != null) {
            renderToolTip(hoveredStack, mouseX, mouseY);
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
                planned == row.hatch.placedCount ? 0xFFFFFF : 0x55FF55);
            drawName(row.stack, left + 20, y + 5, right - COUNT_BOX_WIDTH * 2 - 34);
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
        drawName(row.stack, left + 20, y + 5, right - countWidth - 4 - (left + 20));
        return true;
    }

    private void drawName(ItemStack stack, int x, int y, int maxWidth) {
        final String name = fontRendererObj.trimStringToWidth(stack.getDisplayName(), Math.max(0, maxWidth));
        fontRendererObj.drawStringWithShadow(name, x, y, 0xDDDDDD);
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
