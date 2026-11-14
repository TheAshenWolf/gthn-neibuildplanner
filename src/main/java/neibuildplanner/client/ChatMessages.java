package neibuildplanner.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import codechicken.nei.ItemStackAmount;

public final class ChatMessages {

    private static final int MAX_LISTED_STACKS = 6;

    private ChatMessages() {}

    public static void send(String translationKey, Object... arguments) {
        final Minecraft minecraft = Minecraft.getMinecraft();

        if (minecraft.thePlayer == null) {
            return;
        }

        final String text = EnumChatFormatting.GOLD + "[Build Planner] "
            + EnumChatFormatting.RESET
            + StatCollector.translateToLocalFormatted(translationKey, arguments);
        minecraft.thePlayer.addChatMessage(new ChatComponentText(text));
    }

    /** One line per stack, capped so a long list doesn't flood the chat. */
    public static void sendStackList(ItemStackAmount stacks) {
        final List<ItemStack> values = stacks.values();
        final int count = values.size();

        for (int i = 0, shown = Math.min(count, MAX_LISTED_STACKS); i < shown; i++) {
            final ItemStack stack = values.get(i);
            send("neibuildplanner.chat.stack_line", stacks.get(stack), stack.getDisplayName());
        }

        if (count > MAX_LISTED_STACKS) {
            send("neibuildplanner.chat.more", count - MAX_LISTED_STACKS);
        }
    }
}
