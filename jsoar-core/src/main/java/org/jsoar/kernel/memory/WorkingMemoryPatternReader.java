package org.jsoar.kernel.memory;

import java.io.IOException;
import java.io.StringReader;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.parser.original.Lexeme;
import org.jsoar.kernel.parser.original.LexemeType;
import org.jsoar.kernel.parser.original.Lexer;
import org.jsoar.kernel.symbols.Identifier;
import org.jsoar.kernel.symbols.SymbolFactory;
import org.jsoar.kernel.tracing.Printer;

import com.google.common.base.Predicate;

public class WorkingMemoryPatternReader
{

    public static Predicate<Wme> GetPredicate(Agent context, String pattern) throws IllegalArgumentException {
		final Printer printer = context.getPrinter();
		final StringReader reader = new StringReader(pattern);
		final SymbolFactory syms = context.getSymbols();
		
		Lexer lex = null;
		try {
			lex = new Lexer(printer, reader);
			lex.setAllowIds(true);
			lex.getNextLexeme();
        }
		catch(IOException e) {
		    e.printStackTrace(); // probably should throw a new exception (e.g., PatternReaderException)
		}
			
        Lexeme idlexeme = lex.getCurrentLexeme();
        Identifier id = null; // assume null (any value), and override as necessary
        if(idlexeme.type == LexemeType.IDENTIFIER)
        {
            id = syms.findIdentifier(idlexeme.id_letter, idlexeme.id_number);
            if(id == null)
            {
                throw new IllegalArgumentException("No such id " + idlexeme.id_letter + idlexeme.id_number);
            }
        }
        else if(!idlexeme.toString().equals("*"))
        {
            throw new IllegalArgumentException("First entry must be identifier or '*'");
        }
        try {
            lex.getNextLexeme();
        } catch(IOException e) {
            e.printStackTrace(); // probably should throw a new exception (e.g., PatternReaderException)
        }
        Lexeme attrlexeme = lex.getCurrentLexeme();
        Object attr = getPatternValue(syms, attrlexeme);
        
        try {
            lex.getNextLexeme();
        } catch(IOException e) {
            e.printStackTrace(); // probably should throw a new exception (e.g., PatternReaderException)
        }
        Lexeme valuelexeme = lex.getCurrentLexeme();
        Object value = getPatternValue(syms, valuelexeme);
		
        return Wmes.newMatcher(syms, id, attr, value, -1);
	}
    
    private static Object getPatternValue(SymbolFactory syms, Lexeme l) throws IllegalArgumentException
    {
        Object value = null; // assume null (any value), and override as necessary
        switch(l.type)
        {
        case IDENTIFIER:
            value = syms.findIdentifier(l.id_letter, l.id_number);
            if(value == null)
            {
                throw new IllegalArgumentException("No such id " + l.id_letter + l.id_number);
            }
            break;
        case SYM_CONSTANT:
            if(!l.toString().equals("*")) // * means any value, which is null
            {
                value = l.toString();
            }
            break;
        case FLOAT:
            value = l.float_val;
            break;
        case INTEGER:
            value = l.int_val;
            break;
        default:
            break;
        }
        
        return value;
    }
}
