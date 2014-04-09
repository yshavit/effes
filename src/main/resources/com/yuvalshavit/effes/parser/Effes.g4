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

methodArg: type
         | OPEN_PAREN VAR_NAME COLON type CLOSE_PAREN
         ;

methodReturnDeclr: ARROW type
                 | ARROW OPEN_PAREN type CLOSE_PAREN
                 | // nothing
                 ;

// data type declr

dataTypeDeclr: DATA TYPE TYPE_NAME dataTypeArgsDeclr?
               (NL | COLON INDENT typeMember+ DEDENT );

dataTypeArgsDeclr: OPEN_PAREN
                   dataTypeArgDeclr (COMMA dataTypeArgDeclr)*
                   CLOSE_PAREN;

dataTypeArgDeclr: VAR_NAME COLON type;

typeMember: DEF methodDeclr                                                     # MethodMember
          ;

// generics and types

type: type (PIPE type)+                                                         # DisunctiveType
    | TYPE_NAME                                                                 # SimpleType
    ;

// blocks and statements

inlinableBlock: stat
              | INDENT stat+ DEDENT
              ;

stat: RETURN exprLine                                                           # ReturnStat
    | methodInvoke NL                                                           # MethodInvokeStat
    | VAR_NAME EQ exprLine                                                      # AssignStat
    | CASE expr OF INDENT caseStatAlternative+ DEDENT                           # CaseStat
    ;

exprLine: expr NL                                                               # SingleLineExpression
        | CASE expr OF INDENT caseAlternative+ DEDENT                           # CaseExpression
        ;

caseStatAlternative: casePattern COLON inlinableBlock;

// expressions

expr: OPEN_PAREN expr CLOSE_PAREN                                               # ParenExpr
    | methodInvoke                                                              # MethodInvokeOrVarExpr
    | expr COLON methodInvoke                                                   # InstanceMethodInvokeOrVarExpr
    | TYPE_NAME ( OPEN_PAREN expr (COMMA expr)* CLOSE_PAREN )?                  # CtorInvoke
    ;

methodInvoke: methodName methodInvokeArgs;

methodInvokeArgs: (expr (COLON expr (COMMA expr)*)?)?;

caseAlternative: casePattern COLON exprBlock;

casePattern: TYPE_NAME (OPEN_PAREN VAR_NAME (COMMA VAR_NAME)* CLOSE_PAREN)?;

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
