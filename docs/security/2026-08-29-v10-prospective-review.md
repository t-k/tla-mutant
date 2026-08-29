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
