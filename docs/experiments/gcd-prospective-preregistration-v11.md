# Prospective v11 Intervention Preregistration

The v11 intervention is a conditional continuation of the frozen v10 prospective evaluation. The v10 review selected exactly one accepted candidate, so the preregistered stopping rule authorizes one insertion-only intervention.

## Frozen intervention

- Input commit before the v11 census: `5666981`
- Selected entry: `nbacg-guer01`
- Selected candidate: `gcd-3c1ee8b4429ee7af9c2f`
- Definition and removed guard: `AbortNoYes`, `\/ pc[i] = "YES" \/ pc[i] = "NO"`
- Adopted operator: `FaultVoteBoundsWithinCrashed`
- Exact predicate: `nSntNoF + nSntYesF <= Cardinality({ i \in Proc : pc[i] = "BYZ" })`
- Predicate SHA-256: `2e114e5ad2a7469fcf28a692500e10c978468dc746cf05d49ba78a95b475490d`
- Root source base identity: SHA-256 `01beedb7aa321e51e626d11fd71298113c56995173569a9814be599f5943703d`, 8,505 bytes
- Insertion: byte offset 7,348, exact reviewed operator text only, 102 bytes
- Derived source identity: SHA-256 `18781906955ab68b3f1b00272c45d5e4145af3c520fdea3fdc59e19cc3cecf92`, 8,607 bytes
- v11 census receipt identity: SHA-256 `1bfdf198494aab7ff91d2e7f3b4fb86e42bfb20a0422ddcab2f54160d228f66a`, 2,080 bytes
- Reproducible application identity: SHA-256 `5d6a7a39aabe1ae9a63a9f363b8c29d50c1e4582caea56a5dc098767cfcf5607`, 3,136,225 bytes

The v11 protocol is identical to the v10 protocol except for the profile. The v11 manifest preserves all eight entries, their order, source identities, limits, type invariants, legacy target invariants, and selection metadata. Only the selected entry gains the reviewed adopted invariant and its insertion-only derived source edit.

## Frozen census

Two executions from the committed inputs produced byte-identical census reports. The canonical census has SHA-256 `ccd616cc07c1fe7d8c4b50a9c16e96dc2b918f6939627343b5e69b5ef0c9d700` and size 25,493 bytes. All eight specifications completed, the corpus retained all 60 GCD sites, and the selected candidate retained its exact v10 candidate ID.

An earlier invocation was attempted after starting the Podman machine in a non-persistent command session. The machine stopped when that session ended, so all eight entries were fail-closed as `FETCH_OR_SANDBOX_ERROR`; no canonical output was published. This was an execution-environment startup error, not a census observation. The successful canonical and repeat runs used a persistent owned shell session and are byte-identical.

## Closure rule

The v11 mutation phase must use the committed v11 census and a separate experiment receipt that binds it. Closure succeeds for the selected candidate only if the candidate is completed, the baseline passes, the mutant survives the type and legacy oracle sets with the expected state-expanding witness, the full oracle set rejects it, and attribution is `adopted-target-invariant-kill`. Any missing candidate, censored execution, baseline rejection, changed witness semantics, or non-adopted attribution remains unresolved rather than being counted as closure.
