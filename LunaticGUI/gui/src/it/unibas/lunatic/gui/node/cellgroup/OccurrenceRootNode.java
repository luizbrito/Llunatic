package it.unibas.lunatic.gui.node.cellgroup;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

public class OccurrenceRootNode extends AbstractNode {

    public OccurrenceRootNode(StepCellGroupNode cellGroupNode) {
        super(Children.create(new OccurrenceTupleFactory(cellGroupNode), true));
    }
}
