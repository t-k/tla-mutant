# GCD-Stratified Corpus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preregister, census, execute, and assess a 15-specification public corpus enriched for GCD action shapes that can prospectively add reachable states without selecting on mutation outcomes.

**Architecture:** Extend the manifest with backward-compatible selection metadata, refactor GCD eligibility into one shared site analyzer, and expose a structure-only census command that runs inside the existing Podman boundary. A host orchestrator persists and validates the census before invoking the unchanged mutation experiment, then joins manifest, census, and raw results into stratified denominators and a decision gate.

**Tech Stack:** Java 21, Gradle 8, JUnit Jupiter 5, Jackson 2, Picocli, SANY/TLC 1.8.0, rootless Podman, Spotless.

---

## File Structure

### Manifest and preregistration

- Modify `src/main/java/dev/tlamutant/corpus/CorpusManifest.java`: optional version-2 profile and entry selection records.
- Modify `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java`: accept only valid version-1 or approved version-2 provenance.
- Create `src/main/java/dev/tlamutant/corpus/StratifiedManifestValidator.java`: profile-specific 5/10 cohort and metadata rules.
- Modify `src/test/java/dev/tlamutant/corpus/ManifestValidatorTest.java`: version-1 compatibility.
- Create `src/test/java/dev/tlamutant/corpus/StratifiedManifestValidatorTest.java`: version-2 cohort and metadata matrix.
- Create `corpus/gcd-stratified-manifest.json`: exact pinned 15-entry preregistration.

### Shared GCD census

- Create `src/main/java/dev/tlamutant/mutation/GcdSite.java`: one candidate plus structural sibling counts.
- Create `src/main/java/dev/tlamutant/mutation/GcdSiteAnalyzer.java`: single source of truth for GCD eligibility.
- Modify `src/main/java/dev/tlamutant/mutation/GcdMutator.java`: map shared sites to mutation candidates.
- Create `src/main/java/dev/tlamutant/census/GcdCensusSite.java`: bounded serializable site data without outcome fields.
- Create `src/main/java/dev/tlamutant/census/GcdCensusReport.java`: sandbox-internal per-specification census report.
- Create `src/main/java/dev/tlamutant/census/GcdCensusAnalyzer.java`: SANY/source-to-census adapter.
- Modify `src/integrationTest/java/dev/tlamutant/mutation/MutatorIntegrationTest.java`: census/mutator identity and structural-shape cases.
- Create `src/testFixtures/resources/tla/census/CensusFixture.tla`: positive and action-without-successor census shapes.
- Create `src/testFixtures/resources/tla/census/CensusFixture.cfg`: finite census fixture configuration.

### Sandbox and corpus census orchestration

- Create `src/main/java/dev/tlamutant/sandbox/SandboxTask.java`: allowlisted `EXPERIMENT` and `CENSUS` tasks.
- Modify `src/main/java/dev/tlamutant/sandbox/PodmanCommand.java`: construct fixed arguments for either task.
- Modify `src/main/java/dev/tlamutant/sandbox/PodmanRunner.java`: overload `run` with an allowlisted task while preserving the existing default.
- Modify `src/main/java/dev/tlamutant/cli/Main.java`: add sandbox-internal `census` and outer `gcd-stratified-experiment` commands.
- Create `src/main/java/dev/tlamutant/census/CorpusCensusEntryResult.java`: one preregistered entry census terminal result.
- Create `src/main/java/dev/tlamutant/census/CorpusCensusReport.java`: full cohort census and stable ordering.
- Create `src/main/java/dev/tlamutant/census/CorpusCensusRunner.java`: fetch and census every entry inside Podman.
- Create `src/main/java/dev/tlamutant/census/CensusConformanceValidator.java`: enriched-site and cluster-share gate.
- Modify `src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java`: both closed commands and unchanged boundaries.
- Create `src/test/java/dev/tlamutant/census/CensusConformanceValidatorTest.java`: no-site, cluster-share, and exact-ID cases.
- Modify `src/sandboxTest/java/dev/tlamutant/sandbox/PodmanContainmentTest.java`: real census execution and cleanup.

### Stratified reporting

- Create `src/main/java/dev/tlamutant/experiment/GcdOutcomeSummary.java`: pure terminal-bucket aggregation.
- Create `src/main/java/dev/tlamutant/experiment/GcdStratifiedReport.java`: joined manifest/census/experiment report and automated gate.
- Create `src/main/java/dev/tlamutant/report/GcdStratifiedMarkdownWriter.java`: detailed cohort, site, witness, and unresolved output.
- Create `src/main/java/dev/tlamutant/report/AtomicJsonWriter.java`: bounded generic atomic JSON persistence.
- Modify `src/main/java/dev/tlamutant/report/JsonReportWriter.java`: delegate existing report writes without changing output semantics.
- Create `src/test/java/dev/tlamutant/experiment/GcdStratifiedReportTest.java`: denominator conservation and gate rules.
- Modify `src/test/java/dev/tlamutant/report/ReportWriterTest.java`: hostile selection/census/result text.

### Generated public artifacts and documentation

- Create `docs/experiments/gcd-stratified-census.json`.
- Create `docs/experiments/gcd-stratified.json`.
- Create `docs/experiments/gcd-stratified.md`.
- Create `docs/experiments/gcd-stratified-assessment.md`.
- Modify `README.md`: reproduce both experiments without overwriting the first.
- Modify `docs/superpowers/plans/2026-08-29-gcd-stratified-coverage.md`: mark obligations with actual evidence.
- Modify ignored `docs.local/IDEA.md`: record the second Phase 0 result.
- Create ignored `docs.local/logs/2026-08-29/2026-08-29-003-gcd-stratified-corpus.md`: work log.

---

### Task 1: Add backward-compatible preregistration metadata

**Files:**
- Modify: `src/main/java/dev/tlamutant/corpus/CorpusManifest.java`
- Modify: `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java`
- Create: `src/main/java/dev/tlamutant/corpus/StratifiedManifestValidator.java`
- Modify: `src/test/java/dev/tlamutant/corpus/ManifestValidatorTest.java`
- Create: `src/test/java/dev/tlamutant/corpus/StratifiedManifestValidatorTest.java`

- [ ] **Step 1: Write failing compatibility and version-2 validator tests**

Add constructors/helpers that deserialize the original manifest and assert `version == 1`, `profile == null`, and every `selection == null`. Add a valid 15-entry version-2 fixture and mutations that independently fail for 4/11 cohort counts, unknown cohort, control with non-control stratum, enriched with control stratum, fewer than three enriched strata, blank/over-500 rationale, unsafe cluster, fewer than three domains, and duplicate IDs.

The core assertion shape is:

```java
CorpusManifest manifest = mapper.readValue(Path.of("corpus/manifest.json").toFile(), CorpusManifest.class);
assertEquals(1, manifest.version());
assertNull(manifest.profile());
assertTrue(manifest.entries().stream().allMatch(entry -> entry.selection() == null));

CorpusManifest accepted = stratifiedManifest(5, 10);
assertSame(accepted, StratifiedManifestValidator.validate(accepted, GLOBAL));
```

- [ ] **Step 2: Run the tests and verify red**

Run:

```sh
./gradlew test --tests '*ManifestValidatorTest'
```

Expected: compilation fails because version-2 records and `StratifiedManifestValidator` do not exist.

- [ ] **Step 3: Add the version-2 records**

Change the manifest signature without removing existing fields:

```java
public record CorpusManifest(
        int version,
        String profile,
        String repository,
        String commit,
        String license,
        List<Entry> entries) {
    public record Entry(
            String id,
            String domain,
            String rootModule,
            String config,
            List<SourceFile> files,
            List<String> typeInvariants,
            List<String> targetInvariants,
            ExecutionPolicy.Limits limits,
            Selection selection) {}

    public record Selection(String cohort, String gcdStratum, String familyCluster, String selectionRationale) {}
}
```

Update direct test constructors by passing `null` selection. Jackson maps missing version-1 fields to `null`.

Update `CorpusFetcher.validateManifest` to accept only these two provenance forms while keeping the repository, 40-hex commit, and MIT license checks unchanged:

```java
boolean acceptedProfile = (manifest.version() == 1 && manifest.profile() == null)
        || (manifest.version() == 2 && "gcd-stratified-v1".equals(manifest.profile()));
if (!acceptedProfile) {
    throw new IllegalArgumentException("Corpus profile is not accepted");
}
```

- [ ] **Step 4: Implement the profile validator**

Implement these allowlists and exact checks:

```java
private static final Set<String> COHORTS = Set.of("control", "gcd-enriched");
private static final Set<String> ENRICHED_STRATA = Set.of(
        "guarded-admission-positive-control",
        "lifecycle-or-pc-ordering",
        "type-only-gap-prospect",
        "collision-or-capacity");
private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
```

Require version 2, profile `gcd-stratified-v1`, 15 entries, 5 controls, 10 enriched entries, 3+ domains, 3+ enriched strata, unique IDs, allowlisted selection values, a safe cluster, and rationale length 1–500 after trimming. Call `ManifestValidator.validateEntry` for every entry.

- [ ] **Step 5: Run green tests and commit**

```sh
./gradlew spotlessApply test --tests '*ManifestValidatorTest'
git add src/main/java/dev/tlamutant/corpus src/test/java/dev/tlamutant/corpus
git commit -m "feat: validate stratified corpus metadata"
```

### Task 2: Make GCD eligibility and census share one analyzer

**Files:**
- Create: `src/main/java/dev/tlamutant/mutation/GcdSite.java`
- Create: `src/main/java/dev/tlamutant/mutation/GcdSiteAnalyzer.java`
- Modify: `src/main/java/dev/tlamutant/mutation/GcdMutator.java`
- Create: `src/main/java/dev/tlamutant/census/GcdCensusSite.java`
- Create: `src/main/java/dev/tlamutant/census/GcdCensusReport.java`
- Create: `src/main/java/dev/tlamutant/census/GcdCensusAnalyzer.java`
- Modify: `src/integrationTest/java/dev/tlamutant/mutation/MutatorIntegrationTest.java`
- Create: `src/testFixtures/resources/tla/census/CensusFixture.tla`
- Create: `src/testFixtures/resources/tla/census/CensusFixture.cfg`

- [ ] **Step 1: Write failing shared-identity and shape tests**

Add an integration test that obtains sites and mutants from the same `SemanticProject`, then requires exact candidate-ID equality:

```java
List<GcdSite> sites = new GcdSiteAnalyzer().analyze(project, source);
List<MutationCandidate> mutants = new GcdMutator().generate(project, source);
assertEquals(
        mutants.stream().map(MutationCandidate::id).toList(),
        sites.stream().map(site -> site.candidate().id()).toList());
assertTrue(sites.stream().allMatch(site -> site.guardLevel() <= 1));
assertTrue(sites.stream().anyMatch(GcdSite::hasActionSuccessor));
```

Add fixture definitions for a state-only conjunction and assert its site is not conforming even if it remains an eligible GCD mutation.

- [ ] **Step 2: Run the integration test and verify red**

```sh
./gradlew integrationTest --tests dev.tlamutant.mutation.MutatorIntegrationTest
```

Expected: compilation fails because `GcdSite` and `GcdSiteAnalyzer` do not exist.

- [ ] **Step 3: Implement the shared site analyzer**

Use the current GCD loop unchanged for eligibility. For every accepted argument, record sibling counts from the same top-level conjunction:

```java
public record GcdSite(
        MutationCandidate candidate,
        int guardLevel,
        int remainingStateConjuncts,
        int actionSuccessorConjuncts) {
    public boolean hasActionSuccessor() {
        return actionSuccessorConjuncts > 0;
    }
}
```

Count `argument.getLevel() <= 1` as state-level and `argument.getLevel() > 1` as action-level. Keep source-range ordering identical to the old mutator. Change `GcdMutator.generate` to:

```java
return new GcdSiteAnalyzer().analyze(project, source).stream()
        .map(GcdSite::candidate)
        .toList();
```

- [ ] **Step 4: Implement outcome-free census serialization**

`GcdCensusSite` contains only:

```java
public record GcdCensusSite(
        String candidateId,
        String module,
        String definition,
        String guard,
        int guardLevel,
        int remainingStateConjuncts,
        int actionSuccessorConjuncts,
        boolean conforms) {}
```

Bound `guard` to 500 characters after removing ANSI, control, bidi, CR, and LF. `GcdCensusReport` contains schema version, module, config, and a stable immutable site list. It must not define verdict, oracle, relation, state-count, or witness fields.

- [ ] **Step 5: Run green tests and commit**

```sh
./gradlew spotlessApply test integrationTest --tests dev.tlamutant.mutation.MutatorIntegrationTest
git add src/main/java/dev/tlamutant/mutation src/main/java/dev/tlamutant/census src/integrationTest src/testFixtures
git commit -m "feat: census GCD action structure"
```

### Task 3: Add a closed sandbox census task

**Files:**
- Create: `src/main/java/dev/tlamutant/sandbox/SandboxTask.java`
- Modify: `src/main/java/dev/tlamutant/sandbox/PodmanCommand.java`
- Modify: `src/main/java/dev/tlamutant/sandbox/PodmanRunner.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`
- Modify: `src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java`
- Modify: `src/sandboxTest/java/dev/tlamutant/sandbox/PodmanContainmentTest.java`

- [ ] **Step 1: Write failing command-contract tests**

Require `EXPERIMENT` to preserve its exact command and `CENSUS` to invoke only:

```text
dev.tlamutant.cli.Main census
--model-dir /input
--module <module>
--config <config>
```

Assert both commands retain network none, read-only root, dropped capabilities, exact labels, resource limits, fixed mounts, and no shell. Assert census contains no `experiment`, state-output, or mutant option.

- [ ] **Step 2: Run command tests and verify red**

```sh
./gradlew test --tests dev.tlamutant.sandbox.PodmanCommandTest
```

Expected: compilation fails because `SandboxTask` and the task-aware command do not exist.

- [ ] **Step 3: Implement the allowlisted task**

```java
public enum SandboxTask {
    EXPERIMENT,
    CENSUS
}
```

Build the fixed suffix in a private function and keep the existing overload defaulting to `EXPERIMENT`. Reject `null` tasks. Add `PodmanRunner.runCensus(...)` as a thin call into the same labeled resource lifecycle and timeout path.

- [ ] **Step 4: Add the sandbox-internal CLI command**

Add `Main.CensusCommand` to the root subcommands. It reads only repository-owned arguments, invokes `GcdCensusAnalyzer`, and writes exactly one bounded JSON document to stdout. It does not accept output paths or run TLC.

- [ ] **Step 5: Add a real containment census test**

Run census against the repository fixture and assert exit zero, parseable `GcdCensusReport`, stable GCD IDs, no outcome fields in its JSON tree, and post-run `podman container exists == 1`. Query matching volumes and require none remain.

- [ ] **Step 6: Run green tests and commit**

```sh
./gradlew spotlessApply test sandboxTest
git add src/main/java/dev/tlamutant/sandbox src/main/java/dev/tlamutant/cli/Main.java src/test src/sandboxTest
git commit -m "feat: isolate public GCD census"
```

### Task 4: Orchestrate and validate the full preregistered census

**Files:**
- Create: `src/main/java/dev/tlamutant/census/CorpusCensusEntryResult.java`
- Create: `src/main/java/dev/tlamutant/census/CorpusCensusReport.java`
- Create: `src/main/java/dev/tlamutant/census/CorpusCensusRunner.java`
- Create: `src/main/java/dev/tlamutant/census/CensusConformanceValidator.java`
- Create: `src/test/java/dev/tlamutant/census/CensusConformanceValidatorTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write failing conformance tests**

Construct pure reports for:

- all 10 enriched entries with one conforming site and controls with any count;
- one enriched entry with no conforming site;
- census/result ID mismatch;
- one cluster owning exactly 25% versus more than 25% of sites;
- census timeout/failure entry;
- duplicate site IDs.

Require the valid report to return unchanged and all invalid reports to throw before mutation orchestration.

- [ ] **Step 2: Run tests and verify red**

```sh
./gradlew test --tests 'dev.tlamutant.census.*'
```

Expected: compilation fails because corpus census records and validator do not exist.

- [ ] **Step 3: Implement stable corpus census records and runner**

`CorpusCensusEntryResult` includes trusted selection metadata plus status, bounded diagnostic, and a nullable per-spec report. Apply the same completed/report consistency invariant as `CorpusEntryResult`. `CorpusCensusRunner` iterates manifest order, uses `CorpusFetcher`, invokes `runCensus`, rejects timeout/truncation/nonzero/invalid JSON, and records the preregistered entry rather than replacing it.

- [ ] **Step 4: Implement conformance validation**

Join by exact entry ID. For enriched entries require at least one `conforms == true` site. Require globally unique candidate IDs. Count every GCD site by family cluster and reject when:

```java
clusterSiteCount * 4 > totalSiteCount
```

Use integer arithmetic to avoid floating-point boundary errors. Any non-completed census entry aborts conformance.

- [ ] **Step 5: Add the outer census command**

Add `gcd-stratified-census` options for manifest, policy, tools JAR, cache, and census report. The call order must be:

```java
StratifiedManifestValidator.validate(manifest, policy.limits());
CorpusCensusReport census = censusRunner.run(manifest);
writeAtomically(censusReport, census);
CensusConformanceValidator.validate(manifest, census);
```

This command never constructs or starts `CorpusExperimentRunner`.

- [ ] **Step 6: Run green tests and commit**

```sh
./gradlew spotlessApply test integrationTest
git add src/main/java/dev/tlamutant/census src/main/java/dev/tlamutant/cli/Main.java src/test
git commit -m "feat: gate mutation on preregistered census"
```

### Task 5: Aggregate stratified outcomes and render detailed reports

**Files:**
- Create: `src/main/java/dev/tlamutant/experiment/GcdOutcomeSummary.java`
- Create: `src/main/java/dev/tlamutant/experiment/GcdStratifiedReport.java`
- Create: `src/main/java/dev/tlamutant/report/GcdStratifiedMarkdownWriter.java`
- Create: `src/main/java/dev/tlamutant/report/AtomicJsonWriter.java`
- Modify: `src/main/java/dev/tlamutant/report/JsonReportWriter.java`
- Create: `src/test/java/dev/tlamutant/experiment/GcdStratifiedReportTest.java`
- Modify: `src/test/java/dev/tlamutant/report/ReportWriterTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write failing denominator and gate tests**

Create a two-site specification and a one-site specification, then assert site totals are three while specification totals are two. Cover every GCD terminal bucket and require conservation:

```java
assertEquals(summary.totalSites(),
        summary.killedUnattributed()
                + summary.stateEqual()
                + summary.stateExpanding()
                + summary.stateIncomparable()
                + summary.stateRestricting()
                + summary.invalid()
                + summary.unresolved());
```

Gate tests must fail for two candidates in one specification, domain, or cluster and become `human-review-eligible` only for two specifications across two domains and two clusters. Human acceptance remains `pending` in automated output.

- [ ] **Step 2: Run tests and verify red**

```sh
./gradlew test --tests dev.tlamutant.experiment.GcdStratifiedReportTest
```

Expected: compilation fails because stratified report records do not exist.

- [ ] **Step 3: Implement pure aggregation**

Join manifest selection, census sites, and experiment entries by exact ID. Filter `MutationResult.family().equals("GCD")`, require census candidate-ID equality, and bucket verdict/relation/status without guessing. Preserve non-GCD totals separately. Return immutable summaries grouped by cohort, stratum, family cluster, and domain in sorted order.

- [ ] **Step 4: Implement detailed safe Markdown**

The writer renders:

- manifest/census identities;
- cohort and stratum tables;
- specification- and site-weighted summaries;
- every incomplete or unresolved diagnostic;
- every GCD gap candidate with definition, source change, relation, and witness;
- automated gate status and explicit human-review pending text.

Expose `MarkdownSanitizer` through package-level functions used by both report writers, or move it to a shared public utility without duplicating sanitizer logic. Extend hostile tests for rationale, cluster, guard, diagnostic, and witness text.

- [ ] **Step 5: Connect the census-consuming experiment command and commit**

Add `gcd-stratified-experiment` options for the version-2 manifest, persisted census report, policy, tools JAR, cache, JSON report, and Markdown report. Parse and validate the census identity and conformance before constructing `CorpusExperimentRunner`. Compute SHA-256 of the persisted census with its exact file-size bound, build `GcdStratifiedReport`, then atomically write JSON and Markdown. `AtomicJsonWriter.write(Path, Object)` uses an owner-created temporary sibling, Jackson pretty printing, and `ATOMIC_MOVE` with replacement; make the existing `JsonReportWriter` delegate to it. The command must not invoke `CorpusCensusRunner`. Run:

```sh
./gradlew spotlessApply test
git add src/main/java/dev/tlamutant/experiment src/main/java/dev/tlamutant/report src/main/java/dev/tlamutant/cli/Main.java src/test
git commit -m "feat: report stratified GCD outcomes"
```

### Task 6: Pin the exact 15-entry public manifest

**Files:**
- Create: `corpus/gcd-stratified-manifest.json`
- Create: `src/integrationTest/java/dev/tlamutant/corpus/StratifiedManifestIntegrationTest.java`

- [ ] **Step 1: Materialize sources only from the exact pinned commit**

Use a temporary clone or GitHub raw blobs at commit `ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`. For every required `.tla` and `.cfg`, record repository path, local path, exact byte size, and lowercase SHA-256. Include wrapper/base modules for ReadersWriters and SingleLaneBridge. Do not copy public source into the repository.

- [ ] **Step 2: Write the manifest with the approved cohort**

Copy the five control entries byte-for-byte in meaning from `corpus/manifest.json`, adding selection metadata. Add the ten enriched entries and their existing configured State Invariants. Do not add or remove an invariant merely to change survival. For MissionariesAndCannibals, omit `Solution` because it is a goal predicate configured to be reached, not a State Invariant oracle; retain its declared type invariant. Apply the outcome-free dependency amendment in the design: use `lamport-mutex`, `dining-philosophers`, and `slush-small` instead of the three candidates that require the separately distributed TLAPM proof library.

- [ ] **Step 3: Add manifest identity tests**

Assert exact profile, commit, entry IDs in approved order, 5/10 cohort split, all source size/digest formats, at least three domains and enriched strata, no duplicate repository paths within an entry, and successful `StratifiedManifestValidator` validation.

- [ ] **Step 4: Commit the preregistration before any public census or mutation run**

```sh
git add corpus/gcd-stratified-manifest.json src/integrationTest/java/dev/tlamutant/corpus/StratifiedManifestIntegrationTest.java
git commit -m "experiment: preregister GCD-stratified corpus"
```

Record this commit ID in the work log before executing any mutant. This commit is the outcome-independent preregistration boundary.

### Task 7: Run and freeze the census before mutation

**Files:**
- Create: `docs/experiments/gcd-stratified-census.json`
- Modify: `docs/superpowers/plans/2026-08-29-gcd-stratified-coverage.md`
- Create ignored: `docs.local/logs/2026-08-29/2026-08-29-003-gcd-stratified-corpus.md`

- [ ] **Step 1: Start Token Tracker if configured and start rootless Podman through the project process policy**

Use the configured Podman machine only for the bounded census and experiment. Record ownership, exact machine state, and pre-run matching container/volume absence. Do not leave the machine running after the task.

- [ ] **Step 2: Run the census command only**

Build the fat JAR and invoke the census phase so it writes `gcd-stratified-census.json` and stops before mutation:

```sh
./gradlew fatJar
java -jar build/libs/tla-mutant-all.jar gcd-stratified-census \
  --manifest corpus/gcd-stratified-manifest.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --census-report docs/experiments/gcd-stratified-census.json
```

Validate no outcome keys exist:

```sh
jq -e 'all(.. | objects | keys[]; . != "verdict" and . != "stateRelation" and . != "mutantOnlyWitness")' \
  docs/experiments/gcd-stratified-census.json
```

- [ ] **Step 3: Validate conformance and repeatability**

Run census a second time to a temporary file. Compare commit, entry IDs, candidate IDs, guard levels, and sibling counts exactly. Verify every enriched entry has a conforming site and cluster share is at most 25%.

- [ ] **Step 4: Commit the frozen census**

```sh
git add docs/experiments/gcd-stratified-census.json docs/superpowers/plans/2026-08-29-gcd-stratified-coverage.md
git commit -m "experiment: freeze preregistered GCD census"
```

The mutation phase must use this committed census digest.

### Task 8: Run the full 15-entry experiment and assess the gate

**Files:**
- Create: `docs/experiments/gcd-stratified.json`
- Create: `docs/experiments/gcd-stratified.md`
- Create: `docs/experiments/gcd-stratified-assessment.md`
- Modify: `README.md`
- Modify: `docs.local/IDEA.md`
- Modify: `docs.local/logs/2026-08-29/2026-08-29-003-gcd-stratified-corpus.md`

- [ ] **Step 1: Run the experiment against the committed census identity**

Invoke:

```sh
java -jar build/libs/tla-mutant-all.jar gcd-stratified-experiment \
  --manifest corpus/gcd-stratified-manifest.json \
  --census-report docs/experiments/gcd-stratified-census.json \
  --policy config/execution-policy.json \
  --tools-jar .tools/tla2tools-1.8.0.jar \
  --cache-dir .corpus \
  --json-report docs/experiments/gcd-stratified.json \
  --markdown-report docs/experiments/gcd-stratified.md
```

Poll bounded long-running execution while keeping user commentary under 60 seconds apart.

- [ ] **Step 2: Verify raw consistency before interpretation**

Use `jq` to assert 15 entries, 5 controls, 10 enriched, all eligible GCD IDs represented exactly once, bucket conservation, candidate witnesses non-null, incomplete reasons non-empty, and the report census digest equals the committed census file.

- [ ] **Step 3: Repeat and compare semantic results**

Run to temporary JSON/Markdown files and compare candidate IDs, execution status, oracle, state relation, verdict, witness, cohort, stratum, and cluster. Runtime-only ordering or diagnostic differences must be normalized or explicitly recorded; semantic differences are a failed reproducibility gate.

- [ ] **Step 4: Write the assessment without overclaiming**

Document:

- exact cohort and static selection rule;
- census and experiment commit/digest identities;
- site- and specification-weighted outcomes;
- whether the automated 2-specification/2-domain/2-cluster gate passed;
- candidate witness dossiers if present;
- explicit `human-review-pending` until the user judges usefulness;
- stronger negative conclusion if no candidates appear.

Update `docs.local/IDEA.md` in Japanese because it is private project documentation. Keep public README and assessment in English.

- [ ] **Step 5: Commit generated evidence**

```sh
git add README.md docs/experiments
git commit -m "experiment: run GCD-stratified public corpus"
```

Do not commit ignored public source cache or private `docs.local` files.

### Task 9: Final verification, independent review, and cleanup

**Files:**
- Modify: `docs/superpowers/plans/2026-08-29-gcd-stratified-coverage.md`
- Modify: ignored `docs.local/logs/2026-08-29/2026-08-29-003-gcd-stratified-corpus.md`

- [ ] **Step 1: Run clean deterministic verification**

```sh
./gradlew clean spotlessCheck test integrationTest sandboxTest
java -jar build/libs/tla-mutant-all.jar validate-manifest \
  --manifest corpus/gcd-stratified-manifest.json \
  --policy config/execution-policy.json
git diff --check
```

Run exact `jq` report conservation checks from Task 8 after the clean build.

- [ ] **Step 2: Verify process and sandbox hygiene**

Require no matching `tla-mutant-*` container or `tla-mutant-scratch-*` volume. Stop only the Podman machine/processes started by this task, verify their exact identities, and confirm no `vfkit` or `gvproxy` child remains.

- [ ] **Step 3: Request clean-context correctness and security reviews**

Give reviewers only the original request, approved design/acceptance criteria, final diff, relevant files, and test results. Correctness review focuses on outcome-independent selection, ID joins, denominators, GCD semantics, and report claims. Security review uses `Must Fix / Should Fix / Notes` and focuses on public census isolation, bounded output, command allowlisting, and cleanup.

- [ ] **Step 4: Resolve every Must Fix and rerun affected verification**

Use TDD for review fixes. Update the coverage ledger with covered, partial, deferred, and debt evidence. Commit coherent fixes frequently.

- [ ] **Step 5: Merge locally and preserve private logs**

After all reviews approve and the worktree is clean, fast-forward the feature branch to local `main`, rerun merged unit/integration validation, remove the feature worktree and branch, and do not push. Record final commits, metrics, review status, and cleanup in the ignored Japanese work log.

---

## Plan Self-Review

- Every design requirement maps to Tasks 1–9.
- Version-1 compatibility is independently covered before version-2 execution.
- Census selection is persisted before any mutation run.
- Public source is never parsed on the host.
- Outcome fields are structurally absent from census records.
- Both denominators and the human-review gate have pure tests and artifact checks.
- No preregistered failure path permits substitution.
- Process cleanup and security review remain mandatory.
- No placeholder or unspecified implementation step remains.

## Execution Decision

The approved Agent DAG uses read-only exploration followed by a single writer, deterministic verification, and clean-context review. Execute this plan inline with `superpowers:executing-plans`; do not dispatch writer subagents or parallel writes.
