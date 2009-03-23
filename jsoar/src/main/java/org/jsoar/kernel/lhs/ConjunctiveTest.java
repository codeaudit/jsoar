/*
 * (c) 2008  Dave Ray
 *
 * Created on Aug 16, 2008
 */
package org.jsoar.kernel.lhs;

import java.util.Formatter;
import java.util.LinkedList;

import org.jsoar.kernel.symbols.Variable;
import org.jsoar.util.ListHead;

/**
 * @author ray
 */
public class ConjunctiveTest extends ComplexTest
{
    
    public LinkedList<Test> conjunct_list = new LinkedList<Test>();
    
    public ConjunctiveTest()
    {
        
    }
    
    private ConjunctiveTest(ConjunctiveTest other)
    {
        for(Test test : other.conjunct_list)
        {
            this.conjunct_list.add(test.copy());
        }
    }
    
    /* (non-Javadoc)
     * @see org.jsoar.kernel.lhs.Test#asConjunctiveTest()
     */
    @Override
    public ConjunctiveTest asConjunctiveTest()
    {
        return this;
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.Test#copy()
     */
    @Override
    public Test copy()
    {
        return new ConjunctiveTest(this);
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.Test#addAllVariables(int, java.util.List)
     */
    @Override
    public void addAllVariables(int tc_number, ListHead<Variable> var_list)
    {
        for(Test child : conjunct_list)
        {
            child.addAllVariables(tc_number, var_list);
        }
    }

    /* (non-Javadoc)
     * @see org.jsoar.kernel.Test#addBoundVariables(int, java.util.List)
     */
    @Override
    public void addBoundVariables(int tc_number, ListHead<Variable> var_list)
    {
        for(Test child : conjunct_list)
        {
            child.addBoundVariables(tc_number, var_list);
        }
    }

    /* (non-Javadoc)
     * @see java.util.Formattable#formatTo(java.util.Formatter, int, int, int)
     */
    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision)
    {
        formatter.format("{ ");
        for(Test t : conjunct_list)
        {
            formatter.format("%s ", t);
        }
        formatter.format("}");
    }
    
    

}