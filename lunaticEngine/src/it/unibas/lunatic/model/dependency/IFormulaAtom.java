package it.unibas.lunatic.model.dependency;

import it.unibas.lunatic.model.expressions.Expression;
import java.util.List;

public interface IFormulaAtom extends Cloneable {

    public void addVariable(FormulaVariable variable);

    public List<FormulaVariable> getVariables();

    public Expression getExpression();
    
    public void setExpression(Expression expression);

    public IFormula getFormula();

    public void setFormula(IFormula formula);

    public String toLongString();

    // atoms are superficially cloned; see PositiveFormula.clone() for deop cloning
    public IFormulaAtom clone();
}
