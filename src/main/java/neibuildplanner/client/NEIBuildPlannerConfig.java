package neibuildplanner.client;

import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.guihook.GuiContainerManager;
import neibuildplanner.Tags;

/** NEI finds this class by its name (NEI...Config) and calls it on the client only. */
public class NEIBuildPlannerConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        GuiContainerManager.addInputHandler(new PlannerInputHandler());
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
