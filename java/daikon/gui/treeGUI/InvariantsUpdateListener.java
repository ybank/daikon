package daikon.gui.treeGUI;

/**
 * This listener should be invoked when the list of invariants which
 * are visible should be recomputed.  Its primary use is to break a
 * circular dependency.
 **/

interface InvariantsUpdateListener {
  public void updateInvariantsDisplay();
}
