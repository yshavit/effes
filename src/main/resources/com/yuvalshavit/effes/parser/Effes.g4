grammar Effes;

tokens {INDENT, DEDENT }
@lexer::header {
  import com.yuvalshavit.antlr4.DenterHelper;
  import com.yuvalshavit.antlr4.DenterOptions;
}
@lexer::members {
  private final DenterHelper denter = DenterHelper.builder()
    .nl(NL)
    .indent(EffesParser.INDENT)
    .dedent(EffesParser.DEDENT)
    .pullToken(EffesLexer.super::nextToken);

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
                      | methodDeclr
                      | typeAliasDeclr
                      | openTypeDeclr
                      ;
// methods

methodDeclr: methodName
             //genericsDeclr
             methodArgs
             methodReturnDeclr
             ((COLON inlinableBlock) | NL)
           ;

methodName: VAR_NAME
          | CMP_OPS
          | MULT_OPS
          | ADD_OPS
          ;

methodArgs: ( methodArg (COMMA methodArg)* )?;

methodArg: type
         | OPEN_PAREN VAR_NAME COLON type CLOSE_PAREN
         ;

methodReturnDeclr: (ARROW type)?;

// type declrs

typeAliasDeclr: TYPE name=TYPE_NAME genericsDeclr EQ targets=type NL;

openTypeDeclr : TYPE name=TYPE_NAME QUESTION
                (NL | INDENT methodDeclr+ DEDENT );

dataTypeDeclr: TYPE TYPE_NAME genericsDeclr dataTypeArgsDeclr?
               (NL | COLON INDENT typeMember+ DEDENT );

dataTypeArgsDeclr: OPEN_PAREN
                   dataTypeArgDeclr (COMMA dataTypeArgDeclr)*
                   CLOSE_PAREN;

dataTypeArgDeclr: VAR_NAME COLON type;

typeMember: methodDeclr                                                         # MethodMember
          | IS TYPE_NAME singleTypeParameters NL                                # OpenTypeAlternative
          ;

genericsDeclr: (OPEN_BRACKET GENERIC_NAME (COMMA GENERIC_NAME)* CLOSE_BRACKET)?;

// generics and types

singleTypeParameters: (OPEN_BRACKET type (COMMA type)* CLOSE_BRACKET)?;

singleType: TYPE_NAME singleTypeParameters                                      # SingleDataType
          | GENERIC_NAME                                                        # SingleGenericType
          ;
          
type: singleType (PIPE singleType)*
    | OPEN_PAREN singleType (PIPE singleType)* CLOSE_PAREN;  // OPEN_PAREN type CLOSE_PAREN ?

// blocks and statements

inlinableBlock: stat
              | INDENT stat+ DEDENT
              ;

stat: RETURN exprLine                                                           # ReturnStat
    | methodInvoke NL                                                           # MethodInvokeStat
    | expr DOT methodInvoke NL                                                  # InstanceMethodInvokeStat
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
    | expr DOT methodInvoke                                                     # InstanceMethodInvokeOrVarExpr
    | TYPE_NAME singleTypeParameters ( OPEN_PAREN expr (COMMA expr)* CLOSE_PAREN )?  # CtorInvoke
    ;

methodInvoke: methodName singleTypeParameters methodInvokeArgs;

methodInvokeArgs: expr
                | OPEN_PAREN expr (COMMA expr)* CLOSE_PAREN
                | /* nothing */
                ;

caseAlternative: casePattern COLON exprBlock;

casePattern: TYPE_NAME (OPEN_PAREN VAR_NAME (COMMA VAR_NAME)* CLOSE_PAREN)?;

exprBlock: expr NL;

// tokens

TYPE: 'type';
COLON: ':';
DOT: '.';
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
IS: 'is';
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
QUESTION: '?';

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
