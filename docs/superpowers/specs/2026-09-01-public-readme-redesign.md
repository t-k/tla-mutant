# Public README Redesign

## Purpose

Rewrite the README around the path a TLA+ practitioner follows when deciding whether to try `tla-mutant`. The README should explain the tool before it explains the experiment that produced it.

The project will be presented as an experimental CLI that can be run today, not as a production-ready verifier and not only as a research artifact.

## Primary audience

The primary reader already uses TLA+ and TLC and wants to test whether a model's State Invariants leave reachable states unconstrained. The reader should be able to answer these questions without opening another document:

1. What does `tla-mutant` do?
2. When is it useful?
3. What does a survivor mean?
4. How can I run a small trusted-local experiment?
5. Which inputs require the Podman boundary?
6. What evidence supports the approach, and what remains unproven?

Experiment reviewers remain a secondary audience. The existing frozen assessments and reproduction commands will remain available after the user-facing sections.

## Positioning and claims

The opening will state the mechanism directly:

> `tla-mutant` mutates a TLA+ model and runs TLC to find behaviors that the model can reach without violating its current State Invariants. A surviving mutant can expose a missing or overly weak invariant, but it is a review candidate—not an automatic defect report.

The README may make the following evidence-backed claims:

- The implemented vertical slice supports GCD, BRL, and UOI mutation families.
- The GCD-stratified experiment produced reproducible state-expanding survivors.
- The preregistered prospective evaluation used eight previously unseen specifications and produced two review-eligible survivors.
- One user-delegated review accepted an invariant proposal, and the separately frozen intervention showed that the added invariant killed the motivating mutant while the type-only oracle still passed.
- The CLI has state-invariant and full-property oracle modes, reproducible identities, bounded execution, and a rootless Podman corpus path.

The README must state the corresponding limits near those claims:

- A surviving mutant is a prompt for review, not proof of a specification defect.
- The accepted proposal is not external human or upstream-maintainer acceptance.
- One successful prospective intervention does not establish general effectiveness.
- The CLI remains experimental and is not a replacement for reviewing the model, properties, and counterexamples.

## Information architecture

The README will use this order:

1. **Overview**: two short paragraphs describing the mechanism, use case, and experimental status.
2. **What it checks**: the supported mutation families and the distinction between state-invariant and full-property modes.
3. **Quick start**: requirements, artifact verification, build, and one trusted-local command.
4. **Reading the result**: explain `KILLED`, `STATE_EXPANDING`, `SURVIVED`, and `UNRESOLVED`, then show the review loop from witness to candidate invariant.
5. **Evidence so far**: summarize the v8, v10, and v11 results with exact denominators and direct links to the frozen assessments.
6. **Limits**: collect the interpretation limits that a user must understand before applying the result.
7. **Security and reproducibility**: explain trusted host execution, the untrusted corpus boundary, verified JAR identities, manifests, deadlines, and fail-closed outcomes.
8. **Detailed experiment reproduction**: retain the existing v8 through v11 commands and provenance notes after the usage-oriented material.

The current original-corpus reproduction command will remain, but it will no longer interrupt the first-use path.

## Writing style

The prose will be concise, specific, and technical without sounding promotional.

- Prefer concrete subjects and verbs: “The runner rejects a mismatched tool frame,” not “Robust validation is provided.”
- Avoid unsupported adjectives such as “powerful,” “comprehensive,” “seamless,” and “production-ready.”
- Do not restate a command in prose when the command is self-explanatory.
- Keep evidence and its limitation in the same section.
- Use short paragraphs for the main narrative and lists only where readers need to compare exact items.
- Avoid repeated summaries, generic transition sentences, and template-like conclusions.
- Preserve the project's exact terms where they carry semantics, including State Invariant, state-expanding, full-property, witness, and unresolved.

## Quick-start boundary

The first executable path will use the direct CLI only for a trusted local model. It will include:

1. `./gradlew verifyTlaTools verifyFrozenApplication fatJar`
2. The explicit application and TLA+ Tools classpath.
3. A minimal command that produces a JSON report.
4. A nearby warning that every classpath JAR executes with the user's host privileges.

The README will direct public, downloaded, or otherwise untrusted specifications to the corpus commands that retain the rootless Podman boundary. It will not imply that direct mode is sandboxed.

## Result interpretation

The result section will distinguish execution classification from human judgment:

- `KILLED`: the configured oracle rejects the mutant.
- `STATE_EXPANDING`: the mutant reaches a state outside the baseline reachable-state set while the selected State Invariants still pass. This is the main invariant-gap review candidate.
- `SURVIVED`: the selected oracle did not kill the mutant, but no qualified state-expansion witness was established.
- `UNRESOLVED`: timeout, resource exhaustion, invalid execution evidence, or another condition prevented a trustworthy verdict.

The README will describe the intended workflow as: inspect the mutation and witness, write the smallest domain-specific invariant that excludes the unwanted state, and rerun the mutant with that invariant enabled. It will not claim that the tool generates a correct invariant automatically.

## Scope of this revision

This revision changes `README.md` and adds no CLI behavior. Existing experiment artifacts, frozen identities, manifests, and commands remain unchanged. Links may be reordered or relabeled, but no experiment result will be recomputed.

Detailed reproduction material will remain in the README for this revision. Moving it to a separate document is deferred until the user-facing README has been evaluated in practice.

## Acceptance criteria

- The first screen explains the tool, its intended use, and its experimental status without requiring experiment-version knowledge.
- A TLA+ user can reach a verified trusted-local command from the top half of the README.
- The state-invariant and full-property modes are distinguished before either command is shown.
- Result classifications are explained in terms a user can act on.
- The v10 and v11 evidence is stated with exact counts and paired with its limitations.
- Trusted and untrusted input paths are not conflated.
- Every existing reproduction command and material assessment link remains reachable.
- Markdown links and fenced shell commands are syntactically valid.
- The prose contains no promotional claims, filler conclusions, or unexplained shifts between user guidance and experiment history.

## Verification

Verification will include:

- `./gradlew spotlessCheck test`
- A local Markdown link-target check for repository-relative links.
- A comparison of documented CLI options against `--help` output for the commands shown in Quick start and full-property examples.
- A manual pass over every numerical claim against the frozen assessment files.
