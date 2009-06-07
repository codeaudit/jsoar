/*
 * Copyright (c) 2009 Dave Ray <daveray@gmail.com>
 */
package org.jsoar.tcl;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.util.commands.SoarCommand;

/**
 * @author ray
 */
final class MaxElaborationsCommand implements SoarCommand
{
    private final Agent agent;

    MaxElaborationsCommand(Agent agent)
    {
        this.agent = agent;
    }

    @Override
    public String execute(String[] args) throws SoarException
    {
        if(args.length > 2)
        {
            // TODO: illegal args
            throw new SoarException(String.format("%s [value]", args[0]));
        }

        if(args.length == 1)
        {
            agent.getPrinter().print("\n%d", agent.getProperties().get(SoarProperties.MAX_ELABORATIONS));
        }
        else
        {
            try
            {
                final int value = Integer.parseInt(args[1].toString());
                agent.getProperties().set(SoarProperties.MAX_ELABORATIONS, value);
            }
            catch(NumberFormatException e)
            {
                throw new SoarException("'" + args[1] + "' is not a valid integer");
            }
            catch(IllegalArgumentException e)
            {
                throw new SoarException(e.getMessage());
            }
        }
        return "";
    }
}