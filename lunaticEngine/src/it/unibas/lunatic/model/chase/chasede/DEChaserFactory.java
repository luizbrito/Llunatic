package it.unibas.lunatic.model.chase.chasede;

import it.unibas.lunatic.LunaticConstants;
import it.unibas.lunatic.OperatorFactory;
import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasede.operators.ChaseDEScenarioProxy;
import it.unibas.lunatic.model.chase.chasede.operators.ChaseDEScenario;
import it.unibas.lunatic.model.chase.chasede.operators.ChaseDeltaEGDs;
import it.unibas.lunatic.model.chase.chasede.operators.IInsertFromSelectNaive;
import it.unibas.lunatic.model.chase.chasede.operators.IRemoveDuplicates;
import it.unibas.lunatic.model.chase.commons.IBuildDatabaseForChaseStep;
import it.unibas.lunatic.model.chase.commons.IBuildDeltaDB;
import it.unibas.lunatic.model.chase.commons.IChaseSTTGDs;
import speedy.model.database.operators.IRunQuery;

public class DEChaserFactory {

    public static IDEChaser getChaser(Scenario scenario) {
        String deChaserStrategy = scenario.getConfiguration().getDeChaser();
        if (deChaserStrategy.equals(LunaticConstants.OPTIMIZED_CHASER)) {
            return getOptimizedChaser(scenario);
        }
        if (deChaserStrategy.equals(LunaticConstants.PROXY_MC_CHASER)) {
            return getProxyMCChaser();
        }
        throw new IllegalArgumentException("DE Chaser " + deChaserStrategy + " is not supported");
    }

    private static IDEChaser getOptimizedChaser(Scenario scenario) {
        IChaseSTTGDs stChaser = OperatorFactory.getInstance().getSTChaser(scenario);
        ChaseDeltaEGDs egdChaser = OperatorFactory.getInstance().getDeltaEGDChaser(scenario);
        IInsertFromSelectNaive insertFromSelectNaive = OperatorFactory.getInstance().getInsertFromSelectNaive(scenario);
        IRunQuery queryRunner = OperatorFactory.getInstance().getQueryRunner(scenario);
        IBuildDeltaDB deltaBuilder = OperatorFactory.getInstance().getDeltaDBBuilderDE(scenario);
        IBuildDatabaseForChaseStep databaseBuilder = OperatorFactory.getInstance().getDatabaseBuilderDE(scenario);
        ChaseDEScenario tgdChaser = new ChaseDEScenario(stChaser, egdChaser, queryRunner, insertFromSelectNaive, deltaBuilder, databaseBuilder);
        return tgdChaser;
    }

    private static IDEChaser getProxyMCChaser() {
        return new ChaseDEScenarioProxy();
    }

}
