# Prospective Invariant Discovery Final Assessment

## Result

The prospective endpoint is positive and the selected intervention closure is machine-verified under the frozen finite model.

The v10 holdout fixed eight previously unevaluated specifications before mutation. Its census completed all eight specifications and enumerated 60 GCD sites. Five specifications completed mutation, three were experiment-censored, 20 GCD outcomes completed, 40 remained unresolved, and two completed survivors met the preregistered review eligibility rule. The complete user-delegated review accepted one candidate and classified the other as `NEEDS_STRENGTHENING`.

The accepted candidate is `nbacg-guer01` / `gcd-3c1ee8b4429ee7af9c2f`, a deletion of `\/ pc[i] = "YES" \/ pc[i] = "NO"` from `AbortNoYes`. With the original type oracle only, the baseline explored 24,922 states and the mutant explored 30,943 states, passed the oracle, and produced a mutant-only state in which `nSntYesF = 1` while no process had status `BYZ`. The reviewed invariant was:

```tla
FaultVoteBoundsWithinCrashed ==
  nSntNoF + nSntYesF <= Cardinality({ i \in Proc : pc[i] = "BYZ" })
```

The v11 intervention inserted only that exact reviewed definition into a private derived copy of the root module and registered it as an adopted target invariant. The v11 census again completed all eight specifications, retained all 60 GCD sites, and retained the exact selected candidate ID. In the corrected v11 experiment, the derived baseline again explored 24,922 states. The selected mutant passed the type-only oracle with 30,943 states, retained `STATE_EXPANDING`, and reproduced the exact v10 mutant-only witness. The full oracle detected the new invariant violation after 435 explored mutant states. The terminal verdict and attribution were both `adopted-target-invariant-kill`.

The review-bound machine assessment reports `closureStatus: VERIFIED`, `selected: 1`, `closed: 1`, and `unresolved: 0`. It reconstructs the corrected result from its frozen manifest, census, receipt, and embedded experiment, then requires the exact selected review key, mutation family, definition, original guard, replacement, v10 eligibility, baseline count, type-only execution, state count, relation, witness, and adopted-target kill. The validator receives the execution application separately, so its receipt check remains bound to the exact JAR that produced the experiment even after the validator itself is strengthened.

This closes the motivating loop: the source experiment exposed behavior outside the existing type-only oracle, the review produced an abstraction-aligned State Invariant, and the separately frozen intervention detected that same behavior without changing the transition relation. The corrected v11 report still contains one eligible candidate because the other v10 proposal, classified as `NEEDS_STRENGTHENING`, remains a type-only survivor; v11 is a closure run, not a new review round.

The first v11 result is preserved separately with an `UNRESOLVED` assessment. It recorded the type-only pass and adopted kill but discarded the type-only relation and witness. A corrective instrumentation run was therefore frozen with new write-once paths. It did not change the holdout, candidate selection, review, predicate, manifest, source edit, transition relation, or execution limits.

## Reproducibility and provenance

- v10 discovery: SHA-256 `3002bf1e67a68d9d015283bcca6c8cd72c2aa8d4d050ca53cf51f240214350c3`, 103,370 bytes
- v10 review ledger: SHA-256 `8018ae9c1fe2db58de41b39d3b335704e7012ffdd710809618d0f198a554cfdd`
- initial unresolved v11 result: SHA-256 `4bb5535421e2c4132491a405d522d252c3e0d38aae4b2bcb9ac2869ccad66925`, 103,853 bytes
- corrective v11 census receipt: SHA-256 `12c865261d3a92abf72214aea401f79e50d9778b2c24434babe9c79554e4086e`, 2,080 bytes
- corrective v11 census: SHA-256 `6952c2aca811ca6a9975414df92b3612d21265177dbe2ba9325d9f0e653e4725`, 25,493 bytes
- corrective v11 experiment receipt: SHA-256 `09767a51ff6f562041cb48ba8ce5a51bf958a95e56d4b93fa0163a988142d783`, 2,387 bytes
- canonical corrected v11 result: SHA-256 `c8a2b771496aa7ff07ec0fb6c4e7648bb5753698cf971bd8ad8188b34c08ead6`, 107,527 bytes
- machine closure assessment: SHA-256 `2c603ebb122f5dc3d9ec7883d0c63e0a7b65d197baec6167cd0c93662d1f52cd`, 1,288 bytes
- reproducible corrective application JAR: SHA-256 `0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd`, 3,148,231 bytes
- reproducible exact-identity validator JAR: SHA-256 `1309e94bf09e436e110c9c22c556a6b977d9c4581f008788f50487a8b05fa0b2`, 3,148,611 bytes

Two clean no-build-cache corrective application builds were byte-identical, and two clean no-build-cache exact-identity validator builds were byte-identical. The two corrective v11 census executions were byte-identical. The two corrective v11 mutation executions were semantically identical under the declared diagnostic normalization, and their Markdown reports were byte-identical. Raw mutation JSON differed only in permitted diagnostic metadata.

Git ancestry preserves both boundaries. The v10 review commit `fbc6bdc` precedes v11 input commit `5666981`, and v11 census commit `dc96bb9` precedes the initial experiment receipt commit `a8370b0`. The initial evidence was declared unresolved in commit `27d3822`. The corrective input commit `2f7444b` precedes corrective census commit `e37bfaf`, which precedes corrective experiment receipt commit `a766eb1`; corrected mutation and closure artifacts were created only afterward.

## Scope and limitations

The result supports the narrow claim that this preregistered workflow found at least one review-accepted State Invariant on an unseen, fixed eight-specification cohort and that the adopted invariant detects its motivating GCD mutant in the bounded model. It improves on the earlier post-selected single-specification demonstration because cohort membership, review rules, censoring, and the intervention cap were fixed before v10 outcomes.

It does not estimate usefulness prevalence in the full public TLA+ corpus. Three specifications were mutation-censored and their 40 sites remain unresolved without replacement. The holdout is small and deliberately enriched. Review was delegated to the agent by the user, not performed by an independent domain owner or upstream maintainer. TLC finite-state evidence is not an unbounded inductive proof, and adoption into the upstream specification remains a separate decision.
