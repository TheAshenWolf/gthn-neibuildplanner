package neibuildplanner.client.planner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;

import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import neibuildplanner.NEIBuildPlanner;

/**
 * Hatches picked in the planner for one multiblock, overriding the preferred blocks there: "on the Tesla tower, use a
 * laser hatch where the energy hatches go". Keyed by controller, hint dot and hatch family, and stored in
 * {@code config/neibuildplanner-overrides.txt}, one override per line: controller, dot, family, item JSON (tab
 * separated).
 */
public final class HatchOverrides {

    private static final String SEPARATOR = "\t";
    private static final Map<String, ItemStack> OVERRIDES = new LinkedHashMap<String, ItemStack>();
    private static File file;

    private HatchOverrides() {}

    public static void load(File configDirectory) {
        file = new File(configDirectory, "neibuildplanner-overrides.txt");
        OVERRIDES.clear();

        if (!file.isFile()) {
            return;
        }

        try {
            final JsonParser parser = new JsonParser();
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            for (int i = 0, count = lines.size(); i < count; i++) {
                final String line = lines.get(i)
                    .trim();

                if (line.isEmpty()) {
                    continue;
                }

                final String[] parts = line.split(SEPARATOR, 4);

                if (parts.length != 4) {
                    NEIBuildPlanner.LOG.error("Skipping malformed hatch override on line {}: {}", i + 1, line);
                    continue;
                }

                final NBTBase tag = NBTJson.toNbt(parser.parse(parts[3]));
                final ItemStack stack = tag instanceof NBTTagCompound ? StackInfo.loadFromNBT((NBTTagCompound) tag)
                    : null;

                if (stack != null) {
                    stack.stackSize = 1;
                    OVERRIDES.put(parts[0] + SEPARATOR + parts[1] + SEPARATOR + parts[2], stack);
                } else {
                    NEIBuildPlanner.LOG
                        .error("Skipping hatch override with an unknown item on line {}: {}", i + 1, line);
                }
            }
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not read hatch overrides from " + file, exception);
        }
    }

    /** @return a single-item copy of the picked hatch, or null when the preferred blocks decide. */
    public static ItemStack get(String controllerKey, int dot, String family) {
        if (controllerKey == null || family == null) {
            return null;
        }

        final ItemStack stack = OVERRIDES.get(key(controllerKey, dot, family));
        return stack == null ? null : stack.copy();
    }

    /** Stores the pick, or removes the override when {@code stack} is null. */
    public static void set(String controllerKey, int dot, String family, ItemStack stack) {
        if (controllerKey == null || family == null) {
            return;
        }

        final String key = key(controllerKey, dot, family);

        if (stack == null) {
            OVERRIDES.remove(key);
        } else {
            final ItemStack copy = stack.copy();
            copy.stackSize = 1;
            OVERRIDES.put(key, copy);
        }

        save();
    }

    /** Item id and damage of the controller; its stack size (the preview tier) and NBT don't matter. */
    public static String controllerKey(ItemStack controller) {
        if (controller == null || controller.getItem() == null) {
            return null;
        }

        final Object name = Item.itemRegistry.getNameForObject(controller.getItem());
        return name == null ? null : name + "@" + controller.getItemDamage();
    }

    private static String key(String controllerKey, int dot, String family) {
        return controllerKey + SEPARATOR + dot + SEPARATOR + family;
    }

    private static void save() {
        if (file == null) {
            return;
        }

        final List<String> lines = new ArrayList<String>();

        for (Map.Entry<String, ItemStack> entry : OVERRIDES.entrySet()) {
            lines.add(entry.getKey() + SEPARATOR + NBTJson.toJson(StackInfo.itemStackToNBT(entry.getValue(), false)));
        }

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            NEIBuildPlanner.LOG.error("Could not save hatch overrides to " + file, exception);
        }
    }
}
