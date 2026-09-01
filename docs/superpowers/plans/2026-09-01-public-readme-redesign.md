# Public README Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `README.md` so a TLA+ practitioner can understand, try, and correctly interpret the experimental CLI before reaching the detailed experiment history.

**Architecture:** Keep one public README, but divide it into a user-facing first half and an evidence/reproduction second half. Preserve the frozen experiment commands and artifacts; change only their placement, surrounding explanation, and links. Validate every numerical claim against frozen assessments and every documented option against the packaged CLI.

**Tech Stack:** Markdown, Gradle, Java 21, Picocli, TLA+ Tools 1.8.0, shell-based documentation checks

---

## File structure

- Modify: `README.md` — public overview, first-use path, result interpretation, evidence, limits, security guidance, and retained reproduction commands.
- Reference: `docs/superpowers/specs/2026-09-01-public-readme-redesign.md` — approved scope and writing constraints.
- Reference: `docs/experiments/gcd-prospective-final-assessment.md` — authoritative v10 and v11 counts, outcome, and limitations.
- Reference: `docs/experiments/gcd-prospective-assessment-v10.md` — authoritative review-decision totals.
- Reference: `docs/experiments/gcd-stratified-assessment.md` — authoritative v8 result summary.
- Create: `docs.local/logs/2026-09-01/2026-09-01-003-public-readme-redesign.md` — ignored local work log.

### Task 1: Establish the documentation acceptance baseline

**Files:**
- Test: `README.md`
- Reference: `docs/superpowers/specs/2026-09-01-public-readme-redesign.md`

- [ ] **Step 1: Confirm that the approved user-facing sections are absent**

Run:

```sh
for heading in "## What it checks" "## Quick start" "## Reading the result" "## Evidence so far" "## Limits" "## Security and reproducibility"; do
  rg -F "$heading" README.md
done
```

Expected: the command does not find all six headings and exits nonzero. This is the documentation equivalent of the red step.

- [ ] **Step 2: Record the authoritative evidence statements before editing**

Run:

```sh
rg -n "eight previously unevaluated|two completed survivors|accepted one candidate|30,943 states|adopted-target-invariant-kill|does not estimate usefulness" docs/experiments/gcd-prospective-final-assessment.md
rg -n '\| `ACCEPT` \| 1 \||\| `NEEDS_STRENGTHENING` \| 1 \|' docs/experiments/gcd-prospective-assessment-v10.md
```

Expected: each quoted fact appears in the frozen assessment files. Do not infer broader claims from these results.

### Task 2: Rewrite the README around the first-use path

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace the current opening with the approved positioning**

Use this opening, followed by one short experimental-status paragraph:

```markdown
# tla-mutant

`tla-mutant` mutates a TLA+ model and runs TLC to find behaviors that the model can reach without violating its current State Invariants. A surviving mutant can expose a missing or overly weak invariant, but it is a review candidate—not an automatic defect report.

The current implementation is an experimental CLI with three mutation families: guard-conjunct deletion (GCD), boundary relaxation (BRL), and update off-by-one (UOI). It supports a State Invariant oracle for finding state-expanding candidates and a full-property oracle for checking every property configured in the original cfg.
```

Keep experiment version names and historical counts below the first six paragraphs. The first screen must explain what the tool does, why a user would run it, and that it remains experimental without requiring knowledge of v8, v10, or v11.

- [ ] **Step 2: Add `What it checks` before requirements**

Explain the three mutation families in one sentence each. Then contrast the modes:

```markdown
- `state-invariants` asks whether the configured State Invariants reject the mutant and records qualified state-expansion evidence. This is the default mode.
- `full-property` leaves the cfg unchanged and lets configured invariants, action properties, temporal properties, fairness, constraints, and deadlock checking contribute to the verdict.
```

State that neither mode decides whether a survivor is a domain defect; a person must inspect the mutation and witness.

- [ ] **Step 3: Turn requirements and trusted-local census into `Quick start`**

Keep Java 21, rootless Podman with cgroup v2, and the pinned execution-policy image as requirements. Show this exact verification and build command:

```sh
./gradlew verifyTlaTools verifyFrozenApplication fatJar
```

Then show the trusted-local census and write its standard output to a JSON file:

```sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main census \
  --model-dir path/to/model \
  --module Spec.tla \
  --config Spec.cfg \
  > build/census.json
```

Place the host-execution warning immediately before the command. State immediately after it that downloaded or otherwise untrusted specifications belong in the rootless Podman corpus path.

- [ ] **Step 4: Add `Reading the result`**

Define the four user-facing outcomes exactly:

```markdown
- `KILLED`: the selected oracle rejected the mutant.
- `STATE_EXPANDING`: the mutant reached a state outside the baseline reachable-state set while the selected State Invariants still passed.
- `SURVIVED`: the selected oracle did not kill the mutant, but the run did not establish a qualified state-expansion witness.
- `UNRESOLVED`: the runner could not produce a trustworthy verdict because of a timeout, resource limit, invalid execution evidence, or another execution failure.
```

Follow with the review loop: inspect the patch and mutant-only witness, write the smallest domain-specific invariant that excludes the unwanted state, and rerun with that invariant. Explicitly state that `tla-mutant` does not generate or approve the invariant automatically.

- [ ] **Step 5: Add `Evidence so far` and `Limits`**

Summarize the evidence without reproducing the complete experiment chronology:

```markdown
The v8 GCD-stratified run found nine reproducible witness-bearing state expansions across six specifications. The preregistered v10 holdout fixed eight previously unseen specifications before mutation; two completed survivors met the review rule. A user-delegated review accepted one invariant proposal and marked the other `NEEDS_STRENGTHENING`. In the separately frozen v11 intervention, the accepted invariant killed its motivating mutant while the type-only oracle still passed with 30,943 explored states.
```

Link the v8 assessment and prospective final assessment from that paragraph. In `Limits`, state all four boundaries from the approved design: review candidate rather than defect proof, delegated rather than independent acceptance, one intervention rather than a prevalence estimate, and finite-state TLC evidence rather than an unbounded proof.

- [ ] **Step 6: Consolidate security guidance**

Create `Security and reproducibility` before the detailed commands. Retain the verified application and TLA+ Tools identities, explicit classpath requirement, trusted-host warning, rootless Podman boundary, frozen manifests, bounded execution, and fail-closed behavior. Avoid repeating the same warning in later experiment sections; link back to this section when needed.

- [ ] **Step 7: Keep detailed commands after the user-facing sections**

Retain the current sections and all their command arguments:

- `Run a full-property experiment`
- `Reproduce the original corpus experiment`
- `Reproduce the frozen v8 GCD-stratified experiment`
- `Run the preregistered v9 invariant intervention`
- `Run the preregistered v10 prospective evaluation`

Remove only introductory paragraphs that have become exact duplicates of the new overview, evidence, limits, or security sections.

- [ ] **Step 8: Run the heading acceptance check**

Run:

```sh
for heading in "## What it checks" "## Quick start" "## Reading the result" "## Evidence so far" "## Limits" "## Security and reproducibility"; do
  rg -F "$heading" README.md
done
```

Expected: all six headings are printed and the command exits zero.

- [ ] **Step 9: Commit the structural rewrite**

```sh
git add README.md
git commit -m "docs: orient README around first use"
```

### Task 3: Verify claims, links, and documented CLI options

**Files:**
- Modify: `README.md` only if verification finds an error
- Test: `README.md`

- [ ] **Step 1: Compare numerical claims with frozen evidence**

Run:

```sh
rg -n "nine reproducible|six specifications|eight previously unseen|two completed survivors|accepted one|NEEDS_STRENGTHENING|30,943" README.md
rg -n "nine reproducible|six specifications" docs/experiments/gcd-stratified-assessment.md
rg -n "eight previously unevaluated|two completed survivors|accepted one candidate|30,943 states" docs/experiments/gcd-prospective-final-assessment.md
```

Expected: README counts match the linked assessments. If the v8 assessment uses different wording, use its exact denominator and do not preserve an unsupported summary.

- [ ] **Step 2: Check repository-relative Markdown targets**

Run:

```sh
rg -o '\]\([^):#]+(?:#[^)]+)?\)' README.md \
  | sed -E 's/^.*\]\(([^)#]+)(#[^)]+)?\)$/\1/' \
  | sort -u \
  | while IFS= read -r target; do test -e "$target" || { echo "missing: $target"; exit 1; }; done
```

Expected: no `missing:` lines and exit zero.

- [ ] **Step 3: Compare shown options with packaged help**

Run:

```sh
./gradlew fatJar
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main census --help > build/census-help.txt
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main experiment --help > build/experiment-help.txt
rg -- '--model-dir|--module|--config' build/census-help.txt
rg -- '--model-dir|--module|--config|--tools-jar|--work-dir|--oracle-mode|--manifest|--timeout-seconds|--experiment-timeout-seconds|--json-report' build/experiment-help.txt
```

Expected: every documented option is present in the corresponding help output.

- [ ] **Step 4: Check prose for the rejected style patterns**

Run:

```sh
rg -n -i '\b(powerful|comprehensive|seamless|revolutionary|production-ready|game-changing)\b' README.md
```

Expected: no matches. Read the overview, evidence, and limits aloud once; remove promotional wording, repeated summaries, generic transitions, and claims whose subject is unclear.

- [ ] **Step 5: Commit any verification corrections**

If `README.md` changed during verification:

```sh
git add README.md
git commit -m "docs: correct README evidence and examples"
```

If no correction was needed, record that fact in the work log and do not create an empty commit.

### Task 4: Run the final verification and record the work

**Files:**
- Create: `docs.local/logs/2026-09-01/2026-09-01-003-public-readme-redesign.md`

- [ ] **Step 1: Run project verification**

Run:

```sh
./gradlew spotlessCheck test verifyFrozenApplication --no-build-cache --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 2: Inspect the complete README diff**

Run:

```sh
git diff HEAD^ --check
git diff HEAD^ -- README.md
```

Expected: no whitespace errors; the first screen states the use case and experimental status without experiment version names; the user-facing path precedes the detailed experiment history; no existing command has lost an argument.

- [ ] **Step 3: Write the ignored local work log**

Record the approved positioning, section order, commits, evidence sources, documentation checks, Gradle result, and whether a push occurred in `docs.local/logs/2026-09-01/2026-09-01-003-public-readme-redesign.md`.

- [ ] **Step 4: Confirm final repository state**

Run:

```sh
git status --short --branch
git log -3 --oneline
```

Expected: no tracked worktree changes. The branch is ahead of `origin/main` until the user authorizes or requests a push.
