package it.unibas.lunatic.test.mc.dbms;

import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasemc.ChaseMCScenario;
import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import it.unibas.lunatic.model.chase.chasemc.operators.dbms.SQLRunQuery;
import it.unibas.lunatic.persistence.relational.QueryStatManager;
import it.unibas.lunatic.test.GenerateModifiedCells;
import it.unibas.lunatic.test.References;
import it.unibas.lunatic.test.UtilityTest;
import it.unibas.lunatic.test.checker.CheckTest;
import junit.framework.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSQLTreatments extends CheckTest {

    private static Logger logger = LoggerFactory.getLogger(TestSQLTreatments.class);

    protected GenerateModifiedCells modifiedCellsGenerator = new GenerateModifiedCells(new SQLRunQuery());

    public void testScenarioFRSPScript() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.treatments_fr_sp_dbms, true);
        setConfigurationForTest(scenario);
        ChaseMCScenario chaser = scenario.getCostManager().getChaser(scenario);
        DeltaChaseStep result = chaser.doChase(scenario);
        QueryStatManager.getInstance().printStatistics();
        if (logger.isDebugEnabled()) logger.debug("Scenario " + getTestName("treatments", scenario));
        if (logger.isDebugEnabled()) logger.debug("Result: " + result.toStringWithSort());
        if (logger.isDebugEnabled()) logger.debug("Number of leaves: " + resultSizer.getPotentialSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of solutions: " + resultSizer.getSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of duplicate solutions: " + resultSizer.getDuplicates(result));
        checkSolutions(result);
        Assert.assertEquals(1, resultSizer.getSolutions(result));
        Assert.assertEquals(0, resultSizer.getDuplicates(result));
    }

    public void testScenarioPOFRS5() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.treatments_fr_s5_poset_dbms, true);
        setConfigurationForTest(scenario);
        setCheckEGDsAfterEachStep(scenario);
        ChaseMCScenario chaser = scenario.getCostManager().getChaser(scenario);
        DeltaChaseStep result = chaser.doChase(scenario);
        if (logger.isDebugEnabled()) logger.debug("Scenario " + getTestName("treatments", scenario));
        if (logger.isDebugEnabled()) logger.debug("Result: " + result.toStringLeavesOnlyWithSort());
        if (logger.isDebugEnabled()) logger.debug("Number of leaves: " + resultSizer.getPotentialSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of solutions: " + resultSizer.getSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of duplicate solutions: " + resultSizer.getDuplicates(result));
        checkSolutions(result);
        Assert.assertEquals(3, resultSizer.getSolutions(result));
        Assert.assertEquals(21, resultSizer.getDuplicates(result));
    }

    public void testScenarioPOS50() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.treatments_poset_dbms, true);
        setConfigurationForTest(scenario);
        ChaseMCScenario chaser = scenario.getCostManager().getChaser(scenario);
        DeltaChaseStep result = chaser.doChase(scenario);
        QueryStatManager.getInstance().printStatistics();
        if (logger.isDebugEnabled()) logger.debug("Scenario " + getTestName("treatments", scenario));
//        if (logger.isDebugEnabled()) logger.debug("Result: " + result.toShortStringWithSort());
        if (logger.isDebugEnabled()) logger.debug("Number of leaves: " + resultSizer.getPotentialSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of solutions: " + resultSizer.getSolutions(result));
        if (logger.isDebugEnabled()) logger.debug("Number of duplicate solutions: " + resultSizer.getDuplicates(result));
        checkSolutions(result);
//        Assert.assertEquals(34, resultSizer.getSolutions(result));
        Assert.assertEquals(22, resultSizer.getSolutions(result));
        Assert.assertEquals(35, resultSizer.getDuplicates(result));
    }
}