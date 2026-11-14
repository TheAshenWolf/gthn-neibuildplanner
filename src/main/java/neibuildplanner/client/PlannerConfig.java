package neibuildplanner.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class PlannerConfig {

    private static final String CATEGORY_PULL = "pull";

    public static boolean pullAllOrNothing = true;
    public static boolean pullMissingChatMessage = true;

    private PlannerConfig() {}

    public static void load(File configDirectory) {
        final Configuration configuration = new Configuration(new File(configDirectory, "neibuildplanner.cfg"));

        pullAllOrNothing = configuration.getBoolean(
            "allOrNothing",
            CATEGORY_PULL,
            true,
            "Cancel the whole bookmark pull (V / Shift+V) when storage cannot supply every item.");
        pullMissingChatMessage = configuration.getBoolean(
            "missingChatMessage",
            CATEGORY_PULL,
            true,
            "List the missing items in chat when a pull is cancelled.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
