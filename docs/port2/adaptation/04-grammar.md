# 04 — Grammar, Parser and Concrete Syntax

Area: the fork's ANTLR 3 `.gpart` additions and how to express them the 7.5.0 way.

Governing policy: **uncertainty meaning comes from the fork; everything else comes from USE 7.5.0;
where they collide, keep the uncertainty behaviour but express it the 7.5.0 way.**

Every claim below names a file:line or pastes real command output. Claims that could not be
established are marked `UNVERIFIABLE`. Confidence is labelled `MEASURED` (I ran it),
`READ-FROM-SOURCE` (I read the code that decides it) or `INFERRED`.

---

## 0. Verdict

The fork's whole concrete-syntax delta is **four** changes in **one** file. Under the policy, three
of the four dissolve into 7.5.0 mechanisms that need **no grammar edit at all**, and the fourth
shrinks to a predicate-gated rule that reserves no words.

| # | fork addition | needs a grammar change in the port? | why |
|---|---|---|---|
| **G1** | `identicalExpression` (`.equals(…)`) | **No — delete it** | 7.5.0 routes `a.equals(b)` through the ordinary named-operation path; registering the fork's `Op_identical` in the opmap restores the meaning exactly *and* repairs two fork parse defects. `MEASURED` |
| **G2** | `queryExpression` optional confidence argument | **Yes — 2 lines** | Load-bearing for `uSelectC`; no collision; merges clean. `MEASURED` |
| **G3** | five U-literal alternatives in `literal` | **Yes — but predicate-gated** | This is the only fork addition with no 7.5.0 equivalent. Express it the way 7.5.0 already expresses `select`/`collect`: a semantic predicate on `IDENT`, not an implicit keyword token. `MEASURED` + `INFERRED` |
| **G4** | `uncertaintyType` rule + `type` alternative | **No — delete it** | 7.5.0 resolves built-in type *names* through `TypeFactory.buildInTypesMap` reached from `simpleType ::= IDENT`. `Integer`/`Real`/`Boolean`/`String` are not keywords either. `MEASURED` |

Net effect on reserved words: the fork adds **6** (`equals`, `UReal`, `UInteger`, `UBoolean`,
`UString`, `SBoolean`) across **6** generated lexers = 36 reservation sites. The port under this
policy adds **0**.

---

## 1. Where the grammar lives, and why one file is six languages

### 1.1 Composition

`OCLBase.gpart` is not one grammar's fragment. `use-core/pom.xml:95-170` (merge-maven-plugin) merges
it into **six** separate ANTLR grammars:

| target grammar | sources (`use-core/pom.xml`) |
|---|---|
| `OCL.g` | `ocl/OCL.gpart`, **`base/OCLBase.gpart`**, `base/OCLLexerRules.gpart` (`:99-102`) |
| `Soil.g` | `soil/Soil.gpart`, `base/SoilBase.gpart`, **`base/OCLBase.gpart`**, … (`:110-113`) |
| `USE.g` | `use/USE.gpart`, `base/USEBase.gpart`, **`base/OCLBase.gpart`**, … (`:121-125`) |
| `ShellCommand.g` | `shell/ShellCommand.gpart`, `base/ShellCommandBase.gpart`, **`base/OCLBase.gpart`**, … (`:133-138`) |
| `Generator.g` | `generator/Generator.gpart`, `base/USEBase.gpart`, **`base/OCLBase.gpart`**, … (`:147-149`) |
| `TestSuite.g` | `testsuite/TestSuite.gpart`, **`base/OCLBase.gpart`**, … (`:156-162`) |

**Consequence, and the reason B4 exists:** every implicit string literal written into a parser rule
in `OCLBase.gpart` becomes a lexer literal token in *all six* languages — OCL, USE model files, SOIL,
ASSL/Generator, TestSuite and the shell. It is reserved everywhere, not just in OCL expressions.

Measured on the fork's own checked-in generated lexers:

```
$ cd .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/parser
$ for g in ocl/OCLLexer use/USELexer soil/SoilLexer shell/ShellCommandLexer \
           testsuite/TestSuiteLexer generator/GeneratorLexer; do
    n=$(grep -c 'match("equals")\|match("UReal")\|match("UInteger")\|match("UBoolean")\|match("UString")\|match("SBoolean")' $g.java)
    printf "  %-28s %s\n" "$(basename $g).java" "$n"
  done
  OCLLexer.java                6
  USELexer.java                6
  SoilLexer.java               6
  ShellCommandLexer.java       6
  TestSuiteLexer.java          6
  GeneratorLexer.java          6

$ grep -o 'match("\(equals\|UReal\|UInteger\|UBoolean\|UString\|SBoolean\)")' ocl/OCLLexer.java | sort -u
match("SBoolean")
match("UBoolean")
match("UInteger")
match("UReal")
match("UString")
match("equals")
```

`MEASURED`. 6 words × 6 lexers = 36 reservation sites.

### 1.2 Line endings — **correction to `spec-parts/13-grammar.md` §13.0**

`docs/port2/spec-parts/13-grammar.md` §13.0 states:

> **Both files differ in line endings.** The fork's `.gpart` files are CRLF, 7.5.0's are LF.

**That is inverted.** Measured:

```
$ file <fork>/base/OCLBase.gpart <port>/base/OCLBase.gpart
…/uncertainty/…/parser/base/OCLBase.gpart: magic text fragment for file(1) cmd, 2nd line "/*", …
…/use-core/src/main/resources/grammars/base/OCLBase.gpart: ASCII text, with CRLF line terminators

$ head -c 16 <fork>/base/OCLBase.gpart | xxd
00000000: 0a2f 2a0a 2d2d 2d2d 2d2d 2d2d 2d20 5374  ./*.--------- St

$ head -c 16 <port>/base/OCLBase.gpart | xxd
00000000: 0d0a 2f2a 0d0a 2d2d 2d2d 2d2d 2d2d 2d20  ../*..---------

$ printf "fork  CR bytes: %s\n" "$(tr -cd '\r' < <fork>/base/OCLBase.gpart | wc -c)"
fork  CR bytes: 0
$ printf "7.5.0 CR bytes: %s\n" "$(tr -cd '\r' < <port>/base/OCLBase.gpart | wc -c)"
7.5.0 CR bytes: 677
```

**The fork is LF. USE 7.5.0 is CRLF.** `MEASURED`. The *conclusion* of §13.0 — normalise before
diffing — is right; only the attribution is backwards.

How badly a naive diff misleads (raw `diff`, no normalisation, fork vs port):

| file | fork lines | fork CRLF lines | port lines | port CRLF lines | raw diff lines | real diff lines |
|---|---|---|---|---|---|---|
| `base/OCLBase.gpart` | 690 | 0 | 677 | 677 | 1367 | 48 (vs 2015 base) |
| `base/OCLLexerRules.gpart` | 127 | 0 | 127 | 127 | **254** | **0** |
| `base/SoilBase.gpart` | 531 | 0 | 531 | 531 | **1062** | **2** (an SVN `$Id$` expansion) |
| `ocl/OCL.gpart` | 65 | 0 | 65 | 65 | **130** | **0** |
| `shell/ShellCommand.gpart` | 80 | 0 | 80 | 80 | **160** | **0** |
| `soil/Soil.gpart` | 79 | 0 | 79 | 79 | **158** | **0** |
| `use/USE.gpart` | 109 | 0 | 109 | 109 | **218** | **0** |
| `base/USEBase.gpart` | 465 | 0 | 587 | **0** | 154 | 154 |
| `generator/Generator.gpart` | 376 | 0 | 376 | **0** | **0** | **0** |

`MEASURED`. Note the two exceptions: `USEBase.gpart` and `Generator.gpart` are **LF on both sides**,
so for those two a naive diff is honest. Every other file is a CRLF trap. `Generator.gpart` is
byte-identical between the 2015 fork and USE 7.5.0.

### 1.3 The port's current grammar baseline

Before any uncertainty work, the port's grammars are byte-identical (modulo CR) to upstream:

```
$ for f in base/OCLBase base/OCLLexerRules base/ShellCommandBase base/SoilBase base/USEBase \
           ocl/OCL shell/ShellCommand soil/Soil testsuite/TestSuite use/USE generator/Generator; do
    d=$(diff <(tr -d '\r' < "$PORT/$f.gpart") <(tr -d '\r' < "$UP/$f.gpart") | grep -c '^[<>]')
    printf "  %-24s changed-lines=%s\n" "$(basename $f)" "$d"
  done
  OCLBase                  changed-lines=0
  OCLLexerRules            changed-lines=0
  ShellCommandBase         changed-lines=0
  SoilBase                 changed-lines=0
  USEBase                  changed-lines=0
  OCL                      changed-lines=0
  ShellCommand             changed-lines=0
  Soil                     changed-lines=0
  TestSuite                changed-lines=0
  USE                      changed-lines=0
  Generator                changed-lines=0
```

`MEASURED`. `$PORT` = `use-core/src/main/resources/grammars`,
`$UP` = `.git/reference-repositories/upstream-use/use-core/src/main/resources/grammars`.

---

## 2. The fork's `.gpart` additions, quoted, with the minimal 7.5.0 equivalent

### 2.1 Isolation method — separating uncertainty from 11 years of version drift

Diffing the fork against 7.5.0 mixes the fork's additions with everything upstream did 2015→2026.
To isolate the fork's *own* delta I diffed it against the upstream commit it branched from. The fork
carries an unexpanded SVN keyword that dates it:

`…/USE-Uncertainty/src/main/org/tzi/use/parser/base/SoilBase.gpart:26`
```
/* $Id: SoilBase.gpart 5494 2015-02-05 12:59:25Z lhamann $ */
```

SVN r5494 maps to upstream commit `750fa544cf3d87129aa0b6d5ee5280ce0bce7557` (2015-02-05):

```
$ cd .git/reference-repositories/upstream-use
$ git log --format='%H %ad' --date=short --grep="trunk@5494 " -1
750fa544cf3d87129aa0b6d5ee5280ce0bce7557 2015-02-05
```

Diffing every `.gpart` at that commit against the fork, CR-normalised:

```
### FORK vs 2015-UPSTREAM (r5494) — CR-normalised ###
OCLBase                  changed-lines=48
OCLLexerRules            changed-lines=0
ShellCommandBase         changed-lines=0
SoilBase                 changed-lines=2
USEBase                  changed-lines=0
Generator                changed-lines=0
OCL                      changed-lines=0
ShellCommand             changed-lines=0
Soil                     changed-lines=0
TestSuite                changed-lines=0
USE                      changed-lines=0
```

`MEASURED`. **The fork changed exactly one grammar file: `base/OCLBase.gpart`.** The 2 lines in
`SoilBase.gpart` are the `$Id$` keyword expansion quoted above — not a grammar change. Nothing in
the lexer rules (`OCLLexerRules.gpart`) was touched: the U-type names and `equals` are *implicit*
tokens created by writing them as string literals in parser rules, never declared lexer rules.

### 2.2 G1 — `identicalExpression`

Fork, `…/parser/base/OCLBase.gpart:124-135` (verbatim):

```antlr
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

plus the rewiring of `expression` at `:74-77` and the comments at `:27` and `:36`:

```antlr
    nIdExp=identicalExpression
    { if ( $nIdExp.n != null ) {
    	 $n = $nIdExp.n;
         $n.setStartToken(tok);
      }
```

USE 7.5.0, `use-core/src/main/resources/grammars/base/OCLBase.gpart:74-77`, has no such rule; the
same slot reads:

```antlr
    nCndImplies=conditionalImpliesExpression
    { if ( $nCndImplies.n != null ) {
    	 $n = $nCndImplies.n;
         $n.setStartToken(tok);
      }
```

**Minimal 7.5.0 equivalent: none — delete the rule.** See §3 for the full argument and proof.

### 2.3 G2 — `queryExpression` optional confidence argument

Fork, `…/OCLBase.gpart:337-349` (verbatim, changed lines marked):

```antlr
queryExpression[ASTExpression range] returns [ASTExpression n]	
@init {
        ASTElemVarsDeclaration decl = new ASTElemVarsDeclaration();
        ASTExpression uncer = null;                                  <-- added
    }:
    op=IDENT 
    LPAREN 
    ( decls=elemVarsDeclaration {decl = $decls.n;} BAR )?
    nExp=expression
    ( COMMA uncerExp=additiveExpression { uncer = $uncerExp.n;} )?   <-- added
    RPAREN
    { $n = new ASTQueryExpression($op, $range, decl, $nExp.n, uncer); }   <-- arg added
    ;
```

7.5.0, `…/OCLBase.gpart:324-332`, is the same rule without those three edits.

**What it buys.** It exists solely for the fork's new query ident `uSelectC`
(`ParserHelper.java:20` `Q_USELECTC = "uSelectC"`, `:34` `Q_USELECTC_ID = 13`). Measured:

```
EXPR  Set{1,2,3}->uSelect(e | UBoolean(e > 1, 0.8))                -> type=Set(Integer) | eval=Set{2,3}
EXPR  Set{1,2,3}->uSelectC(e | UBoolean(e > 1, 0.8), 0.5)          -> type=Set(Integer) | eval=Set{2,3}
EXPR  Set{1,2,3}->uSelectC(e | UBoolean(e > 1, 0.8), 0.9)          -> type=Set(Integer) | eval=Set{}
EXPR  Set{1,2,3}->uSelectC(e | UBoolean(e > 1, 0.8))               -> COMPILE-ERROR: probe:1:12: 'uSelectC' need to specify the confidence.
EXPR  let uSelect : Integer = 1 in uSelect                         -> type=Integer | eval=1 : Integer
```

`MEASURED`. The confidence value is load-bearing (0.5 keeps, 0.9 drops), and the mandatory-argument
error comes from `ASTQueryExpression.java:180-183`.

**No collision.** `uSelect` and `uSelectC` are entries in `ParserHelper.queryIdentMap`
(`ParserHelper.java:50-51`), reached through the semantic predicate at `OCLBase.gpart:322`, so they
are **ordinary IDENTs** — the last probe line above uses `uSelect` as a `let` variable and it works.

**Minimal 7.5.0 equivalent: the same three edits.** 7.5.0 never touched `queryExpression`, so it
merges cleanly. But it carries a latent defect the port should fix under B7:

```
EXPR  Set{1,2,3}->select(e | e > 1, 0.9)   -> type=Set(Integer) | eval=Set{2,3} : Set(Integer)
EXPR  Set{1,2,3}->select(e | e > 1)        -> type=Set(Integer) | eval=Set{2,3} : Set(Integer)
EXPR  Set{1,2,3}->collect(e | e * 2, 0.5)  -> type=Bag(Integer) | eval=Bag{2,4,6} : Bag(Integer)
```

`MEASURED`. The extra argument is accepted on *every* query operation and then silently discarded —
`ASTQueryExpression.java:114-115` computes it, and only the `Q_USELECTC_ID` branch at `:178-183`
ever reads it; every other branch (`:152-176`) ignores it. `READ-FROM-SOURCE` + `MEASURED`. The port
should reject a confidence argument on any query ident other than `uSelectC`.

### 2.4 G3 — the five U-literal alternatives

Fork, `…/OCLBase.gpart:495-500` (verbatim), inserted into `literal` between the `STRING` and `HASH`
alternatives:

```antlr
    | 'UString' LPAREN usve=additiveExpression COMMA usue=additiveExpression RPAREN { $n = new ASTUStringLiteral($usve.n,$usue.n); }
    | 'UReal' LPAREN urve=additiveExpression COMMA urue=additiveExpression RPAREN { $n = new ASTURealLiteral($urve.n,$urue.n); }
    | 'UBoolean' LPAREN ubve=conditionalImpliesExpression COMMA ubpe=additiveExpression RPAREN { $n = new ASTUBooleanLiteral($ubve.n, $ubpe.n); }
    | 'UInteger' LPAREN uive=additiveExpression COMMA uiue=additiveExpression RPAREN { $n = new ASTUIntegerLiteral($uive.n, $uiue.n); }
    | 'SBoolean' LPAREN ubve=additiveExpression COMMA udve=additiveExpression COMMA uuve=additiveExpression COMMA uave=additiveExpression RPAREN
       { $n = new ASTSBooleanLiteral($ubve.n, $udve.n, $uuve.n, $uave.n); }
```

**This is the only fork addition with no 7.5.0 equivalent.** I confirmed there is no zero-grammar
route. In 7.5.0 a bare `UReal(2, 0.5)` (no receiver) reaches
`ASTOperationExpression.gen` `:187-226`, which tries, in order: variable (skipped — has parentheses),
implicit expression context, then `ctx.model().getClassifier(opname)` as a constructor call, then
throws at `:222-224`:

```java
                } else {
                    throw new SemanticException(fOp, "Undefined " + 
                                                ( fHasParentheses ? "operation" : "variable" ) + 
                                                " `" + opname + "'.");
                }
```

`READ-FROM-SOURCE`. The standard-operation map is never consulted on that path, so registering an
`OpGeneric` named `UReal` would not help. Confirmed by measurement even after registering `UReal`
as a type name (§2.5):

```
EXPR  UReal(2, 0.5)   -> ERROR: probe:1:0: Undefined operation `UReal'. |
```

`MEASURED`.

**Minimal 7.5.0 equivalent: keep the rule, but drop the keyword tokens.** 7.5.0 already has the
idiom, in this very rule file: `select`, `collect`, `forAll`, `iterate` &c. are *not* reserved words,
they are `IDENT`s admitted by a semantic predicate. `OCLBase.gpart:309` and `:322`, identical text on
both sides:

```antlr
      { org.tzi.use.parser.base.ParserHelper.isQueryIdent(input.LT(1)) }?
```

That this keeps the names unreserved is measured on stock 7.5.0:

```
EXPR  let select : Integer = 1 in select      -> type=Integer | eval=1 : Integer
EXPR  let collect : Integer = 1 in collect    -> type=Integer | eval=1 : Integer
EXPR  Tuple{select:1, collect:2}              -> type=Tuple(select:Integer,collect:Integer)
```

`MEASURED`. So the port's `literal` should gain **one** predicate-gated alternative delegating to one
new rule, in the shape 7.5.0 already uses:

```antlr
    | { org.tzi.use.parser.base.ParserHelper.isUncertaintyLiteralIdent(input.LT(1)) }?
      { input.LA(2) == LPAREN }?
      nULit=uncertaintyLiteral { $n = $nULit.n; }
```

with `uncertaintyLiteral` capturing `IDENT LPAREN expression (COMMA expression)* RPAREN` and
dispatching on the token text, and `ParserHelper` gaining an `uncertaintyLiteralIdentMap` alongside
the existing `queryIdentMap` (`ParserHelper.java:36-52`). Arity checking moves out of the grammar and
into the AST node, where a real error message can be produced.

Confidence: the *need* for a grammar rule is `MEASURED`; the predicate shape is `INFERRED` from the
`isQueryIdent` precedent — I did not regenerate the parser, because building is out of scope for this
area. It must be validated when the grammar is actually built.

A second reason to prefer the predicate form: the fork's fixed-arity alternatives make argument
errors into *parser* errors with no diagnostic value.

```
EXPR  UReal(2.5)              -> COMPILE-ERROR: probe:line 1:9 mismatched input ')' expecting , |
EXPR  UReal(1,0.1,0.2)        -> COMPILE-ERROR: probe:line 1:11 mismatched input ',' expecting ) |
EXPR  SBoolean(0.8,0.1,0.1)   -> COMPILE-ERROR: probe:line 1:20 mismatched input ')' expecting , |
```

`MEASURED`.

### 2.5 G4 — `uncertaintyType`

Fork, `…/OCLBase.gpart:631-634` (verbatim) plus the `type` alternative at `:627` and its comment at
`:617`:

```antlr
uncertaintyType returns [ASTType n]
:
    name=('UReal'|'UInteger'|'UBoolean'|'UString' | 'SBoolean') { $n = new ASTSimpleType($name); }
    ;
```

```antlr
    | nTUncertainty=uncertaintyType { $n = $nTUncertainty.n; }
```

Note what the action does: `new ASTSimpleType($name)` — *exactly what `simpleType` already does*
(`OCLBase.gpart:649-652`, identical on both sides):

```antlr
simpleType returns [ASTSimpleType n]
:
    name=IDENT { $n = new ASTSimpleType($name); }
    ;
```

The rule is pure redundancy: it hard-codes as ANTLR keywords what 7.5.0 resolves by name lookup. In
7.5.0, `ASTSimpleType.gen` (`ASTSimpleType.java:45-69`) resolves the text through
`TypeFactory.mkSimpleType` (`TypeFactory.java:132-139`), which is a plain string map
(`TypeFactory.java:36, 50-58`):

```java
    private static final Map<String, Type> buildInTypesMap = new HashMap<String, Type>();
    static {
    	buildInTypesMap.put("Integer", integerType);
    	buildInTypesMap.put("UnlimitedNatural", unlimitedNaturalType);
    	buildInTypesMap.put("String", stringType);
    	buildInTypesMap.put("Boolean", booleanType);
    	buildInTypesMap.put("Real", realType);
    	buildInTypesMap.put("OclAny", oclAnyType);
    	buildInTypesMap.put("OclVoid", voidType);
    }
```

`Integer`, `Real`, `Boolean`, `String` are **not grammar keywords in 7.5.0**. The U-types should not
be either.

**Proof.** I registered a single extra entry in that map by reflection — no grammar edit — and every
type position the fork's `uncertaintyType` rule serves started working:

```
$ java -cp outtype:$CPB TypeAdapt 'let u : UReal = 1.0 in u' 'oclUndefined(UReal)' \
        'oclEmpty(Set(UReal))' 'UReal(2, 0.5)' '@FILE:/tmp/probe-grammar/utype2.use'
registered UReal in TypeFactory.buildInTypesMap; map now = [Integer, UnlimitedNatural, OclAny, UReal, Real, OclVoid, String, Boolean]
EXPR  let u : UReal = 1.0 in u                                   -> type=Real
EXPR  oclUndefined(UReal)                                        -> type=Real
EXPR  oclEmpty(Set(UReal))                                       -> type=Set(Real)
EXPR  UReal(2, 0.5)                                              -> ERROR: probe:1:0: Undefined operation `UReal'. |
SPEC  utype2.use                                                 -> OK model=U
```

where `utype2.use` is

```
model U
class Sensor
attributes
  reading : UReal
operations
  get() : UReal = self.reading
end
```

`MEASURED`. **Caveat, stated plainly:** the probe maps `"UReal"` to the existing `RealType`
*instance* as a stand-in (`RealType` is `final`, so no subclass could be made in a scratch driver).
This proves the **name-resolution path** only — that `simpleType ::= IDENT` reaches the map and the
map is enough. It says nothing about U-type semantics; that is another area's business. The reported
`type=Real` is the stand-in showing through, not a claim about the port.

The one thing the map does *not* reach is the literal — last probe line, consistent with §2.4.

**Minimal 7.5.0 equivalent: delete `uncertaintyType`; register the five names in
`TypeFactory.buildInTypesMap`.**

### 2.6 G5 — a non-change worth recording

The fork also has, at `…/OCLBase.gpart:665`:

```antlr
    { $n = new ASTCollectionType(op, $elemType.n); $n.setStartToken(op);}
```

against 2015 upstream's `{ … if ($n != null) $n.setStartToken(op);}`. This looks like a fork edit but
**7.5.0 made the identical change independently** (`…/grammars/base/OCLBase.gpart:643` is byte-equal
to the fork line). Convergent; nothing to port. `MEASURED`.

---

## 3. Decision B4 — the `equals` collision

### 3.1 What `identicalExpression` actually buys — probed, not reasoned

`equals` is not a syntactic curiosity; it names a real operation. Fork,
`…/uml/ocl/expr/operations/StandardOperationsAny.java:18` registers it, and `:130-179` defines it:

```java
final class Op_identical extends OpGeneric {
	public String name() {
		return "equals";
	}
	…
	public Type matches(Type params[]) {
		if (params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null)
			return TypeFactory.mkBoolean();
		else
			return null;
	}
```

Compare its sibling `Op_equal` (name `"="`), `:34-67`:

```java
	public Type matches(Type params[]) {
		boolean twoArgsAndCommonSupertype = params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null;
		boolean someOfThemIsUncertaintyValue = params[1] instanceof UncertainType || params[0] instanceof UncertainType;
		…
			if (someOfThemIsUncertaintyValue && !someOfThemIsUndefined) {
				if (someOfThemIsSBooleanValue)
					result = TypeFactory.mkSBoolean();
				else
					result = TypeFactory.mkUBoolean();
			}
			else
				result = TypeFactory.mkBoolean();
```

So: **`=` is uncertainty-lifted; `.equals(…)` is not.** `.equals` is the escape hatch back to crisp
Boolean structural equality. Measured against the fork jars (`use.jar` +
`atenearesearchgroup.uncertainty.jar` + `antlr-3.4-complete.jar`, driver
`OCLCompiler.compileExpression(new ModelFactory().createModel("m"), expr, "probe", …)`):

```
EXPR  Set{UReal(2,0.5), 1, 2.5}                    -> type=Set(UReal) | eval=Set{1,2.5,UReal(2.0, 0.5)} : Set(UReal)
EXPR  Set{UReal(2,0.5), 1, 2.5}->sum()             -> type=UReal | eval=UReal(5.5, 0.5) : UReal
EXPR  Set{1, 2.5}                                  -> type=Set(Real) | eval=Set{1,2.5} : Set(Real)
EXPR  UReal(2,0.5) = UReal(2,0.5)                  -> type=UBoolean | eval=UBoolean(true, 1.0) : UBoolean
EXPR  UReal(2,0.5).equals(UReal(2,0.5))            -> type=Boolean  | eval=true : Boolean
EXPR  UReal(2,0.5) = UReal(2.4,0.5)                -> type=UBoolean | eval=UBoolean(true, 0.689) : UBoolean
EXPR  UReal(2,0.5).equals(UReal(2.4,0.5))          -> type=Boolean  | eval=false : Boolean
EXPR  UBoolean(true,0.9) = UBoolean(true,0.9)      -> type=UBoolean | eval=UBoolean(true, 1.0) : UBoolean
EXPR  UBoolean(true,0.9).equals(UBoolean(true,0.9))-> type=Boolean  | eval=true : Boolean
```

`MEASURED`. (First three rows reproduce the brief's worked example exactly, which validates the
harness.) The decisive pair is rows 6–7: `= ` on two *different* UReals yields
`UBoolean(true, 0.689)` — a graded answer — while `.equals` yields plain `false`.

**This meaning must be preserved.** It is genuinely uncertainty semantics and is not obtainable from
`=`.

### 3.2 Is it reachable any other way *in the fork*? No.

```
EXPR  Set{1}->equals(Set{1})              -> COMPILE-ERROR: probe:line 1:8 no viable alternative at input 'equals' |
EXPR  (1).equals(1)                       -> type=Boolean | eval=true : Boolean
EXPR  (1).equals(1).equals(true)          -> type=Boolean | eval=true : Boolean
```

`MEASURED`. In the fork the *only* reachable form is `X.equals(Y)` via `identicalExpression`; the
arrow form and the named-operation form are parse errors, because the keyword token prevents `IDENT`
from ever matching `equals`. Note also that the brief's phrasing "`a equals b`" does not exist — the
fork's concrete syntax is `a.equals(b)`, per `OCLBase.gpart:132`.

### 3.3 Two latent fork defects in the same rule

`identicalExpression` sits at the **top** of the precedence chain — `expression` → `identicalExpression`
→ `conditionalImpliesExpression` → … So `.equals(…)` binds looser than `implies`, `or`, `and`, `=`,
`<`, `+`, everything:

```
EXPR  (1).equals(1) and true              -> COMPILE-ERROR: probe:line 1:14 missing EOF at 'and' |
EXPR  (1).equals(1) implies true          -> COMPILE-ERROR: probe:line 1:14 missing EOF at 'implies' |
EXPR  1 + 2 .equals(3)                    -> type=Boolean | eval=true : Boolean
EXPR  true and (1).equals(1)              -> COMPILE-ERROR: probe:1:5: Undefined operation `Boolean.and(Integer)'. |
```

`MEASURED`.

* **D1 — right-composition is impossible.** `X.equals(Y) and Z` and `X.equals(Y) implies Z` do not
  parse at all. There is no way to use the result of `.equals` in a boolean expression except by
  wrapping the whole thing, e.g. `if X.equals(Y) then … endif` (which does parse).
* **D2 — left-composition silently mis-associates.** `true and (1).equals(1)` parses as
  `(true and 1).equals(1)`, not `true and ((1).equals(1))`. Here it happens to surface as a type
  error; where the types line up it would be a *silent wrong parse*.

`1 + 2 .equals(3)` returning `true` shows the same thing benignly: it is `(1+2).equals(3)`.

D1 is not hypothetical for this port — it is exactly the shape of the third upstream fixture
(`t133_import_datetime.use:12`, `self.date.equals(other.date) and self.time.before(other.time)`),
which would fail on the `and` even if the declaration problem were solved.

### 3.4 The collision, measured on the real fixtures

Complete inventory of `equals` in 7.5.0's model/test-fixture languages:

```
$ grep -rn "equals" --include="*.use" --include="*.soil" --include="*.cmd" --include="*.in" \
       --include="*.assl" --include="*.testsuite" . | grep -v reference-repositories
use-gui/src/it/resources/testfiles/shell/imports/t133_import_datetime.use:12:	    (self.date.equals(other.date) and self.time.before(other.time))
use-gui/src/it/resources/testfiles/shell/t098.use:11:	equals(t : Date1) : Boolean
use-gui/src/it/resources/testfiles/shell/t019.in:6:# tests evaluation of the equals method of Bags
use-gui/src/it/resources/testfiles/shell/t077.in:62:-- equalsIgnoreCase
use-gui/src/it/resources/testfiles/shell/t077.in:63:?'abcd'.equalsIgnoreCase('ABCD')
use-gui/src/it/resources/testfiles/shell/t077.in:65:?'abcd'.equalsIgnoreCase('AbcD')
use-gui/src/it/resources/testfiles/shell/t077.in:67:?'abcd'.equalsIgnoreCase('ABBD')
use-gui/src/it/resources/testfiles/shell/t077.in:69:?''.equalsIgnoreCase('')
use-gui/src/it/resources/testfiles/shell/t077.in:71:?'abcd'.equalsIgnoreCase('')
use-gui/src/it/resources/testfiles/shell/imports/t133_import_date.use:29:	equals(other:Date):Boolean =
```

`MEASURED`. Two of these are **not** collisions and should be struck from the risk list:

* `t019.in:6` — the word appears in a `#` comment.
* `t077.in:62-71` — **`equalsIgnoreCase` is safe.** ANTLR 3's lexer does maximal munch correctly and
  `IDENT` wins over the shorter literal. Measured on *both* implementations:

```
### FORK ###
EXPR  'abcd'.equalsIgnoreCase('ABCD')     -> type=Boolean | eval=true : Boolean
EXPR  'abcd'.equalsIgnoreCase('ABBD')     -> type=Boolean | eval=false : Boolean
EXPR  ''.equalsIgnoreCase('')             -> type=Boolean | eval=true : Boolean
### PLAIN 7.5.0 ###
EXPR  'abcd'.equalsIgnoreCase('ABCD')     -> type=Boolean | eval=true : Boolean
EXPR  'abcd'.equalsIgnoreCase('ABBD')     -> type=Boolean | eval=false : Boolean
EXPR  ''.equalsIgnoreCase('')             -> type=Boolean | eval=true : Boolean
```

`MEASURED`. That leaves exactly the three fixtures the brief names. Feeding the *real* file to the
fork's compiler:

```
$ java -cp … ForkProbe '@FILE:…/use-gui/src/it/resources/testfiles/shell/t098.use'
SPEC  t098.use -> COMPILE-ERROR: …/t098.use:line 11:1 no viable alternative at input 'equals' |
                                 …/t098.use:line 11:10 mismatched input ':' expecting ( |
                                 …/t098.use:line 11:17 no viable alternative at input ')' |
                                 …/t098.use:line 12:0 mismatched input 'end' expecting ( |
```

`MEASURED`. The collision is real and reproduced on the shipped fixture.

**Why this breaks a passing test rather than merely changing an error.** `t098.in` is a pure
negative test — its entire content is three expected-output lines:

```
$ nl -ba use-gui/src/it/resources/testfiles/shell/t098.in
     1	*t098.use:88:6: Class `LoyaltyAccount' already contains an operation named `isEmpty'.
     2	*t098.use:19:16: Expression `(result = self.partners->collect($e : ProgramPartner | $e.deliveredServices))' can never evaluate to true because `Set(Service)' and `Bag(Service)' are unrelated.
     3	*You can change this check using the -extendedTypeSystemChecks switch.
```

`MEASURED`. The test asserts the *exact* error text. Under the fork's grammar the parse dies at
line 11 and the expected line-88 semantic error is never reached, so the comparison fails.

Scope: `ShellIT` builds one `DynamicTest` per `.in` file
(`use-gui/src/it/java/org/tzi/use/main/shell/ShellIT.java:58-65`), and
`ls use-gui/src/it/resources/testfiles/shell/*.in | wc -l` = **129** — matching the brief. The two
affected drivers are `t098.in` and `t133_imports.in`
(`t133_imports.use:3-5` imports the Date/Time/DateTime chain).

### 3.5 The adaptation — keep the meaning, drop the collision

**Delete `identicalExpression`; leave `expression` calling `conditionalImpliesExpression` as 7.5.0
does; register the fork's `Op_identical` in the standard-operation map.**

The reason this works is that 7.5.0 already routes `X.equals(Y)` somewhere useful. On stock 7.5.0 the
failure is *semantic*, not syntactic:

```
EXPR  (1).equals(1)                       -> COMPILE-ERROR: probe:1:4: Undefined operation named `equals' in expression `Integer.equals(Integer)'. |
EXPR  Set{1}->equals(Set{1})              -> COMPILE-ERROR: probe:1:8: Undefined operation named `equals' in expression `Set(Integer)->equals(Set(Integer))'. |
EXPR  let equals : Integer = 1 in equals  -> type=Integer | eval=1 : Integer
EXPR  Tuple{equals:1}                     -> type=Tuple(equals:Integer) | eval=Tuple{equals=1}
EXPR  Set{1}->collect(equals | equals)    -> type=Bag(Integer) | eval=Bag{1} : Bag(Integer)
```

`MEASURED`. The expression parses and reaches the opmap lookup in `ExpStdOp.create`
(`ExpStdOp.java:104-131`); only the entry is missing. So supply the entry.

**Proof.** I added `ExpStdOp.addOperation(new Op_identical())` — a faithful transcription of the
fork's `StandardOperationsAny.java:130-179` — to a driver against `use-core/target/classes`, with
**no grammar edit whatsoever**:

```
### USE 7.5.0 + Op_identical registered, NO grammar change ###
EXPR  (1).equals(1)                       -> type=Boolean | eval=true : Boolean
EXPR  (1).equals(2)                       -> type=Boolean | eval=false : Boolean
EXPR  Set{1}->equals(Set{1})              -> type=Boolean | eval=true : Boolean
EXPR  (1).equals(1) and true              -> type=Boolean | eval=true : Boolean
EXPR  false and true.equals(false)        -> type=Boolean | eval=false : Boolean
EXPR  (1).equals(1) implies false         -> type=Boolean | eval=false : Boolean
EXPR  let equals : Integer = 1 in equals  -> type=Integer | eval=1 : Integer
EXPR  Tuple{equals:1}                     -> type=Tuple(equals:Integer) | eval=Tuple{equals=1}
EXPR  Set{1}->collect(equals | equals)    -> type=Bag(Integer) | eval=Bag{1} : Bag(Integer)
EXPR  'a'.equals('a')                     -> type=Boolean | eval=true : Boolean
EXPR  Undefined.equals(Undefined)         -> type=Boolean | eval=true : Boolean
SPEC  eq.use                              -> OK model=M
SPEC  t098.use                            -> COMPILE-ERROR: …/t098.use:88:6: Class `LoyaltyAccount' already contains an operation named `isEmpty'. |
SPEC  t133_import_date.use                -> OK model=Date
```

`MEASURED`. Read that against the baseline (same driver, registration removed):

```
### BASELINE 7.5.0, no registration ###
SPEC  eq.use                   -> OK model=M
SPEC  t098.use                 -> COMPILE-ERROR: …/t098.use:88:6: Class `LoyaltyAccount' already contains an operation named `isEmpty'. |
SPEC  t133_import_date.use     -> OK model=Date
```

`MEASURED`. **t098 produces byte-identical output with and without the adaptation** — the `isEmpty`
error is pre-existing and is precisely what `t098.in` line 1 expects. Zero regression.

Three-way test of the `t133_import_datetime.use:12` call shape, transcribed into a self-contained
model (the real file needs import resolution my string-based driver cannot supply — see §7):

```
model DT
class Date     attributes d : Integer operations equals(other:Date):Boolean = self.d = other.d end
class Time     attributes t : Integer operations before(other:Time):Boolean = self.t < other.t end
class DateTime
attributes date : Date  time : Time
operations
  -- call shape copied verbatim from t133_import_datetime.use:12
  before(other:DateTime):Boolean =
    (self.date.equals(other.date) and self.time.before(other.time))
end
```

```
### BASELINE 7.5.0 ###                    SPEC  dt.use -> OK model=DT
### 7.5.0 + Op_identical registered ###   SPEC  dt.use -> OK model=DT
### FORK ###                              SPEC  dt.use -> COMPILE-ERROR: line 7:2 mismatched input 'equals' expecting 'end' |
```

`MEASURED`.

### 3.6 User-defined `equals` is not shadowed

The obvious hazard: two of the three fixtures declare `equals` as a *user* operation. Does a
registered standard `equals` hijack the call? **No.** `ASTOperationExpression.genObjOperation`
(`ASTOperationExpression.java:606-665`) resolves the user `MOperation` first and only falls through
at `:660-661`:

```java
        } else {
            // try standard operation
            res = genStdOperation(ctx, fOp, opname, fArgExprs);
        }
```

`READ-FROM-SOURCE`. Measured with a discriminating return type — if the standard op won, the body
would be `Boolean` and the declared `Integer` return would not type-check:

```
model Shadow
class D
attributes d : Integer
operations
  equals(o : D) : Integer = 42
  probe(o : D) : Integer = self.equals(o)
end
```

```
### 7.5.0 + registered standard equals ###   SPEC  shadow.use -> OK model=Shadow
### baseline 7.5.0 (no registration) ###     SPEC  shadow.use -> OK model=Shadow
```

`MEASURED`. Identical. The user operation wins in both.

Note this is also a **behaviour change relative to the fork, in the port's favour**: the fork cannot
dispatch to a user-defined `equals` at all, because the token prevents the operation-call parse. The
port matches 7.5.0's dispatch, which is what the policy asks for.

### 3.7 What the adaptation gains

| property | fork | port under this adaptation |
|---|---|---|
| `X.equals(Y)` crisp Boolean equality | yes | yes (`MEASURED`, §3.5) |
| `X->equals(Y)` arrow form | parse error | works (`MEASURED`) |
| `X.equals(Y) and Z` (defect D1) | parse error | works (`MEASURED`) |
| `A and X.equals(Y)` associativity (defect D2) | mis-parses as `(A and X).equals(Y)` | correct — binds at postfix level (`MEASURED`: `false and true.equals(false)` → `false`) |
| dispatch to a user-defined `equals` | impossible | works, user op wins (`MEASURED`, §3.6) |
| `equals` as let/tuple/iterator/operation name | reserved word | ordinary identifier (`MEASURED`) |
| `t098.in`, `t133_imports.in` | broken | unchanged from baseline (`MEASURED`) |
| reserved words added | 1 × 6 lexers | 0 |

D1 and D2 are B7-class defects; this adaptation **fixes** them rather than reproducing them, which is
what B7 requires.

---

## 4. Every literal form the fork accepts

### 4.1 The forms, with measured arity

Fixed arity, enforced by the grammar; no optional or variadic forms exist.

| # | form | arity | arg-1 sub-grammar | arg-n sub-grammar | AST node | grammar line |
|---|---|---|---|---|---|---|
| L1 | `UReal(v, u)` | exactly 2 | `additiveExpression` | `additiveExpression` | `ASTURealLiteral` | `OCLBase.gpart:496` |
| L2 | `UInteger(v, u)` | exactly 2 | `additiveExpression` | `additiveExpression` | `ASTUIntegerLiteral` | `:498` |
| L3 | `UBoolean(v, c)` | exactly 2 | **`conditionalImpliesExpression`** | `additiveExpression` | `ASTUBooleanLiteral` | `:497` |
| L4 | `UString(s, c)` | exactly 2 | `additiveExpression` | `additiveExpression` | `ASTUStringLiteral` | `:495` |
| L5 | `SBoolean(b, d, u, a)` | exactly 4 | `additiveExpression` | `additiveExpression` | `ASTSBooleanLiteral` | `:499-500` |

Arity measured:

```
EXPR  UReal(2.5, 0.1)              -> type=UReal    | eval=UReal(2.5, 0.1) : UReal
EXPR  UReal(2, 0)                  -> type=UReal    | eval=UReal(2.0, 0.0) : UReal
EXPR  UReal(2.5)                   -> COMPILE-ERROR: probe:line 1:9 mismatched input ')' expecting , |
EXPR  UReal(1,0.1,0.2)             -> COMPILE-ERROR: probe:line 1:11 mismatched input ',' expecting ) |
EXPR  UInteger(5, 0.2)             -> type=UInteger | eval=UInteger(5, 0.2) : UInteger
EXPR  UInteger(5)                  -> COMPILE-ERROR: probe:line 1:10 mismatched input ')' expecting , |
EXPR  UBoolean(true, 0.9)          -> type=UBoolean | eval=UBoolean(true, 0.9) : UBoolean
EXPR  UBoolean(true)               -> COMPILE-ERROR: probe:line 1:13 mismatched input ')' expecting , |
EXPR  UString('abc', 0.95)         -> type=UString  | eval=UString('abc', 0.95) : UString
EXPR  UString('abc')               -> COMPILE-ERROR: probe:line 1:13 mismatched input ')' expecting , |
EXPR  SBoolean(0.8, 0.1, 0.1, 0.5) -> type=SBoolean | eval=SBoolean(0.8, 0.1, 0.1, 0.5) : SBoolean
EXPR  SBoolean(true)               -> COMPILE-ERROR: probe:line 1:13 mismatched input ')' expecting , |
EXPR  SBoolean(0.8,0.1,0.1)        -> COMPILE-ERROR: probe:line 1:20 mismatched input ')' expecting , |
```

`MEASURED`. **The 1-argument constructors documented in the fork's own Java README
(`uDataTypes/Libraries/Java/README.md:126` `SBoolean(b:Boolean)`, `:198` `UString(s:String, c:Real)`)
are not reachable from OCL concrete syntax.** Only the arities above exist.

### 4.2 The argument sub-grammar asymmetry — a real, load-bearing detail

`UBoolean`'s first argument is `conditionalImpliesExpression`; every other argument position in every
form is `additiveExpression`. Since `additiveExpression` sits *below* `relationalExpression` in the
precedence chain, comparison and boolean operators are rejected there:

```
EXPR  UBoolean(1 < 2 and 3 > 2, 0.9)               -> type=UBoolean | eval=UBoolean(true, 0.9) : UBoolean
EXPR  UReal(1 < 2, 0.1)                            -> COMPILE-ERROR: probe:line 1:8 mismatched input '<' expecting , |
EXPR  UBoolean(true, 1 < 2)                        -> COMPILE-ERROR: probe:line 1:17 missing ) at '<' |
EXPR  UReal(if true then 1 else 2 endif, 0.1)      -> type=UReal | eval=UReal(1.0, 0.1) : UReal
EXPR  UReal(1, if true then 0.1 else 0.2 endif)    -> type=UReal | eval=UReal(1.0, 0.1) : UReal
EXPR  UReal(1+1, 0.1*2)                            -> type=UReal | eval=UReal(2.0, 0.2) : UReal
```

`MEASURED`. `if…endif` works in any position because `ifExpression` is reached through
`primaryExpression`, below `additiveExpression`. The port must reproduce the *accepting* cases; the
rejecting cases are grammar accidents, and the predicate-gated rule of §2.4 (which would use full
`expression` for every argument) would accept strictly more. That is a deliberate, documentable
widening, not a semantic change — no corpus entry relies on `UReal(1 < 2, …)` being rejected.

### 4.3 Five verbatim corpus examples per form

The corpus is the fork's four expression files under
`.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/`
plus the model corpus at `.git/reference-repositories/uncertainty/uDataTypes/`.

Cross-check on **B6** — the corpus files reproduce the 79 figure exactly:

```
lines whose RESULT is Undefined:
  UBooleanExpression.in        16
  UCollectionOperations.in      0
  UIntegerExpression.in        38
  URealExpression.in           25
  TOTAL = 79
```

`MEASURED` (`grep -cE '^-> Undefined'`).

**L1 — `UReal(v, u)`** (1590 genuine literal occurrences repo-wide):

```
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/URealExpression.in:7:UReal(2, 0)
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/URealExpression.in:13:UReal(2, -2)
uDataTypes/CaseStudies/OzobotRobots/UMovingRobot.soil:23:!m4.rotate:=UReal(5.0, 0.01) * UReal(3.141592653589793,0.01) / UReal(4.0, 0.01)
uDataTypes/CaseStudies/RobotBattle/RobotBattle.use:65:		UReal(1.570796326791001,y.uncertainty())
uDataTypes/CaseStudies/Drones/UDrones.use:26:	  if ((y >= UReal(0.0, 0.0)).value()) then
```

**L2 — `UInteger(v, u)`** (926):

```
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in:7:UInteger(-5, 0.0)
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in:13:UInteger(-5, -0.5)
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UIntegerExpression.in:19:UInteger(-5, -5)
uDataTypes/CaseStudies/Drones/UDrones_2.soil:10:!c.now := UInteger(1524199495,0.1)
uDataTypes/CaseStudies/Drones/UDrones_5_step1.soil:102:!c.now := a + UInteger(1,0.0)
```

**L3 — `UBoolean(v, c)`** (181). Note lines 7, 10, 13, 16 exercise exactly the arg-1/arg-2
asymmetry of §4.2 — arg 1 takes `or`/`and`, arg 2 takes only additive expressions:

```
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in:7:UBoolean(3 + 2, 1)
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in:10:UBoolean(3 / 0, 1)
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in:13:UBoolean(true or false, UReal(2, 3))
USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in:16:UBoolean(true and false, 3 / 0)
uDataTypes/Libraries/OCLTypes-USE4.2/Collections_extended.txt:7:source->iterate(e, acc:UBoolean=UBoolean(true, 1.0) | acc.uAnd(P(e)))
```

**L4 — `UString(s, c)` — GAP, five examples do not exist.** There is **exactly one** genuine
occurrence in the entire reference corpus:

```
uDataTypes/CaseStudies/TrafficControl/Traffic.use:53:	pic.numberPlate := UString('1243ABC',0.95); 
```

**L5 — `SBoolean(b, d, u, a)` — GAP, there are zero.** Not one 4-argument `SBoolean` literal exists
in any `.use`, `.soil`, `.in`, `.cmd`, `.txt` or `.assl` file in the reference repositories. The only
statements of the form are prose in the fork's Java README:

```
uDataTypes/Libraries/Java/README.md:13:… a binomial opinion about a given fact X by a belief agent A is represented as a quadruple ``SBoolean(b,d,u,a)`` where
uDataTypes/Libraries/Java/README.md:128:    SBoolean(b:Real, d:Real, u:Real, a:Real) 
```

Counts, by literal form and file type (`!new` object-creation excluded — see the trap below):

```
--- UReal ---      1314 .in   239 .soil   19 .txt   18 .use     (1590 total)
--- UInteger ---    905 .in    12 .soil    5 .txt    4 .use      (926 total)
--- UBoolean ---    175 .in     0 .soil    5 .txt    1 .use      (181 total)
--- UString ---       0 .in     0 .soil    0 .txt    1 .use      (  1 total)
--- SBoolean ---      0 .in     0 .soil    0 .txt    0 .use      (  0 total)
```

`MEASURED`. The `.in` column being 0 for `UString`/`SBoolean` independently confirms
`docs/port2/audit-03-acceptance.md:522` ("`UString`/`SBoolean` absent from the corpus … `grep -c` is
0 in all four `.in` files"). **This is a real coverage gap for B2** (SBoolean ported in full,
39 operations): the concrete syntax for constructing an `SBoolean` has no corpus witness at all, so
the port's `SBoolean` literal cannot be validated against the historical oracle by replaying corpus
entries. It needs a purpose-built differential sweep.

**A counting trap, recorded so nobody re-derives it wrongly.** Naive `grep -c 'UBoolean('` reports
208, `'UString('` 7 and `'SBoolean('` 6 — but 26, 6 and 6 of those respectively are SOIL **object
creation**, not OCL literals:

```
uDataTypes/Libraries/OCLTypes-USE4.2/UStringTest.soil:2:!new UString('s1')
uDataTypes/Libraries/OCLTypes-USE4.2/SBoolean.soil:2:!new SBoolean('o1')
uDataTypes/Libraries/OCLTypes-USE4.2/OCLTypes.soil:2:!new UBoolean('t')
```

These come from `uDataTypes/Libraries/OCLTypes-USE4.2/`, which models the U-types as ordinary UML
**classes** for *stock* USE 4.2 — a pure-OCL alternative to the built-in extension. They exercise
`SoilBase.gpart`'s `new` statement, never `literal`. Indeed they cannot even be loaded by the fork,
because the fork reserved the very names the library uses for classes:

```
$ java -cp … ForkProbe '@FILE:…/uDataTypes/Libraries/OCLTypes-USE4.2/OCLTypes.use'
SPEC  OCLTypes.use -> COMPILE-ERROR: …/OCLTypes.use:line 9:6 no viable alternative at input 'UString' |
```

`MEASURED`. The fork's binary cannot read its own companion library. Under the adaptation of §2.5
(names resolved by map, not reserved) this self-inconsistency disappears.

---

## 5. 7.5.0 grammar changes since 2015, and whether the fork's additions conflict

Produced by diffing the fork (= 2015 upstream + uncertainty) against 7.5.0, CR-normalised, then
subtracting the fork's own hunks from §2.

### 5.1 Changes inside `OCLBase.gpart` — the file the fork edits

| # | 7.5.0 change | 7.5.0 location | collides with a fork addition? |
|---|---|---|---|
| C1 | `operationExpression` gained a model-qualifier alternative `(modelQualifier=IDENT HASH name=IDENT \| name=IDENT)` | `grammars/base/OCLBase.gpart:367-369` | **No.** The fork does not touch `operationExpression`. **But it interacts with the G1 fix**: this is the rule that now carries `equals`, and it is where the model-qualified form `M#equals(…)` becomes available for free. |
| C2 | `literal` gained `modelQualifier=IDENT HASH enumName=IDENT '::' enumLit=IDENT` | `:484-485` | **No.** The fork's five U-literals start with distinct tokens; 7.5.0's alternative is IDENT-initial. Both can coexist. Under the predicate form of §2.4 the port's alternative is *also* IDENT-initial, so ordering and the `input.LA(2) == LPAREN` guard matter — the enum form's second token is `HASH`, not `LPAREN`, so the guard separates them. `INFERRED`. |
| C3 | `type` gained `\| modelQualifiedType`, and rule `modelQualifiedType ::= IDENT HASH IDENT` | `:600, 610, 670-677` | **Yes — direct slot conflict.** The fork adds `\| uncertaintyType` as the fourth alternative of `type`; 7.5.0 already put `modelQualifiedType` there. §2.5 dissolves this: delete `uncertaintyType` and the slot is uncontested. |
| C4 | `inStateExpression` gained `'oclIsInState'` as an alias and an `@init` token capture | `:399-409` | **No.** Untouched by the fork. Must be kept. |
| C5 | `collectionType` dropped its `if ($n != null)` guard | `:643` | **No** — convergent, the fork made the same change. See §2.6. |

### 5.2 Changes in the other `.gpart` files the fork never edited

The fork's additions live only in `OCLBase.gpart` (§2.1), so these merge without textual conflict —
but they matter because they define the *token namespace* that a reserved word would invade.

| # | 7.5.0 change | location | interaction |
|---|---|---|---|
| C6 | `importStatement` (`import … from "…"`), `elementIdent ::= [id "#"] id`, model import wiring | `grammars/base/USEBase.gpart:9-37, 48-61` | **This is what makes B4 bite twice.** The `t133` fixture chain exists only because of imports; two of the three `equals` collisions are inside it. |
| C7 | `dataTypeDefinition` (`[abstract] dataType id … end`) | `grammars/base/USEBase.gpart:86-113` | `t133_import_date.use:29` declares `equals` on a **dataType**, a construct that did not exist in 2015. The fork could not have anticipated it. |
| C8 | `ShellCommandBase`: `objType = simpleType` → `objType = objectType`, new `objectType ::= (IDENT HASH)? IDENT` | `grammars/base/ShellCommandBase.gpart:106, 123-130` | **No conflict**, but note it is another IDENT-initial rule that the fork's reserved words would have blocked for the names in question. |
| C9 | `TestSuite.gpart`: one comment line removed (`['finish' cmdList 'end']`) | `grammars/testsuite/TestSuite.gpart:87` | Cosmetic. |
| C10 | `SoilBase.gpart`: `$Id$` unexpanded | `:26` | Cosmetic; the fork's copy has the expanded keyword (§2.1). |

### 5.3 Would the fork's reserved words collide with 7.5.0 fixtures?

Only `equals` does. Exhaustive search of the model/test-fixture languages:

```
$ grep -rn "\bUReal\b\|\bUInteger\b\|\bUBoolean\b\|\bUString\b\|\bSBoolean\b" \
       --include="*.use" --include="*.soil" --include="*.cmd" --include="*.in" \
       --include="*.assl" --include="*.testsuite" . | grep -v reference-repositories
(no matches)
```

`MEASURED` — every hit for those names in the repository is in `.java` test code under
`use-core/src/test/java/org/tzi/use/uncertainty/differential/`, which the grammar does not read.
Likewise `uSelect`/`uSelectC` have no fixture hits.

So the U-type reservation has **zero present collision surface** in 7.5.0. It is still worth removing
(§2.4, §2.5) because it is (a) unnecessary — 7.5.0 has a better mechanism, (b) a standing hazard for
any future model, and (c) demonstrably self-defeating (§4.3, the fork cannot load its own library).
The mechanism, for the record:

```
model U
class UReal
attributes
  v : Real
end
```
```
### 'class UReal' under FORK ###             -> COMPILE-ERROR: line 2:6 no viable alternative at input 'UReal' |
### 'class UReal' under BASELINE 7.5.0 ###   -> OK model=U
```

`MEASURED`.

---

## 6. Net grammar delta for the port

| | fork | port under this policy |
|---|---|---|
| `.gpart` files changed | 1 (`base/OCLBase.gpart`) | 1 (`base/OCLBase.gpart`) |
| new parser rules | 2 (`identicalExpression`, `uncertaintyType`) | 1 (`uncertaintyLiteral`) |
| existing rules altered | 4 (`expression`, `queryExpression`, `literal`, `type`) | 2 (`queryExpression`, `literal`) |
| executable grammar lines added | 31 | ~10 `INFERRED` |
| reserved words added | 6 | **0** |
| reservation sites across the six lexers | 36 | **0** |
| non-grammar work this displaces onto | — | `TypeFactory.buildInTypesMap` (+5 entries), `ExpStdOp`/`StandardOperationsAny` (+1 op), `ParserHelper` (+1 ident map) |
| upstream fixtures broken | 2 of 129 `ShellIT` | 0 (`MEASURED`) |
| fork parse defects carried forward | D1, D2 | 0 — both fixed (`MEASURED`) |

`expression` and `expressionOnly` stay byte-identical to 7.5.0, which removes the port's largest
merge surface in this file.

---

## 7. Gaps, caveats and what remains unverified

1. **The predicate-gated `literal` rule of §2.4 was not built.** Regenerating the parser needs
   Maven + the ANTLR plugin, which this area does not own. The *need* for a grammar rule is
   `MEASURED` (§2.4, `UReal(2, 0.5)` → `Undefined operation`); the *shape* is `INFERRED` from the
   `isQueryIdent` precedent at `OCLBase.gpart:309/322` plus the measured fact that predicate-gated
   idents stay unreserved. **Must be validated at build time**, including the ANTLR 3 lookahead
   interaction between the new IDENT-initial `literal` alternative and the existing
   `enumName=IDENT '::' enumLit=IDENT` alternative and `propertyCall`.
2. **`SBoolean` literal syntax has no corpus witness at all** (§4.3: 0 occurrences) and `UString`
   has exactly 1. B2 ports SBoolean in full (39 operations), but its *concrete syntax* cannot be
   validated by corpus replay. A purpose-built differential sweep is required, and the `UString`
   result must not be generalised from a single data point.
3. **The `TypeFactory` proof used a stand-in Type instance.** `RealType` is `final`, so the probe
   mapped `"UReal"` to the existing `RealType`. This establishes the name-resolution path only. The
   `type=Real` in that output is the stand-in, not a claim about the port's type semantics.
4. **The real `t133` import chain was not compiled.** `USECompiler.compileSpecification(String, …)`
   leaves `Context.getfFileUri()` null, so import resolution throws
   `NullPointerException: Cannot invoke "java.net.URI.toString()"` — identically with and without the
   adaptation, so it yields no signal either way. §3.5 substitutes a self-contained transcription of
   the `t133_import_datetime.use:12` call shape. `UNVERIFIABLE` by this harness; verify by running
   `ShellIT` when the port builds.
5. **`ShellIT` was not executed.** The 129 figure is `ls …/testfiles/shell/*.in | wc -l` combined
   with `ShellIT.java:58-65` (one `DynamicTest` per `.in`). Whether all 129 currently pass on this
   branch is outside this area and unmeasured here.
6. **Only `equals` was checked for maximal-munch safety.** `equalsIgnoreCase` is measured safe
   (§3.4). If the port were to keep any implicit token after all, every identifier in the fixtures
   that has that token as a prefix would need the same check.
7. **`spec-parts/13-grammar.md` §13.0 needs correcting** (§1.2): it states the CRLF direction
   backwards. Its arithmetic elsewhere (8 hunks / 35 added / 8 removed / 31 executable) describes the
   fork's patch applied to 7.5.0 and is consistent with §2 here; only the line-ending attribution is
   wrong.

---

## Appendix — reproducing every measurement

Fork (historical implementation):

```bash
L=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CP="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar:$L/guava-20.0.jar"
javac -cp "$CP" -d out ForkProbe.java && java -cp "out:$CP" ForkProbe '<expr>' '@FILE:<model.use>'
```

Note: in the fork, `VarBindings` is `org.tzi.use.uml.ocl.value.VarBindings`, **not**
`org.tzi.use.parser.base.VarBindings` as the brief's recipe suggests. Verified:

```
$ unzip -l $L/use.jar | grep -i VarBindings
     1390  2021-02-24 20:09   org/tzi/use/uml/ocl/value/VarBindings$Entry.class
     2948  2021-02-24 20:09   org/tzi/use/uml/ocl/value/VarBindings.class
```

Plain USE 7.5.0 and the two adaptation probes:

```bash
M=~/.m2/repository
BCP="/home/xoruser/msc-4/use-msc2026/use-core/target/classes:\
$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:\
$M/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar:\
$M/jline/jline/2.14.6/jline-2.14.6.jar"
javac -cp "$BCP" -d outbase  BaseProbe.java   # stock 7.5.0
javac -cp "$BCP" -d outadapt EqAdapt.java     # + ExpStdOp.addOperation(new Op_identical())
javac -cp "$BCP" -d outtype  TypeAdapt.java   # + TypeFactory.buildInTypesMap.put("UReal", …)
```

`failureaccess-1.0.1.jar` is required in addition to guava for `USECompiler.compileSpecification`;
without it model compiles die with
`NoClassDefFoundError: com/google/common/util/concurrent/internal/InternalFutureFailureAccess`.

Drivers live in `/tmp/probe-grammar/` (scratch, outside the repo). No Maven was run. No file outside
`docs/port2/adaptation/` was modified. The reference repositories were read only.
