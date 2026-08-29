# v9 Invariant Closed-Loop Design

## Goal

Demonstrate one complete post-discovery invariant-improvement loop without changing the frozen v8 experiment. The v9 intervention adds the reviewed `NoRequestAllocationOverlap` predicate to the pinned SimpleAllocator specification, proves that the baseline still passes, proves that the previously surviving Request GCD mutant is killed specifically by the new predicate, and distinguishes target-invariant kills from type-only kills.

This is an intervention experiment derived from a v8 witness. It demonstrates that the workflow can turn a survivor into a useful reviewed invariant and verify its effect. It is not an out-of-sample estimate of discovery rate or general usefulness.

## Frozen Boundary

The following v8 files remain byte-for-byte unchanged:

- `corpus/gcd-stratified-manifest.json`
- `docs/experiments/gcd-stratified-census.json`
- `docs/experiments/gcd-stratified.json`
- `docs/experiments/gcd-stratified.md`

v9 uses distinct manifest, census, JSON result, and Markdown report paths. Every v9 artifact identifies the v9 manifest, execution policy, application JAR, toolchain, and derived source bytes.

## Adopted Invariant

The adopted SimpleAllocator predicate is:

```tla
NoRequestAllocationOverlap ==
    \A c \in Clients : unsat[c] \cap alloc[c] = {}
```

The v8 Request GCD mutant replaced the guard `unsat[c] = {} /\ alloc[c] = {}` with `TRUE` and reached a state in which the same resource was both requested and allocated by one client. The predicate rules out exactly that overlap while preserving the original model's reachable states.

## Derived Source Provenance

The upstream SimpleAllocator source does not define the adopted predicate. v9 therefore introduces a bounded manifest-pinned source edit instead of changing the verified corpus cache, vendoring a full source file, or replacing the upstream repository.

Each derived edit records:

- the existing manifest-listed relative source path;
- the verified base byte size and SHA-256 digest;
- the exact byte insertion offset;
- the inserted UTF-8 text;
- the expected derived byte size and SHA-256 digest.

The edit is applied only after the upstream base file has passed its existing size and digest checks. The insertion offset must be on a UTF-8 boundary, the inserted text must satisfy a small byte limit and contain no NUL or disallowed control characters, and the derived bytes must match the declared size and digest. Any mismatch fails closed as an integrity rejection.

The host never parses or executes the public TLA+ source. It copies the verified input into the existing read-only snapshot, records the edit metadata in the bounded command, and applies the edit only to the private mutable model copy inside the Podman sandbox. SANY validates the derived module before baseline execution. The original cache and mounted input remain unchanged.

The predicate is inserted after the existing action definitions and before the module terminator. A new census supplies the authoritative mutation IDs; v8 candidate IDs are not assumed to remain stable.

### Rejected alternatives

- Editing v8 artifacts would destroy the historical experiment boundary.
- Editing `.corpus` would violate the declared upstream digest and cache immutability.
- A wrapper root module would hide the imported `Request` action from the current root-module-only mutation routing.
- A fork would broaden the trust source for the whole corpus.
- Naming an undefined invariant in the manifest would make the baseline invalid.

## Oracle Sets and Attribution

For every v9 corpus entry, the pipeline preserves three ordered invariant lists:

- `type`: the manifest's type invariants;
- `legacy target`: target invariants present before the v9 intervention;
- `adopted target`: newly reviewed target invariants introduced by v9.

The full oracle is `type + legacy target + adopted target`. The legacy oracle is `type + legacy target`. The type-only oracle is `type`.

The full oracle runs for the baseline and every mutant. Attribution reruns occur only when the full oracle completes with a state-invariant kill.

Classification is exclusive:

| Full oracle | Type-only oracle | Legacy oracle | Attribution |
|---|---|---|---|
| kill | kill | not needed | `type-invariant-kill` |
| kill | pass | pass | `adopted-target-invariant-kill` |
| kill | pass | kill | `legacy-target-invariant-kill` |
| kill | pass | unresolved | `kill-attribution-unresolved` |
| kill | unresolved | not run | `kill-attribution-unresolved` |

When there are no adopted targets, a type-only pass followed by a full kill is a `legacy-target-invariant-kill` and the legacy rerun is unnecessary because it is identical to the full oracle. When the full list equals the type-only list, a full state-invariant kill is classified directly as `type-invariant-kill`.

An oracle pass means a completed TLC exploration with valid state output. Syntax, semantic, evaluation, timeout, out-of-memory, output, resource, or tool failures are unresolved and never counted as a target or type kill.

The structured mutation result records the attribution category and the execution/oracle outcome of every rerun that was actually performed. Legacy v8 JSON, which lacks this object and uses `state-invariant-kill-unattributed`, remains readable and renderable.

## SimpleAllocator Closure Criteria

The v9 closed loop succeeds only if all of the following are true:

1. The derived SimpleAllocator baseline passes `TypeInvariant`, `ResourceMutex`, and `NoRequestAllocationOverlap`.
2. The Request GCD mutant that removes `unsat[c] = {} /\ alloc[c] = {}` is killed by the full oracle.
3. The same Request mutant passes the type-only oracle.
4. The same Request mutant passes the legacy oracle and remains state-expanding there.
5. The Request mutant is reported as `adopted-target-invariant-kill`.
6. At least one existing full-oracle kill, expected to be the Allocate GCD mutant, is reported as `type-invariant-kill` when its type-only rerun also kills it.
7. A non-killed mutant, expected to include Return, receives no attribution rerun and retains its state relation.
8. Report bucket totals conserve the generated/evaluated mutant denominators.

The report must state that the adopted invariant was selected from the v8 witness before the v9 run. Killing that witness is confirmatory evidence for the closed loop, not independent validation.

## Data and Compatibility Changes

The corpus manifest schema advances for derived edit provenance and adopted-target metadata. Profiles v1 through v8 remain accepted with no derived edits and no adopted targets; v9 additionally permits the bounded fields.

The mutation result schema advances to add a nullable structured attribution object. The corpus report schema advances to aggregate:

- adopted-target-invariant kills;
- legacy-target-invariant kills;
- type-invariant kills;
- unresolved attributions;
- historical unattributed kills.

Historical unattributed kills remain a separate compatibility bucket and are not silently reclassified.

The CLI retains the existing flat `--state-invariant` interface for trusted standalone compatibility. The corpus path uses separate repeated arguments for type, legacy-target, and adopted-target invariants. Derived source edits are passed as bounded structured JSON in one argument through `ProcessBuilder`; no shell interprets their contents.

## Application Identity and Preregistration

Changing the Java application changes the application JAR digest, so v9 requires a new census. The standard build must no longer pretend that the changed HEAD can reproduce the historical v8 JAR. A versioned application lock outside the packaged JAR records the expected current application digest, while v8 remains reproducible through its historical commit and artifacts.

The experiment order is:

1. Commit outcome-free implementation, tests, v9 manifest, and current application identity lock.
2. Build the application twice from clean inputs and require identical bytes and SHA-256 digests.
3. Run the v9 census twice to new write-once paths and require byte-identical output.
4. Review and commit the selected v9 census and its externally supplied digest.
5. Run the v9 mutation experiment twice to new write-once paths using the frozen census digest.
6. Require semantic outcome equality and report the closure criteria.

No v9 mutation is executed before the implementation, manifest, application identity, and census have been preregistered.

## Security Analysis

The manifest-pinned edit crosses the untrusted-input boundary and therefore requires explicit review.

- The edit can target only a manifest-listed `.tla` regular file.
- Base and derived sizes and SHA-256 digests are mandatory.
- At most one edit per file is allowed in v9, with a fixed small insertion limit.
- Replacement and deletion are not supported.
- Paths, offsets, UTF-8, controls, size arithmetic, and digest syntax are validated before Podman starts.
- The edit is applied with byte-array operations, never a shell, template evaluator, or host-side TLA parser.
- The source copy is owner-only and private to the sandbox scratch tree.
- SANY and TLC still run with no network, a read-only root filesystem, dropped capabilities, fixed resource limits, bounded output, and exact-container cleanup.
- Reports record logical paths and digests, not host paths or unrestricted inserted text.

Security review output is recorded as `Must Fix / Should Fix / Notes`. Any Must Fix finding blocks the v9 run.

## Test Strategy

Development follows red-green-refactor TDD without mocks.

- Unit tests cover manifest edit validation, exact byte insertion, derived digest checking, immutability, argument separation, attribution reduction, compatibility deserialization, and report conservation.
- Real SANY/TLC fixtures cover target, type, and non-kill paths and prove that only killed mutants receive attribution reruns.
- Corpus integration tests prove that v8 is unchanged and v9 changes only the reviewed SimpleAllocator input and invariant registration.
- Sandbox tests prove that malformed edit metadata fails before execution, valid edits affect only private scratch bytes, command transport uses argument arrays, and containers are removed in every terminal path.
- The final v9 experiment supplies the end-to-end SimpleAllocator closure evidence.

## Non-Goals

- General arbitrary patching of public corpus source.
- Automatic invariant synthesis or automatic acceptance.
- Attribution to one conjunct when multiple newly adopted targets are introduced together. v9 introduces exactly one adopted target.
- Re-estimation of mutation score or candidate usefulness across the corpus.
- Investigation of the four previously observed tool errors until the closed loop is complete.
