package neibuildplanner.client;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PreferredTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (PreferredBlocks.contains(event.itemStack)) {
            event.toolTip
                .add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("neibuildplanner.tooltip.preferred"));
        }
    }
}
