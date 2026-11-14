package neibuildplanner.client.ae2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import neibuildplanner.NEIBuildPlanner;
import neibuildplanner.client.ChatMessages;

/**
 * Requests AE2 autocrafts for a list of items, one craft confirmation screen at a time.
 * <p>
 * This drives NotEnoughEnergistics' own request chain (the one behind Ctrl+click on a recipe): NEE subtracts what the
 * terminal and your inventory already hold, keeps only items that have a pattern, opens the confirmation screen for
 * the first one, and opens the next one each time a confirmation screen closes. Cancel on any screen stops the chain.
 * NEE keeps that chain in private fields, so they are set through reflection here.
 */
public final class GroupCraftRequester {

    private static final String HANDLER_CLASS = "com.github.vfyjxf.nee.nei.NEECraftingPreviewHandler";
    private static final String TRACKER_CLASS = "com.github.vfyjxf.nee.utils.IngredientTracker";

    private GroupCraftRequester() {}

    public static void request(GuiContainer terminalGui, ItemStackAmount items) {
        if (!TerminalStorage.isAvailable() || !TerminalStorage.isTerminal(terminalGui)) {
            ChatMessages.send("neibuildplanner.chat.craft_no_terminal");
            return;
        }

        if (items.isEmpty()) {
            ChatMessages.send("neibuildplanner.chat.craft_nothing");
            return;
        }

        try {
            final Class<?> handlerClass = Class.forName(HANDLER_CLASS);
            final Class<?> trackerClass = Class.forName(TRACKER_CLASS);
            final Object handler = handlerClass.getField("instance")
                .get(null);

            final Method getModId = handlerClass.getDeclaredMethod("getModID", Container.class);
            getModId.setAccessible(true);
            final String modId = (String) getModId.invoke(null, terminalGui.inventorySlots);

            if (modId == null || modId.isEmpty()) {
                ChatMessages.send("neibuildplanner.chat.craft_no_terminal");
                return;
            }

            final Object tracker = trackerClass
                .getConstructor(GuiContainer.class, codechicken.nei.recipe.IRecipeHandler.class, int.class)
                .newInstance(terminalGui, new StackListHandler(items), 0);
            final int requestCount = ((List<?>) trackerClass.getMethod("getRequireStacks")
                .invoke(tracker)).size();

            if (requestCount == 0) {
                ChatMessages.send("neibuildplanner.chat.craft_nothing");
                return;
            }

            setField(handlerClass, handler, "modID", modId);
            setField(handlerClass, handler, "patternCompound", null);
            setField(handlerClass, handler, "isAutoStart", Boolean.FALSE);
            setField(handlerClass, handler, "isRequesting", Boolean.FALSE);
            setField(handlerClass, handler, "tracker", tracker);

            ChatMessages.send("neibuildplanner.chat.craft_started", requestCount);

            final Method requestNext = handlerClass.getDeclaredMethod("requestNextIngredient");
            requestNext.setAccessible(true);
            requestNext.invoke(handler);
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not start the AE2 craft request chain through NEE", exception);
            ChatMessages.send("neibuildplanner.chat.craft_failed");
        }
    }

    private static void setField(Class<?> owner, Object instance, String name, Object value) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
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
