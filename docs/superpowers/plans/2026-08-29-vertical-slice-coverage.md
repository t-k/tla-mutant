# TLA Mutant Vertical Slice Coverage Ledger

## Issue Draft

The repository has a detailed product idea but no implementation. Build the smallest trustworthy experiment that can determine whether GCD-01, BRL-01, and UOI-01 produce state-expanding model mutants that survive configured state invariants and lead to useful invariant-improvement candidates across 10–20 public TLA+ configurations.

The experiment must not overstate a candidate as a genuine requirement defect, mutate operators shared by the model and its properties, treat weakened properties as sensitivity probes without an expected relation, or execute public corpus input directly on the host.

## Source Investigation Summary

The repository began with no commits, build manifest, source code, tests, or CI. The source of truth is the product draft in `docs.local/IDEA.md`, the review log in `docs.local/logs/2026-08-29/2026-08-29-001-idea-significance-review.md`, and the approved design in `docs/superpowers/specs/2026-08-29-vertical-slice-design.md`.

Official TLA+ Tools source at commit `9787e65714c37d94eebab40774bff401bd9f616d` confirms that SANY exposes semantic nodes, levels, locations, and operator references; TLC supports tool-mode message codes and custom zero-argument `IStateWriter` implementations; TLC states expose complete variable values; and repeated TLC checks should run in separate JVM processes.

## Code Evidence Log

| Evidence ID | Type | Evidence | Implication |
|---|---|---|---|
| E001 | evidence | `docs.local/IDEA.md:755-827` defines GCD-01, BRL-01, and UOI-01. | Candidate generation and exclusion rules require direct tests. |
| E002 | evidence | `docs.local/IDEA.md:1034-1072` limits stable mutators to semantically clear positive guard and primed-RHS contexts. | Negative polarity and ambiguous contexts are coverage obligations. |
| E003 | evidence | `docs.local/IDEA.md:1113-1128` requires TLC tool-mode classification. | Human prose parsing cannot be the primary verdict source. |
| E004 | evidence | `docs.local/IDEA.md:1240-1301` separates execution, oracle, semantic relation, and final verdict. | Tests must preserve orthogonal result axes. |
| E005 | evidence | `docs.local/IDEA.md:1440-1553` requires complete-state audit and forbids fingerprint-only equality. | Canonicalization and collision verification are critical. |
| E006 | evidence | `docs.local/IDEA.md:2290-2335` defines mutation quality and reproducibility thresholds. | Corpus reports need denominators, rates, and pinned inputs. |
| E007 | evidence | The approved design requires model/property/scope role reachability and excludes Shared/Coupled definitions. | Semantic-role slicing is a release blocker. |
| E008 | evidence | The security review requires public input to run only in a closed rootless Podman sandbox. | Sandbox containment needs independent tests and fail-closed behavior. |
| E009 | inference | Public specifications often use helper definitions and multiple modules. | The corpus must record dependency hashes and exclusion reasons, not silently skip sites. |
| E010 | assumption | Actionable invariant improvements can be judged from public specification context. | Agent review is only a proxy until a human validates the candidates. |

## Specification Delta

- S01: The final verdict name is `state-invariant-gap-candidate`, not a genuine-defect assertion.
- S02: Only definitions whose role membership is exactly `{model}` are eligible for model mutation.
- S03: Property Probe is separated into wiring, contribution, and vacuity categories with explicit expected outcomes.
- S04: GCD-01, BRL-01, and UOI-01 are the only implemented mutator families.
- S05: Every mutation changes one semantic site and one source range, reruns SANY, and never changes the original checkout.
- S06: TLC results are classified from tool-mode codes, process state, and validated state output.
- S07: State equality uses complete canonical values plus SHA-256 identities, never TLC's fingerprint alone.
- S08: Public corpus execution occurs only in the pinned, resource-limited Podman sandbox.
- S09: Every corpus input, tool, image, policy, and output is content-addressed and reproducible.
- S10: Automated candidate review is labeled as a proxy and cannot establish a natural-language requirement defect.

## Invariants

- I01: A Property-only, scope-only, Shared/Coupled, unreachable, or ambiguous site is never executed as a Model Mutation.
- I02: A generated mutant contains exactly one non-empty source replacement.
- I03: Invalid syntax, invalid semantics, evaluation error, timeout, out-of-memory, sandbox rejection, integrity failure, and incomplete output are neither killed nor survived.
- I04: Two states are equal only when their complete canonical representations are byte-equal after hash lookup.
- I05: A gap candidate always contains a mutant-only complete-state witness.
- I06: A state-equal or state-restricting survivor is never a gap candidate.
- I07: No public corpus byte is parsed or executed on the host.
- I08: Every started corpus container is removed after success, failure, timeout, or cancellation.
- I09: Manifest limits cannot weaken the global execution policy, and missing or unenforceable limits fail closed.
- I10: Reports do not contain host absolute paths, credentials, uncontrolled terminal sequences, or unbounded diagnostics.

## Partition Axes and Constraints

| Axis | Values |
|---|---|
| Semantic role | Model-only, Property-only, scope-only, Shared/Coupled, unreachable, ambiguous INSTANCE |
| Expression level | Constant, state, action, temporal |
| Mutation family | GCD, BRL, UOI |
| GCD context | Top-level positive state guard, nested positive, negative, primed assignment, successor constraint, conditional branch |
| BRL operator | `<`, `>`, `<=`, `>=`, property comparison, EXCEPT context, primed context |
| UOI RHS | Numeral, integer arithmetic, `x + 1`, unknown domain, unprimed equality |
| TLC outcome | Completed pass, invariant violation, syntax error, semantic error, evaluation error, timeout, OOM, tool error |
| State relation | Equal, expanding, restricting, incomparable, unresolved |
| Value form | Integer, Boolean, string, model value, set, tuple, record, function, nested value |
| Sandbox outcome | Success, input rejection, integrity rejection, resource limit, timeout, ignored termination, output flood |

Constraints:

- Only Model-only sites can pair with an executable mutation family.
- GCD and BRL require positive current-state guard context.
- UOI requires a primed assignment RHS with syntactically established integer arithmetic.
- Public corpus cases always use the sandbox runner; repository fixture cases may use the trusted local runner.
- A relation is computed only when baseline and mutant exploration both complete with validated canonical state output.

## Coverage Obligations

| ID | Type | Required behavior or risk | Risk | Minimum checks | Preferred verification | Status |
|---|---|---|---|---|---|---|
| O01 | specification | Use the candidate verdict terminology and preserve its bounded meaning. | Critical | 2 | report unit test + end-to-end fixture | covered |
| O02 | invariant | Exclude every non-Model-only semantic role. | Critical | 2 | graph unit test + real SANY fixture | partial |
| O03 | mutation | GCD changes only a positive top-level current-state guard conjunct. | High | 2 | candidate table + golden patch/SANY | partial |
| O04 | mutation | BRL changes only `<` to `<=` and `>` to `>=` in eligible guards. | High | 2 | candidate table + golden patch/SANY | partial |
| O05 | mutation | UOI changes only supported integer primed RHS forms. | High | 2 | candidate table + golden patch/SANY | partial |
| O06 | invariant | One mutant has one non-empty source replacement. | Critical | 2 | patch property test + golden diff | covered |
| O07 | compatibility | Patched modules remain SANY-valid or are classified invalid outside the score. | High | 1 | real SANY integration | covered |
| O08 | failure_mode | TLC tool-mode messages and process termination map to orthogonal result axes. | Critical | 2 | reducer unit tests + real TLC fixtures | partial |
| O09 | invariant | Type-only kills are not meaningful invariant kills. | High | 1 | real TLC rerun fixture | deferred |
| O10 | invariant | Complete state canonicalization is deterministic for every supported value form. | Critical | 2 | golden values + repeated TLC run | covered |
| O11 | invariant | Hash collisions cannot establish false equality. | Critical | 1 | forced-collision analyzer test | covered |
| O12 | specification | Equal, expanding, restricting, incomparable, and unresolved relations classify correctly. | Critical | 2 | pure set tests + real TLC fixtures | partial |
| O13 | invariant | Every gap candidate contains a mutant-only state witness. | Critical | 2 | analyzer unit test + end-to-end fixture | covered |
| O14 | security | Manifest paths, source blobs, and module resolution remain in the closed input set. | Critical | 2 | adversarial filesystem tests + sandbox integration | partial |
| O15 | security | Java/TLC launch ignores hostile environment, classpath, override, and filename input. | Critical | 2 | ProcessBuilder contract test + sandbox integration | partial |
| O16 | security | Sandbox resource and output limits fail closed. | Critical | 2 | policy unit test + Podman containment test | partial |
| O17 | lifecycle | The exact started container and child process tree are reaped in every terminal path. | Critical | 2 | controlled timeout + post-run inspection | partial |
| O18 | reproducibility | Corpus, toolchain, image, policy, and generated artifacts have verified identities. | High | 2 | manifest unit test + repeated experiment | covered |
| O19 | experiment | At least 10 configurations across three domains pass baseline validation. | High | 1 | pinned corpus run | covered |
| O20 | experiment | Corpus metrics have explicit denominators and all exclusions/unresolved outcomes have reasons. | High | 1 | report consistency test | covered |
| O21 | observability | JSON and Markdown reports are bounded and neutralize hostile text. | High | 2 | serializer test + malicious fixture | covered |
| O22 | debt | Safely reproduce an actual JVM out-of-memory termination. | Medium | 0 | deferred bounded sandbox scenario | debt |

## Coverage Ledger

| Obligation | Primary artifact | Secondary artifact | Overlap | Current status | Debt or note |
|---|---|---|---|---|---|
| O01–O07 | semantic and mutator unit tests | real SANY fixture suite | layer_overlap | partial | Core positive path is covered; negative-context and ambiguous-INSTANCE matrices remain incomplete. |
| O08–O09 | TLC result reducer tests | real TLC integration suite | layer_overlap | partial | Pass, invariant kill, and timeout are covered; type-only attribution was not implemented. |
| O10–O13 | canonical state/relation tests | real TLC relation fixture suite | layer_overlap | covered | OOM remains O22 debt unless safe. |
| O14–O18 | input, policy, and runner tests | rootless Podman sandbox suite | intentional_overlap | partial | Real success containment and cleanup pass; the full hostile-input/resource matrix remains debt. |
| O19–O20 | corpus manifest validation | pinned corpus experiment | layer_overlap | covered | Ten configurations complete reproducibly; both unresolved outcomes carry TLC tool-error status and event codes. |
| O21 | report serializer tests | corpus report inspection | layer_overlap | covered | Both individual and corpus Markdown paths share bounded neutralization and hostile-input coverage. |
| O22 | none | none | unique | debt | Must not risk host stability merely to reproduce OOM. |

## Test Scenarios

- T001: A helper reachable only from `Next` is Model-only and eligible.
- T002: A helper reachable from both `Next` and `Invariant` is Shared/Coupled and excluded with both root paths.
- T003: Property-only, scope-only, unreachable, and ambiguous instantiated definitions are excluded with precise reasons.
- T004: GCD replaces exactly one eligible guard conjunct with `TRUE`; negative, nested, primed, and successor conjuncts produce no candidate.
- T005: BRL mutates eligible `<` and `>` tokens; `<=`, `>=`, property, EXCEPT, and primed contexts produce no candidate.
- T006: UOI mutates numeral and integer-arithmetic primed RHS forms; unknown and unprimed forms produce no candidate.
- T007: UTF-8, CRLF, comments, multiline expressions, and adjacent operators retain all bytes outside the replacement range.
- T008: Passing, invariant-violating, syntax-invalid, semantic-invalid, evaluation-error, timed-out, and tool-failed runs remain distinct.
- T009: A TypeOK-only kill passes the target-invariant rerun and is reported separately.
- T010: Reordered sets, records, and functions produce the same canonical state; distinct typed values do not collide.
- T011: Equal, strict-superset, strict-subset, and incomparable small TLC models produce their expected relation.
- T012: A weak invariant plus GCD or BRL yields a mutant-only state witness and `state-invariant-gap-candidate`.
- T013: Traversal paths, symlinks, special files, case collisions, unlisted modules, and digest mismatches are rejected.
- T014: Hostile Java variables, fake executables, arbitrary classpath entries, Java overrides, and option-like filenames do not affect the command.
- T015: Network attempts, output floods, inode exhaustion, scratch escape, ignored termination, timeout, success, and failure leave no container behind.
- T016: A repeated pinned-corpus run produces the same candidate list and semantic results, or records a bounded runtime-only difference.
- T017: Malicious diagnostic text cannot inject ANSI behavior, raw HTML, code-fence termination, bidirectional text, NUL, or host paths into reports.

## Optional Verification Suggestions

- Real SANY/TLC integration is `executed-by-request`: the user explicitly requested the bounded experiment, so no additional approval is needed.
- Rootless Podman containment testing is `executed-by-request`: it is required to run the requested public corpus safely.
- Property-based testing is `suggested_pending_implementation`: source-range preservation, path normalization, and state-set relation algebra fit generated testing and will be added if the selected Java test stack can keep failures deterministic.
- Mutation fuzzing is `deferred_heavy`: parser fuzzing could improve robustness but is outside the vertical slice and would not directly validate the product thesis.
- PICT, ACTS, Alloy, and Dafny are `not_applicable` for the initial slice because the explicit semantic-role, mutator, outcome, relation, and sandbox matrices are small enough for direct scenario tests.

## Questions for the User

None. The user delegated implementation and product decisions to the agent for this bounded slice.

## Coverage Debt

- D001: A safe deterministic real JVM OOM case may not be available; keep the outcome path tested by a controlled sandbox process if necessary.
- D002: Agent review of candidate usefulness is only a proxy for human validation.
- D003: INSTANCE/source-operator identity may require conservative exclusion beyond the initial corpus.
- D004: Transition-only differences, fairness, liveness, SYMMETRY, VIEW, and constraints remain outside the slice.
- D005: Type-only attribution reruns were deliberately deferred because the Phase 0 run produced no gap candidate and did not pass the product-value gate.
- D006: The adversarial Podman matrix is incomplete. Before any claim of safe general-purpose untrusted execution, add real traversal, hostile environment, network, output, inode, timeout, ignored-termination, and cleanup tests.
- D007: Two generated mutants remain unresolved TLC tool errors. Their event codes are recorded, but root-cause interpretation remains work for a later experiment.
