# v9 Invariant Closed-Loop Implementation Plan

> Execute in order with red-green-refactor TDD. Commit after each green task. The frozen v8 manifest and experiment artifacts are read-only historical inputs.

**Goal:** Add a digest-bound SimpleAllocator invariant intervention, attribute state-invariant kills to type, legacy target, or the newly adopted target, and run a separately frozen reproducible v9 census and experiment.

**Architecture:** Extend the corpus manifest with optional adopted targets and one bounded insertion edit. Preserve the three oracle sets across the host/Podman/CLI boundary, create a verified derived model copy inside the sandbox, and make the experiment runner conditionally rerun only full-oracle kills. Keep legacy flat standalone execution and v8 JSON compatible.

**Technology:** Java 21, Gradle, JUnit 5, Jackson, Picocli, SANY/TLC, rootless Podman.

---

## Task 1: Manifest schema and derived edit validator

**Files:**

- Modify: `src/main/java/dev/tlamutant/corpus/CorpusManifest.java`
- Modify: `src/main/java/dev/tlamutant/corpus/ManifestValidator.java`
- Modify: `src/main/java/dev/tlamutant/corpus/StratifiedManifestValidator.java`
- Test: `src/test/java/dev/tlamutant/corpus/ManifestValidatorTest.java`
- Test: `src/test/java/dev/tlamutant/corpus/StratifiedManifestValidatorTest.java`

**RED:** Add tests for legacy constructor/JSON defaults, v9 acceptance, v1-v8 rejection of v9-only fields, adopted-name uniqueness across all categories, and the complete invalid edit table: unlisted/non-TLA path, wrong base metadata, invalid digest, negative/out-of-range offset, oversized or forbidden inserted text, duplicate edit, invalid derived size arithmetic.

**GREEN:** Add `adoptedTargetInvariants` and `derivedSourceEdits` with empty defaults and a source-compatible legacy constructor. Add the bounded `DerivedSourceEdit` record. Validate the edit against its referenced `SourceFile`, and make schema/profile v9 the only profile that permits v9-only fields.

**Verify:**

```text
./gradlew test --tests 'dev.tlamutant.corpus.ManifestValidatorTest' --tests 'dev.tlamutant.corpus.StratifiedManifestValidatorTest' --rerun-tasks
```

**Commit:** `feat: validate v9 derived source edits`

## Task 2: Private derived model workspace

**Files:**

- Create: `src/main/java/dev/tlamutant/corpus/DerivedModelWorkspace.java`
- Test: `src/test/java/dev/tlamutant/corpus/DerivedModelWorkspaceTest.java`

**RED:** Test exact UTF-8 insertion, source immutability, copy of all regular input files, base mismatch, derived mismatch, split UTF-8 offset, existing/non-empty destination rejection, symlink/special-file rejection, and cleanup.

**GREEN:** Implement a closeable owner-private workspace that copies only regular non-symlink input files, applies validated byte insertions, verifies base and derived identities, and exposes the derived root. Use byte-array operations only.

**Verify:**

```text
./gradlew test --tests 'dev.tlamutant.corpus.DerivedModelWorkspaceTest' --rerun-tasks
```

**Commit:** `feat: create verified derived model workspace`

## Task 3: Preserve oracle/edit metadata through Podman and CLI

**Files:**

- Modify: `src/main/java/dev/tlamutant/sandbox/PodmanCommand.java`
- Modify: `src/main/java/dev/tlamutant/sandbox/PodmanRunner.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`
- Modify: `src/main/java/dev/tlamutant/census/CorpusCensusRunner.java`
- Modify: `src/main/java/dev/tlamutant/experiment/CorpusExperimentRunner.java`
- Test: `src/test/java/dev/tlamutant/sandbox/PodmanCommandTest.java`
- Test: add focused CLI parsing/derived-workspace tests at the closest existing layer.

**RED:** Require distinct repeated `--type-invariant`, `--legacy-target-invariant`, and `--adopted-target-invariant` arguments, one bounded `--derived-source-edit-json` argument per edit, and census edit transport. Prove the exact inserted text is one argument and never shell-expanded. Reject overlaps, unsafe names, excessive arguments, malformed JSON, and census oracle arguments.

**GREEN:** Introduce one immutable execution request value used by `PodmanCommand` and `PodmanRunner`. Serialize edit records with the strict mapper. The inner `census` and `experiment` commands build a `DerivedModelWorkspace` under the fixed `/scratch/work` path before invoking SANY/TLC. Preserve the old flat `--state-invariant` standalone option as a compatibility path that cannot be mixed with separated oracle options.

**Verify:**

```text
./gradlew test --tests 'dev.tlamutant.sandbox.PodmanCommandTest' --tests 'dev.tlamutant.cli.*' --rerun-tasks
```

**Commit:** `feat: carry v9 oracle provenance through sandbox`

## Task 4: Type and target kill attribution

**Files:**

- Create: `src/main/java/dev/tlamutant/experiment/KillAttribution.java`
- Create: `src/main/java/dev/tlamutant/experiment/InvariantOracleSets.java`
- Modify: `src/main/java/dev/tlamutant/experiment/MutationResult.java`
- Modify: `src/main/java/dev/tlamutant/experiment/ExperimentReport.java`
- Modify: `src/main/java/dev/tlamutant/experiment/ExperimentRunner.java`
- Modify: `src/main/java/dev/tlamutant/experiment/StateInvariantConfig.java` only if required for explicit output separation.
- Add fixture: `src/testFixtures/resources/tla/attribution/AttributionFixture.tla`
- Add fixture: `src/testFixtures/resources/tla/attribution/AttributionFixture.cfg`
- Test: `src/test/java/dev/tlamutant/experiment/StateInvariantConfigTest.java`
- Test: add `src/test/java/dev/tlamutant/experiment/KillAttributionTest.java`
- Test: `src/integrationTest/java/dev/tlamutant/experiment/ExperimentIntegrationTest.java`

**RED:** Add a pure truth-table test and a real TLC fixture with one adopted-target kill, one type kill, and one passing mutant. Assert type-only and legacy evidence, exact verdicts, schema version, and deterministic attribution output files. Assert no rerun files for the passing mutant and no redundant legacy/type rerun when oracle sets are identical.

**GREEN:** Build distinct full/type-only/legacy configurations. Run the full oracle first; only a completed state-invariant kill enters attribution. Run type-only next and legacy only when needed. Store a nullable structured attribution containing category and performed execution/oracle outcomes. Preserve flat callers as historical unattributed mode. Advance the experiment schema.

**Verify:**

```text
./gradlew test --tests 'dev.tlamutant.experiment.*' --rerun-tasks
./gradlew integrationTest --tests 'dev.tlamutant.experiment.ExperimentIntegrationTest' --rerun-tasks
```

**Commit:** `feat: attribute invariant kills by oracle set`

## Task 5: Report compatibility and conserving v9 buckets

**Files:**

- Modify: `src/main/java/dev/tlamutant/experiment/GcdOutcomeSummary.java`
- Modify: `src/main/java/dev/tlamutant/experiment/GcdStratifiedReport.java`
- Modify: `src/main/java/dev/tlamutant/report/GcdStratifiedMarkdownWriter.java`
- Modify: `src/main/java/dev/tlamutant/report/MarkdownReportWriter.java`
- Test: `src/test/java/dev/tlamutant/experiment/GcdStratifiedReportTest.java`
- Test: `src/test/java/dev/tlamutant/report/ReportWriterTest.java`

**RED:** Deserialize the committed v8 report, assert its 51 terminal outcomes and seven historical unattributed kills, and render it without invented attribution. Add a mixed v9 result set and require adopted, legacy, type, attribution-unresolved, historical-unattributed, survivor, invalid, and unresolved buckets to conserve both site and specification totals.

**GREEN:** Advance only the new report schema. Map structured attribution to explicit buckets, retain the legacy verdict bucket, validate schema-specific terminal requirements, and render a clearly labeled attribution summary plus the post-selection warning.

**Verify:**

```text
./gradlew test --tests 'dev.tlamutant.experiment.GcdStratifiedReportTest' --tests 'dev.tlamutant.report.ReportWriterTest' --rerun-tasks
```

**Commit:** `feat: report attributed invariant kills`

## Task 6: Preregister the v9 intervention and current application identity

**Files:**

- Create: `corpus/gcd-stratified-manifest-v9.json`
- Create: `config/current-application.json`
- Modify: `build.gradle.kts`
- Modify: `src/test/java/dev/tlamutant/build/ArchiveReproducibilityTest.java`
- Modify: `src/integrationTest/java/dev/tlamutant/corpus/StratifiedManifestIntegrationTest.java`
- Modify: `README.md`

**RED:** Require v8 manifest/artifact identities to remain unchanged. Require v9 corpus membership to equal v8 and only SimpleAllocator to add the declared edit and adopted target. Require the build verification task to read the external current application lock instead of the historical v8 census.

**GREEN:** Copy the v8 manifest to the v9 path, advance its schema/profile, and add the exact SimpleAllocator insertion with computed base/derived size and digest. Update the build lock task without packaging the lock into the JAR. Document separate v8 history and v9 commands.

Build the fat JAR twice from clean Gradle outputs and compare bytes. Write the resulting size/digest to the current application lock, rebuild, and require `verifyFrozenApplication` to pass.

**Verify:**

```text
./gradlew clean fatJar --no-build-cache
sha256sum build/libs/tla-mutant-all.jar
./gradlew clean fatJar --no-build-cache
sha256sum build/libs/tla-mutant-all.jar
./gradlew verifyFrozenApplication
./gradlew integrationTest --tests 'dev.tlamutant.corpus.StratifiedManifestIntegrationTest' --rerun-tasks
```

**Commit:** `experiment: preregister v9 invariant intervention`

## Task 7: Full verification and security review

**Files:**

- Create: `docs/security/2026-08-29-v9-derived-source-review.md`
- Update: `docs/superpowers/plans/2026-08-29-v9-invariant-closed-loop-coverage.md`

Run a clean-context read-only security and correctness review. Record findings under `Must Fix / Should Fix / Notes`. Fix every Must Fix with a new failing regression test before changing production code. Update coverage statuses with exact test evidence.

**Verify:**

```text
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication --rerun-tasks
git diff --check
```

**Commit:** `test: verify v9 invariant closed loop`

## Task 8: Freeze the v9 census before mutation

**Files:**

- Create: `docs/experiments/gcd-stratified-census-v9.json`
- Create: `docs/experiments/gcd-stratified-v9-preregistration.md`

Start the exact rootless Podman machine only when required. Run `gcd-stratified-census` twice with the v9 manifest into distinct new paths. Compare bytes, validate every entry and application/manifest identity, record the selected census digest in the preregistration note, remove the duplicate, and commit the outcome-free census before mutation.

**Commit:** `experiment: freeze v9 gcd census`

## Task 9: Run and assess the v9 intervention

**Files:**

- Create: `docs/experiments/gcd-stratified-v9.json`
- Create: `docs/experiments/gcd-stratified-v9.md`
- Create: `docs/experiments/gcd-stratified-assessment-v9.md`
- Update: `README.md`
- Update: `docs/superpowers/plans/2026-08-29-v9-invariant-closed-loop-coverage.md`

Run `gcd-stratified-experiment` twice with the committed v9 census and externally supplied digest into distinct new paths. Compare semantic output while allowing only explicitly documented runtime diagnostics. Verify all SimpleAllocator closure criteria by candidate definition/original expression, not by a v8 ID. Record whether the loop succeeded and explicitly state the post-selection limitation.

Do not investigate the four previous tool errors unless the closed-loop criteria have completed and time remains.

**Commit:** `experiment: validate v9 invariant closed loop`

## Task 10: Final cleanup and integration

Stop Gradle daemons started by this work. Stop the exact Podman machine only if this task started it. Inspect labeled `dev.tlamutant.run` containers/volumes and relevant child PIDs; remove only verified task-owned remnants. Run final verification from the feature branch, perform a clean-context correctness review, fast-forward the main branch, and rerun the relevant merged-tree checks.

Write the private Japanese work log at `docs.local/logs/2026-08-29/` in the repository root, not the worktree. Record commits, verification commands, frozen digests, experiment conclusion, and any remaining debt.
