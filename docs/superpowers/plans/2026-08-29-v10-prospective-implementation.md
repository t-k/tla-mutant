# v10 Prospective Invariant Evaluation Implementation Plan

> **Execution contract:** The completed Agent DAG selected one root writer. Execute this plan task-by-task in the current worktree, using read-only reviewers only at the named security and correctness gates. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and execute a fail-closed v10 discovery and conditional v11 intervention that prospectively evaluates survivor-derived State Invariants on eight previously unevaluated specifications.

**Architecture:** Add dedicated Java prospective records, validators, reducers, reports, and CLI commands while retaining the historical v1-v9 validation and reporting contracts unchanged. A single immutable preflight object binds every trusted input before fetch or Podman; v10 derives a complete review dossier, and v11 accepts only edits traceable to deterministic `ACCEPT` decisions.

**Tech Stack:** Java 21, Jackson strict JSON, picocli, JUnit 5, AssertJ/JUnit assertions, SANY/TLC 1.8.0, rootless Podman, Gradle, SHA-256 content identities.

---

## File Structure

### New production files

- `src/main/java/dev/tlamutant/prospective/HistoricalSourceRegistry.java`: schema-versioned historical source identities and canonical set digest.
- `src/main/java/dev/tlamutant/prospective/ProspectiveProtocol.java`: exact cohort, risk classes, eligibility, rubric, cap, and endpoint rules.
- `src/main/java/dev/tlamutant/prospective/ProspectiveReceipt.java`: named digest/size identities for every frozen phase input.
- `src/main/java/dev/tlamutant/prospective/ProspectivePreflight.java`: bounded snapshot, role, alias, identity, manifest, registry, and protocol validation before execution.
- `src/main/java/dev/tlamutant/prospective/ProspectiveText.java`: one bounded control/bidi-safe text validator used by protocol, dossier, and review types.
- `src/main/java/dev/tlamutant/prospective/ProspectiveCensusValidator.java`: v10/v11 census identity, entry retention, and candidate uniqueness without historical enriched-cohort constraints.
- `src/main/java/dev/tlamutant/prospective/ProspectiveDiscoveryReport.java`: complete v10 terminal outcomes and conserving discovery summaries.
- `src/main/java/dev/tlamutant/prospective/ProspectiveReviewDossier.java`: mechanically derived eligible candidate set.
- `src/main/java/dev/tlamutant/prospective/ProspectiveReviewLedger.java`: user-delegated terminal decisions, criteria, predicate, operator, and evidence.
- `src/main/java/dev/tlamutant/prospective/ProspectiveReviewValidator.java`: dossier-ledger bijection, rubric, acceptance criteria, and deterministic intervention selection.
- `src/main/java/dev/tlamutant/prospective/ProspectiveAssessment.java`: discovery/review/intervention denominator reduction and success status.
- `src/main/java/dev/tlamutant/prospective/ProspectiveInterventionValidator.java`: review-to-v11 entry, source edit, adopted oracle, and candidate binding.
- `src/main/java/dev/tlamutant/prospective/ProspectiveSemanticComparator.java`: reproducibility projection that ignores only declared diagnostic fields.
- `src/main/java/dev/tlamutant/report/ProspectiveMarkdownWriter.java`: generated discovery, review, and closure Markdown.
- `src/main/java/dev/tlamutant/report/FrozenTextWriter.java`: write-once atomic UTF-8 text publication.
- `src/main/java/dev/tlamutant/corpus/ValidatedCorpusManifest.java`: opaque token created only by historical or prospective validation and accepted by shared runners.

### Modified production files

- `src/main/java/dev/tlamutant/cli/Main.java`: six dedicated prospective commands and bounded options.
- `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java`: overload accepting an opaque validated manifest without weakening the existing public method.
- `src/main/java/dev/tlamutant/census/CorpusCensusRunner.java`: prospective overload using the validated token.
- `src/main/java/dev/tlamutant/experiment/CorpusExperimentRunner.java`: prospective overload that preserves failures for exactly the validated entries while retaining the 10-20 entry legacy method.

### New and modified tests

- `src/test/java/dev/tlamutant/prospective/HistoricalSourceRegistryTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveHoldoutValidatorTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveReceiptTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveCensusValidatorTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveDiscoveryReportTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveReviewDossierTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveReviewValidatorTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveAssessmentTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveInterventionValidatorTest.java`
- `src/test/java/dev/tlamutant/prospective/ProspectiveSemanticComparatorTest.java`
- `src/test/java/dev/tlamutant/report/FrozenTextWriterTest.java`
- `src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java`
- `src/integrationTest/java/dev/tlamutant/prospective/ProspectiveCliPreflightIntegrationTest.java`
- `src/integrationTest/java/dev/tlamutant/prospective/ProspectiveEvaluationIntegrationTest.java`
- `src/sandboxTest/java/dev/tlamutant/prospective/ProspectiveContainmentTest.java`
- `src/testFixtures/resources/tla/prospective/ProspectiveFixture.tla`
- `src/testFixtures/resources/tla/prospective/ProspectiveFixture.cfg`

## Task 1: Freeze Historical Evidence and Define Canonical Source Identity

**Files:**
- Create: `src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java`
- Create: `src/main/java/dev/tlamutant/prospective/HistoricalSourceRegistry.java`
- Test: `src/test/java/dev/tlamutant/prospective/HistoricalSourceRegistryTest.java`

- [ ] **Step 1: Write the failing exact v8/v9 artifact freeze test**

Define one ordered map from every frozen v8/v9 boundary artifact path to its SHA-256 and size. The test reads bytes without following links and asserts every expected identity. The reviewed literals are:

| Path | SHA-256 | Size |
|---|---|---:|
| `corpus/gcd-stratified-manifest.json` | `1caeb7734ea814e0d9d2e007ae0711f7fa9dcc332e2cce50b7fb780b08e5ec7e` | 17202 |
| `docs/experiments/gcd-stratified-census.json` | `6de7c400622557a545a82c3db0d29b20f304ab2e0c47ec29eddfea2e9a72df95` | 24623 |
| `docs/experiments/gcd-stratified.json` | `12de6c80837a4871ea09a63173b88bbb6f1bc7c2c4b0826f6f35084230dcfd5e` | 119289 |
| `docs/experiments/gcd-stratified.md` | `43ce8e7fa8f5867d4024d8ea9e44df95ce767b6a4aa657ad46ae8743dffc5f7a` | 27807 |
| `corpus/gcd-stratified-manifest-v9.json` | `521937e0b3288d50f51e77d162e18d6ef1515ec8b644034c71f2d2ea68522bc4` | 17832 |
| `docs/experiments/gcd-stratified-census-v9.json` | `cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb` | 24623 |
| `docs/experiments/gcd-stratified-v9.json` | `aa2cad9943a5216238a1907a233af41dc5d95bb82ffba9655a35f71b8af38ffb` | 143902 |
| `docs/experiments/gcd-stratified-v9.md` | `aa36b720a2c0156961e4ff9ecf4eb5357c0d647c858ed41f6d2403c0069688a9` | 28510 |
| `docs/experiments/gcd-stratified-v9-preregistration.md` | `04d94443265411a7c8222f2cb002b3dd02cd7c7deb8d0619d5f00bc7b534839e` | 2525 |
| `docs/experiments/gcd-stratified-assessment-v9.md` | `a57980bc2dac0e4de96cfdd67cb1948d42a2953da6908d3998aefa7e4176b0f4` | 3906 |
| `docs/experiments/gcd-stratified-human-review.md` | `17732c93c31198a881afdc6a8442f67acbe1bc4164ec1d1f995e08024e8e6773` | 7560 |

- [ ] **Step 2: Run the freeze test and verify RED**

Run:

```bash
./gradlew integrationTest --tests 'dev.tlamutant.prospective.HistoricalArtifactFreezeIntegrationTest' --rerun-tasks
```

Expected: FAIL because the test class or v9 literal identities do not yet exist.

- [ ] **Step 3: Write the failing canonical registry tests**

Specify the public record components and two digest operations. `canonicalSourceSetSha256` and `canonicalRootPathSetSha256` return lowercase SHA-256 strings computed from the canonical serializations below; they never return a declared digest without recomputing it.

```java
public record HistoricalSourceRegistry(
        int schemaVersion,
        String repository,
        String commit,
        int rootPathCount,
        String rootPathSetSha256,
        List<SourceIdentity> sources) {
    public record SourceIdentity(
            String manifestPath,
            String entryId,
            String role,
            String repositoryPath,
            String sha256,
            long sizeBytes) {}

}
```

Canonical source serialization is UTF-8 lines sorted by repository path, digest, size, manifest path, entry ID, and role:

```text
repositoryPath<TAB>sha256<TAB>sizeBytes<TAB>manifestPath<TAB>entryId<TAB>role<LF>
```

Canonical root serialization is each distinct root repository path in lexical order followed by LF. Tests reject duplicates, unsafe paths, wrong counts, and declared digest mismatch.

- [ ] **Step 4: Verify registry tests RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.HistoricalSourceRegistryTest' --rerun-tasks
```

Expected: FAIL because `HistoricalSourceRegistry` is missing.

- [ ] **Step 5: Implement the minimal immutable registry**

Use `List.copyOf`, lowercase 64-character SHA-256 validation, positive sizes, repository-path traversal rejection, duplicate composite-key rejection, exact count checks, and `MessageDigest` over the specified canonical UTF-8 bytes.

- [ ] **Step 6: Verify GREEN and commit**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.HistoricalSourceRegistryTest' integrationTest --tests 'dev.tlamutant.prospective.HistoricalArtifactFreezeIntegrationTest' --rerun-tasks
```

Expected: PASS.

Commit:

```bash
git add src/main/java/dev/tlamutant/prospective/HistoricalSourceRegistry.java src/test/java/dev/tlamutant/prospective/HistoricalSourceRegistryTest.java src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java
git commit -m "test: freeze prospective historical boundary"
```

## Task 2: Validate the Exact Holdout and Protocol

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveText.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveProtocol.java`
- Create: `src/main/java/dev/tlamutant/corpus/ValidatedCorpusManifest.java`
- Create: `src/main/java/dev/tlamutant/corpus/ProspectiveHoldoutValidator.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveHoldoutValidatorTest.java`

- [ ] **Step 1: Write RED tests for exact membership and unseen identity**

Create fixture builders for the approved eight IDs and assert:

```java
assertDoesNotThrow(() -> ProspectiveHoldoutValidator.validate(manifest, registry, protocol, limits));
assertRejected(withHistoricalPathUnderNewId());
assertRejected(withHistoricalBytesUnderNewPath());
assertRejected(withMissingRegistrySource());
assertRejected(withRiskClassChanged());
assertRejected(withOracleSetChanged());
assertRejected(withAdoptedInvariantOrEdit());
```

Use no mocks. The validator must compare exact protocol entry order, IDs, domains, risk class, repository paths, file identities, type/legacy oracle lists, and limits.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveHoldoutValidatorTest' --rerun-tasks
```

Expected: FAIL because prospective protocol and validator types are absent.

- [ ] **Step 3: Implement bounded protocol and text records**

Use this shape:

```java
public record ProspectiveProtocol(
        int schemaVersion,
        String profile,
        String repository,
        String commit,
        int standardRiskEntries,
        int highRiskEntries,
        int interventionCap,
        HistoricalSet historicalSet,
        Eligibility eligibility,
        ReviewRubric rubric,
        List<Entry> entries) {
    public record Entry(String id, String domain, String riskClass,
                        List<String> repositoryPaths, List<String> typeInvariants,
                        List<String> legacyTargetInvariants, String rationale) {}
    public record HistoricalSet(int rootPathCount, String rootPathSetSha256,
                                int sourceCount, String sourceSetSha256) {}
    public record Eligibility(List<String> requiredExecutionStatuses,
                              List<String> allowedStateRelations,
                              boolean requireWitness) {}
    public record ReviewRubric(String reviewerMode, List<String> criteria,
                               List<String> terminalDecisions) {}
}
```

`ProspectiveText.requireSafe` accepts bounded printable text plus LF and TAB only where explicitly allowed and rejects NUL, ISO controls, bidi marks, absolute paths, and traversal.

- [ ] **Step 4: Implement the opaque validation token**

`ValidatedCorpusManifest` exposes only `manifest()` and `purpose()`; its constructor is package-private. `ProspectiveHoldoutValidator` is in the same `corpus` package and returns `new ValidatedCorpusManifest(manifest, "prospective-v10")` only after all checks. Do not add v10/v11 to `StratifiedManifestValidator.PROFILES`.

- [ ] **Step 5: Verify GREEN and legacy regression**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveHoldoutValidatorTest' --tests 'dev.tlamutant.corpus.StratifiedManifestValidatorTest' --rerun-tasks
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/tlamutant/prospective src/main/java/dev/tlamutant/corpus/ValidatedCorpusManifest.java src/main/java/dev/tlamutant/corpus/ProspectiveHoldoutValidator.java src/test/java/dev/tlamutant/prospective/ProspectiveHoldoutValidatorTest.java
git commit -m "feat: validate prospective holdout identity"
```

## Task 3: Add Receipt-Bound Preflight and Write-Once Text

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveReceipt.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectivePreflight.java`
- Create: `src/main/java/dev/tlamutant/report/FrozenTextWriter.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveReceiptTest.java`
- Test: `src/test/java/dev/tlamutant/report/FrozenTextWriterTest.java`
- Integration: `src/integrationTest/java/dev/tlamutant/prospective/ProspectiveCliPreflightIntegrationTest.java`

- [ ] **Step 1: Write RED receipt role and alias tests**

Use exact discovery roles `registry`, `protocol`, `manifest`, `policy`, `tools`, `application`; add `census` for mutation. Assert independent rejection for missing/extra role, digest, size, symlink, duplicate-key/trailing JSON, input-input alias, input-output alias, output-cache containment, and existing canonical output.

```java
assertThrows(IllegalArgumentException.class, () -> preflight.validate(inputsWithWrong("protocol")));
assertThrows(IllegalArgumentException.class, () -> preflight.validate(inputsWithAlias("manifest", "jsonReport")));
```

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReceiptTest' --tests 'dev.tlamutant.report.FrozenTextWriterTest' --rerun-tasks
```

Expected: FAIL because the types are missing.

- [ ] **Step 3: Implement immutable receipt and preflight**

```java
public record ProspectiveReceipt(
        int schemaVersion,
        String phase,
        String profile,
        List<Artifact> artifacts) {
    public record Artifact(String role, String sha256, long sizeBytes) {}
}

public final class ProspectivePreflight {}
```

`ProspectivePreflight` exposes `validateDiscovery(DiscoveryPaths)` returning `ValidatedDiscoveryInputs` and `validateExperiment(ExperimentPaths)` returning `ValidatedExperimentInputs`; both declare `IOException`. Read the receipt first with `BoundedFileSnapshot`, snapshot all declared inputs, compare the exact role set and identities, validate normalized path aliases, then parse registry/protocol/manifest/policy. Do not create cache/output directories in preflight. Return objects retaining the snapshots until command completion.

- [ ] **Step 4: Implement `FrozenTextWriter`**

Mirror `FrozenJsonWriter`: create a sibling temporary file, write UTF-8, atomically move without `REPLACE_EXISTING`, and remove the temporary file in `finally`.

- [ ] **Step 5: Add a process-level preflight integration test**

Invoke the CLI with a malformed public TLA path and wrong protocol digest. Assert the receipt error is returned, the cache/output paths remain absent, and no Podman invocation marker exists.

- [ ] **Step 6: Verify GREEN and commit**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReceiptTest' --tests 'dev.tlamutant.report.FrozenTextWriterTest' integrationTest --tests 'dev.tlamutant.prospective.ProspectiveCliPreflightIntegrationTest' --rerun-tasks
```

Expected: PASS.

Commit:

```bash
git add src/main/java/dev/tlamutant/prospective/ProspectiveReceipt.java src/main/java/dev/tlamutant/prospective/ProspectivePreflight.java src/main/java/dev/tlamutant/report/FrozenTextWriter.java src/test/java/dev/tlamutant/prospective/ProspectiveReceiptTest.java src/test/java/dev/tlamutant/report/FrozenTextWriterTest.java src/integrationTest/java/dev/tlamutant/prospective/ProspectiveCliPreflightIntegrationTest.java
git commit -m "feat: seal prospective execution inputs"
```

## Task 4: Run a Failure-Preserving Prospective Census

**Files:**
- Modify: `src/main/java/dev/tlamutant/corpus/CorpusFetcher.java`
- Modify: `src/main/java/dev/tlamutant/census/CorpusCensusRunner.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveCensusValidator.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveCensusValidatorTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write RED census retention tests**

Build an eight-entry census with completed sites, completed zero sites, and failed entries. Assert validation retains all eight in manifest order, validates completed report module/config identity, validates globally unique candidate IDs, and rejects replacement, omission, reordering, or input identity mismatch.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveCensusValidatorTest' --rerun-tasks
```

Expected: FAIL because the validator is absent.

- [ ] **Step 3: Add validated-token runner overloads**

Keep `CorpusFetcher.fetch(CorpusManifest, Entry, ...)` and `CorpusCensusRunner.run(CorpusManifest, ...)` unchanged. Add overloads accepting `ValidatedCorpusManifest` and delegate to one private per-entry implementation. The new overload must not call historical profile/count validation because the opaque token already proves the prospective contract.

- [ ] **Step 4: Implement `ProspectiveCensusValidator`**

Validate receipt/census identities and all eight terminal entries. Permit zero total sites and incomplete entries. Never require a conforming site or the historical 25% family-cluster cap. A failed entry must have `report == null` and a bounded diagnostic; a completed entry must have a valid report.

- [ ] **Step 5: Add `prospective-census` command**

Required options:

```text
--registry --protocol --manifest --receipt --policy --tools-jar --cache-dir
--census-report --failed-census-report
```

Call preflight before `Files.createDirectories(cacheDirectory)` or runner construction. Publish success/failure only through `FrozenJsonWriter`.

- [ ] **Step 6: Verify GREEN and commit**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveCensusValidatorTest' --tests 'dev.tlamutant.corpus.CorpusFetcherTest' integrationTest --rerun-tasks
```

Expected: PASS.

Commit:

```bash
git add src/main/java/dev/tlamutant/corpus/CorpusFetcher.java src/main/java/dev/tlamutant/census/CorpusCensusRunner.java src/main/java/dev/tlamutant/prospective/ProspectiveCensusValidator.java src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/prospective/ProspectiveCensusValidatorTest.java
git commit -m "feat: run prospective census without replacement"
```

## Task 5: Produce Complete Discovery Outcomes and a Semantic Comparator

**Files:**
- Modify: `src/main/java/dev/tlamutant/experiment/CorpusExperimentRunner.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveDiscoveryReport.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveSemanticComparator.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveDiscoveryReportTest.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveSemanticComparatorTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write RED terminalization and conservation tests**

Table-drive completed result buckets and baseline-unresolved synthesized outcomes. Assert one terminal GCD outcome per census site and one specification terminal entry for every frozen member, including zero-site and failed census entries. Assert tool errors stay unresolved.

- [ ] **Step 2: Write RED semantic comparison tests**

Two reports differing only in approved diagnostic seed, PID, timestamp, or scratch path compare equal. Any difference in entry status, candidate ID, execution/oracle status, state relation, verdict, baseline/mutant/type/legacy counts, witness, or attribution compares unequal with the first JSON pointer difference.

- [ ] **Step 3: Verify RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveDiscoveryReportTest' --tests 'dev.tlamutant.prospective.ProspectiveSemanticComparatorTest' --rerun-tasks
```

Expected: FAIL because both production types are absent.

- [ ] **Step 4: Implement the prospective experiment overload**

Retain the legacy `run(CorpusManifest)` count/domain checks. Add `run(ValidatedCorpusManifest)` and delegate both paths to a private ordered per-entry loop. Use the existing type/legacy/adopted `SandboxInput.attributed` transport.

- [ ] **Step 5: Implement the dedicated report and comparator**

`ProspectiveDiscoveryReport` binds receipt and census identities, contains the original census/experiment records, a flat ordered terminal GCD list keyed by `(entryId,candidateId)`, and summaries for completed baseline, evaluated, kill categories, state relations, invalid, unresolved, and witness-bearing eligible candidates.

- [ ] **Step 6: Add `prospective-experiment` and `compare-prospective-reports` commands**

The experiment adds `--census-report`, verifies the receipt role and census bytes, writes JSON/Markdown once, and never reruns census. The comparator exits zero only for semantic equality and writes no artifact.

- [ ] **Step 7: Verify GREEN and commit**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveDiscoveryReportTest' --tests 'dev.tlamutant.prospective.ProspectiveSemanticComparatorTest' --tests 'dev.tlamutant.experiment.GcdStratifiedReportTest' integrationTest --rerun-tasks
```

Expected: PASS.

Commit:

```bash
git add src/main/java/dev/tlamutant/experiment/CorpusExperimentRunner.java src/main/java/dev/tlamutant/prospective/ProspectiveDiscoveryReport.java src/main/java/dev/tlamutant/prospective/ProspectiveSemanticComparator.java src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/prospective/ProspectiveDiscoveryReportTest.java src/test/java/dev/tlamutant/prospective/ProspectiveSemanticComparatorTest.java
git commit -m "feat: report prospective discovery outcomes"
```

## Task 6: Derive the Complete Review Dossier

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveReviewDossier.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveReviewDossierTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write RED eligibility matrix tests**

Independently negate each required condition and assert exclusion. Assert inclusion only for completed full-oracle `PASSED` results with `STATE_EXPANDING` or `STATE_INCOMPARABLE` and a nonblank witness.

```java
assertEquals(List.of(new CandidateKey("entry-a", "gcd-a")), dossier.candidates().stream().map(Candidate::key).toList());
```

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReviewDossierTest' --rerun-tasks
```

Expected: FAIL because dossier derivation is absent.

- [ ] **Step 3: Implement deterministic dossier derivation**

Sort by entry ID then candidate ID. Bind result, census, source snapshot, protocol, and receipt digests. Copy bounded mutation source, guard, counts, oracle lists, relation, and witness. Reject duplicate keys and inconsistent report/census candidates.

- [ ] **Step 4: Add `prospective-review-dossier` command**

Required inputs are receipt, protocol, manifest, census, and v10 result. Output is one write-once JSON path. The command performs no review and emits no predicate proposal.

- [ ] **Step 5: Verify GREEN and commit**

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReviewDossierTest' --rerun-tasks
git add src/main/java/dev/tlamutant/prospective/ProspectiveReviewDossier.java src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/prospective/ProspectiveReviewDossierTest.java
git commit -m "feat: derive prospective review dossier"
```

## Task 7: Validate Every Review Decision and Reduce Assessment

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveReviewLedger.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveReviewValidator.java`
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveAssessment.java`
- Create: `src/main/java/dev/tlamutant/report/ProspectiveMarkdownWriter.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveReviewValidatorTest.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveAssessmentTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Write RED review bijection and seven-criterion tests**

Use the seven terminal decisions from the design. Reject missing, duplicate, extra, unknown, changed protocol/dossier digest, unsafe evidence, missing predicate/operator, and `ACCEPT` with any criterion false.

```java
public record Criteria(boolean exactPredicate, boolean witnessFalsifies,
                       boolean baselineHolds, boolean actionsPreserve,
                       boolean nonvacuous, boolean nonredundant,
                       boolean abstractionAligned) {}
```

- [ ] **Step 2: Write RED cap and conservation tests**

Assert `reviewed == eligible == sum(decisions)`, deterministic first-three selection after all decisions, and accepted overflow accounting. Zero eligible plus an empty ledger is valid and yields negative discovery without v11 authorization.

- [ ] **Step 3: Verify RED**

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReviewValidatorTest' --tests 'dev.tlamutant.prospective.ProspectiveAssessmentTest' --rerun-tasks
```

Expected: FAIL because review and assessment types are missing.

- [ ] **Step 4: Implement ledger validation and assessment**

Bind exact predicate text and its SHA-256, operator, bounded criterion evidence, reviewer mode `user-delegated-agent`, protocol digest, and dossier digest. Sort decisions by candidate key. `ProspectiveAssessment` reports discovery success independently from closure status.

- [ ] **Step 5: Add `validate-prospective-review` command**

Validate the ledger and publish the assessment Markdown once. Print a bounded validated assessment JSON to stdout for scripting. Do not mutate the ledger or dossier.

- [ ] **Step 6: Verify GREEN and commit**

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveReviewValidatorTest' --tests 'dev.tlamutant.prospective.ProspectiveAssessmentTest' --tests 'dev.tlamutant.report.ReportWriterTest' --rerun-tasks
git add src/main/java/dev/tlamutant/prospective/ProspectiveReviewLedger.java src/main/java/dev/tlamutant/prospective/ProspectiveReviewValidator.java src/main/java/dev/tlamutant/prospective/ProspectiveAssessment.java src/main/java/dev/tlamutant/report/ProspectiveMarkdownWriter.java src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/prospective/ProspectiveReviewValidatorTest.java src/test/java/dev/tlamutant/prospective/ProspectiveAssessmentTest.java
git commit -m "feat: validate prospective usefulness review"
```

## Task 8: Bind Accepted Reviews to v11 Interventions

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveInterventionValidator.java`
- Test: `src/test/java/dev/tlamutant/prospective/ProspectiveInterventionValidatorTest.java`
- Integration: `src/integrationTest/java/dev/tlamutant/prospective/ProspectiveEvaluationIntegrationTest.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`
- Create: `src/testFixtures/resources/tla/prospective/ProspectiveFixture.tla`
- Create: `src/testFixtures/resources/tla/prospective/ProspectiveFixture.cfg`

- [ ] **Step 1: Write RED traceability tests**

Reject zero accepts with any v11 entry, unselected/rejected/fourth candidate, cross-entry reuse, changed candidate/operator/predicate bytes/path/offset/base/derived identity, changed review digest, non-root edit, or adopted oracle mismatch. Require derived census to retain the exact accepted candidate ID before mutation.

- [ ] **Step 2: Write the real TLC fixture RED test**

The fixture must expose one accepted-style GCD survivor. The test creates a review-bound derived predicate and asserts full baseline pass, target mutant type-only pass, legacy pass with expansion, full adopted kill, and `adopted-target-invariant-kill`.

- [ ] **Step 3: Verify RED**

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveInterventionValidatorTest' integrationTest --tests 'dev.tlamutant.prospective.ProspectiveEvaluationIntegrationTest' --rerun-tasks
```

Expected: FAIL because intervention validation and fixtures are missing.

- [ ] **Step 4: Implement intervention validation**

Reuse `DerivedSourceEditValidator` for byte-level checks, then additionally require review-bound predicate digest, exact operator, selected candidate key, same root module, same entry, unchanged legacy oracles, and candidate preservation in the derived census.

- [ ] **Step 5: Add v11 census and experiment commands**

`prospective-intervention-census` binds v10 receipt/census/result/dossier/review, v11 protocol/manifest/receipt, policy, tools, and application. `prospective-intervention-experiment` additionally binds the v11 census. Both publish once and use the validated-token runner overloads.

- [ ] **Step 6: Verify GREEN and commit**

```bash
./gradlew test --tests 'dev.tlamutant.prospective.ProspectiveInterventionValidatorTest' integrationTest --tests 'dev.tlamutant.prospective.ProspectiveEvaluationIntegrationTest' --rerun-tasks
git add src/main/java/dev/tlamutant/prospective/ProspectiveInterventionValidator.java src/main/java/dev/tlamutant/cli/Main.java src/test/java/dev/tlamutant/prospective/ProspectiveInterventionValidatorTest.java src/integrationTest/java/dev/tlamutant/prospective/ProspectiveEvaluationIntegrationTest.java src/testFixtures/resources/tla/prospective
git commit -m "feat: bind prospective reviews to interventions"
```

## Task 9: Prove the Prospective Sandbox Boundary

**Files:**
- Create: `src/sandboxTest/java/dev/tlamutant/prospective/ProspectiveContainmentTest.java`
- Modify: `src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java`

- [ ] **Step 1: Write RED containment tests**

Cover receipt failure, successful census, successful mutation, timeout, interruption, review-binding failure, and derived edit. Each case asserts exact labeled container/volume cleanup and unchanged host source. Receipt/review failures assert no container was ever created.

- [ ] **Step 2: Verify RED with an explicitly owned Podman machine**

Start the machine in a persistent PTY only if it was initially stopped. Run:

```bash
./gradlew sandboxTest --tests 'dev.tlamutant.prospective.ProspectiveContainmentTest' --rerun-tasks
```

Expected: FAIL because the prospective containment surface is incomplete.

- [ ] **Step 3: Make the minimum command/cleanup changes**

Retain shellless argv, `--network=none`, read-only root, dropped capabilities, no-new-privileges, tmpfs, seccomp, CPU/memory/PID/file/output/state/wall-clock bounds, and exact run labels. Do not add broad process cleanup.

- [ ] **Step 4: Verify GREEN and commit**

```bash
./gradlew test --tests 'dev.tlamutant.sandbox.PodmanCommandTest' sandboxTest --tests 'dev.tlamutant.prospective.ProspectiveContainmentTest' --rerun-tasks
git add src/sandboxTest/java/dev/tlamutant/prospective/ProspectiveContainmentTest.java src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java
git commit -m "test: prove prospective sandbox containment"
```

## Task 10: Freeze Outcome-Free v10 Inputs

**Files:**
- Create: `corpus/historical-source-registry-v10.json`
- Create: `config/prospective-protocol-v10.json`
- Create: `corpus/gcd-prospective-manifest-v10.json`
- Create: `docs/experiments/gcd-prospective-receipt-v10.json`
- Modify: `config/current-application.json`
- Modify: `README.md`
- Update: `docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md`

- [ ] **Step 1: Generate reviewed source identities from the pinned checkout**

For every historical manifest and the exact eight prospective entries, compute SHA-256 and byte size from `tlaplus/Examples@ed3cc4c35f7001d7cec08a22dcd698dae7570dc3`. Include every imported repository-local module required by the root.

- [ ] **Step 2: Validate the exact oracle mapping against each CFG**

Record all configured State Invariants. Classify type invariants only when the source/config explicitly names a type predicate; all other configured invariants are legacy targets. Never omit an upstream configured invariant.

- [ ] **Step 3: Build the application twice from clean uncached inputs**

```bash
./gradlew --stop
./gradlew clean fatJar --no-build-cache --rerun-tasks
cp build/libs/tla-mutant-all.jar build/tla-mutant-all.first.jar
./gradlew clean fatJar --no-build-cache --rerun-tasks
cmp build/tla-mutant-all.first.jar build/libs/tla-mutant-all.jar
shasum -a 256 build/tla-mutant-all.first.jar build/libs/tla-mutant-all.jar
```

Expected: byte-identical JARs. Update `config/current-application.json` with the exact digest and size and run `./gradlew verifyFrozenApplication --rerun-tasks`.

- [ ] **Step 4: Create and validate the receipt**

The receipt binds registry, protocol, manifest, policy, tools, and application. Validate it with the real CLI before any prospective census.

- [ ] **Step 5: Run all pre-census gates**

```bash
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication --rerun-tasks
python3 /Users/tk/.agents/skills/candidate-tournament/scripts/validate_tournament.py .codex/candidate-tournaments/v10-prospective-boundary/tournament.json
git diff --exit-code b6c5483 -- corpus/gcd-stratified-manifest.json corpus/gcd-stratified-manifest-v9.json docs/experiments/gcd-stratified-census.json docs/experiments/gcd-stratified.json docs/experiments/gcd-stratified.md docs/experiments/gcd-stratified-census-v9.json docs/experiments/gcd-stratified-v9.json docs/experiments/gcd-stratified-v9.md
```

Expected: PASS and no historical diff.

- [ ] **Step 6: Obtain security and correctness GO**

Use read-only clean-context reviewers. Record security findings as `Must Fix / Should Fix / Notes`. Any Must Fix blocks census. Fix every Must Fix test-first and rebuild/refreeze the application identity.

- [ ] **Step 7: Commit all outcome-free inputs**

```bash
git add corpus/historical-source-registry-v10.json config/prospective-protocol-v10.json corpus/gcd-prospective-manifest-v10.json docs/experiments/gcd-prospective-receipt-v10.json config/current-application.json README.md docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md
git commit -m "experiment: preregister prospective holdout"
```

No v10 census or mutation may precede this commit.

## Task 11: Freeze the v10 Census Before Mutation

**Files:**
- Create: `docs/experiments/gcd-prospective-census-v10.json`
- Create: `docs/experiments/gcd-prospective-experiment-receipt-v10.json`
- Create: `docs/experiments/gcd-prospective-preregistration-v10.md`

- [ ] **Step 1: Run the census twice to distinct new paths**

Use `prospective-census` with the committed receipt. Write the canonical census under `docs/experiments` and the repeat under `build/reports`. Do not replace any failed entry.

- [ ] **Step 2: Compare census bytes**

```bash
cmp docs/experiments/gcd-prospective-census-v10.json build/reports/gcd-prospective-census-v10-repeat.json
shasum -a 256 docs/experiments/gcd-prospective-census-v10.json
```

Expected: byte-identical files.

- [ ] **Step 3: Audit all eight terminal entries**

Assert exact IDs/order and that every entry has either a completed report, including zero sites, or one explicit failure status and diagnostic.

- [ ] **Step 4: Commit the census before mutation**

```bash
git add docs/experiments/gcd-prospective-census-v10.json docs/experiments/gcd-prospective-preregistration-v10.md
git commit -m "experiment: freeze prospective v10 census"
```

Create the experiment receipt from the committed discovery receipt, the frozen census, and the unchanged registry, protocol, manifest, policy, tools, and application artifacts. Commit it with the census; no mutation may start from an experiment receipt created outside that commit.

## Task 12: Run v10 Discovery and Freeze the Dossier

**Files:**
- Create: `docs/experiments/gcd-prospective-v10.json`
- Create: `docs/experiments/gcd-prospective-v10.md`
- Create: `docs/experiments/gcd-prospective-review-dossier-v10.json`

- [ ] **Step 1: Run v10 mutation twice**

Use the frozen census, the discovery receipt, and the experiment receipt. The command requires `--discovery-receipt docs/experiments/gcd-prospective-receipt-v10.json` and `--receipt docs/experiments/gcd-prospective-experiment-receipt-v10.json`. Write canonical and repeat reports to distinct paths.

- [ ] **Step 2: Compare semantic results**

```bash
java -jar build/libs/tla-mutant-all.jar compare-prospective-reports \
  --left docs/experiments/gcd-prospective-v10.json \
  --right build/reports/gcd-prospective-v10-repeat.json
```

Expected: exit 0.

- [ ] **Step 3: Generate the complete dossier**

Run `prospective-review-dossier` once. Independently assert its key set equals the eligible projection from the frozen result.

- [ ] **Step 4: Commit discovery and dossier before review**

```bash
git add docs/experiments/gcd-prospective-v10.json docs/experiments/gcd-prospective-v10.md docs/experiments/gcd-prospective-review-dossier-v10.json
git commit -m "experiment: publish prospective v10 discovery"
```

## Task 13: Perform and Freeze User-Delegated Review

**Files:**
- Create: `docs/experiments/gcd-prospective-review-v10.json`
- Create: `docs/experiments/gcd-prospective-assessment-v10.md`
- Update: `docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md`

- [ ] **Step 1: Review every dossier candidate**

For each candidate, inspect only the frozen dossier, pinned source/config, and frozen rubric. Produce one exact predicate proposal where applicable and criterion-by-criterion evidence. Do not inspect or create v11 mutation results during review.

- [ ] **Step 2: Validate the ledger and publish assessment**

Run `validate-prospective-review`. Require `reviewed == eligible` and all conservation rules.

- [ ] **Step 3: Commit review before any v11 input**

```bash
git add docs/experiments/gcd-prospective-review-v10.json docs/experiments/gcd-prospective-assessment-v10.md docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md
git commit -m "experiment: review prospective invariant candidates"
```

- [ ] **Step 4: Apply the preregistered stopping rule**

If accepted count is zero, skip Tasks 14-15 and record negative discovery. If accepted count is positive, continue with exactly the first three accepted candidate keys in lexical order.

## Task 14: Freeze Conditional v11 Intervention Inputs and Census

**Files:**
- Create conditionally: `config/prospective-protocol-v11.json`
- Create conditionally: `corpus/gcd-prospective-manifest-v11.json`
- Create conditionally: `docs/experiments/gcd-prospective-receipt-v11.json`
- Create conditionally: `docs/experiments/gcd-prospective-census-v11.json`
- Create conditionally: `docs/experiments/gcd-prospective-preregistration-v11.md`

- [ ] **Step 1: Generate review-bound insertion-only edits**

For each selected accepted decision, insert the exact reviewed operator and predicate into its root module. Compute base/derived digest, size, UTF-8 offset, and exact inserted bytes. Do not change source cache bytes.

- [ ] **Step 2: Validate and commit v11 preregistration before census**

The v11 receipt binds all v10 evidence, review ledger, v11 protocol/manifest, policy, tools, and application.

```bash
git add config/prospective-protocol-v11.json corpus/gcd-prospective-manifest-v11.json docs/experiments/gcd-prospective-receipt-v11.json
git commit -m "experiment: preregister prospective interventions"
```

- [ ] **Step 3: Run v11 census twice and require identical bytes**

Require every selected accepted candidate ID to remain present after derived edits.

- [ ] **Step 4: Commit v11 census before mutation**

```bash
git add docs/experiments/gcd-prospective-census-v11.json docs/experiments/gcd-prospective-preregistration-v11.md
git commit -m "experiment: freeze prospective v11 census"
```

## Task 15: Run v11 Closure and Publish Final Assessment

**Files:**
- Create conditionally: `docs/experiments/gcd-prospective-v11.json`
- Create conditionally: `docs/experiments/gcd-prospective-v11.md`
- Create: `docs/experiments/gcd-prospective-final-assessment.md`
- Modify: `README.md`
- Update: `docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md`

- [ ] **Step 1: Run v11 twice and compare semantics**

Require baseline, target candidate, type/legacy/adopted attribution, counts, witness, and verdict equality across runs.

- [ ] **Step 2: Evaluate discovery and closure independently**

Report exact denominators, every censored entry, every review decision, accepted overflow, baseline rejection, unresolved intervention, and successful closure. Use only the narrow user-delegated finite-model claim from the design.

- [ ] **Step 3: Run final security and correctness review**

Provide reviewers only the original request, acceptance criteria, final diff, relevant artifacts, and test results. Resolve all Must Fix test-first.

- [ ] **Step 4: Run final deterministic verification**

```bash
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication --rerun-tasks
git diff --check
git status --short
```

Repeat historical digest and git ancestry audits, semantic comparator commands, exact Podman label checks, and application/census identities.

- [ ] **Step 5: Commit final evidence**

```bash
git add README.md docs/experiments docs/security docs/superpowers/plans/2026-08-29-v10-prospective-coverage.md
git commit -m "experiment: assess prospective invariant discovery"
```

## Task 16: Merge and Clean Up

**Files:**
- Create in root checkout only: `docs.local/logs/2026-08-29/2026-08-29-006-prospective-invariant-evaluation.md`

- [ ] **Step 1: Verify the feature branch and fast-forward main**

Confirm the main checkout is clean, merge with `git merge --ff-only feat/v10-prospective-evaluation`, and rerun non-Podman verification on merged HEAD.

- [ ] **Step 2: Stop exact owned processes and resources**

List and remove only task-labeled containers/volumes, stop the specifically started Podman machine, run `./gradlew --stop`, verify the machine is stopped and no Gradle daemon is running, and inspect PPID 1 processes without killing unrelated processes.

- [ ] **Step 3: Write the Japanese local work log**

Record design, frozen identities, commit sequence, census/result/review/closure outcomes, tests, reviews, limitations, and cleanup under `docs.local/logs/2026-08-29` in the main checkout.

- [ ] **Step 4: Remove the merged worktree and branch**

From the main checkout, verify the branch is an ancestor of main, remove `.worktree/v10-prospective-evaluation`, prune worktrees, and delete the merged feature branch.

## Plan Self-Review

- Every design section maps to Tasks 1-16.
- No historical validator, census conformance rule, report schema, or public runner contract is relaxed.
- Every production behavior starts with a named RED test and exact focused command.
- The user-delegated review boundary and external-human limitation remain explicit.
- The outcome sequence prevents mutation before census, review before dossier, and v11 before review.
- Candidate Tournament selected one writer and rejected broad refactoring and split-runtime orchestration.
- TLA+/TLC and Podman execution is authorized by the user's request; no additional optional-tool approval is required.
