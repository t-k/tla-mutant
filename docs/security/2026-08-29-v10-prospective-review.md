# v10 Prospective Evaluation Security Review

## Scope

This review covers the historical-source exclusion boundary, protocol and manifest freeze, discovery and experiment receipt chain, write-once publication, review authorization, prospective intervention binding, and rootless Podman execution used by the v10 prospective evaluation.

## Must Fix

None remain. The initial review findings were resolved before the public census:

- The experiment receipt now binds the actual discovery-receipt bytes and census bytes, and preflight compares the census wrapper with that supplied discovery receipt.
- The review command derives the intervention cap from the digest-bound protocol and exposes no outcome-time cap override.
- A validated review is an unforgeable capability bound to the exact dossier and ledger; intervention validation rejects capability reuse with different review evidence.
- The terminal decision vocabulary matches the approved preregistration, and every dossier candidate includes its configured type and legacy target oracle sets.
- The canonical assessment reports every terminal-decision count and the exact selected interventions.
- The prospective repository is allowlisted to `https://github.com/tlaplus/Examples`, and path separation resolves existing ancestor symlinks.

## Should Fix

- `ExecutionPolicy` still ignores unknown properties. Strict JSON syntax is enforced, but the security-critical policy schema should eventually model or explicitly document every accepted metadata field.
- Post-creation container inspection verifies read-only root, network isolation, privilege mode, and resource limits, but does not independently reverify capability drop, `no-new-privileges`, seccomp, user identity, or read-only bind attributes.
- Path canonicalization resolves existing ancestor symlinks but does not prevent a local concurrent actor from replacing an ancestor symlink during a long execution.
- Review selection takes the first three accepted candidate keys globally, while intervention validation permits at most one edit per entry. If multiple accepted candidates from one entry occupy the first three positions, v11 fails closed; the selection rule should be clarified before v11 publication.

## Notes

- The discovery receipt binds six artifacts. All digest and size identities match the reviewed files.
- The application JAR was built twice from clean, uncached inputs and was byte-identical: SHA-256 `31e4ad6b9fcf4edc8ac954eb9851f99867f9e8020832f53ac6961ab9b2c6714a`, size `3118864` bytes.
- The protocol SHA-256 is `34ae5cd40876b8774381fb778dcd6cc3a2a5b8ffe7bcebbe07202bb3096a1a0f`, size `7809` bytes.
- Unit and integration verification completed 162 tests with zero failures, errors, or skips. Rootless Podman sandbox containment also passed all four tests, left no labeled containers or volumes, and the machine was stopped afterward.
- No v10 corpus cache, canonical census, or failed census existed at review time.
- The configured Security Specialist instruction file was absent from the environment, so the required Must Fix / Should Fix / Notes perspective was applied directly.

Verdict: GO after committing the complete outcome-free input set and rechecking a clean worktree and unchanged receipt/application identities.

## Final v11 closure review

### Must Fix

None remain. The first v11 report was preserved as `UNRESOLVED` after review found that it discarded the type-only state relation and witness. The corrective implementation retains both values and requires exact continuity with v10. A later replacement-resistance review found that a reused candidate ID could carry substituted mutation content; the final validator now requires exact equality of the family, definition, original guard, and replacement across the dossier-bound v10 source and v11 intervention. Any mismatch yields `UNRESOLVED`.

The closure command also requires `--execution-application`. This keeps the intervention receipt bound to the exact application JAR that produced the run while allowing the independently strengthened validator JAR to verify it.

### Should Fix

- `ProspectiveClosureAssessment` does not embed the digest and size of every input artifact. The frozen receipt chain and reproducible command provide traceability, but embedding those identities would make the assessment more self-contained.
- Container inspection verifies the principal resource boundary but does not independently reverify every effective capability, security option, user namespace, read-only mount, and ulimit after creation.
- Existing-ancestor canonicalization and separate write-once outputs leave limited local TOCTOU and partial-publication exposure. Revalidation immediately before publication and atomic bundle publication would further harden the workflow.

### Notes

- The corrective execution JAR is SHA-256 `0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd`, size `3148231` bytes. The final exact-identity validator JAR is SHA-256 `1309e94bf09e436e110c9c22c556a6b977d9c4581f008788f50487a8b05fa0b2`, size `3148611` bytes.
- The corrected result is SHA-256 `c8a2b771496aa7ff07ec0fb6c4e7648bb5753698cf971bd8ad8188b34c08ead6`, size `107527` bytes. The machine assessment is SHA-256 `2c603ebb122f5dc3d9ec7883d0c63e0a7b65d197baec6167cd0c93662d1f52cd`, size `1288` bytes.
- Final verification completed 149 unit, 22 integration, and four sandbox tests with no failures, errors, or skips. No owned labeled containers or volumes remained. The Gradle daemon was stopped, the Podman machine reported both `Running: false` and `Starting: false`, and the orphan-process audit was empty.
- Independent correctness and security rereviews both returned `GO` with no blocking or high-severity findings. The configured Security Specialist instruction file was absent, so the required Must Fix / Should Fix / Notes perspective was applied directly.

Final verdict: GO.

## Assessment provenance follow-up

### Must Fix

None. Schema version 2 embeds exactly 18 canonically ordered artifact identities. Missing, unexpected, duplicate, reordered, malformed, or zero-sized entries fail assessment construction. The closure CLI derives these identities from the same bounded snapshots used by preflight and result reconstruction, plus a bounded snapshot of its validator JAR.

### Should Fix

- `validator-application` is measured from the `Main` code-source path after the JVM has loaded validator classes. A same-user concurrent replacement or in-place write could make the measured path bytes differ from already loaded class bytes. Eliminating this bounded local TOCTOU risk would require an immutable launcher or frozen-copy execution model; the current result assumes no hostile same-user mutation during validation.
- Container post-creation inspection and multi-output atomic publication retain the earlier defense-in-depth opportunities documented above.

### Notes

- The schema version 2 assessment is SHA-256 `b8615d046ca5c43807c11988c96722c1710278836a28d6a49604fc085748bff7`, size `3889` bytes. Its closure decision and outcome projection are byte-identical to schema version 1 after removing schema and provenance fields.
- The provenance-aware validator JAR is reproducible at SHA-256 `99cc4020d7ecd691b2285c1d9d12a1299155228b6e8ff346e6cf54193b1a6148`, size `3152637` bytes. The assessment distinguishes it from the receipt-bound execution JAR at SHA-256 `0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd`, size `3148231` bytes.
- Integration verification recomputes every repository-resident digest and size, rejects direct symlinks and non-regular files, and asserts that execution and validator JAR identities differ.
- Final verification completed 154 unit, 23 integration, and four sandbox tests with no failures, errors, or skips. No owned labeled containers or volumes remained; the Gradle daemon and Podman machine were stopped, and the orphan-process audit was empty.
- Independent correctness review returned `GO` with no Blocker or High findings. Independent security review returned `GO` with no Must Fix findings and identified the bounded local validator-JAR TOCTOU risk above.

Follow-up verdict: GO.
