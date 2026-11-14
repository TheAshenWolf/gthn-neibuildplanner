package neibuildplanner.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;

import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import neibuildplanner.NEIBuildPlanner;

/**
 * Items the player wants the multiblock preview to use whenever a structure position accepts them: hatches, busses,
 * glass, coils, any tiered block. Earlier entries win when several preferred items fit the same position.
 */
public final class PreferredBlocks {

    private static final List<ItemStack> PREFERRED = new ArrayList<ItemStack>();
    private static File file;

    private PreferredBlocks() {}

    public static void load(File configDirectory) {
        file = new File(configDirectory, "neibuildplanner-preferred.txt");
        PREFERRED.clear();

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

                final NBTBase tag = NBTJson.toNbt(parser.parse(line));
                final ItemStack stack = tag instanceof NBTTagCompound ? StackInfo.loadFromNBT((NBTTagCompound) tag)
                    : null;

                if (stack != null) {
                    stack.stackSize = 1;
                    PREFERRED.add(stack);
                } else {
                    NEIBuildPlanner.LOG.error("Skipping unknown preferred block on line {}: {}", i + 1, line);
                }
            }
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not read preferred blocks from " + file, exception);
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }

        final List<String> lines = new ArrayList<String>();

        for (int i = 0, count = PREFERRED.size(); i < count; i++) {
            lines.add(NBTJson.toJson(StackInfo.itemStackToNBT(PREFERRED.get(i), false)));
        }

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            NEIBuildPlanner.LOG.error("Could not save preferred blocks to " + file, exception);
        }
    }

    /** @return true if the stack is now preferred, false if it was removed. */
    public static boolean toggle(ItemStack stack) {
        final int index = indexOf(stack);

        if (index >= 0) {
            PREFERRED.remove(index);
            save();
            return false;
        }

        final ItemStack copy = stack.copy();
        copy.stackSize = 1;
        PREFERRED.add(copy);
        save();
        return true;
    }

    public static boolean contains(ItemStack stack) {
        return stack != null && stack.getItem() != null && indexOf(stack) >= 0;
    }

    /** @return a fresh single-item copy of the first preferred stack the predicate accepts, or null. */
    public static ItemStack firstMatch(Predicate<ItemStack> predicate) {
        for (int i = 0, count = PREFERRED.size(); i < count; i++) {
            final ItemStack candidate = PREFERRED.get(i)
                .copy();

            if (predicate.test(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static int indexOf(ItemStack stack) {
        for (int i = 0, count = PREFERRED.size(); i < count; i++) {
            if (StackInfo.equalItemAndNBT(PREFERRED.get(i), stack, true)) {
                return i;
            }
        }

        return -1;
    }
}
