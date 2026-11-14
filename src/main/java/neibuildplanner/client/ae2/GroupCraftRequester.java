package neibuildplanner.client.ae2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.github.vfyjxf.nee.network.NEENetworkHandler;
import com.github.vfyjxf.nee.network.packet.PacketCraftingRequest;
import com.github.vfyjxf.nee.utils.IngredientTracker;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiCraftConfirm;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import neibuildplanner.NEIBuildPlanner;
import neibuildplanner.client.ChatMessages;

/**
 * Requests AE2 autocrafts for a list of items, one craft confirmation screen at a time.
 * <ul>
 * <li>Start (button or Enter): the craft starts and the next item's confirmation opens.</li>
 * <li>Cancel: this item is skipped and the next one opens. AE2 also bookmarks the items that were missing.</li>
 * <li>Esc (the screen closes instead of returning to the terminal): the whole chain stops.</li>
 * </ul>
 * NEE's ingredient tracker decides what to request (what the network and inventory lack, and only items with a
 * pattern); NEE's packet opens the confirmation screen on the server. Only touch this class after
 * {@link TerminalStorage#isAvailable()} confirmed NEE is installed.
 */
public final class GroupCraftRequester {

    private enum State {
        IDLE,
        WAITING_FOR_CONFIRM,
        CONFIRM_OPEN
    }

    private static final GroupCraftRequester INSTANCE = new GroupCraftRequester();
    private static boolean registered;

    private final Deque<IAEStack<?>> queue = new ArrayDeque<IAEStack<?>>();
    private final List<String> skipped = new ArrayList<String>();
    private State state = State.IDLE;
    private String terminalModId = "";
    private IAEStack<?> current;
    private boolean cancelPressed;
    private int startedCount;
    private Field cancelButtonField;

    private GroupCraftRequester() {}

    public static void request(GuiContainer terminalGui, ItemStackAmount items) {
        if (!TerminalStorage.isAvailable() || !TerminalStorage.isTerminal(terminalGui)) {
            ChatMessages.send("neibuildplanner.chat.craft_no_terminal");
            return;
        }

        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }

        INSTANCE.begin(terminalGui, items);
    }

    private void begin(GuiContainer terminalGui, ItemStackAmount items) {
        reset();

        if (items.isEmpty()) {
            ChatMessages.send("neibuildplanner.chat.craft_nothing");
            return;
        }

        try {
            terminalModId = findTerminalModId(terminalGui.inventorySlots);
            final IngredientTracker tracker = new IngredientTracker(terminalGui, new StackListHandler(items), 0);
            queue.addAll(tracker.getRequireStacks());
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not work out which items to request from AE2", exception);
            ChatMessages.send("neibuildplanner.chat.craft_failed");
            reset();
            return;
        }

        if (terminalModId == null || terminalModId.isEmpty()) {
            ChatMessages.send("neibuildplanner.chat.craft_no_terminal");
            reset();
            return;
        }

        if (queue.isEmpty()) {
            ChatMessages.send("neibuildplanner.chat.craft_nothing");
            return;
        }

        ChatMessages.send("neibuildplanner.chat.craft_started", queue.size());
        requestNext();
    }

    private void requestNext() {
        current = queue.poll();

        if (current == null) {
            finish();
            return;
        }

        cancelPressed = false;
        state = State.WAITING_FOR_CONFIRM;
        NEENetworkHandler.getInstance()
            .sendToServer(
                new PacketCraftingRequest(
                    terminalModId,
                    PacketCraftingRequest.COMMAND_OPEN_CRAFT_CONFIRM,
                    current.toNBTGeneric(),
                    current.getStackSize(),
                    false));
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (state == State.IDLE) {
            return;
        }

        if (event.gui instanceof GuiCraftConfirm) {
            state = State.CONFIRM_OPEN;
            return;
        }

        if (event.gui == null) {
            // Esc, or the terminal was closed: stop everything.
            stop();
            return;
        }

        if (state == State.WAITING_FOR_CONFIRM) {
            // Back at a terminal without a confirmation screen: the server could not plan this one.
            skipped.add(current.getDisplayName());
            requestNext();
            return;
        }

        // Left the confirmation screen for the terminal: Start or Cancel.
        if (cancelPressed) {
            skipped.add(current.getDisplayName());
        } else {
            startedCount++;
        }

        requestNext();
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (state == State.CONFIRM_OPEN && event.gui instanceof GuiCraftConfirm
            && event.button == getCancelButton((GuiCraftConfirm) event.gui)) {
            cancelPressed = true;
        }
    }

    private GuiButton getCancelButton(GuiCraftConfirm gui) {
        try {
            if (cancelButtonField == null) {
                cancelButtonField = GuiCraftConfirm.class.getDeclaredField("cancel");
                cancelButtonField.setAccessible(true);
            }

            return (GuiButton) cancelButtonField.get(gui);
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not find the Cancel button on AE2's craft confirmation screen", exception);
            return null;
        }
    }

    private void finish() {
        ChatMessages.send("neibuildplanner.chat.craft_done", startedCount, skipped.size());
        reportSkipped();
        reset();
    }

    private void stop() {
        final int notRequested = queue.size() + (current == null ? 0 : 1);
        ChatMessages.send("neibuildplanner.chat.craft_stopped", startedCount, notRequested);
        reportSkipped();
        reset();
    }

    private void reportSkipped() {
        if (!skipped.isEmpty()) {
            ChatMessages.send("neibuildplanner.chat.craft_skipped", String.join(", ", skipped));
        }
    }

    private void reset() {
        queue.clear();
        skipped.clear();
        current = null;
        cancelPressed = false;
        startedCount = 0;
        state = State.IDLE;
    }

    /** NEE needs to know which terminal flavour is open; its own lookup is private. */
    private static String findTerminalModId(Container container) throws Exception {
        final Class<?> handlerClass = Class.forName("com.github.vfyjxf.nee.nei.NEECraftingPreviewHandler");
        final Method getModId = handlerClass.getDeclaredMethod("getModID", Container.class);
        getModId.setAccessible(true);
        return (String) getModId.invoke(null, container);
    }

    /** Minimal recipe handler whose only "recipe" lists the requested items; NEE's tracker reads its ingredients. */
    private static final class StackListHandler extends TemplateRecipeHandler {

        private final List<PositionedStack> ingredients = new ArrayList<PositionedStack>();

        private StackListHandler(ItemStackAmount items) {
            for (ItemStack stack : items.values()) {
                final ItemStack sized = stack.copy();
                sized.stackSize = (int) Math.min(Integer.MAX_VALUE, items.get(stack));
                ingredients.add(new PositionedStack(sized, 0, 0, false));
            }
        }

        @Override
        public List<PositionedStack> getIngredientStacks(int recipe) {
            return ingredients;
        }

        @Override
        public String getRecipeName() {
            return "Bookmark group";
        }

        @Override
        public String getGuiTexture() {
            return "textures/gui/container/crafting_table.png";
        }
    }
}
