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

typeDeclr: 'type' TYPE_NAME genericsDeclr? typeDef;

typeDef: ':' typeDeclrBody
       | '=' disjunctiveType
       ;

typeDeclrBody: typeDeclrElement+;

typeDeclrElement: methodDeclr
                | '@pattern' '->' disjunctiveType methodDef
                | '@create' methodDef
                ;

methodDeclr: funcModifiers?
             methodName
             methodReturnDeclr?
             methodDef
           ;

funcModifiers: '[' VAR_NAME+ ']';

methodName: VAR_NAME
          | CMP_OPS
          | MULT_OPS
          | ADD_OPS
          ;

methodReturnDeclr: '->' disjunctiveType;

methodDef: ':' exprBlock ;

// TODO ambiguity between if-else as an expr or stat, ditto with case

// data type declr

dataTypeDeclr: 'data type'
               TYPE_NAME genericsDeclr?
               dataTypeArgs?
             ;

dataTypeArgs: '(' dataTypeArg (',' dataTypeArg)* ')';

dataTypeArg: VAR_NAME ':' disjunctiveType;

// generics and types

genericsDeclr: '[' genericDeclr (',' genericDeclr)* ']';

genericDeclr: TYPE_NAME
            | GENERIC_NAME genericParamRestriction?
            ;

genericParamRestriction: ':' disjunctiveType;

disjunctiveType: atomicType ('|' atomicType)*;

atomicType: GENERIC_NAME genericParamRestriction? # GenericAtom
          | TYPE_NAME genericsDeclr?              # ConcreteAtom
          | '(' atomicType (',' atomicType)* ')'  # TupleAtom
          ;

// blocks and statements

block: INDENT stat+ DEDENT;

stat: ifStatFragment elseStatFragment* elseStatFragment? # IfElseStat
    | VAR_NAME '=' expr                                  # AssignStat
    ;

// A note on if-else: Unlike C/Java, an if-else's bodies are blocks, not
// statements; a block isn't a statement. So we have to treat "else if" as a
// special section rather than just an "else" followed by a stat.
ifStatFragment: 'if' expr block;
elseIfStatFragment: 'else' 'if' expr block;
elseStatFragment: 'else' block;

// expressions

expr: 'if' expr ifExprBody          # IfExpr
    | 'case' expr 'of' casePatterns # CaseOfExpr
    | INT                           # IntLiteral
    | VAR_NAME                      # VarExpr
    | TYPE_NAME methodInvokeArgs?   # CtorInvoke
    | VAR_NAME methodInvokeArgs     # MethodInvoke
    ;

methodInvokeArgs: '(' expr* ')';

ifExprBody: 'then' expr 'else' expr // TODO else if
          | INDENT 'then' exprBlock ('else' 'if' expr 'then' exprBlock)* 'else' exprBlock
          ;

exprBlock: expr
         | stat ? INDENT stat* expr DEDENT;

casePatterns: INDENT casePattern+ DEDENT;

casePattern: TYPE_NAME casePatternArgs? ':' (expr | block);

casePatternArgs: '(' casePatternArg ')';

casePatternArg: VAR_NAME
              | '_'
              ;

OPEN_PAREN: '(';
CLOSE_PAREN: ')';
COMMA: ',';

// tokens

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
