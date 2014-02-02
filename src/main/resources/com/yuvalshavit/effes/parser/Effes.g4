grammar Effes;

dataTypeDeclr: 'data type'
               TYPE_NAME generic?
               dataTypeArgs?
             ;



dataTypeArgs: '(' dataTypeArg (',' dataTypeArg)* ')';

dataTypeArg: VAR_NAME ':' disjunctiveType;

typeDeclr: 'type' TYPE_NAME generic? typeDeclrBody;

typeDeclrBody: '=' disjunctiveType;

disjunctiveType: atomicType ('|' atomicType)*;

atomicType: GENERIC_NAME
          | TYPE_NAME generic?
          ;

generic: '[' genericParam (',' genericParam)* ']';

genericParam: TYPE_NAME
            | GENERIC_NAME (':' genericParamRestriction)?
            ;

genericParamRestriction: disjunctiveType;

INDENT: '{';
OUTDENT: '}';
NL: ('\r'? '\n') | EOF;
WS: [ \t]+ -> skip;
LINE_COMMENT: '--' .*? NL;

TYPE_NAME: [A-Z]+ [A-Z0-9]* [a-z] [a-zA-Z0-9]*;
GENERIC_NAME: [A-Z]+ [A-Z0-9]*;
VAR_NAME: [a-z]+ [a-z0-9_]*;
