grammar Effes;

tokens {INDENT, DEDENT }
@lexer::header {
  import com.yuvalshavit.antlr4.DenterHelper;
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
}

// type declr

typeDeclr: TYPE TYPE_NAME genericsDeclr? typeDef;

typeDef: COLON typeDeclrBody
       | EQ disjunctiveType
       ;

typeDeclrBody: typeDeclrElement+;

typeDeclrElement: methodDeclr
                | PATTERN ARROW disjunctiveType methodDef
                | CREATE methodDef
                ;

methodDeclr: funcModifiers?
             methodName
             methodReturnDeclr?
             methodDef
           ;

funcModifiers: OPEN_BRACE VAR_NAME+ CLOSE_BRACE;

methodName: VAR_NAME
          | CMP_OPS
          | MULT_OPS
          | ADD_OPS
          ;

methodReturnDeclr: ARROW disjunctiveType;

methodDef: COLON exprBlock ;

// TODO ambiguity between if-else as an expr or stat, ditto with case

// data type declr

dataTypeDeclr: DATA TYPE
               TYPE_NAME genericsDeclr?
               dataTypeArgs?
             ;

dataTypeArgs: OPEN_PAREN dataTypeArg (COMMA dataTypeArg)* CLOSE_PAREN;

dataTypeArg: VAR_NAME COLON disjunctiveType;

// generics and types

genericsDeclr: OPEN_BRACE genericDeclr (COMMA genericDeclr)* CLOSE_BRACE;

genericDeclr: TYPE_NAME
            | GENERIC_NAME genericParamRestriction?
            ;

genericParamRestriction: COLON disjunctiveType;

disjunctiveType: atomicType ('|' atomicType)*;

atomicType: GENERIC_NAME genericParamRestriction? # GenericAtom
          | TYPE_NAME genericsDeclr?              # ConcreteAtom
          | OPEN_PAREN atomicType (COMMA atomicType)* CLOSE_PAREN  # TupleAtom
          ;

// blocks and statements

block: INDENT stat+ DEDENT;

stat: ifStatFragment elseStatFragment* elseStatFragment? # IfElseStat
    | VAR_NAME EQ expr                                  # AssignStat
    ;

// A note on if-else: Unlike C/Java, an if-else's bodies are blocks, not
// statements; a block isn't a statement. So we have to treat "else if" as a
// special section rather than just an "else" followed by a stat.
ifStatFragment: IF expr block;
elseIfStatFragment: ELSE IF expr block;
elseStatFragment: ELSE block;

// expressions

expr: IF expr ifExprBody          # IfExpr
    | CASE expr OF casePatterns # CaseOfExpr
    | INT                           # IntLiteral
    | VAR_NAME                      # VarExpr
    | TYPE_NAME methodInvokeArgs?   # CtorInvoke
    | VAR_NAME methodInvokeArgs     # MethodInvoke
    ;

methodInvokeArgs: OPEN_PAREN expr* CLOSE_PAREN;

ifExprBody: THEN expr ELSE expr // TODO else if
          | INDENT THEN exprBlock (ELSE IF expr THEN exprBlock)* ELSE exprBlock
          ;

exprBlock: expr
         | stat ? INDENT stat* expr DEDENT;

casePatterns: INDENT casePattern+ DEDENT;

casePattern: TYPE_NAME casePatternArgs? COLON (expr | block);

casePatternArgs: OPEN_PAREN casePatternArg (COMMA casePatternArg)* CLOSE_PAREN;

casePatternArg: VAR_NAME
              | UNDERSCORE
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
OPEN_BRACE: '[';
CLOSE_BRACE: ']';
COMMA: ',';
PIPE: '|';
IF: 'if';
THEN: 'then';
ELSE: 'else';
CASE: 'case';
OF: 'of';
UNDERSCORE: '_';

TYPE_NAME: [A-Z]+ [A-Z0-9]* [a-z] [a-zA-Z0-9]*;
GENERIC_NAME: [A-Z]+ [A-Z0-9]*;
VAR_NAME: [a-z]+ [a-z0-9_]*;
CMP_OPS: '==' | '!=' | '<' | '<=' | '>' | '>=';
MULT_OPS: '*' | '/';
ADD_OPS: '+' | '-';
INT: '-'? [1-9] [0-9]*;

NL: ('\r'? '\n' ' '*) | EOF;
WS: [ \t]+ -> skip;
LINE_COMMENT: '--' ~[\r\n]* -> skip;
