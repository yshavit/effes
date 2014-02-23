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

// type declr

typeDeclr: TYPE TYPE_NAME genericsDeclr? typeDef;

typeDef: COLON typeDeclrBody
       | EQ disjunctiveType NL
       ;

typeDeclrBody: INDENT typeDeclrElement+ DEDENT;

typeDeclrElement: methodDeclr                                                   # TypeMethodDeclr
                | PATTERN ARROW tupleType COLON methodDef                       # TypePatternDeclr
                | CREATE methodDef                                              # TypeCreateDeclr
                ;

methodDeclr: funcModifiers?
             methodName
             methodArgs
             (methodReturnDeclr)?
             (NL | COLON methodDef)
           ;

funcModifiers: OPEN_BRACKET VAR_NAME+ CLOSE_BRACKET;

methodName: VAR_NAME
          | CMP_OPS
          | MULT_OPS
          | ADD_OPS
          ;

methodArgs: ( methodArg (COLON methodArg (COMMA methodArg)*)? )?;

methodArg: VAR_NAME disjunctiveType?;

methodReturnDeclr: ARROW disjunctiveType;

methodDef: block; // TODO expr directly, without the "return"?

// data type declr

dataTypeDeclr: DATA TYPE
               TYPE_NAME genericsDeclr?
               dataTypeArgs?
             ;

dataTypeArgs: OPEN_PAREN dataTypeArg (COMMA dataTypeArg)* CLOSE_PAREN;

dataTypeArg: VAR_NAME COLON disjunctiveType;

// generics and types

genericsDeclr: OPEN_BRACKET genericDeclr (COMMA genericDeclr)* CLOSE_BRACKET;

genericDeclr: TYPE_NAME                                                         # ConcreteGeneric
            | GENERIC_NAME genericParamRestriction?                             # NamedGeneric
            ;

genericParamRestriction: COLON disjunctiveType;

disjunctiveType: atomicType (PIPE atomicType)*;

atomicType: GENERIC_NAME genericParamRestriction?                               # GenericAtom
          | TYPE_NAME genericsDeclr?                                            # ConcreteAtom
          | tupleType                                                           # TupleAtom
          ;

tupleType: OPEN_PAREN atomicType (COMMA atomicType)* CLOSE_PAREN;

// blocks and statements

block: stat
     | INDENT stat+ DEDENT
     ;

stat: ifStatFragment elseIfStatFragment* elseStatFragment?                      # IfElseStat
    | CASE expr OF INDENT caseStatPattern+ DEDENT                               # CaseStat
    | VAR_NAME EQ expr NL                                                       # AssignStat
    | RETURN expr NL                                                            # ReturnStat
    ;

// A note on if-else: Unlike C/Java, an if-else's bodies are blocks, not
// statements; a block isn't a statement. So we have to treat "else if" as a
// special section rather than just an "else" followed by a stat.
ifStatFragment: IF expr block;
elseIfStatFragment: ELSE IF expr block;
elseStatFragment: ELSE block;

// expressions

expr: OPEN_PAREN expr CLOSE_PAREN                                               # ParenExpr
    | expr AT disjunctiveType                                                   # ComponentCastExpr
    | expr MULT_OPS expr                                                        # MultOrDivExpr
    | expr ADD_OPS expr                                                         # AddOrSubExpr
    | ADD_OPS expr                                                              # UnaryExpr
    | DOLLAR <assoc=right> expr                                                 # DollarExpr
    | expr DUBSLASH                                                             # PipeExpr
    | INT                                                                       # IntLiteral
    | DECIMAL                                                                   # DecimalLiteral
    | OPEN_PAREN expr (COMMA expr)+ CLOSE_PAREN                                 # TupleExpr
    | IF expr THEN expr ELSE expr                                               # IfExpr
    | CASE expr OF caseExprs                                                    # CaseOfExpr
    | VAR_NAME                                                                  # VarExpr
    | TYPE_NAME ctorInvokeArgs?                                                 # CtorInvoke
    | expr methodName (expr (COLON expr (COMMA expr)*)?)?                       # MethodInvoke
    ;

/**
 * The expr rule is rewritten in such a way that it takes an int arg and needs
 * a surrounding context. For test purposes, it's convenient to have a variant
 * that can be called "plain".
 */
expr__ForTest__: expr;

ctorInvokeArgs: OPEN_PAREN methodInvokeArgs CLOSE_PAREN;

methodInvokeArgs:
                | expr (COMMA expr)*
                ;

caseExprs: INDENT caseExprPattern+ DEDENT;

caseStatPattern: caseMatcher COLON block;

caseExprPattern: caseMatcher COLON exprBlock;

caseMatcher: TYPE_NAME casePatternArgs?
           | casePatternArgs
           | UNDERSCORE
           ;


casePatternArgs: OPEN_PAREN casePatternArg (COMMA casePatternArg)* CLOSE_PAREN;

casePatternArg: VAR_NAME
              | UNDERSCORE
              ;

exprBlock: expr NL
         | expr WHERE INDENT stat+ DEDENT
         ;

// tokens

DATA: 'data';
TYPE: 'type';
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
CASE: 'case';
OF: 'of';
RETURN: 'return';
WHERE: 'where';
UNDERSCORE: '_';
DOLLAR: '$';
DUBSLASH: '\\\\';
AT: '@';

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
