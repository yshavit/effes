grammar Effes;

tokens { INDENT, DEDENT }
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

dataTypeDeclr: 'data type'
               TYPE_NAME generic?
               dataTypeArgs?
             ;

dataTypeArgs: '(' dataTypeArg (',' dataTypeArg)* ')';

dataTypeArg: VAR_NAME ':' disjunctiveType;

typeDeclr: 'type' TYPE_NAME generic? typeDeclrBody;

typeDeclrBody: '=' disjunctiveType
             | ':' INDENT 'todo' DEDENT;

disjunctiveType: atomicType ('|' atomicType)*;

atomicType: GENERIC_NAME
          | TYPE_NAME generic?
          ;

generic: '[' genericParam (',' genericParam)* ']';

genericParam: TYPE_NAME
            | GENERIC_NAME (':' genericParamRestriction)?
            ;

genericParamRestriction: disjunctiveType;

NL: ('\r'? '\n' ' '*) | EOF;
WS: [ \t]+ -> skip;
LINE_COMMENT: '--' ~[\r\n]* -> skip;

TYPE_NAME: [A-Z]+ [A-Z0-9]* [a-z] [a-zA-Z0-9]*;
GENERIC_NAME: [A-Z]+ [A-Z0-9]*;
VAR_NAME: [a-z]+ [a-z0-9_]*;
