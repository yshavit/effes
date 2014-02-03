package com.yuvalshavit.effes.parser;

import com.google.common.base.Supplier;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

public final class DenterHelper {
  private final Queue<Token> dentsBuffer = new ArrayDeque<>();
  private final Deque<Integer> indentations = new ArrayDeque<>();
  private final Supplier<Token> tokens;
  private final int nlToken;
  private final int indentToken;
  private final int dedentToken;
  private Token nextNonNL;

  public DenterHelper(Supplier<Token> tokens, int nlToken, int indentToken, int dedentToken) {
    this.tokens = tokens;
    this.nlToken = nlToken;
    this.indentToken = indentToken;
    this.dedentToken = dedentToken;
  }

  public Token nextToken() {
    Token t;
    if (indentations.isEmpty()) {
      // look for the first non-NL
      do {
        t = tokens.get();
      }
      while(t.getType() == nlToken);
      indentations.push(0);
      nextNonNL = t;
      if (t.getCharPositionInLine() > 0) {
        indentations.push(t.getCharPositionInLine());
        dentsBuffer.add(createToken(indentToken, t));
      }
    }
    if (!dentsBuffer.isEmpty()) {
      t = dentsBuffer.remove();
    } else if (nextNonNL != null) {
      t = nextNonNL;
      nextNonNL = null;
    } else {
      t = tokens.get();
    }
    final Token r;
    if (t.getType() == nlToken) {
      // fast-forward to the next non-NL
      Token nextNext = tokens.get();
      while (nextNext.getType() == nlToken) {
        t = nextNext;
        nextNext = tokens.get();
      }
      // nextNext is now a non-NL token; queue it up for the next call to this method
      if (nextNext.getType() == Token.EOF) {
        return unwindToEof(nextNext);
      }
      nextNonNL = nextNext;

      String nlText = t.getText();
      int indent = nlText.length() - 1; // every NL has one \n char, so shorten the length to account for it
      if (indent > 0 && nlText.charAt(0) == '\r') {
        --indent; // If the NL also has a \r char, we should account for that as well
      }
      int prevIndent = indentations.peek();
      if (indent == prevIndent) {
        r = t; // just a newline
      } else if (indent > prevIndent) {
        indentations.push(indent);
        r = createToken(indentToken, t);
      } else {
        r = unwindTo(indent, t);
      }
    } else if (t.getType() == Token.EOF && indentations.size() > 1) {
      r = unwindToEof(t);
    } else {
      r = t;
    }
    return r;
  }

  private Token createToken(int tokenType, Token copyFrom) {
    CommonToken r = new CommonToken(copyFrom);
    r.setType(tokenType);
    return r;
  }

  /**
   * Returns a DEDENT token, and also queues up additional DEDENTS as necessary.
   * @param targetIndent the "size" of the indentation (number of spaces) by the end
   * @param copyFrom the triggering token
   * @return a DEDENT token
   */
  private Token unwindTo(int targetIndent, Token copyFrom) {
    assert dentsBuffer.isEmpty() : dentsBuffer;
    // To make things easier, we'll queue up ALL of the dedents, and then pop off the first one.
    // For example, here's how some text is analyzed:
    //
    //  Text          :  Indentation  :  Action         : Indents Deque
    //  [ baseline ]  :  0            :  nothing        : [0]
    //  [ --foo    ]  :  2            :  INDENT         : [0, 2]
    //  [ ---bar   ]  :  3            :  INDENT         : [0, 2, 3]
    //  [ baz      ]  :  0            :  DEDENT x2      : [0]
    //  [ --again  ]  :  2            :  INDENT         : [0, 1]
    //  [ -weird   ]  :  1            :  DEDENT,INDENT  : [0, 1]
    //
    // This method is only interested in the DEDENT actions, although it may also enqueue an INDENT as seen above.
    // (This will probably cause a parse error, but that's not our concern!)

    while (true) {
      int prevIndent = indentations.pop();
      if (prevIndent == targetIndent) {
        break;
      }
      if (targetIndent > prevIndent) {
        // "weird" condition above
        indentations.push(prevIndent); // restore previous indentation, since we've indented from it
        dentsBuffer.add(createToken(indentToken, copyFrom));
        break;
      }
      dentsBuffer.add(createToken(dedentToken, copyFrom));
    }
    indentations.push(targetIndent);
    return dentsBuffer.remove();
  }

  private Token unwindToEof(Token copyFrom) {
    assert copyFrom.getType() == Token.EOF : copyFrom;
    Token token = unwindTo(0, copyFrom);
    dentsBuffer.add(copyFrom);
    return token;
  }
}
