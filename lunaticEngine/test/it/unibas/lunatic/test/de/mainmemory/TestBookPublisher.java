package it.unibas.lunatic.test.de.mainmemory;

import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasede.DEChaserFactory;
import it.unibas.lunatic.model.database.IDatabase;
import it.unibas.lunatic.test.References;
import it.unibas.lunatic.test.UtilityTest;
import it.unibas.lunatic.test.checker.CheckTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBookPublisher extends CheckTest {

    private static Logger logger = LoggerFactory.getLogger(TestBookPublisher.class);
    
    public void testScenarioST() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.bookPublisher_plain);
        IDatabase result = DEChaserFactory.getChaser(scenario).doChase(scenario);
        if (logger.isDebugEnabled()) logger.debug(result.toString());
        checkExpectedInstances(result, scenario);
    }
//
    public void testScenarioEGD() throws Exception {
        Scenario scenario = UtilityTest.loadScenario(References.bookPublisher_egd);
        IDatabase result = DEChaserFactory.getChaser(scenario).doChase(scenario);
        if (logger.isDebugEnabled()) logger.debug(result.toString());
        checkExpectedInstances(result, scenario);
    }
}