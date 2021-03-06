package it.unibas.lunatic.model.algebra.operators;

import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.commons.operators.ChaseUtility;
import it.unibas.lunatic.model.dependency.Dependency;
import it.unibas.lunatic.model.dependency.FormulaVariable;
import it.unibas.lunatic.model.dependency.operators.DependencyUtility;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import speedy.SpeedyConstants;
import speedy.model.algebra.Difference;
import speedy.model.algebra.Distinct;
import speedy.model.algebra.IAlgebraOperator;
import speedy.model.algebra.Limit;
import speedy.model.algebra.Project;
import speedy.model.algebra.ProjectWithoutOIDs;
import speedy.model.algebra.ProjectionAttribute;
import speedy.model.algebra.aggregatefunctions.CountAggregateFunction;
import speedy.model.database.AttributeRef;
import speedy.model.database.TableAlias;
import speedy.utility.SpeedyUtility;

public class BuildAlgebraTreeForStandardChase {

    private static Logger logger = LoggerFactory.getLogger(BuildAlgebraTreeForStandardChase.class);

    private BuildAlgebraTree treeBuilder = new BuildAlgebraTree();

    public IAlgebraOperator generateAlgebraForTargetTGD(Dependency extTGD, Scenario scenario) {
        if (!scenario.getConfiguration().isChaseRestricted()) {
            return generateAlgebraTreeWithDistinct(extTGD, scenario);
        }
        return generateAlgebraTreeWithDifference(extTGD, scenario);
    }

    public IAlgebraOperator generateAlgebraForSourceToTargetTGD(Dependency stTGD, Scenario scenario) {
        if (scenario.getConfiguration().isChaseRestricted() && !ChaseUtility.isRewriteSTTGDs(scenario)) {
            return generateAlgebraTreeWithDifference(stTGD, scenario);
        } else {
            return generateAlgebraTreeWithDistinct(stTGD, scenario);
        }
    }

    public IAlgebraOperator generateAlgebraTreeWithDifference(Dependency extTGD, Scenario scenario) {
        return generate(extTGD, scenario, true, false);
    }

    public IAlgebraOperator generateAlgebraTreeWithDistinct(Dependency extTGD, Scenario scenario) {
        return generate(extTGD, scenario, false, true);
    }

    private IAlgebraOperator generate(Dependency extTGD, Scenario scenario, boolean useDifference, boolean useDistinct) {
        if (logger.isDebugEnabled()) logger.debug("Generating standard query for dependency " + extTGD);
        if (!DependencyUtility.hasUniversalVariablesInConclusion(extTGD)) {
            return generateAlgebraTreeForNoUniversalVariablesInConclusion(extTGD, scenario);
        }
        List<FormulaVariable> universalVariablesInConclusion = DependencyUtility.getUniversalVariablesInConclusion(extTGD);
        if (logger.isDebugEnabled()) logger.debug("Universal variables: " + universalVariablesInConclusion);
        List<AttributeRef> universalAttributesInPremise = DependencyUtility.getUniversalAttributesInPremise(universalVariablesInConclusion);
        if (logger.isDebugEnabled()) logger.debug("Universal attributes in premise: " + universalAttributesInPremise);
        IAlgebraOperator premiseOperator = buildPremiseOperator(extTGD, scenario, universalVariablesInConclusion);
        if (logger.isDebugEnabled()) logger.debug("Premise operator\n" + premiseOperator);
        if (!useDifference && !useDistinct) {
            return premiseOperator;
        }
        if (!useDifference) {
            return addDistinct(premiseOperator, universalAttributesInPremise);
        }
        IAlgebraOperator conclusionOperator = buildConclusionOperator(extTGD, scenario, universalVariablesInConclusion);
        if (logger.isDebugEnabled()) logger.debug("Conclusion operator\n" + conclusionOperator);
        Difference difference = new Difference();
        difference.addChild(premiseOperator);
        difference.addChild(conclusionOperator);
        if (logger.isDebugEnabled()) logger.debug("Difference operator: " + difference);
        IAlgebraOperator root = difference;
        if (ChaseUtility.isUseLimit1ForTGD(extTGD, scenario)) {
            Limit limit = new Limit(1);
            limit.addChild(root);
            if (logger.isDebugEnabled()) logger.debug("Adding limit operator. " + limit);
            root = limit;
        }
        return root;
    }

    public IAlgebraOperator buildPremiseOperator(Dependency dependency, Scenario scenario, List<FormulaVariable> universalVariables) {
        IAlgebraOperator premiseOperator = treeBuilder.buildTreeForPremise(dependency, scenario);
        List<AttributeRef> universalAttributes = DependencyUtility.getUniversalAttributesInPremise(universalVariables);
        if (logger.isDebugEnabled()) logger.debug("Universal attributes in premise: " + universalAttributes);
        if (universalAttributes.isEmpty()) {
            throw new IllegalArgumentException("There are no universal variables in premise, for dependency " + dependency.toLogicalString());
        }
        IAlgebraOperator root = new ProjectWithoutOIDs(SpeedyUtility.createProjectionAttributes(universalAttributes));
        root.addChild(premiseOperator);
        return root;
    }

    private IAlgebraOperator buildConclusionOperator(Dependency dependency, Scenario scenario, List<FormulaVariable> universalVariables) {
        IAlgebraOperator conclusion = treeBuilder.buildTreeForConclusion(dependency, scenario);
        List<AttributeRef> universalAttributes = DependencyUtility.getUniversalAttributesInConclusion(universalVariables);
        if (logger.isDebugEnabled()) logger.debug("Universal attributes in conclusion: " + universalAttributes);
        ProjectWithoutOIDs root = new ProjectWithoutOIDs(SpeedyUtility.createProjectionAttributes(universalAttributes));
        root.addChild(conclusion);
        return root;
    }

    private IAlgebraOperator addDistinct(IAlgebraOperator premiseOperator, List<AttributeRef> universalAttributesInPremise) {
        ProjectWithoutOIDs project = new ProjectWithoutOIDs(SpeedyUtility.createProjectionAttributes(universalAttributesInPremise));
        project.addChild(premiseOperator);
        Distinct distinct = new Distinct();
        distinct.addChild(project);
        return distinct;
    }

    private IAlgebraOperator generateAlgebraTreeForNoUniversalVariablesInConclusion(Dependency extTGD, Scenario scenario) {
        IAlgebraOperator premiseOperator = treeBuilder.buildTreeForPremise(extTGD, scenario);
        if (logger.isDebugEnabled()) logger.debug("Premise operator\n" + premiseOperator);
        IAlgebraOperator conclusionOperator = treeBuilder.buildTreeForConclusion(extTGD, scenario);
        if (logger.isDebugEnabled()) logger.debug("Conclusion operator\n" + conclusionOperator);
        //P = SELECT COUNT(*) FROM premise LIMIT 1
        List<ProjectionAttribute> projectionPremiseAttribute = new ArrayList<ProjectionAttribute>();
        CountAggregateFunction premiseCountAggregate = new CountAggregateFunction(new AttributeRef(new TableAlias(SpeedyConstants.COUNT), SpeedyConstants.COUNT));
        projectionPremiseAttribute.add(new ProjectionAttribute(premiseCountAggregate));
        Project premiseCount = new Project(projectionPremiseAttribute);
        premiseCount.addChild(premiseOperator);
        Limit premiseLimit1 = new Limit(1);
        premiseLimit1.addChild(premiseCount);
        //0 = SELECT 0 = SELECT COUNT(*) FROM premise LIMIT 0
        List<ProjectionAttribute> projectionPremiseAttribute0 = new ArrayList<ProjectionAttribute>();
        CountAggregateFunction countAggregate0 = new CountAggregateFunction(new AttributeRef(new TableAlias(SpeedyConstants.COUNT), SpeedyConstants.COUNT));
        projectionPremiseAttribute0.add(new ProjectionAttribute(countAggregate0));
        Project premiseCount0 = new Project(projectionPremiseAttribute0);
        premiseCount0.addChild(premiseOperator);
        Limit premiseLimit0 = new Limit(0);
        premiseLimit0.addChild(premiseCount0);
        //C = SELECT COUNT(*) FROM conclusion LIMIT 1
        List<ProjectionAttribute> projectionConclusionAttribute = new ArrayList<ProjectionAttribute>();
        CountAggregateFunction conclusionCountAggregate = new CountAggregateFunction(new AttributeRef(new TableAlias(SpeedyConstants.COUNT), SpeedyConstants.COUNT));
        projectionConclusionAttribute.add(new ProjectionAttribute(conclusionCountAggregate));
        Project conclusionCount = new Project(projectionConclusionAttribute);
        conclusionCount.addChild(conclusionOperator);
        Limit conclusionLimit1 = new Limit(1);
        conclusionLimit1.addChild(conclusionCount);
        //P' = P - 0
        Difference premiseDifference = new Difference();
        premiseDifference.addChild(premiseLimit1);
        premiseDifference.addChild(premiseLimit0);
        //P' - C
        Difference result = new Difference();
        result.addChild(premiseDifference);
        result.addChild(conclusionLimit1);
        if (logger.isDebugEnabled()) logger.debug("Algebra tree for no universal variables in conclusion:\n " + result);
        return result;
    }

}
