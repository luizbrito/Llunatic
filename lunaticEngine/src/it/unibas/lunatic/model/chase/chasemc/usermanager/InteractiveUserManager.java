package it.unibas.lunatic.model.chase.chasemc.usermanager;

import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import java.util.List;

public class InteractiveUserManager implements IUserManager {

    public boolean isUserInteractionRequired(List<DeltaChaseStep> newSteps, DeltaChaseStep root, Scenario scenario) {
        return true;
    }

    @Override
    public String toString() {
        return "Interactive";
    }
}
