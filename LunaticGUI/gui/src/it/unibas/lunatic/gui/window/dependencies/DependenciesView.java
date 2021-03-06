package it.unibas.lunatic.gui.window.dependencies;

import it.unibas.lunatic.gui.ExplorerTopComponent;
import org.openide.nodes.Node;

/**
 * Top component which displays something.
 */
public class DependenciesView extends ExplorerTopComponent {

    public DependenciesView() {
        initComponents();        
        associateExplorerLookup();
        view.setRootVisible(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        view = new it.unibas.lunatic.gui.window.utils.ExpandedView();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));
        add(view);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private it.unibas.lunatic.gui.window.utils.ExpandedView view;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }

    @Override
    public void setRootContext(Node node) {
        explorer.setRootContext(node);
        view.expandAll();
    }

    @Override
    public void removeRootContext() {
        setDisplayName(Bundle.CTL_DepsTopComponent());
        explorer.setRootContext(Node.EMPTY);
    }
}
