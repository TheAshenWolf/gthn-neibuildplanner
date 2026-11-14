package neibuildplanner.client;

import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.guihook.GuiContainerManager;
import neibuildplanner.Tags;

/** NEI finds this class by its name (NEI...Config) and calls it on the client only. */
public class NEIBuildPlannerConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        final PlannerInputHandler handler = new PlannerInputHandler();
        GuiContainerManager.addInputHandler(handler);
        GuiContainerManager.addTooltipHandler(handler);
    }

    @Override
    public String getName() {
        return "NEI Build Planner";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
