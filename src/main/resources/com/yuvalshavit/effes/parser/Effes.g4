grammar Effes;

dataType: 'data type'
          TYPE_NAME
          dataTypeArgs?
        ;

dataTypeArgs: '(' dataTypeArg (',' dataTypeArg)* ')';

dataTypeArg: VAR_NAME COLON disjunctiveType;

disjunctiveType: atomicType ('|' atomicType)*;

atomicType: TYPE_NAME generic?;

generic: '[' GENERIC_NAME (':' disjunctiveType)? ']';

INDENT: '{';
OUTDENT: '}';
NL: '\r'? '\n';
WS: [ \t]+ -> skip;
LINE_COMMENT: '--' .*? NL;

TYPE_NAME: [A-Z]+ [A-Z0-9]* [a-z] [a-zA-Z0-9]*;
GENERIC_NAME: [A-Z]+ [A-Z0-9]*;
VAR_NAME: [a-z]+ [a-z0-9_]*;
COLON: ':';