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

compilationBodyElement: typeDeclr
                      | dataTypeDeclr
                      ;

// type declr

typeDeclr: TYPE TYPE_NAME genericsDeclr? typeDef;

typeDef: COLON typeDeclrBody
       | EQ type NL
       ;

typeDeclrBody: INDENT typeDeclrElement+ DEDENT;

typeDeclrElement: methodDeclr                                                   # TypeMethodDeclr
                | PATTERN ARROW tupleType COLON methodDef                       # TypePatternDeclr
                | CREATE ctorDeclrArgs COLON methodDef                          # TypeCreateDeclr
                ;

ctorDeclrArgs: OPEN_PAREN methodArg (COMMA methodArg)* CLOSE_PAREN;

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

methodArg: VAR_NAME type?                                                       # NormalMethodArg
         | OPEN_PAREN methodArg (COMMA methodArg)* CLOSE_PAREN                  # MethodTupleArg
         ;

methodReturnDeclr: ARROW type;

methodDef: inlinableBlock; // TODO expr directly, without the "return"?

// data type declr

dataTypeDeclr: DATA TYPE
               TYPE_NAME genericsDeclr?
               dataTypeArgs?
               NL
             ;

dataTypeArgs: OPEN_PAREN dataTypeArg (COMMA dataTypeArg)* CLOSE_PAREN;

dataTypeArg: VAR_NAME type;

// generics and types

genericsDeclr: OPEN_BRACKET genericDeclr (COMMA genericDeclr)* CLOSE_BRACKET;

genericDeclr: TYPE_NAME                                                         # ConcreteGeneric
            | GENERIC_NAME genericParamRestriction?                             # NamedGeneric
            ;

genericParamRestriction: COLON type;

type: OPEN_PAREN type CLOSE_PAREN                                               # GroupedType
    | type type                                                                 # ConjunciveType
    | type PIPE type                                                            # DisjunctiveType
    | GENERIC_NAME genericParamRestriction?                                     # GenericAtom
    | TYPE_NAME genericsDeclr?                                                  # ConcreteAtom
    | tupleType                                                                 # TupleAtom
    ;

tupleType: OPEN_PAREN VAR_NAME? type (COMMA VAR_NAME? type)+ CLOSE_PAREN;

// blocks and statements

inlinableBlock: stat
              | INDENT stat+ DEDENT
              ;

block: INDENT stat+ DEDENT;

stat: ifStatFragment elseIfStatFragment* elseStatFragment?                      # IfElseStat
    | CASE expr OF INDENT caseStatPattern+ DEDENT                               # CaseStat
    | VAR_NAME type? EQ exprLine                                                # AssignStat
    | RETURN exprLine                                                           # ReturnStat
    ;

exprLine: expr NL
        | multilineExpr
        ;

// A note on if-else: Unlike C/Java, an if-else's bodies are blocks, not
// statements; a block isn't a statement. So we have to treat "else if" as a
// special section rather than just an "else" followed by a stat.
ifStatFragment: IF expr block;
elseIfStatFragment: ELSE IF expr block;
elseStatFragment: ELSE block;

// expressions

expr: OPEN_PAREN expr CLOSE_PAREN                                               # ParenExpr
    | expr AT type                                                              # ComponentCastExpr
    | expr MULT_OPS expr                                                        # MultOrDivExpr
    | expr ADD_OPS expr                                                         # AddOrSubExpr
    | ADD_OPS expr                                                              # UnaryExpr
    | DOLLAR <assoc=right> expr                                                 # DollarExpr
    | expr DUBSLASH                                                             # PipeExpr
    | INT                                                                       # IntLiteral
    | DECIMAL                                                                   # DecimalLiteral
    | OPEN_PAREN expr (COMMA expr)+ CLOSE_PAREN                                 # TupleExpr
    | IF cond=expr THEN expr ELSE expr                                          # IfExpr
    | VAR_NAME                                                                  # VarExpr
    | TYPE_NAME ctorInvokeArgs?                                                 # CtorInvoke
    | expr methodName (expr (COLON expr (COMMA expr)*)?)?                       # MethodInvoke
    | expr LCOMPOSE expr                                                        # LeftCompose
    ;

multilineExpr: CASE expr OF caseExprs                                           # CaseOfExpr
             ;

/**
 * The expr rule is rewritten in such a way that it takes an int arg and needs
 * a surrounding context. For test purposes, it's convenient to have a variant
 * that can be called "plain".
 */
expr__ForTest__: expr | multilineExpr;

type__ForTest__: type;

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
         | multilineExpr
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
