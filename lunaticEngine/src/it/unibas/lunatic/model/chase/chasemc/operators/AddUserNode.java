package it.unibas.lunatic.model.chase.chasemc.operators;

import it.unibas.lunatic.LunaticConstants;
import it.unibas.lunatic.Scenario;
import it.unibas.lunatic.model.chase.chasemc.CellGroup;
import it.unibas.lunatic.model.chase.chasemc.CellGroupCell;
import it.unibas.lunatic.model.chase.chasemc.ChangeDescription;
import speedy.model.database.ConstantValue;
import it.unibas.lunatic.model.chase.chasemc.DeltaChaseStep;
import it.unibas.lunatic.model.chase.chasemc.partialorder.IPartialOrder;

public class AddUserNode {

    private ChangeCellMC cellChanger;
    private OccurrenceHandlerMC occurrenceHandler;

    public AddUserNode(ChangeCellMC cellChanger, OccurrenceHandlerMC occurrenceHandler) {
        this.cellChanger = cellChanger;
        this.occurrenceHandler = occurrenceHandler;
    }

    public DeltaChaseStep addUserNode(DeltaChaseStep father, Scenario scenario) {
        DeltaChaseStep newStep = new DeltaChaseStep(scenario, father, LunaticConstants.CHASE_EDIT_USER, LunaticConstants.CHASE_EDIT_USER);
        newStep.setEditedByUser(true);
        father.addChild(newStep);
        return newStep;
    }

    public CellGroup addChange(DeltaChaseStep userNode, CellGroup cellGroup, ConstantValue newValue, Scenario scenario) {
        if (!userNode.isEditedByUser()) {
            throw new IllegalArgumentException("Changes are allowed only into nodes edited by user");
        }
        CellGroup newCellGroup = cellGroup.clone();
        CellGroupCell userCell = CellGroupIDGenerator.getNextUserCell(newValue);
        newCellGroup.addUserCell(userCell);
        IPartialOrder po = scenario.getPartialOrder();
        po.setCellGroupValue(newCellGroup, scenario);
        ChangeDescription changeSet = new ChangeDescription(newCellGroup, LunaticConstants.CHASE_USER);
        cellChanger.changeCells(changeSet.getCellGroup(), userNode.getDeltaDB(), userNode.getId(), scenario);
        cellChanger.flush(userNode.getDeltaDB());
        if (scenario.getConfiguration().isRemoveDuplicates()) {
            this.occurrenceHandler.generateCellGroupStats(userNode);
        }
        return newCellGroup;
    }
}
