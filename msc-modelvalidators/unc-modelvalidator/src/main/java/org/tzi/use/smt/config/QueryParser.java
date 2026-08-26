package org.tzi.use.smt.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recursive-descent parser for the single {@code query} configuration value. */
public final class QueryParser {
  private final ConfigurationVocabulary vocabulary;
  private final List<Token> tokens;
  private final Set<String> referencedInvariants = new LinkedHashSet<>();
  private int index;
  private int othersPosition = -1;

  private QueryParser(String source, ConfigurationVocabulary vocabulary) {
    this.vocabulary = vocabulary;
    tokens = tokenize(source);
  }

  public static QueryExpr parse(String source, ConfigurationVocabulary vocabulary) {
    if (source == null || source.isBlank()) {
      throw new ConfigurationReadException("query at position 1: expected a query expression");
    }
    return new QueryParser(source, vocabulary).parseRoot();
  }

  private QueryExpr parseRoot() {
    ScenarioProfile profile = ScenarioProfile.EXISTS;
    boolean explicitProfile = false;
    if (peekWord("exists") || peekWord("cover") || peekWord("uniform")) {
      explicitProfile = true;
      profile = ScenarioProfile.valueOf(consume().text().toUpperCase(Locale.ROOT));
    }
    int expressionPosition = peek().position();
    QueryExpr expression = parseOr();
    expect(TokenKind.END, "end of query");
    if (othersPosition >= 0 && referencedInvariants.size() != 1) {
      throw error(othersPosition, "others requires exactly one target invariant");
    }
    if (explicitProfile && expression instanceof QueryExpr.InvariantIndependence) {
      throw error(
          expressionPosition, "invariant-independence cannot be combined with a scenario profile");
    }
    QueryExpr profiled = new QueryExpr.Profiled(profile, expression);
    return profiled.equals(new QueryExpr.Profiled(ScenarioProfile.EXISTS, new QueryExpr.Satisfy()))
        ? QueryExpr.SATISFY
        : profiled;
  }

  private QueryExpr parseOr() {
    QueryExpr result = parseAnd();
    while (matchWord("or")) result = new QueryExpr.Or(result, parseAnd());
    return result;
  }

  private QueryExpr parseAnd() {
    QueryExpr result = parseUnary();
    while (matchWord("and")) result = new QueryExpr.And(result, parseUnary());
    return result;
  }

  private QueryExpr parseUnary() {
    if (matchWord("not")) return new QueryExpr.Not(parseUnary());
    if (match(TokenKind.LEFT_PAREN)) {
      QueryExpr nested = parseOr();
      expect(TokenKind.RIGHT_PAREN, "')'");
      return nested;
    }
    return parseAtom();
  }

  private QueryExpr parseAtom() {
    Token first = expect(TokenKind.WORD, "an atom, aggregate, or macro");
    return switch (first.lower()) {
      case "satisfy" -> new QueryExpr.Satisfy();
      case "invariant-independence" -> new QueryExpr.InvariantIndependence();
      case "counterexample" -> new QueryExpr.Counterexample(parseMacroTarget());
      case "fragile" -> new QueryExpr.Fragile(parseMacroTarget());
      case "true", "false", "undef" -> parseFunctionalClassification(first);
      case "nominal", "uncertain" -> parseNaturalClassification(first);
      default ->
          throw error(
              first.position(),
              "expected an atom, aggregate, or macro but found '" + first.text() + "'");
    };
  }

  private QueryExpr parseFunctionalClassification(Token outcomeToken) {
    expect(TokenKind.LEFT_PAREN, "'('");
    TranslationMode mode = mode(expect(TokenKind.WORD, "nominal or uncertain"));
    expect(TokenKind.COMMA, "','");
    String invariant = resolveInvariant(expect(TokenKind.WORD, "an invariant reference"));
    expect(TokenKind.RIGHT_PAREN, "')'");
    InvariantOutcome outcome =
        switch (outcomeToken.lower()) {
          case "true" -> InvariantOutcome.TRUE;
          case "false" -> InvariantOutcome.FALSE;
          default -> InvariantOutcome.UNDEFINED;
        };
    return new QueryExpr.Classification(mode, invariant, outcome);
  }

  private QueryExpr parseNaturalClassification(Token modeToken) {
    TranslationMode mode = mode(modeToken);
    Token subject = expect(TokenKind.WORD, "an invariant reference, 'all', or 'others'");
    if (subject.lower().equals("all") || subject.lower().equals("others")) {
      if (subject.lower().equals("others")) othersPosition = subject.position();
      expectWord("are");
      expectWord("true");
      return new QueryExpr.Aggregate(
          mode,
          subject.lower().equals("all")
              ? QueryExpr.AggregateScope.ALL
              : QueryExpr.AggregateScope.OTHERS);
    }
    String invariant = resolveInvariant(subject);
    expectWord("is");
    Token outcomeToken = expect(TokenKind.WORD, "true, false, or undefined");
    InvariantOutcome outcome =
        switch (outcomeToken.lower()) {
          case "true" -> InvariantOutcome.TRUE;
          case "false" -> InvariantOutcome.FALSE;
          case "undefined", "undef" -> InvariantOutcome.UNDEFINED;
          default ->
              throw error(
                  outcomeToken.position(),
                  "expected true, false, or undefined but found '" + outcomeToken.text() + "'");
        };
    return new QueryExpr.Classification(mode, invariant, outcome);
  }

  private String parseMacroTarget() {
    expect(TokenKind.LEFT_PAREN, "'('");
    String invariant = resolveInvariant(expect(TokenKind.WORD, "one target invariant"));
    if (peek().kind() == TokenKind.COMMA) {
      throw error(peek().position(), "expected ')' after one target invariant");
    }
    expect(TokenKind.RIGHT_PAREN, "')'");
    return invariant;
  }

  private TranslationMode mode(Token token) {
    return switch (token.lower()) {
      case "nominal" -> TranslationMode.NOMINAL;
      case "uncertain" -> TranslationMode.UNCERTAIN;
      default ->
          throw error(
              token.position(), "expected nominal or uncertain but found '" + token.text() + "'");
    };
  }

  private String resolveInvariant(Token token) {
    List<String> matches = new ArrayList<>();
    for (String configuredName : vocabulary.invariantNames()) {
      String qualified = configuredName.replaceFirst("_", "::");
      String simple = qualified.substring(qualified.indexOf("::") + 2);
      if (token.text().equals(configuredName)
          || token.text().equals(qualified)
          || token.text().equals(simple)) {
        matches.add(qualified);
      }
    }
    if (matches.isEmpty())
      throw error(token.position(), "unknown invariant '" + token.text() + "'");
    if (matches.size() > 1) {
      throw error(
          token.position(), "ambiguous invariant '" + token.text() + "'; use Class::Invariant");
    }
    referencedInvariants.add(matches.getFirst());
    return matches.getFirst();
  }

  private boolean peekWord(String expected) {
    return peek().kind() == TokenKind.WORD && peek().lower().equals(expected);
  }

  private boolean matchWord(String expected) {
    if (!peekWord(expected)) return false;
    consume();
    return true;
  }

  private void expectWord(String expected) {
    Token token = expect(TokenKind.WORD, "'" + expected + "'");
    if (!token.lower().equals(expected)) {
      throw error(token.position(), "expected '" + expected + "' but found '" + token.text() + "'");
    }
  }

  private boolean match(TokenKind kind) {
    if (peek().kind() != kind) return false;
    consume();
    return true;
  }

  private Token expect(TokenKind kind, String expected) {
    Token token = peek();
    if (token.kind() != kind) {
      throw error(token.position(), "expected " + expected + " but found '" + token.text() + "'");
    }
    return consume();
  }

  private Token peek() {
    return tokens.get(index);
  }

  private Token consume() {
    return tokens.get(index++);
  }

  private static ConfigurationReadException error(int position, String message) {
    return new ConfigurationReadException("query at position " + position + ": " + message);
  }

  private static List<Token> tokenize(String source) {
    List<Token> result = new ArrayList<>();
    int offset = 0;
    while (offset < source.length()) {
      char c = source.charAt(offset);
      if (Character.isWhitespace(c)) {
        offset++;
      } else if (c == '(' || c == ')' || c == ',') {
        TokenKind kind =
            c == '(' ? TokenKind.LEFT_PAREN : c == ')' ? TokenKind.RIGHT_PAREN : TokenKind.COMMA;
        result.add(new Token(kind, Character.toString(c), offset++ + 1));
      } else {
        int start = offset;
        while (offset < source.length()) {
          char current = source.charAt(offset);
          if (Character.isWhitespace(current)
              || current == '('
              || current == ')'
              || current == ',') {
            break;
          }
          offset++;
        }
        result.add(new Token(TokenKind.WORD, source.substring(start, offset), start + 1));
      }
    }
    result.add(new Token(TokenKind.END, "<end>", source.length() + 1));
    return List.copyOf(result);
  }

  private enum TokenKind {
    WORD,
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    END
  }

  private record Token(TokenKind kind, String text, int position) {
    String lower() {
      return text.toLowerCase(Locale.ROOT);
    }
  }
}
