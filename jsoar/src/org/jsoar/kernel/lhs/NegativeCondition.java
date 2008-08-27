/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 16, 2008
 */
package org.jsoar.kernel.lhs;

import java.util.List;

import org.jsoar.kernel.symbols.Variable;

/**
 * @author ray
 */
public class NegativeCondition extends ThreeFieldCondition
{
    public NegativeCondition(PositiveCondition positiveCondition)
    {
        // TODO Auto-generated constructor stub
    }

    public PositiveCondition negate()
    {
        return new PositiveCondition(this);
    }
    
    /* (non-Javadoc)
     * @see org.jsoar.kernel.Condition#asNegativeCondition()
     */
    public NegativeCondition asNegativeCondition()
    {
        return this;
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.Condition#addBoundVariables(int, java.util.List)
     */
    @Override
    public void addBoundVariables(int tc_number, List<Variable> var_list)
    {
        // Do nothing
    }
    
    
    
}
