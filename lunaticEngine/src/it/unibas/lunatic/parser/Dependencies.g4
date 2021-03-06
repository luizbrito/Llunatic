grammar Dependencies;

@lexer::header {
package it.unibas.lunatic.parser.output;
}

@parser::header {
package it.unibas.lunatic.parser.output;

import it.unibas.lunatic.LunaticConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unibas.lunatic.parser.operators.ParseDependencies;
import it.unibas.lunatic.model.dependency.*;
import speedy.model.database.AttributeRef;
import speedy.model.expressions.Expression;
import java.util.Stack;
}

@parser::members {
private static Logger logger = LoggerFactory.getLogger(DependenciesParser.class);

private ParseDependencies generator = new ParseDependencies();

private Stack<IFormula> formulaStack = new Stack<IFormula>();

private Dependency dependency;
private DED ded;
private IFormula dedPremise;
private IFormula formulaWN;
private PositiveFormula positiveFormula;
private IFormulaAtom atom;
private FormulaAttribute attribute;
private StringBuilder expressionString;
private String leftConstant;
private String rightConstant;
private int counter;

public void setGenerator(ParseDependencies generator) {
      this.generator = generator;
}

}
@lexer::members {

public void emitErrorMessage(String msg) {
	throw new it.unibas.lunatic.exceptions.ParserException(msg);
}
}

prog: dependencies {  }  ;

dependencies:    
	         ('STTGDs:' sttgd+ { counter = 0;} |
	          'DED-STTGDs:' dedstgd+ {counter = 0;} )?
	         ('ExtTGDs:' etgd+ { counter = 0;} |
	          'DED-ExtTGDs:' dedetgd+ {counter = 0;} )?
	         ('EGDs:' egd+ { counter = 0;} |
	          'DED-EGDs:' dedegd+ {counter = 0;} | 
	          'ExtEGDs:' eegd+ { counter = 0;} )?
	         ('DCs:' dc+ { counter = 0;} )?;

sttgd:	 	 dependency {  dependency.setType(LunaticConstants.STTGD); dependency.setId("m" + counter++); generator.addSTTGD(dependency); } ;

etgd:	 	 dependency {  dependency.setType(LunaticConstants.ExtTGD); dependency.setId("t" + counter++); generator.addExtTGD(dependency); } ;

dc:	 	 dependency {  dependency.setType(LunaticConstants.DC); dependency.setId("d" + counter++); generator.addDC(dependency); } ;

egd:	 	 dependency {  dependency.setType(LunaticConstants.EGD); dependency.setId("e" + counter++); generator.addEGD(dependency); } ;
		    
eegd:	 	 dependency {  dependency.setType(LunaticConstants.ExtEGD); dependency.setId("e" + counter++); generator.addExtEGD(dependency); } ;
		    
dedstgd:	 ded {  ded.setType(LunaticConstants.STTGD); ded.setId("ded_m" + counter++); generator.addDEDSTTGD(ded); } ;		    
		    
dedetgd:	 ded {  ded.setType(LunaticConstants.ExtTGD); ded.setId("ded_t" + counter++); generator.addDEDExtTGD(ded); } ;		    

dedegd:	         ded {  ded.setType(LunaticConstants.EGD); ded.setId("ded_e" + counter++); generator.addDEDExtEGD(ded); } ;		    

dependency:	 (id = IDENTIFIER':')? {  dependency = new Dependency(); 
                    formulaWN = new FormulaWithNegations(); 
                    formulaStack.push(formulaWN);
                    dependency.setPremise(formulaWN);
                    if(((DependencyContext)_localctx).id!=null) dependency.setId(((DependencyContext)_localctx).id.getText()); }
		 positiveFormula  ( negatedFormula   )* '->' 
		 ('#fail' 
		 {  formulaStack.clear(); 
                    dependency.setConclusion(NullFormula.getInstance());}
		 |
		 {  formulaStack.clear(); } 
                  conclusionFormula) '.' ;
		    
ded:	         {  ded = new DED(); 
                    formulaWN = new FormulaWithNegations(); 
                    formulaStack.push(formulaWN);
                    dedPremise = formulaWN;}
		 positiveFormula  ( negatedFormula   )* '->' 
		 dedConclusion ('|' dedConclusion)* '.';

dedConclusion:    '[' { formulaStack.clear(); 
                        dependency = new Dependency();
                        ded.addAssociatedDependency(dependency);                        
                        dependency.setPremise(dedPremise.clone());
			positiveFormula = new PositiveFormula(); 
                        dependency.setConclusion(positiveFormula); }
                  atom (',' atom )* ']';	  
                  		    
positiveFormula: {  positiveFormula = new PositiveFormula(); 
                    positiveFormula.setFather(formulaStack.peek()); 
                    formulaStack.peek().setPositiveFormula(positiveFormula); }
                  relationalAtom (',' atom )* ;

negatedFormula:  {  formulaWN = new FormulaWithNegations(); 
		    formulaWN.setFather(formulaStack.peek());
		    formulaStack.peek().addNegatedFormula(formulaWN);
                    formulaStack.push(formulaWN); }
                 'and not exists''(' ( positiveFormula ( negatedFormula )* ) ')'
                 {  formulaStack.pop(); };

conclusionFormula: {  positiveFormula = new PositiveFormula(); 
                      dependency.setConclusion(positiveFormula); }
                  atom (',' atom )* ;

atom	:	 relationalAtom | builtin | comparison;	

relationalAtom:	 name=IDENTIFIER { atom = new RelationalAtom(((RelationalAtomContext)_localctx).name.getText()); } '(' attribute (',' attribute)* ')'
		 {  positiveFormula.addAtom(atom); atom.setFormula(positiveFormula); };

builtin	:	 expression=EXPRESSION  
                 {  atom = new BuiltInAtom(positiveFormula, new Expression(generator.clean(((BuiltinContext)_localctx).expression.getText()))); 
                    positiveFormula.addAtom(atom);  } ;         

comparison :	 {   expressionString = new StringBuilder(); 
		     leftConstant = null;
		     rightConstant = null;}
                 leftargument 
                 oper=OPERATOR { expressionString.append(" ").append(((ComparisonContext)_localctx).oper.getText()); }
                 rightargument 
                 {  Expression expression = new Expression(expressionString.toString()); 
                    atom = new ComparisonAtom(positiveFormula, expression, leftConstant, rightConstant, ((ComparisonContext)_localctx).oper.getText()); 
                    positiveFormula.addAtom(atom); } ;

leftargument:	 ('$'var=IDENTIFIER { expressionString.append(((LeftargumentContext)_localctx).var.getText()); } |
                 constant=(STRING | NUMBER) { expressionString.append(((LeftargumentContext)_localctx).constant.getText()); leftConstant = ((LeftargumentContext)_localctx).constant.getText();}
                 );
                 
rightargument:	 ('$'var=IDENTIFIER { expressionString.append(((RightargumentContext)_localctx).var.getText()); } |
                 constant=(STRING | NUMBER) { expressionString.append(((RightargumentContext)_localctx).constant.getText()); rightConstant = ((RightargumentContext)_localctx).constant.getText();}
                 );

attribute:	 attr=IDENTIFIER ':' { attribute = new FormulaAttribute(((AttributeContext)_localctx).attr.getText()); } value
		 { ((RelationalAtom)atom).addAttribute(attribute); } ;

value	:	 '$'var=IDENTIFIER { attribute.setValue(new FormulaVariableOccurrence(new AttributeRef(((RelationalAtom)atom).getTableName(), attribute.getAttributeName()), ((ValueContext)_localctx).var.getText())); } |
                 constant=(STRING | NUMBER) { attribute.setValue(new FormulaConstant(((ValueContext)_localctx).constant.getText())); } |
                 nullValue=NULL { attribute.setValue(new FormulaConstant(((ValueContext)_localctx).nullValue.getText(), true)); } |
                 expression=EXPRESSION { attribute.setValue(new FormulaExpression(new Expression(generator.clean(((ValueContext)_localctx).expression.getText())))); };

OPERATOR:	 '==' | '!=' | '>' | '<' | '>=' | '<=';

IDENTIFIER  :    (LETTER) (LETTER | DIGIT | '_' | '-' )*;

//STRING  :  	 '"' (LETTER | DIGIT| '-' | '.' | ' ' | '_' | '*' | '/' )+ '"';
STRING  :         '"' ~('\r' | '\n' | '"')* '"';
NUMBER	: 	 ('-')? DIGIT+ ('.' DIGIT+)?;
NULL    :        '#NULL#';
fragment DIGIT:  '0'..'9' ;
fragment LETTER: 'a'..'z'|'A'..'Z' ;
WHITESPACE : 	 ( '\t' | ' ' | '\r' | '\n'| '\u000C' )+ { skip(); } ;
LINE_COMMENT :   '//' ~( '\r' | '\n' )* { skip(); } ;
EXPRESSION:      '{'(.)*?'}';