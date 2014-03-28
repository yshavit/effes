grammar Effes;

tokens {INDENT, DEDENT }
@lexer::header {
  import com.yuvalshavit.antlr4.DenterHelper;
  import com.yuvalshavit.antlr4.DenterOptions;
}
@lexer::members {
  private final DenterHelper denter = new DenterHelper(NL, EffesParser.INDENT, EffesParser.DEDENT) {
    @Override
    public Token pullToken() {
      return EffesLexer.super.nextToken();
    }
  };

  @Override
  public Token nextToken() {
    return denter.nextToken();
  }
  
  public DenterOptions getDenterOptions() {
    return denter.getOptions();
  }
}

// top level

compilationUnit: NL
               | compilationBodyElement* EOF
               ;

compilationBodyElement: dataTypeDeclr
                      | DEF methodDeclr
                      ;
// methods

methodDeclr: methodName
             methodArgs
             methodReturnDeclr
             COLON inlinableBlock
           ;

methodName: VAR_NAME
          | CMP_OPS
          | MULT_OPS
          | ADD_OPS
          ;

methodArgs: ( methodArg (COLON methodArg (COMMA methodArg)*)? )?;

methodArg: VAR_NAME? type
         ;

methodReturnDeclr: ARROW type;

// data type declr

dataTypeDeclr: DATA TYPE
               TYPE_NAME
               NL
             ;

// generics and types

type: OPEN_PAREN TYPE_NAME CLOSE_PAREN                                          # ParenSingleType
    | OPEN_PAREN type (PIPE type)+ CLOSE_PAREN                                  # DisunctiveType
    | TYPE_NAME                                                                 # SimpleType
    ;

// blocks and statements

inlinableBlock: stat
              | INDENT stat+ DEDENT
              ;

stat: RETURN exprLine                                                           # ReturnStat
    | methodName methodInvokeArgs NL                                            # MethodInvoke
    | VAR_NAME EQ exprLine                                                      # AssignStat
    ;

exprLine: expr NL                                                               # SingleLineExpression
        | CASE expr OF caseExprs                                                # CaseExpression
        ;

// expressions

expr: OPEN_PAREN expr CLOSE_PAREN                                               # ParenExpr
    | VAR_NAME                                                                  # VarExpr
    | methodName methodInvokeArgs                                               # MethodInvokeExpr
    | TYPE_NAME                                                                 # CtorInvoke
    ;

methodInvokeArgs: (expr (COLON expr (COMMA expr)*)?)?;

caseExprs: INDENT caseExprPattern+ DEDENT;

caseExprPattern: caseMatcher COLON exprBlock;

caseMatcher: TYPE_NAME                                                          # SimpleCtorMatch
           ;

exprBlock: expr NL;

/**
 * The expr rule is rewritten in such a way that it takes an int arg and needs
 * a surrounding context. For test purposes, it's convenient to have a variant
 * that can be called "plain".
 */
expr__ForTest__: expr;

// tokens

DATA: 'data';
TYPE: 'type';
DEF: 'def';
COLON: ':';
EQ: '=';
ARROW: '->';
PATTERN: '@pattern';
CREATE: '@create';
OPEN_PAREN: '(';
CLOSE_PAREN: ')';
OPEN_BRACKET: '[';
CLOSE_BRACKET: ']';
OPEN_BRACE: '{';
CLOSE_BRACE: '}';
COMMA: ',';
PIPE: '|';
IF: 'if';
THEN: 'then';
ELSE: 'else';
THIS: 'this';
CASE: 'case';
OF: 'of';
RETURN: 'return';
WHERE: 'where';
UNDERSCORE: '_';
DOLLAR: '$';
DUBSLASH: '\\\\';
AT: '@';
LCOMPOSE: '</';

INT: '0' | [1-9] [0-9]*;
DECIMAL: INT '.' [0-9]+ DECIMAL_EXPONENT?
       | INT DECIMAL_EXPONENT;
fragment DECIMAL_EXPONENT: ([eE] ('-'|'+')? [1-9][0-9]*)?;
TYPE_NAME: [A-Z]+ [A-Z0-9]* [a-z] [a-zA-Z0-9]*;
GENERIC_NAME: [A-Z]+ [A-Z0-9]*;
VAR_NAME: [a-z]+ [a-zA-Z0-9_]*;
ADD_OPS: '+' | '-';
CMP_OPS: '==' | '!=' | '<' | '<=' | '>' | '>=';
MULT_OPS: '*' | '/';

NL: ('\r'? '\n' ' '*) | EOF;
WS: [ \t]+ -> skip;
LINE_COMMENT: '--' ~[\r\n]* -> skip;
