/*
 * Copyright (c) 2009  Dave Ray <daveray@gmail.com>
 *
 */
package org.jsoar.tcl;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.util.commands.SoarCommand;

/**
 * @author ray
 */
final class EchoCommand implements SoarCommand
{
    private final Agent agent;

    EchoCommand(Agent agent)
    {
        this.agent = agent;
    }

    @Override
    public String execute(String[] args) throws SoarException
    {
        boolean noNewLine = false;
        for(int i = 1; i < args.length; ++i)
        {
            final String argString = args[i].toString();
            if("--nonewline".equals(argString))
            {
                noNewLine = true;
            }
            else
            {
                agent.getPrinter().print(argString);
            }
        }
        if(!noNewLine)
        {
            agent.getPrinter().print("\n");
        }
        agent.getPrinter().flush();
        return "";
    }
}