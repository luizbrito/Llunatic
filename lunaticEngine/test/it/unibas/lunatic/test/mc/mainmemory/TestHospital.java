package it.unibas.lunatic.test.mc.mainmemory;

import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasemc.ChaseMCScenario;
import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import it.unibas.lunatic.test.References;
import it.unibas.lunatic.test.UtilityTest;
import it.unibas.lunatic.test.checker.CheckTest;
import junit.framework.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestHospital extends CheckTest {

    private static Logger logger = LoggerFactory.getLogger(TestHospital.class);

    public void testScenario() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.hospital);
//        scenario.getCostManager().setDoBackward(false);
        setConfigurationForTest(scenario);
        ChaseMCScenario chaser = scenario.getCostManager().getChaser(scenario);
        DeltaChaseStep result = chaser.doChase(scenario);
        if (logger.isDebugEnabled()) logger.debug("Scenario " + getTestName("hospital", scenario));
//        if (logger.isDebugEnabled()) logger.debug(result.toShortStringWithSortWithoutDuplicates());
        if (logger.isDebugEnabled()) logger.debug(result.toStringWithSort());
        if (logger.isDebugEnabled()) logger.debug("Number of solutions: " + resultSizer.getPotentialSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of duplicates: " + resultSizer.getDuplicates(result));
        Assert.assertEquals(31, resultSizer.getSolutions(result));
        Assert.assertEquals(29, resultSizer.getDuplicates(result));
        checkSolutions(result);
    }
}