package it.unibas.lunatic.model.chase.chasemc.operators;

import it.unibas.lunatic.LunaticConstants;
import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasemc.CellGroup;
import it.unibas.lunatic.model.chase.chasemc.CellGroupCell;
import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import it.unibas.lunatic.model.chase.chasemc.EquivalenceClassForTGD;
import it.unibas.lunatic.model.chase.chasemc.TGDEquivalenceClassCells;
import it.unibas.lunatic.model.chase.commons.ChaseStats;
import it.unibas.lunatic.model.chase.commons.operators.ChaseUtility;
import it.unibas.lunatic.model.chase.commons.IChaseState;
import it.unibas.lunatic.model.dependency.Dependency;
import it.unibas.lunatic.model.dependency.FormulaVariable;
import it.unibas.lunatic.model.dependency.FormulaVariableOccurrence;
import it.unibas.lunatic.model.generators.IValueGenerator;
import it.unibas.lunatic.model.generators.SkolemFunctionGenerator;
import it.unibas.lunatic.model.dependency.operators.DependencyUtility;
import it.unibas.lunatic.utility.LunaticUtility;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import speedy.SpeedyConstants;
import speedy.model.algebra.IAlgebraOperator;
import speedy.model.algebra.operators.ITupleIterator;
import speedy.model.database.AttributeRef;
import speedy.model.database.CellRef;
import speedy.model.database.IDatabase;
import speedy.model.database.IValue;
import speedy.model.database.NullValue;
import speedy.model.database.TableAlias;
import speedy.model.database.Tuple;
import speedy.model.database.TupleOID;
import speedy.model.database.operators.IOIDGenerator;
import speedy.model.database.operators.IRunQuery;
import speedy.utility.SpeedyUtility;

public class ChaseTGDEquivalenceClass {

    private static Logger logger = LoggerFactory.getLogger(ChaseTGDEquivalenceClass.class);

    private IRunQuery queryRunner;
    private IOIDGenerator oidGenerator;
    private OccurrenceHandlerMC occurrenceHandler;
    private ChangeCellMC cellChanger;

    private ICorrectCellGroupID cellGroupIDFixer = new CorrectCellGroupID();
    private CheckSatisfactionAfterUpgradesTGD satisfactionChecker = new CheckSatisfactionAfterUpgradesTGD();

    public ChaseTGDEquivalenceClass(IRunQuery queryRunner, IOIDGenerator oidGenerator, OccurrenceHandlerMC occurrenceHandler, ChangeCellMC cellChanger) {
        this.queryRunner = queryRunner;
        this.oidGenerator = oidGenerator;
        this.occurrenceHandler = occurrenceHandler;
        this.cellChanger = cellChanger;
    }

    public boolean chaseDependency(DeltaChaseStep currentNode, Dependency tgd, IAlgebraOperator query,
            Scenario scenario, IChaseState chaseState, IDatabase databaseForStep) {
        if (logger.isDebugEnabled()) logger.debug("** Chasing dependency " + tgd);
        if (logger.isDebugEnabled()) logger.debug("Database " + databaseForStep.printInstances());
        if (logger.isTraceEnabled()) logger.trace("DeltaDB " + currentNode.getDeltaDB().printInstances());
        if (logger.isDebugEnabled()) logger.debug("Current Step " + currentNode.toLongStringLeavesOnlyWithSort());
        if (logger.isTraceEnabled()) logger.debug("Query " + query);
        long violationQueryStart = new Date().getTime();
        ITupleIterator it = queryRunner.run(query, scenario.getSource(), databaseForStep);
        long violationQueryEnd = new Date().getTime();
        ChaseStats.getInstance().addStat(ChaseStats.TGD_VIOLATION_QUERY_TIME, violationQueryEnd - violationQueryStart);
        List<EquivalenceClassForTGD> equivalenceClasses = new ArrayList<EquivalenceClassForTGD>();
        Map<CellGroupCell, List<TGDEquivalenceClassCells>> cellGroupMap = new HashMap<CellGroupCell, List<TGDEquivalenceClassCells>>();
        List<IValue> lastUniversalValues = null;
        long equivalenceClassStart = new Date().getTime();
        while (it.hasNext()) {
            Tuple tuple = it.next();
            if (logger.isDebugEnabled()) logger.debug("Analyzing tuple " + tuple);
            List<IValue> universalValuesInConclusion = DependencyUtility.extractUniversalValuesInConclusion(tuple, tgd);
            if (logger.isDebugEnabled()) logger.debug("Detected violation on values " + universalValuesInConclusion);
            if (lastUniversalValues == null || LunaticUtility.areDifferentConsideringOrder(lastUniversalValues, universalValuesInConclusion)) {
                //New equivalence class
                if (logger.isDebugEnabled()) logger.debug("New equivalence class with universal values " + universalValuesInConclusion);
                EquivalenceClassForTGD equivalenceClass = new EquivalenceClassForTGD(tgd);
                equivalenceClasses.add(equivalenceClass);
                lastUniversalValues = universalValuesInConclusion;
            }
            EquivalenceClassForTGD equivalenceClass = equivalenceClasses.get(equivalenceClasses.size() - 1);
            addTupleInEquivalenceClass(tuple, equivalenceClass, tgd, cellGroupMap);
        }
        long equivalenceClasEnd = new Date().getTime();
        ChaseStats.getInstance().addStat(ChaseStats.TGD_EQUIVALENCE_CLASS_TIME, equivalenceClasEnd - equivalenceClassStart);
        it.close();
        if (logger.isDebugEnabled()) logger.debug("Equivalence classes\n " + LunaticUtility.printCollection(equivalenceClasses));
        if (logger.isDebugEnabled()) logger.debug("CellGroup Map\n " + LunaticUtility.printMap(cellGroupMap));
        List<TGDEquivalenceClassCells> updates = generateUpdates(cellGroupMap, currentNode.getDeltaDB(), currentNode.getId(), tgd, scenario);
        applyChanges(updates, currentNode.getDeltaDB(), currentNode.getId(), scenario);
        if (logger.isDebugEnabled()) logger.debug("** Updates " + LunaticUtility.printCollection(updates));
        return !updates.isEmpty();
    }

    private void addTupleInEquivalenceClass(Tuple tuple, EquivalenceClassForTGD equivalenceClass, Dependency tgd, Map<CellGroupCell, List<TGDEquivalenceClassCells>> cellGroupMap) {
        List<FormulaVariable> universalVariables = DependencyUtility.getUniversalVariablesInConclusion(tgd);
        for (FormulaVariable universalVariable : universalVariables) {
            List<CellGroupCell> cellsForVariable = findPremiseCells(tuple, universalVariable, tgd);
            TGDEquivalenceClassCells targetCellsToInsert = equivalenceClass.getTargetCellForVariable(universalVariable);
            if (targetCellsToInsert == null) {
                IValue cellGroupValue = cellsForVariable.get(0).getValue();
                targetCellsToInsert = new TGDEquivalenceClassCells(cellGroupValue);
                equivalenceClass.setTargetCellToInsertForVariable(universalVariable, targetCellsToInsert);
            }
            CellGroup cellGroup = targetCellsToInsert.getCellGroup();
            for (CellGroupCell cellForVariable : cellsForVariable) {
                if (cellForVariable.getType().equals(LunaticConstants.TYPE_OCCURRENCE)) {
                    cellGroup.addOccurrenceCell(cellForVariable);
                }
                if (cellForVariable.getType().equals(LunaticConstants.TYPE_JUSTIFICATION)) {
                    cellGroup.addJustificationCell(cellForVariable);
                }
                addCellToCellGroupMap(cellForVariable, targetCellsToInsert, cellGroupMap);
            }
            if (!equivalenceClass.hasNewCellsForVariable(universalVariable)) {
                //New cells for equivalence class need to be generated only once
                generateNewCells(universalVariable, cellGroup.getValue(), equivalenceClass);
            }
        }
        List<FormulaVariable> existentialVariables = DependencyUtility.getExistentialVariables(tgd);
        for (FormulaVariable existentialVariable : existentialVariables) {
            IValue nullValue = generateNullValueForVariable(existentialVariable, tgd.getTargetGenerators(), tuple);
            TGDEquivalenceClassCells targetCellsToInsert = equivalenceClass.getTargetCellForVariable(existentialVariable);
            if (targetCellsToInsert == null) {
                targetCellsToInsert = new TGDEquivalenceClassCells(nullValue);
                equivalenceClass.setTargetCellToInsertForVariable(existentialVariable, targetCellsToInsert);
            }
            if (!equivalenceClass.hasNewCellsForVariable(existentialVariable)) {
                //New cells for equivalence class need to be generated only once
                generateNewCells(existentialVariable, nullValue, equivalenceClass);
                //Need to initialize cell groups for new cells, otherwise missing
                for (CellGroupCell nullCell : targetCellsToInsert.getNewCells()) {
                    addCellToCellGroupMap(nullCell, targetCellsToInsert, cellGroupMap);
                }
            }
        }
    }

    private List<CellGroupCell> findPremiseCells(Tuple tuple, FormulaVariable formulaVariable, Dependency tgd) {
        List<FormulaVariableOccurrence> premiseOccurrences = formulaVariable.getPremiseRelationalOccurrences();
        List<FormulaVariableOccurrence> positiveOccurrences = ChaseUtility.findPositiveOccurrences(tgd.getPremise().getPositiveFormula(), premiseOccurrences);
        List<AttributeRef> premiseAttributesForVariable = extractAttributeRefsForVariable(positiveOccurrences);
        List<CellGroupCell> premiseCells = new ArrayList<CellGroupCell>();
        for (AttributeRef attributeRef : premiseAttributesForVariable) {
            TupleOID originalOid = new TupleOID(ChaseUtility.getOriginalOid(tuple, attributeRef));
            CellRef cellRef = new CellRef(originalOid, ChaseUtility.unAlias(attributeRef));
            IValue cellValue = tuple.getCell(attributeRef).getValue();
            String type = LunaticConstants.TYPE_OCCURRENCE;
            if (cellRef.getAttributeRef().isSource()) {
                type = LunaticConstants.TYPE_JUSTIFICATION;
            }
            CellGroupCell cellGroupCell = new CellGroupCell(cellRef, cellValue, null, type, null);
            premiseCells.add(cellGroupCell);
        }
        return premiseCells;
    }

    private List<AttributeRef> extractAttributeRefsForVariable(List<FormulaVariableOccurrence> occurrences) {
        List<AttributeRef> result = new ArrayList<AttributeRef>();
        for (FormulaVariableOccurrence formulaVariableOccurrence : occurrences) {
            result.add(formulaVariableOccurrence.getAttributeRef());
        }
        return result;
    }

    private void generateNewCells(FormulaVariable universalVariable, IValue value, EquivalenceClassForTGD equivalenceClass) {
        for (FormulaVariableOccurrence occurrence : universalVariable.getConclusionRelationalOccurrences()) {
            TableAlias tableAlias = occurrence.getTableAlias();
            AttributeRef attributeRef = occurrence.getAttributeRef();
            TupleOID tupleOID = equivalenceClass.getTupleOIDForTable(tableAlias);
            if (tupleOID == null) {
                tupleOID = new TupleOID(oidGenerator.getNextOID(tableAlias.getTableName()));
                equivalenceClass.putTupleOIDForTableAlias(tableAlias, tupleOID);
            }
            CellGroupCell newCell = new CellGroupCell(tupleOID, ChaseUtility.unAlias(attributeRef), value, new NullValue(SpeedyConstants.NULL), LunaticConstants.TYPE_OCCURRENCE, true);
            equivalenceClass.getTargetCellForVariable(universalVariable).addNewCell(newCell);
        }
    }

    private void addCellToCellGroupMap(CellGroupCell cell, TGDEquivalenceClassCells targetCellsToInsert, Map<CellGroupCell, List<TGDEquivalenceClassCells>> cellMap) {
        List<TGDEquivalenceClassCells> targetCellsForCell = cellMap.get(cell);
        if (targetCellsForCell == null) {
            targetCellsForCell = new ArrayList<TGDEquivalenceClassCells>();
            cellMap.put(cell, targetCellsForCell);
        }
        if (!targetCellsForCell.contains(targetCellsToInsert)) {
            targetCellsForCell.add(targetCellsToInsert);
        }
    }

    private List<TGDEquivalenceClassCells> generateUpdates(Map<CellGroupCell, List<TGDEquivalenceClassCells>> cellMap, IDatabase deltaDB, String step, Dependency tgd, Scenario scenario) {
        long start = new Date().getTime();
        if (logger.isDebugEnabled()) logger.debug("Generating updates for cell map\n" + SpeedyUtility.printMap(cellMap));
        Set<List<TGDEquivalenceClassCells>> analizedSets = new HashSet<List<TGDEquivalenceClassCells>>();
        List<TGDEquivalenceClassCells> candidateUpdates = new ArrayList<TGDEquivalenceClassCells>();
        Set<TupleOID> candidateTuplesToRemoveDueToSAU = new HashSet<TupleOID>();
        List<TGDEquivalenceClassCells> subsumedCellGroups = new ArrayList<TGDEquivalenceClassCells>();
        Set<TupleOID> tuplesToGenerate = new HashSet<TupleOID>();
        for (List<TGDEquivalenceClassCells> group : cellMap.values()) {
            if (analizedSets.contains(group)) {
                continue;
            }
            analizedSets.add(group);
            TGDEquivalenceClassCells mergedCellsToInsert = mergeAll(group);
            CellGroup canonicalCellGroup = mergedCellsToInsert.getCellGroup().clone();
            if (logger.isDebugEnabled()) logger.debug("Canonical CellGroup: " + canonicalCellGroup);
            CellGroup enrichedCellGroup = this.occurrenceHandler.enrichCellGroups(mergedCellsToInsert.getCellGroup(), deltaDB, step, scenario);
            if (logger.isDebugEnabled()) logger.debug("Enriched CellGroup: " + enrichedCellGroup);
            mergedCellsToInsert.setCellGroup(enrichedCellGroup);
            this.cellGroupIDFixer.correctCellGroupId(mergedCellsToInsert.getCellGroup());
            checkAndSetOriginalValues(mergedCellsToInsert.getCellGroup());
            if (logger.isDebugEnabled()) logger.debug("Enriched CellGroup after Fixing ID Value and Original Values: " + mergedCellsToInsert.getCellGroup());
            if (inCandidateTupleToRemoveDueToSAU(mergedCellsToInsert, candidateTuplesToRemoveDueToSAU) //This cell group generates tuples we already decided to remove
                    || satisfactionChecker.isSatisfiedAfterUpgrades(mergedCellsToInsert, canonicalCellGroup, tgd, scenario)) { //Or violation is satisfied after repair
                candidateTuplesToRemoveDueToSAU.addAll(getTupleOIDs(mergedCellsToInsert));
                subsumedCellGroups.add(mergedCellsToInsert);
            } else {
                addNewCells(mergedCellsToInsert);
                candidateUpdates.add(mergedCellsToInsert);
                tuplesToGenerate.addAll(getTupleOIDs(mergedCellsToInsert));
            }
        }
        tuplesToGenerate.removeAll(candidateTuplesToRemoveDueToSAU);
        if (logger.isDebugEnabled()) logger.debug("Tuples to remove due to SAU: " + candidateTuplesToRemoveDueToSAU);
        if (logger.isDebugEnabled()) logger.debug("Tuples to generate: " + tuplesToGenerate);
        if (logger.isDebugEnabled()) logger.debug("Subsumed cell groups: " + subsumedCellGroups);
        for (TGDEquivalenceClassCells cellGroup : subsumedCellGroups) {
            if (isToKeep(cellGroup, tuplesToGenerate)) {
                if (logger.isDebugEnabled()) logger.debug("Cell group needed " + cellGroup);
                addNewCells(cellGroup);
                candidateUpdates.add(cellGroup);
                candidateTuplesToRemoveDueToSAU.removeAll(getTupleOIDs(cellGroup));
            }
        }
        List<TGDEquivalenceClassCells> result = new ArrayList<TGDEquivalenceClassCells>();
        // needs to filter at the end, some early updates might have survived
        for (TGDEquivalenceClassCells candidate : candidateUpdates) {
            if (inCandidateTupleToRemoveDueToSAU(candidate, candidateTuplesToRemoveDueToSAU)) {
                if (logger.isDebugEnabled()) logger.debug("Skipping " + candidate + " due to SAU");
                continue;
            }
            result.add(candidate);
        }
        long end = new Date().getTime();
        ChaseStats.getInstance().addStat(ChaseStats.TGD_GENERATE_UPDATE_TIME, end - start);
        return result;
    }

    private TGDEquivalenceClassCells mergeAll(List<TGDEquivalenceClassCells> group) {
        Iterator<TGDEquivalenceClassCells> iterator = group.iterator();
        TGDEquivalenceClassCells result = iterator.next();
        while (iterator.hasNext()) {
            TGDEquivalenceClassCells otherCellsToInsert = iterator.next();
            CellGroupUtility.mergeCells(otherCellsToInsert.getCellGroup(), result.getCellGroup());
            result.getNewCells().addAll(otherCellsToInsert.getNewCells());
        }
        return result;
    }

    private void addNewCells(TGDEquivalenceClassCells mergedCellsToInsert) {
        CellGroup cellGroup = mergedCellsToInsert.getCellGroup();
        for (CellGroupCell newCell : mergedCellsToInsert.getNewCells()) {
            newCell.setLastSavedCellGroupId(cellGroup.getId()); // new cell will be saved
            cellGroup.addOccurrenceCell(newCell);
        }
    }

    private void applyChanges(List<TGDEquivalenceClassCells> updates, IDatabase deltaDB, String id, Scenario scenario) {
        long equivalenceClassStart = new Date().getTime();
        for (TGDEquivalenceClassCells update : updates) {
            this.cellChanger.changeCells(update.getCellGroup(), deltaDB, id, scenario);
        }
        this.cellChanger.flush(deltaDB);
        long equivalenceClasEnd = new Date().getTime();
        ChaseStats.getInstance().addStat(ChaseStats.TGD_REPAIR_TIME, equivalenceClasEnd - equivalenceClassStart);
    }

    private IValue generateNullValueForVariable(FormulaVariable existentialVariable, Map<AttributeRef, IValueGenerator> targetGenerators, Tuple tuple) {
        IValueGenerator generator = targetGenerators.get(existentialVariable.getConclusionRelationalOccurrences().get(0).getAttributeRef());
        IValue value = generator.generateValue(tuple);
        if (generator instanceof SkolemFunctionGenerator) {
            String type = ((SkolemFunctionGenerator) generator).getType();
            String skolemValue = null;
            if (SpeedyUtility.isBigInt(type)) {
                skolemValue = SpeedyConstants.BIGINT_SKOLEM_PREFIX + Math.abs(value.toString().hashCode());
            }
            if (SpeedyUtility.isDoublePrecision(type)) {
                skolemValue = SpeedyConstants.BIGINT_SKOLEM_PREFIX + Math.abs(value.toString().hashCode()); //Automatic conversion of bigint to doubleprecision
            }
            if (skolemValue != null) {
                value = new NullValue(skolemValue);
            }
        }
        return value;
    }

    private void checkAndSetOriginalValues(CellGroup cellGroup) {
        for (CellGroupCell cell : cellGroup.getAllCells()) {
            if (cell.getOriginalValue() == null) {
                cell.setOriginalValue(cell.getValue());
                cell.setToSave(true);
            }
        }
    }

    private Set<TupleOID> getTupleOIDs(TGDEquivalenceClassCells mergedCellsToInsert) {
        Set<TupleOID> result = new HashSet<TupleOID>();
        for (CellGroupCell cell : mergedCellsToInsert.getNewCells()) {
            result.add(cell.getTupleOID());
        }
        return result;
    }

    private boolean inCandidateTupleToRemoveDueToSAU(TGDEquivalenceClassCells candidate, Set<TupleOID> tuplesToRemoveDueToSAU) {
        Set<TupleOID> tuplesInCandidate = getTupleOIDs(candidate);
        for (TupleOID candidateTupleOID : tuplesInCandidate) {
            if (tuplesToRemoveDueToSAU.contains(candidateTupleOID)) {
                return true;
            }
        }
        return false;
    }

    private boolean isToKeep(TGDEquivalenceClassCells cellGroup, Set<TupleOID> tuplesToGenerate) {
        Set<TupleOID> newTuples = getTupleOIDs(cellGroup);
        for (TupleOID newTuple : newTuples) {
            if (!tuplesToGenerate.contains(newTuple)) {
                return false;
            }
        }
        return true;
    }

}
