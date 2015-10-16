package it.unibas.lunatic.model.chase.commons;

import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import it.unibas.lunatic.LunaticConstants;
import it.unibas.lunatic.exceptions.ChaseException;
import it.unibas.lunatic.model.chase.chasemc.CellGroupCell;

import it.unibas.lunatic.model.chase.commons.control.IChaseState;
import it.unibas.lunatic.model.chase.chasemc.Repair;
import it.unibas.lunatic.model.dependency.ComparisonAtom;
import it.unibas.lunatic.model.dependency.Dependency;
import it.unibas.lunatic.model.dependency.FormulaVariable;
import it.unibas.lunatic.model.dependency.FormulaVariableOccurrence;
import it.unibas.lunatic.model.dependency.IFormulaAtom;
import it.unibas.lunatic.model.dependency.PositiveFormula;
import it.unibas.lunatic.model.dependency.RelationalAtom;
import it.unibas.lunatic.model.dependency.VariableEquivalenceClass;
import speedy.model.expressions.Expression;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import speedy.SpeedyConstants;
import speedy.model.algebra.IAlgebraOperator;
import speedy.model.algebra.Scan;
import speedy.model.algebra.Select;
import speedy.model.database.AttributeRef;
import speedy.model.database.Cell;
import speedy.model.database.CellRef;
import speedy.model.database.ConstantValue;
import speedy.model.database.IValue;
import speedy.model.database.TableAlias;
import speedy.model.database.Tuple;
import speedy.model.database.TupleOID;
import speedy.model.database.mainmemory.datasource.IntegerOIDGenerator;

public class ChaseUtility {
    
    private static Logger logger = LoggerFactory.getLogger(ChaseUtility.class);
    
    public static List<Cell> findCellsForVariable(FormulaVariable variable, Tuple premiseTuple) {
        if (logger.isTraceEnabled()) logger.debug("Finding cells for variable " + variable);
        if (logger.isTraceEnabled()) logger.debug("Premise tuple: " + premiseTuple);
        List<Cell> result = new ArrayList<Cell>();
        for (FormulaVariableOccurrence occurrence : variable.getPremiseRelationalOccurrences()) {
            AttributeRef occurrenceAttribute = occurrence.getAttributeRef();
            TupleOID originalOid = new TupleOID(ChaseUtility.getOriginalOid(premiseTuple, occurrenceAttribute));
            if (logger.isTraceEnabled()) logger.debug("Occurrence: " + occurrenceAttribute);
            if (logger.isTraceEnabled()) logger.debug("OID: " + originalOid);
            Cell cell = new Cell(originalOid, occurrenceAttribute, premiseTuple.getCell(occurrenceAttribute).getValue());
            result.add(cell);
        }
        return result;
    }

//    private static TupleOID findOID(Tuple premiseTuple, AttributeRef occurrenceAttribute) {
//        AttributeRef oidAttribute = new AttributeRef(occurrenceAttribute.getTableAlias(), "oid");
//        for (Cell cell : premiseTuple.getCells()) {
//            if (cell.getAttributeRef().equals(oidAttribute)) {
//                return new TupleOID(cell.getValue());
//            }
//        }
//        throw new IllegalArgumentException("Unable to find tuple oid for attribute " + occurrenceAttribute + " in tuple " + premiseTuple);
//    }
    public static IValue findValueForVariable(FormulaVariable variable, Tuple premiseTuple) {
        AttributeRef attributeOfFirstOccurrence = variable.getPremiseRelationalOccurrences().get(0).getAttributeRef();
        for (Cell cell : premiseTuple.getCells()) {
            if (cell.getAttributeRef().equals(attributeOfFirstOccurrence)) {
                return cell.getValue();
            }
        }
        throw new IllegalArgumentException("Unable to find occurrence for attribute " + attributeOfFirstOccurrence + " in tuple " + premiseTuple);
    }
    
    public static IAlgebraOperator buildQuery(String deltaTableName, TupleOID tid, String stepId) {
        Scan scan = new Scan(new TableAlias(deltaTableName));
        List<Expression> expressions = new ArrayList<Expression>();
        Expression tidExpression = new Expression(SpeedyConstants.TID + " == \"" + tid + "\"");
        tidExpression.changeVariableDescription(SpeedyConstants.TID, new AttributeRef(deltaTableName, SpeedyConstants.TID));
        expressions.add(tidExpression);
        Expression stepExpression = new Expression(SpeedyConstants.STEP + " == \"" + stepId + "\"");
        stepExpression.changeVariableDescription(SpeedyConstants.STEP, new AttributeRef(deltaTableName, SpeedyConstants.STEP));
        expressions.add(stepExpression);
        Select select = new Select(expressions);
        select.addChild(scan);
        return select;
    }
    
    public static Tuple buildTuple(TupleOID tid, String stepId, IValue newValue, IValue originalValue, IValue groupID, String deltaTableName, String attributeName) {
        TupleOID oid = new TupleOID(IntegerOIDGenerator.getNextOID());
        Tuple tuple = new Tuple(oid);
        tuple.addCell(new Cell(oid, new AttributeRef(deltaTableName, SpeedyConstants.TID), new ConstantValue(tid)));
        tuple.addCell(new Cell(oid, new AttributeRef(deltaTableName, SpeedyConstants.STEP), new ConstantValue(stepId)));
        tuple.addCell(new Cell(oid, new AttributeRef(deltaTableName, attributeName), newValue));
        tuple.addCell(new Cell(oid, new AttributeRef(deltaTableName, LunaticConstants.GROUP_ID), groupID));
        tuple.addCell(new Cell(oid, new AttributeRef(deltaTableName, LunaticConstants.CELL_ORIGINAL_VALUE), originalValue));
        return tuple;
    }
    
    public static boolean isSatisfied(Dependency egd, Tuple premiseTuple) {
        for (IFormulaAtom atom : egd.getConclusion().getAtoms()) {
            if (!(atom instanceof ComparisonAtom)) {
                throw new ChaseException("Illegal egd. Only comparisons are allowed in the conclusion: " + egd);
            }
            ComparisonAtom comparison = (ComparisonAtom) atom;
            if (comparison.getVariables().size() != 2) {
                throw new ChaseException("Unable to handle extended egd: constants appear in conclusion;  " + egd);
            }
            FormulaVariable v1 = comparison.getVariables().get(0);
            FormulaVariable v2 = comparison.getVariables().get(1);
            IValue val1 = ChaseUtility.findValueForVariable(v1, premiseTuple);
            IValue val2 = ChaseUtility.findValueForVariable(v2, premiseTuple);
            if (!val1.equals(val2)) {
                return false;
            }
        }
        return true;
    }

//    public static List<FormulaVariableOccurrence> findTargetOccurrences(FormulaVariable variable) {
//        List<FormulaVariableOccurrence> result = new ArrayList<FormulaVariableOccurrence>();
//        for (FormulaVariableOccurrence occurrence : variable.getPremiseRelationalOccurrences()) {
//            if (occurrence.getAttributeRef().getTableAlias().isSource()) {
//                continue;
//            }
//            result.add(occurrence);
//        }
//        return result;
//    }
    public static List<FormulaVariableOccurrence> findTargetOccurrences(VariableEquivalenceClass variableEquivalenceClass) {
        List<FormulaVariableOccurrence> result = new ArrayList<FormulaVariableOccurrence>();
        for (FormulaVariableOccurrence occurrence : variableEquivalenceClass.getPremiseRelationalOccurrences()) {
            if (occurrence.getAttributeRef().getTableAlias().isSource()) {
                continue;
            }
            result.add(occurrence);
        }
        return result;
    }
    
    public static List<FormulaVariableOccurrence> findPositivePremiseOccurrences(Dependency dependency, FormulaVariable variable) {
        List<FormulaVariableOccurrence> result = new ArrayList<FormulaVariableOccurrence>();
        for (FormulaVariableOccurrence formulaVariableOccurrence : variable.getPremiseRelationalOccurrences()) {
            if (containsAlias(dependency.getPremise().getPositiveFormula(), formulaVariableOccurrence.getTableAlias())) {
                result.add(formulaVariableOccurrence);
            }
        }
        return result;
    }
    
    public static List<FormulaVariableOccurrence> findPositivePremiseOccurrences(Dependency dependency, VariableEquivalenceClass eqv) {
        List<FormulaVariableOccurrence> result = new ArrayList<FormulaVariableOccurrence>();
        for (FormulaVariable variable : eqv.getVariables()) {
            result.addAll(findPositivePremiseOccurrences(dependency, variable));
        }
        return result;
    }
    
    public static List<FormulaVariableOccurrence> findPositiveOccurrences(PositiveFormula positiveFormula, List<FormulaVariableOccurrence> premiseRelationalOccurrences) {
        List<FormulaVariableOccurrence> result = new ArrayList<FormulaVariableOccurrence>();
        for (FormulaVariableOccurrence formulaVariableOccurrence : premiseRelationalOccurrences) {
            if (containsAlias(positiveFormula, formulaVariableOccurrence.getTableAlias())) {
                result.add(formulaVariableOccurrence);
            }
        }
        return result;
    }
    
    public static List<AttributeRef> filterConclusionOccurrences(List<AttributeRef> attributes, Dependency dependency) {
        List<AttributeRef> result = new ArrayList<AttributeRef>();
        ComparisonAtom comparisonAtom = (ComparisonAtom) dependency.getConclusion().getAtoms().get(0);
        FormulaVariable v1 = comparisonAtom.getVariables().get(0);
        FormulaVariable v2 = comparisonAtom.getVariables().get(1);
        for (AttributeRef attributeRef : attributes) {
            if (!containsOccurrences(attributeRef, v1) && !containsOccurrences(attributeRef, v2)) {
                result.add(attributeRef);
            }
        }
        return result;
    }
    
    public static boolean containsOccurrences(AttributeRef attributeRef, FormulaVariable v) {
        for (FormulaVariableOccurrence formulaVariableOccurrence : v.getPremiseRelationalOccurrences()) {
            if (formulaVariableOccurrence.getAttributeRef().equals(attributeRef)) {
                return true;
            }
        }
        return false;
    }
    
    public static AttributeRef unAlias(AttributeRef attribute) {
        TableAlias unaliasedTable = new TableAlias(attribute.getTableName(), attribute.getTableAlias().isSource(), attribute.isAuthoritative());
        return new AttributeRef(unaliasedTable, attribute.getName());
    }
    
    public static TableAlias unAlias(TableAlias alias) {
        TableAlias unaliasedTable = new TableAlias(alias.getTableName(), alias.isSource(), alias.isAuthoritative());
        return unaliasedTable;
    }

//    public static int generateLLUNId(CellGroup cellGroup) {
//        Set<CellRef> cellsToChange = cellGroup.getOccurrences();
//        Set<Cell> provenancesToChange = cellGroup.getProvenances();
//        List<String> cellStrings = new ArrayList<String>();
//        for (CellRef cell : cellsToChange) {
//            cellStrings.add(cell.toString());
//        }
//        for (Cell cell : provenancesToChange) {
//            cellStrings.add(new CellRef(cell).toString());
//        }
//        Collections.sort(cellStrings);
//        return cellStrings.toString().hashCode();
//    }
    public static DeltaChaseStep getRoot(DeltaChaseStep step) {
        DeltaChaseStep root = step;
        while (root.getFather() != null) {
            root = root.getFather();
        }
        return root;
    }
    
    public static String getDeltaRelationName(String tableName, String attributeName) {
        return tableName + LunaticConstants.DELTA_TABLE_SEPARATOR + attributeName;
    }
    
    public static String getChaseNodeId(DeltaChaseStep father, String localId) {
        if (father == null) {
            return localId;
        }
        return father.getId() + "." + localId;
    }
    
    public static List<VariableEquivalenceClass> findJoinVariablesInTarget(Dependency egd) {
        List<VariableEquivalenceClass> result = new ArrayList<VariableEquivalenceClass>();
        for (VariableEquivalenceClass variableEquivalenceClass : egd.getPremise().getLocalVariableEquivalenceClasses()) {
            List<FormulaVariableOccurrence> targetOccurrences = findTargetOccurrences(variableEquivalenceClass);
            List<FormulaVariableOccurrence> positiveOccurrences = findPositiveOccurrences(egd.getPremise().getPositiveFormula(), variableEquivalenceClass.getPremiseRelationalOccurrences());
            if (positiveOccurrences.size() > 1 && !targetOccurrences.isEmpty()) {
                result.add(variableEquivalenceClass);
            }
        }
        return result;
    }
    
    public static boolean containsAlias(PositiveFormula positiveFormula, TableAlias tableAlias) {
        for (IFormulaAtom formulaAtom : positiveFormula.getAtoms()) {
            if (formulaAtom instanceof RelationalAtom) {
                RelationalAtom relationalAtom = (RelationalAtom) formulaAtom;
                if (relationalAtom.getTableAlias().equals(tableAlias)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static boolean containsUnaliasTable(String table, Set<TableAlias> symmetricAtoms) {
        for (TableAlias tableAlias : symmetricAtoms) {
            if (tableAlias.getTableName().equals(table)) {
                return true;
            }
        }
        return false;
    }
    
    public static IValue getOriginalOid(Tuple tuple, AttributeRef attributeRef) {
        Cell oidCell = tuple.getCell(new AttributeRef(attributeRef.getTableAlias(), SpeedyConstants.OID));
        return oidCell.getValue();
    }
    
    public static void stopChase(IChaseState chaseState) {
        chaseState.notifyChaseInterruption();
        throw new ChaseException("Chase interrupted by user");
    }
    
    public static String generateChaseStepIdForEGDs(String egdId, int i, Repair repair) {
        return egdId + "_" + i + "_" + repair.getChaseModes() + "#";
    }
    
    public static String generateChaseStepIdForTGDs(Dependency eTgd) {
//        return "t" + eTgd.getId();
        return eTgd.getId();
    }
    
    public static String getTmpTableForTGDViolations(Dependency tgd, String table, boolean appendSchema) {
//        String tableName = tgd.getId() + "_" + table;
        String tableName = "violation_" + tgd.getId();
        if (appendSchema) {
            tableName = LunaticConstants.WORK_SCHEMA + "." + tableName;
        }
        return tableName;
    }
    
    public static Set<CellRef> createCellRefsFromCells(Collection<CellGroupCell> cells) {
        Set<CellRef> result = new HashSet<CellRef>();
        for (Cell cell : cells) {
            result.add(new CellRef(cell));
        }
        return result;
    }
    
    public static IValue findFirstOrderedValue(Map<IValue, Integer> occurrenceHistogram) {
        List<Entry<IValue, Integer>> entryList = sortEntriesWithValues(occurrenceHistogram);
        return entryList.get(0).getKey();
    }
    
    public static IValue findMostFrequentValueIfAny(Map<IValue, Integer> occurrenceHistogram) {
        List<Entry<IValue, Integer>> entryList = sortEntriesWithValues(occurrenceHistogram);
        IValue firstMaxValue = entryList.get(0).getKey();
        return firstMaxValue;
        //TODO++ Check MostFrequent
//        Integer firstMax = entryList.get(0).getValue();
//        Integer secondMax = null;
//        if (entryList.size() > 1) {
//            secondMax = entryList.get(1).getValue();
//        }
//        if (secondMax == null || firstMax > secondMax) { 
//            return firstMaxValue;
//        }
//        return null;
    }
    
    private static List<Entry<IValue, Integer>> sortEntriesWithValues(Map<IValue, Integer> occurrenceHistogram) {
        List<Entry<IValue, Integer>> entryList = new ArrayList<Entry<IValue, Integer>>(occurrenceHistogram.entrySet());
        Collections.sort(entryList, new Comparator<Entry<IValue, Integer>>() {
            public int compare(Entry<IValue, Integer> entry1, Entry<IValue, Integer> entry2) {
                if (entry1.getValue().equals(entry2.getValue())) {
                    return entry1.getKey().toString().compareTo(entry2.getKey().toString());
                }
                return -1 * entry1.getValue().compareTo(entry2.getValue());
            }
        });
        return entryList;
    }
    
    public static AttributeRef extractAttributeRef(String key) {
        String attributeRefString = key.substring(LunaticConstants.TYPE_ADDITIONAL.length() + 1);
        String tableName = attributeRefString.substring(0, attributeRefString.indexOf("."));
        String attributeName = attributeRefString.substring(attributeRefString.indexOf(".") + 1);
        return new AttributeRef(tableName, attributeName);
    }
    
}
