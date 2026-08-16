# 13 — Grammar and Parser

Port specification, uncertainty extension. Scope: ANTLR 3 grammar fragments and the parser-side
AST classes they instantiate.

Every claim below cites a file and line, a symbol, or the shell command that produced it.
Anything not established is marked `UNVERIFIABLE`.

## 13.0 Path correction (read this first)

The task brief points at `…/USE-Uncertainty/src/main/org/tzi/use/parser/base/OCLBase.gpart` for the
fork (correct) and implies a `parser/` sibling in 7.5.0. **In 7.5.0 the grammar fragments are not
under `parser/`.** They were moved to a resources tree:

| | path |
|---|---|
| fork (Ant, Java 1.7) | `/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/parser/base/OCLBase.gpart` |
| 7.5.0 (Maven) | `/home/xoruser/msc-4/use-msc2026/use-core/src/main/resources/grammars/base/OCLBase.gpart` |

`/home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/parser/base/` contains only
`BaseParser.java` and `ParserHelper.java` — no `.gpart`. Verified:

```
ls -la /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/parser/base/
find /home/xoruser/msc-4/use-msc2026/use-core/src -name "*.gpart"
```

**Both files differ in line endings.** The fork's `.gpart` files are CRLF, 7.5.0's are LF. A naive
`diff` reports every line as changed. All diffs in this document were produced after normalising:

```
sed 's/\r$//' <file> > <normalised>
```

Working copies used throughout:
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad/{up,fk}_OCLBase.txt`.

---

## 13.1 `OCLBase.gpart` — diff in behaviour, uncertainty additions only

### 13.1.1 Separating uncertainty from version drift

A raw normalised diff of fork vs 7.5.0 `OCLBase.gpart` shows more than uncertainty. The fork branched
from an older USE, so it *lacks* three 7.5.0 features. These are **not** uncertainty changes and must
**not** be carried into the port:

| 7.5.0 feature absent from the fork | 7.5.0 location |
|---|---|
| `modelQualifier` on operation calls (`M#op`) | `up_OCLBase.txt:367-369` (`operationExpression`) |
| `modelQualifier` on enum literals (`M#E::lit`) | `up_OCLBase.txt:484-485` (`literal`) |
| `modelQualifiedType ::= IDENT HASH IDENT` and its `type` alternative | `up_OCLBase.txt:600, 610, 670-677` |
| `'oclIsInState'` as an alias for `'oclInState'` | `up_OCLBase.txt:400-405` (`inStateExpression`) |

The port must **keep** all four and add uncertainty on top. Everything below is uncertainty-only.

### 13.1.2 The minimal edit — measured

I constructed the uncertainty-only patch by applying exactly the uncertainty hunks to the 7.5.0 file
and diffing. Reproduce:

```
cd /tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad
diff -u up_OCLBase.txt cand_OCLBase.txt > minimal.patch
awk '/^\+[^+]/{a++} /^-[^-]/{d++} END{print "added:",a+0," removed:",d+0}' minimal.patch
grep -c "^@@" minimal.patch
```

Result:

| metric | value |
|---|---|
| hunks | 8 |
| lines added | 35 |
| lines removed | 8 |
| net line delta | +30 (678 → 708 lines) |
| of the 35 added, comment-only | 4 (2 rewordings at hunks 1–2, `identicalExpression` header ×3, `\| uncertaintyType` ×1) |
| **executable grammar lines added** | **31** |
| new parser rules | 2 (`identicalExpression`, `uncertaintyType`) |
| existing rules altered | 4 (`expression`, `queryExpression`, `literal`, `type`) |

**Headline: the minimal edit to `OCLBase.gpart` is 8 hunks / +35 −8 lines, of which 31 added lines
are executable grammar.**

### 13.1.3 Change A — new rule `identicalExpression`, spliced into the precedence chain

7.5.0 `expression` bottoms out directly in `conditionalImpliesExpression`
(`up_OCLBase.txt:74-76`). The fork inserts a new level *above* it.

7.5.0 (`up_OCLBase.txt:74-76`), inside `expression`:

```
    nCndImplies=conditionalImpliesExpression
    { if ( $nCndImplies.n != null ) {
    	 $n = $nCndImplies.n;
         $n.setStartToken(tok);
      }
```

Fork (`OCLBase.gpart:74-76`):

```
    nIdExp=identicalExpression
    { if ( $nIdExp.n != null ) {
    	 $n = $nIdExp.n;
         $n.setStartToken(tok);
      }
```

New rule, fork `OCLBase.gpart:124-135`, inserted immediately after `variableDeclaration` and before
the `conditionalImpliesExpression` comment block:

```
/* ------------------------------------
   identicalExpression ::=
     conditionalImpliesExpression { ".equals(" conditionalImpliesExpression ")" }
*/
identicalExpression returns [ASTExpression n]
:
    conImpExp=conditionalImpliesExpression {$n = $conImpExp.n; }
    (
        DOT op='equals' LPAREN n1=conditionalImpliesExpression RPAREN
        { $n = new ASTBinaryExpression($op, $n, $n1.n); }
    )*
    ;
```

Two comment lines are also reworded to match (`up_OCLBase.txt:27` and `:36`, both
`conditionalImpliesExpression` → `identicalExpression`). Cosmetic; no behavioural effect.

**Behavioural consequences.**

1. `'equals'` becomes an **implicit keyword token**. ANTLR 3 auto-creates a token type for the inline
   literal `'equals'` and the lexer then matches `equals` as that token, *never* as `IDENT`. Proof —
   the fork's checked-in generated parsers list it in `tokenNames`:
   `USE-Uncertainty/src/main/org/tzi/use/parser/ocl/OCLParser.java:40` contains `"'equals'"`, as do
   `parser/use/USEParser.java:46` and `parser/testsuite/TestSuiteParser.java:42`.
2. It is left-associative and **binds looser than `implies`** — `a implies b .equals( c )` parses as
   `(a implies b).equals(c)`, because both operands are `conditionalImpliesExpression`.
3. It binds **tighter than `let … in`**, since `let` is handled in `expression` above
   `identicalExpression`.
4. The right operand is a full `conditionalImpliesExpression`, so it cannot itself be a `let`.
5. The token `$op` is handed to `ASTBinaryExpression`, which resolves it by name. The resolving
   operation is fork-only: `class Op_identical` in
   `USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java:130-179`,
   whose `name()` returns `"equals"` (line 132), `kind()` is `SPECIAL`, `isInfixOrPrefix()` is
   `true`, and `matches` accepts any two types with a least common supertype, returning `Boolean`.
   7.5.0 has no operation named `equals` — verified:
   `grep -n '"equals"' use-core/src/main/java/org/tzi/use/uml/ocl/expr/operations/StandardOperationsAny.java`
   returns nothing.

**This change is not confined to OCL.** `OCLBase.gpart` is textually included into every composite
grammar. The fork's generated `.g` files show `identicalExpression` present in all six:
`parser/ocl/OCL.g:191`, `parser/use/USE.g:700`, `parser/soil/Soil.g:736`,
`parser/generator/Generator.g:967`, `parser/testsuite/TestSuite.g:298`,
`parser/shell/ShellCommand.g:489`. So `equals` becomes a reserved word in the `.use` model language,
SOIL, ASSL, the test-suite language and the shell — not just in expressions. See §13.5.

### 13.1.4 Change B — `queryExpression` gains an optional second argument

7.5.0 (`up_OCLBase.txt:324-331`):

```
queryExpression[ASTExpression range] returns [ASTExpression n]	
@init {ASTElemVarsDeclaration decl = new ASTElemVarsDeclaration(); }:
    op=IDENT 
    LPAREN 
    ( decls=elemVarsDeclaration {decl = $decls.n;} BAR )?
    nExp=expression
    RPAREN
    { $n = new ASTQueryExpression($op, $range, decl, $nExp.n); }
    ;
```

Fork (`OCLBase.gpart:337-349`):

```
queryExpression[ASTExpression range] returns [ASTExpression n]	
@init {
        ASTElemVarsDeclaration decl = new ASTElemVarsDeclaration();
        ASTExpression uncer = null;
    }:
    op=IDENT 
    LPAREN 
    ( decls=elemVarsDeclaration {decl = $decls.n;} BAR )?
    nExp=expression
    ( COMMA uncerExp=additiveExpression { uncer = $uncerExp.n;} )?
    RPAREN
    { $n = new ASTQueryExpression($op, $range, decl, $nExp.n, uncer); }
    ;
```

Net: `@init` 1 line → 4 lines (+3), one alternative line added, one action line changed.

**Behavioural consequences.**

- The `ASTQueryExpression` constructor gains a 5th parameter. Fork signature,
  `parser/ocl/ASTQueryExpression.java:49-53`:
  `ASTQueryExpression(Token op, ASTExpression range, ASTElemVarsDeclaration declList, ASTExpression expr, ASTExpression uncertainty)`.
  7.5.0 has the 4-arg form. This is a **breaking constructor change**; the port should prefer adding
  an overload so 7.5.0 call sites are untouched.
- `queryExpression` is reached only through the semantic predicate
  `{ ParserHelper.isQueryIdent(input.LT(1)) }?` — fork `OCLBase.gpart:322`, 7.5.0
  `up_OCLBase.txt:309`. So the new comma arm is *not* offered to arbitrary operation calls.
- **But it is offered to every query ident.** The grammar accepts `->collect(x | x.a, 5)` and
  `->forAll(x | p, 5)`. `ASTQueryExpression.gen` reads `fUncertainty` only in the
  `Q_USELECTC_ID` branch (`ASTQueryExpression.java:178-184`); for every other query id the extra
  argument is **parsed and silently discarded**. That is a silent-acceptance regression the port
  should close (reject a non-null uncertainty argument for any op other than `uSelectC`).
- The confidence argument is `additiveExpression`, not `expression`. It therefore cannot contain a
  relational or boolean operator: `->uSelectC(e | p, a > b)` is a **syntax error**. Chain confirmed
  at `up_OCLBase.txt:190` (`relationalExpression`) and `:204` (`additiveExpression`):
  `relationalExpression` sits *above* `additiveExpression`.

**Companion edit outside the grammar** — `ParserHelper.java`. Reproduce:

```
diff -u /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use/parser/base/ParserHelper.java \
        /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/parser/base/ParserHelper.java
```

Six added lines, three hunks:

```
+    final static String Q_USELECT  = "uSelect";
+    final static String Q_USELECTC  = "uSelectC";
+    public final static int Q_USELECT_ID  = 12;
+    public final static int Q_USELECTC_ID = 13;
+        queryIdentMap.put(Q_USELECT,        Integer.valueOf(Q_USELECT_ID));
+        queryIdentMap.put(Q_USELECTC,       Integer.valueOf(Q_USELECTC_ID));
```

Note `uSelect`/`uSelectC` are **`IDENT`s registered in a map**, not keyword tokens. They do not
enter the reserved-word set. This is a materially lower-risk mechanism than the one used for the
literals, and is worth noting as the pattern to prefer.

### 13.1.5 Change C — five new `literal` alternatives

Fork `OCLBase.gpart:488-501`, showing the surrounding alternatives for placement (inserted between
the `STRING` alternative and the `HASH enumLit=IDENT` alternative):

```
literal returns [ASTExpression n]
:
      t='true'   { $n = new ASTBooleanLiteral(true); }
    | f='false'  { $n = new ASTBooleanLiteral(false); }
    | i=INT    { $n = new ASTIntegerLiteral($i); }
    | r=REAL   { $n = new ASTRealLiteral($r); }
    | s=STRING { $n = new ASTStringLiteral($s); }
    | 'UString' LPAREN usve=additiveExpression COMMA usue=additiveExpression RPAREN { $n = new ASTUStringLiteral($usve.n,$usue.n); }
    | 'UReal' LPAREN urve=additiveExpression COMMA urue=additiveExpression RPAREN { $n = new ASTURealLiteral($urve.n,$urue.n); }
    | 'UBoolean' LPAREN ubve=conditionalImpliesExpression COMMA ubpe=additiveExpression RPAREN { $n = new ASTUBooleanLiteral($ubve.n, $ubpe.n); }
    | 'UInteger' LPAREN uive=additiveExpression COMMA uiue=additiveExpression RPAREN { $n = new ASTUIntegerLiteral($uive.n, $uiue.n); }
    | 'SBoolean' LPAREN ubve=additiveExpression COMMA udve=additiveExpression COMMA uuve=additiveExpression COMMA uave=additiveExpression RPAREN
       { $n = new ASTSBooleanLiteral($ubve.n, $udve.n, $uuve.n, $uave.n); }
    | HASH enumLit=IDENT { $n = new ASTEnumLiteral($enumLit);}
```

Six added lines (`SBoolean` spans two). Placement is not load-bearing for correctness — each
alternative starts with a distinct keyword token, so ANTLR decides on LA(1) — but keep the order for
diff hygiene.

**`SBoolean` is the fifth literal and the port plan must account for it.** The task brief names four.

### 13.1.6 Change D — `uncertaintyType`

Fork `OCLBase.gpart:617` (comment), `:627` (alternative), `:631-634` (rule):

```
    | tupleType
    | uncertaintyType
*/
type returns [ASTType n]
@init { Token tok = null; }
:
    { tok = input.LT(1); /* remember start of type */ }
    (
      nTSimple=simpleType { $n = $nTSimple.n; if ($n != null) $n.setStartToken(tok); }
    | nTCollection=collectionType { $n = $nTCollection.n; if ($n != null) $n.setStartToken(tok); }
    | nTTuple=tupleType { $n = $nTTuple.n; if ($n != null) $n.setStartToken(tok); }
    | nTUncertainty=uncertaintyType { $n = $nTUncertainty.n; }
    )
    ;

uncertaintyType returns [ASTType n]
:
    name=('UReal'|'UInteger'|'UBoolean'|'UString' | 'SBoolean') { $n = new ASTSimpleType($name); }
    ;
```

**This rule is pure damage repair, and the port should understand why before copying it.**

`uncertaintyType` produces `new ASTSimpleType($name)` — byte-for-byte what `simpleType` produces
(`up_OCLBase.txt:627-630`: `simpleType : name=IDENT { $n = new ASTSimpleType($name); }`).
`ASTSimpleType.gen` calls `TypeFactory.mkSimpleType(name)`
(`USE-Uncertainty/src/main/org/tzi/use/parser/ocl/ASTSimpleType.java:45`), which looks the name up in
`buildInTypesMap` — and the fork's `TypeFactory` already registers all five
(`USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/TypeFactory.java:59-70`: `"UInteger"`,
`"UString"`, `"SBoolean"`, `"UBoolean"`, `"UReal"`).

So if `UReal` still lexed as `IDENT`, `simpleType` would have resolved it with **zero grammar
changes**. `uncertaintyType` exists *only* because change C turned those five names into keyword
tokens and thereby broke `simpleType` for them. It is a consequence of the design in §13.1.5, not an
independent feature. Note also that it drops the `setStartToken(tok)` call the other three
alternatives make — a minor inconsistency that degrades error positions for uncertainty types.

`uncertaintyType` is reachable through `collectionType` too (`up_OCLBase.txt:637-644`:
`('Collection'|'Set'|'Sequence'|'Bag'|'OrderedSet') LPAREN elemType=type RPAREN`), which is what
makes `Set(UReal)` work — exercised 9 times in `UCollectionOperations.in`.

### 13.1.7 Rules NOT changed

Confirmed unchanged by uncertainty in `OCLBase.gpart`: `collectionLiteral`, `emptyCollectionLiteral`,
`tupleLiteral`, `undefinedLiteral`, `iterateExpression`, `elemVarsDeclaration`,
`conditionalImpliesExpression` and everything below it down to `primaryExpression`,
`propertyCall`, `typeOnly`, `paramList`. The five uncertainty literals sit inside `literal`, which is
reached from `primaryExpression`, so postfix (`.value()`, `->uSelect(...)`) applies to them for free
with no grammar change.

---

## 13.2 `OCLLexerRules.gpart` — no change

**No new token types. The file is identical.**

```
cd /tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad
diff -u up_OCLLexerRules.txt fk_OCLLexerRules.txt   # empty
```

Both are 127 lines and differ only in line endings (and in a trailing-newline: the fork has no final
newline). `WS`, `SL_COMMENT`, `ML_COMMENT`, `ARROW`…`STAR`, `INT`, `REAL`, `RANGE_OR_INT`, `STRING`,
`NON_OCL_STRING`, `ESC`, `HEX_DIGIT`, `IDENT`, `VOCAB` are byte-identical.

**Minimal edit to `OCLLexerRules.gpart`: 0 lines.**

This matters. It means every uncertainty keyword is an **implicit token** created by ANTLR from an
inline literal in the *parser* grammar. Six such tokens are introduced across §13.1:

| token | introduced at |
|---|---|
| `'UReal'` | `OCLBase.gpart:496`, `:633` |
| `'UInteger'` | `OCLBase.gpart:498`, `:633` |
| `'UBoolean'` | `OCLBase.gpart:497`, `:633` |
| `'UString'` | `OCLBase.gpart:495`, `:633` |
| `'SBoolean'` | `OCLBase.gpart:499`, `:633` |
| `'equals'` | `OCLBase.gpart:132` |

Confirmed against the fork's checked-in generated lexer/parser vocabularies
(`parser/ocl/OCLParser.java:40`, `parser/use/USEParser.java:46`,
`parser/testsuite/TestSuiteParser.java:42`), each of which lists all six.

**Port consequence:** because ANTLR gives inline literals precedence over `IDENT`, these six words
become reserved across *every* USE input language. That is the single largest blast-radius item in
this section. See §13.5.

---

## 13.3 Concrete syntax of each uncertainty literal

Test-harness convention for all corpus files: an expression, then a line beginning `->` with the
expected `toStringWithType()` output; `#` starts a comment; `\\` continues a line. Harness:
`USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java:107-140`
(reader) and `:94` (assertion). Corpus totals:

```
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/
for f in *.in; do echo "$f: $(grep -c '^->' $f)"; done
```

`UBooleanExpression.in` 118, `UCollectionOperations.in` 44, `UIntegerExpression.in` 692,
`URealExpression.in` 573 — **1427 cases total**.

### 13.3.1 `UReal`

```
'UReal' LPAREN additiveExpression COMMA additiveExpression RPAREN
```

Arity 2 — (value, uncertainty). Both operands `additiveExpression`, i.e. `+`/`-` and tighter; a
relational or boolean operator is a syntax error. Semantic check in
`parser/ocl/ASTURealLiteral.java:27-31`: value must be Integer or Real, uncertainty must be Integer
or Real. Builds `ExpConstUReal` (`ASTURealLiteral.java:34`).

Verbatim corpus examples:

| example | citation |
|---|---|
| `UReal(2, 0)` | `URealExpression.in:7` |
| `UReal(2, -2)` | `URealExpression.in:13` |
| `UReal(2+2, 3)` | `URealExpression.in:25` |
| `UReal(55.23, 9.34)` | `URealExpression.in:28` |
| `UReal(55.23, -66.34)` | `URealExpression.in:34` |
| `UReal(0.34, 55.23)` | `URealExpression.in:37` |

`URealExpression.in:25` is the one that proves the operand is an expression, not a numeric token.
Negative uncertainty is accepted and normalised (`:13` → `-> UReal(2.0, 2.0) : UReal`, line 14).

### 13.3.2 `UInteger`

```
'UInteger' LPAREN additiveExpression COMMA additiveExpression RPAREN
```

Arity 2 — (value, uncertainty). Builds `ExpConstUInteger`
(`parser/ocl/ASTUIntegerLiteral.java:28`); an `ExpInvalidException` is rethrown as
`SemanticException` (`:30-32`).

| example | citation |
|---|---|
| `UInteger(-5, 0.0)` | `UIntegerExpression.in:7` |
| `UInteger(-5, -0.5)` | `UIntegerExpression.in:13` |
| `UInteger(-5, 2)` | `UIntegerExpression.in:16` |
| `UInteger(3, 39)` | `UIntegerExpression.in:22` |
| `UInteger(Undefined, Undefined)` | `UIntegerExpression.in:28` |
| `UInteger(3 + 4*2-3, UReal(4, 3.3).value() + 1)` | `UIntegerExpression.in:37` |

`:37` is the important one: it proves both operands are full `additiveExpression`s including postfix
calls and nested uncertainty literals. `:28` proves `Undefined` is a legal operand (it reaches
`additiveExpression` via `primaryExpression → literal → undefinedLiteral`).

### 13.3.3 `UBoolean` — note the asymmetry

```
'UBoolean' LPAREN conditionalImpliesExpression COMMA additiveExpression RPAREN
```

Arity 2 — (value, probability). **The first operand is `conditionalImpliesExpression`, not
`additiveExpression`** — the only literal that differs. It must be, because the value is a boolean
and `true or false` would not parse as an `additiveExpression`. The second operand stays
`additiveExpression`. Builds `ExpConstUBoolean` (`parser/ocl/ASTUBooleanLiteral.java:28`).

| example | citation |
|---|---|
| `UBoolean(true or false, UReal(2, 3))` | `UBooleanExpression.in:13` |
| `UBoolean(true and false, 3 / 0)` | `UBooleanExpression.in:16` |
| `UBoolean(true or false, 1 - 0.4)` | `UBooleanExpression.in:30` |
| `UBoolean(false, 0.42)` | `UBooleanExpression.in:39` |
| `UBoolean(false, 0.5) and UBoolean(false, 0.2)` | `UBooleanExpression.in:45` |
| `UBoolean(true, 0.79) and true` | `UBooleanExpression.in:66` |

`:13`, `:30` and `:45` are the load-bearing ones: `:13`/`:30` prove the first operand admits `or`/
`and` and the second admits nested literals and arithmetic; `:45` proves the literal composes as an
operand of ordinary OCL boolean operators with no grammar change.

Error-path cases worth porting as-is: `UBoolean(3 + 2, 1)` → `Value must be Boolean`
(`UBooleanExpression.in:7-8`); `UBoolean(true or false, UReal(2, 3))` →
`Probability must be a Integer or Real` (`:13-14`).

### 13.3.4 `UString`

```
'UString' LPAREN additiveExpression COMMA additiveExpression RPAREN
```

Arity 2 — (value, confidence). Builds `ExpConstUString` (`parser/ocl/ASTUStringLiteral.java:26`).
Note this class overrides only `gen` and `getFreeVariables` — **it has no `toString()`**, unlike the
other four (`ASTURealLiteral.java:44`, `ASTUBooleanLiteral.java:45`, `ASTUIntegerLiteral.java:45`,
`ASTSBooleanLiteral.java:52`). Port should add one for parity.

**`UNVERIFIABLE` — no corpus example exists.** Zero occurrences across the whole uncertainty corpus:

```
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/
grep -c UString *.in     # UBooleanExpression.in:0 UCollectionOperations.in:0 UIntegerExpression.in:0 URealExpression.in:0
```

The five-examples requirement **cannot be met for `UString`**. The grammar rule above is read
directly from `OCLBase.gpart:495` and is certain; the *accepted concrete syntax* is therefore known
but **entirely unexercised**. The port must either write new fixtures or ship `UString` explicitly
marked as untested.

### 13.3.5 `SBoolean`

```
'SBoolean' LPAREN additiveExpression COMMA additiveExpression COMMA additiveExpression COMMA additiveExpression RPAREN
```

**Arity 4** — (belief, disbelief, uncertainty, agent); parameter names from
`parser/ocl/ASTSBooleanLiteral.java:12-15`. Builds `ExpConstSBoolean` (`:33`). This is the subjective-
logic opinion type and is the one literal the task brief does not mention.

**`UNVERIFIABLE` — no corpus example exists.** `grep -c SBoolean *.in` returns 0 for all four files
(same command as §13.3.4). Same consequence as `UString`.

### 13.3.6 Summary table

| literal | arity | operand rules | AST class | corpus cases |
|---|---|---|---|---|
| `UReal` | 2 | additive, additive | `ASTURealLiteral` | many (573 in `URealExpression.in`) |
| `UInteger` | 2 | additive, additive | `ASTUIntegerLiteral` | many (692 in `UIntegerExpression.in`) |
| `UBoolean` | 2 | **conditionalImplies**, additive | `ASTUBooleanLiteral` | many (118 in `UBooleanExpression.in`) |
| `UString` | 2 | additive, additive | `ASTUStringLiteral` | **0** |
| `SBoolean` | 4 | additive ×4 | `ASTSBooleanLiteral` | **0** |

### 13.3.7 Ruling on `ASTUncertainLiteral` — REFUTED

The port plan names a single `ASTUncertainLiteral`. **No such class exists in the historical tree,
and it should not be created.**

```
ls /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/parser/ocl/ | grep -iE "^ASTU|SBool"
```

yields `ASTUBooleanLiteral.java`, `ASTUIntegerLiteral.java`, `ASTURealLiteral.java`,
`ASTUStringLiteral.java`, `ASTSBooleanLiteral.java` (plus the pre-existing 7.5.0
`ASTUnaryExpression`, `ASTUndefinedLiteral`, `ASTUnlimitedNaturalLiteral`, which are unrelated).

The per-type split is **load-bearing, not incidental**:

- the classes have different arities — 2 for four of them, **4** for `ASTSBooleanLiteral`;
- their `gen` methods enforce different semantic checks — `ASTURealLiteral.java:27-31` rejects
  non-numeric value/uncertainty with bespoke messages (`"Value must be Integer or Real"`,
  `"Uncertainty must be Integer or Real"`), while `ASTUIntegerLiteral`/`ASTUBooleanLiteral`/
  `ASTUStringLiteral` do no pre-check and instead translate `ExpInvalidException` from the `ExpConst*`
  constructor;
- they construct five distinct target expressions: `ExpConstUReal`, `ExpConstUInteger`,
  `ExpConstUBoolean`, `ExpConstUString`, `ExpConstSBoolean`;
- their `toString()` renderings differ (and `ASTUStringLiteral` has none).

Collapsing these into one class would require a runtime tag plus a five-way switch in `gen`, which is
strictly worse than the ANTLR grammar's own five-way dispatch that already exists at
`OCLBase.gpart:495-500`. **Port five classes, keep the fork's names, and correct the port plan.**

### 13.3.8 Dead code: `ASTSBooleanDefExpression`

`USE-Uncertainty/src/main/org/tzi/use/parser/ocl/ASTSBooleanDefExpression.java` exists but is
referenced by nothing:

```
cd /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
grep -rn "ASTSBooleanDefExpression" --include=*.java --include=*.gpart --include=*.g .
```

returns only its own declaration (`:11`) and constructor (`:15`). No grammar rule instantiates it.
**Do not port it.**

---

## 13.4 Query-expression syntax: `uSelect` / `uSelectC`

### 13.4.1 Surface syntax

```
source '->' 'uSelect'  '(' [ elemVarsDeclaration '|' ] expression ')'
source '->' 'uSelectC' '(' [ elemVarsDeclaration '|' ] expression ',' additiveExpression ')'
```

Both go through the *same* `queryExpression` rule (`OCLBase.gpart:337-349`); the grammar does not
distinguish them. `uSelect`/`uSelectC` are plain `IDENT`s admitted by the semantic predicate
`{ ParserHelper.isQueryIdent(input.LT(1)) }?` at `OCLBase.gpart:322` because
`ParserHelper.queryIdentMap` now contains them (`ParserHelper.java`, Q_USELECT/Q_USELECTC, §13.1.4).

The confidence argument is **optional in the grammar and mandatory only in the AST**:
`ASTQueryExpression.java:180-181` throws
`SemanticException(fOp, "'" + opname + "' need to specify the confidence.")` when
`Q_USELECTC_ID` is reached with `uncertainty == null`. Conversely, supplying a confidence to
`uSelect` is silently ignored (§13.1.4).

Dispatch: `ASTQueryExpression.java:152-153` → `new ExpUSelect(decl, range, expr)`;
`:178-184` → `new ExpUSelectC(decl, range, expr, uncertainty)`. Both are listed in the
single-element-variable group (`:128-137`), so **at most one iterator variable** is allowed —
`:141-145` throws `"Only one element variable in <op> expression allowed."` otherwise.

### 13.4.2 Corpus examples, all from `UCollectionOperations.in`

`uSelect` (5 occurrences; `grep -c 'uSelect(' UCollectionOperations.in` → 5):

| # | expression | citation | expected result |
|---|---|---|---|
| 1 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e \| e >= 2)` | `UCollectionOperations.in:139` | `-> Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)` (`:140`) |
| 2 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e \| e <= 2)` | `UCollectionOperations.in:142` | `-> Set{1,UReal(2.0, 0.5)} : Set(UReal)` (`:143`) |
| 3 | `let A = Set{2, 3, UReal(3, 0.5)} in (A->iterate(v; acc : Set(UReal) = Set {} \| if (v > 2).toBoolean() then acc->including(v) else acc endif) ).equals(A->uSelect(e\|e>2))` | `UCollectionOperations.in:146` | `-> true : Boolean` (`:147`) |
| 4 | `(A->iterate(v; acc : Sequence(UReal) = Sequence {} \| if (v > 2).toBoolean() then acc->including(v) else acc endif) ).equals(A->uSelect(e\|e>2))` — with `let A = Sequence{UReal(-3,5), 2.3, UReal(2,3), UReal(67,3), -50} in \\` on `:149` | `UCollectionOperations.in:150` | `-> true : Boolean` (`:151`) |
| 5 | `(A->iterate(v; acc : Bag(UReal) = Bag {} \| if (v > 2).toBoolean() then acc->including(v) else acc endif) ).equals(A->uSelect(e\|e>2))` — with `let A = Bag{2.3, UReal(2,3), UReal(67,3)} in \\` on `:153` | `UCollectionOperations.in:154` | `-> true : Boolean` (`:155`) |

`uSelectC` (4 occurrences; `grep -c 'uSelectC(' UCollectionOperations.in` → 4):

| # | expression | citation | expected result |
|---|---|---|---|
| 1 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e \| e >= 2, 0.49)` | `UCollectionOperations.in:160` | `-> Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)} : Set(UReal)` (`:161`) |
| 2 | `Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelectC(e \| e <= 2, 0.49)` | `UCollectionOperations.in:163` | `-> Set{1,UReal(2.0, 0.5)} : Set(UReal)` (`:164`) |
| 3 | `(A->iterate (v ; acc : Set(UReal) = Set {} \| if (v >= 2). toBooleanC (C) then acc -> including (v) else acc endif ) ).equals( A->uSelectC(e \| e >= 2, C) )` — with `let A = Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)} in let C = 0.7 in \\` on `:168` | `UCollectionOperations.in:169` | `-> true : Boolean` (`:170`) |
| 4 | same shape, with `let A = Set{UReal(52, 0.5), 3.2, 2, UReal(-53, 20), UReal(20, 5)} in let C = 0.45 in \\` on `:172` | `UCollectionOperations.in:173` | `-> true : Boolean` (`:174`) |

### 13.4.3 What these examples pin down

- Example pair 1/2 at `:139`/`:142` vs `:160`/`:163` is the **behavioural discriminator**: identical
  source and predicate, but `uSelectC` at confidence `0.49` additionally admits `UReal(2.0, 0.5)`
  into the `>= 2` result. Any port that wires `uSelectC` to `ExpUSelect` still passes `uSelect` tests
  and fails exactly here. Make these two the smoke test.
- `:169`/`:173` prove the confidence argument may be a **variable** (`C`), not just a literal — it is
  parsed as `additiveExpression`, and a bare `IDENT` reaches that via
  `primaryExpression`. They also pin the documented postcondition:
  `uSelectC(e | p, C)` ≡ `iterate` with `(p).toBooleanC(C)`.
- `:146`, `:150`, `:154` establish the same postcondition for `uSelect` over `Set`, `Sequence` and
  `Bag` — i.e. `uSelect` must preserve the source collection kind.
- All nine examples use `.equals(...)` or feed a `Set(UReal)` type annotation, so **§13.1.3, §13.1.4
  and §13.1.6 are jointly exercised by this one file**. `UCollectionOperations.in` is the integration
  fixture for the whole grammar change.
- `->uSelect` is used only in arrow position in the corpus. **`UNVERIFIABLE`:** whether dot-position
  `X.uSelect(...)` was intended or tested — no corpus case exercises it.

---

## 13.5 Ambiguity risk, per added rule

### 13.5.0 The upstream fixture, located

The brief's path is **correct**:
`/home/xoruser/msc-4/use-msc2026/use-core/src/test/resources/org/tzi/use/parser/test_expr.in`
exists (10199 bytes). It is consumed by
`use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java:79`. Same directory also holds the
model-compilation corpus `t1.use`…`t37_imports.use` with paired `.fail` expected-error files, and
`test_spec.use`.

A second, larger fixture set that this section depends on:
`use-gui/src/it/resources/testfiles/shell/*.{use,in,expected}`, driven by
`use-gui/src/it/java/org/tzi/use/main/shell/ShellIT.java` — a JUnit 5 `@TestFactory` (`:63-80`) that
emits one `DynamicTest` per `.in` file and loads the same-named `.use`.

### 13.5.1 Risk table

| # | added rule | collides with | severity | catching fixture |
|---|---|---|---|---|
| 1 | `identicalExpression` (`'equals'` keyword) | user-defined operation **named** `equals`; any call `x.equals(y)` | **HIGH — confirmed live collision** | `use-gui/src/it/resources/testfiles/shell/t098.use:11`; `…/shell/imports/t133_import_date.use:29`; `…/shell/imports/t133_import_datetime.use:12` |
| 2 | `'UReal'`…`'SBoolean'` keyword tokens | any class / dataType / attribute / operation / role / variable named `UReal`, `UInteger`, `UBoolean`, `UString`, `SBoolean` | HIGH in principle, **no live collision** | `test_expr.in`, `t1.use`…`t37_imports.use`, `testfiles/shell/*.use` — all clean today |
| 3 | `queryExpression` optional `COMMA additiveExpression` | nothing syntactically; **silently accepts** a bogus extra arg to `select`/`collect`/`forAll`/… | MEDIUM (silent acceptance, not a parse break) | **no upstream fixture catches this** — gap |
| 4 | `uncertaintyType` alternative in `type` | `simpleType ::= IDENT` — disjoint by token class, so none | LOW | `test_expr.in`, `t*.use` type declarations |
| 5 | `uSelect` / `uSelectC` in `queryIdentMap` | a model operation named `uSelect`/`uSelectC` on a collection | LOW, **no live collision** | `testfiles/shell/*.use` |

### 13.5.2 Risk 1 in detail — this one is real, not hypothetical

`'equals'` at `OCLBase.gpart:132` is an inline literal, so ANTLR lexes `equals` as a keyword token and
**never** as `IDENT`. Two upstream consequences:

**(a) Operation declarations break.** `operationDefinition` binds the operation name to `IDENT`:
`use-core/src/main/resources/grammars/base/USEBase.gpart:264-269`

```
operationDefinition[ASTClassifier c] returns [ASTOperation n]
@init { boolean isConstructor = false; }
:
	as = annotationSet
    name = IDENT
    pl = paramList
```

`use-gui/src/it/resources/testfiles/shell/t098.use:11` declares, inside `class Date1`:

```
	equals(t : Date1) : Boolean
```

With the fork's rule in place, `equals` arrives as the `'equals'` token and `name = IDENT` cannot
match — a **parse failure on an upstream fixture**. `t098.in` is an expected-error file whose three
lines (`t098.in:1-3`) name only the duplicate-`isEmpty` and unrelated-types diagnostics, so any new
parse error changes the output and fails the test.

Same declaration form at `use-gui/src/it/resources/testfiles/shell/imports/t133_import_date.use:29`:

```
	equals(other:Date):Boolean =
```

**(b) Call sites are silently rerouted.**
`use-gui/src/it/resources/testfiles/shell/imports/t133_import_datetime.use:12`

```
	    (self.date.equals(other.date) and self.time.before(other.time))
```

If (a) were somehow fixed, this call would still be captured by `identicalExpression` and resolved to
`Op_identical` (`StandardOperationsAny.java:130`) instead of the user-defined `Date::equals`
(`t133_import_date.use:29-32`), which compares day/month/year. Different semantics, no diagnostic.

**Port directive.** Do not introduce `'equals'` as a keyword. Options, in order of preference:

1. Drop `identicalExpression` entirely and register `Op_identical` under a name that cannot collide
   (or reuse the existing `=`), leaving the call to be parsed by the ordinary `operationExpression`
   path — the same low-risk mechanism `uSelect` uses.
2. Keep the rule but gate it with a semantic predicate on the token *text*
   (`{ input.LT(2).getText().equals("equals") }?`) so `equals` stays an `IDENT`, and resolve
   user-defined `equals` operations ahead of `Op_identical`.
3. Accept the break and amend `t098.use`, `t133_import_date.use`, `t133_import_datetime.use` — **not
   recommended**; it silently narrows the modelling language for every downstream user.

Verification commands:

```
cd /home/xoruser/msc-4/use-msc2026
grep -rnE "\bequals\s*\(" --include=*.use --include=*.soil . | grep -v reference-repositories
sed -n '260,270p' use-core/src/main/resources/grammars/base/USEBase.gpart
```

### 13.5.3 Risk 2 in detail — reserved words, currently latent

The five type keywords have the same mechanism as `equals` and therefore the same reach: `class
UReal`, `attributes x : ... UString`, a role named `SBoolean`, a `let UReal = …` — all become parse
errors. Today nothing in the repository trips it:

```
cd /home/xoruser/msc-4/use-msc2026
grep -rlnwE "UReal|UInteger|UBoolean|UString|SBoolean" \
  --include=*.use --include=*.soil --include=*.cmd --include=*.in --include=*.assl . \
  | grep -v reference-repositories        # → no output
cd use-core/src/test/resources
grep -rnwE "UReal|UInteger|UBoolean|UString|SBoolean" .    # → no output
```

So the port can ship this **without breaking any existing fixture**, but the language is
narrowed. Unlike risk 1, that is arguably the intended design: these are genuinely new built-in type
names, and `TypeFactory.buildInTypesMap` (fork `TypeFactory.java:59-70`) reserves them at the
semantic level anyway. Document it as a deliberate, breaking-in-principle change and add a negative
fixture (`class UReal … end` → expected error) so the behaviour is pinned rather than accidental.

Note the leverage available here: because `mkSimpleType` already resolves all five names, the
`literal` rule is the *only* reason they must be keywords. A predicated form
(`{ input.LT(1).getText().equals("UReal") }? IDENT LPAREN …`) would keep them as `IDENT`s, make
`uncertaintyType` (§13.1.6) unnecessary, and eliminate risk 2 outright. Worth costing before copying
the fork verbatim.

### 13.5.4 Risk 3 in detail — the untested gap

Nothing upstream calls a query operation with a spurious second argument, so no fixture would notice
that `->collect(x | x.a, 99)` now parses and discards `99`. This is a **coverage gap the port
introduces**, not one it inherits. Add a negative fixture asserting that a second argument to any
query ident other than `uSelectC` is a compile error, and add the corresponding guard in
`ASTQueryExpression.gen` (the natural place is alongside the existing null-check at
`ASTQueryExpression.java:180-181`).

### 13.5.5 Risks 4 and 5 — low

**Risk 4.** After the port, `type` has five alternatives, LA(1)-disjoint by token class: `simpleType` starts with
`IDENT`, `collectionType` with one of five collection keywords, `tupleType` with `'Tuple'`,
`uncertaintyType` with one of the five uncertainty keywords, `modelQualifiedType` (7.5.0) with
`IDENT HASH`. The only genuine LA(1) conflict in that set — `simpleType` vs `modelQualifiedType`,
both starting `IDENT` — is **pre-existing in 7.5.0** and untouched by this port. `uncertaintyType`
adds no new conflict. Any regression would surface in the type declarations throughout
`use-core/src/test/resources/org/tzi/use/parser/t*.use` and `test_spec.use`.

**Risk 5.** `uSelect`/`uSelectC` enter `queryIdentMap`, not the token vocabulary, so they stay
`IDENT`s. The only collision is a model operation of that exact name applied to a collection, which
the `isQueryIdent` predicate at `OCLBase.gpart:322` would now capture first. No such operation exists
in the repository (`grep -rn "uSelect" --include=*.use .` outside the reference repos → nothing).

---

## 13.6 Port checklist

| item | action | evidence |
|---|---|---|
| `OCLLexerRules.gpart` | **no edit** | §13.2 |
| `OCLBase.gpart` | 8 hunks, +35 −8; preserve 7.5.0's `modelQualifier`, `modelQualifiedType`, `oclIsInState` | §13.1.1–13.1.6 |
| `ParserHelper.java` | +6 lines (`Q_USELECT`, `Q_USELECTC`, ids 12/13, two map puts) | §13.1.4 |
| `ASTQueryExpression` | add 5-arg ctor as an **overload**; add guard rejecting a confidence arg for non-`uSelectC` ops | §13.1.4, §13.5.4 |
| AST literal classes | port **five** per-type classes; do **not** create `ASTUncertainLiteral` | §13.3.7 |
| `ASTSBooleanDefExpression` | do **not** port — dead code | §13.3.8 |
| `ASTUStringLiteral` | add missing `toString()` | §13.3.4 |
| `uncertaintyType` | port, but add `setStartToken(tok)` for parity; reconsider if the predicated-literal route is taken | §13.1.6 |
| `'equals'` keyword | **do not ship as-is** — breaks `t098.use`, `t133_import_date.use`, `t133_import_datetime.use` | §13.5.2 |
| `UString`, `SBoolean` | write new fixtures or ship marked untested — zero corpus coverage | §13.3.4, §13.3.5 |
| smoke test | `UCollectionOperations.in:139-143` vs `:160-164` | §13.4.3 |
