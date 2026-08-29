# v10 Prospective Census Preregistration

The outcome-free v10 holdout was committed at `7aec546` before any public candidate census or mutation execution. The cohort contains the exact eight entries in `gcd-prospective-manifest-v10.json`, with six standard-risk entries and two high-risk observations retained without replacement.

## Frozen identities

- Discovery receipt SHA-256: `b1eb4678386621b5fdeb062c2517b6bd30c4572f24631df8199037c76cc64d2a`
- Application SHA-256: `31e4ad6b9fcf4edc8ac954eb9851f99867f9e8020832f53ac6961ab9b2c6714a`
- Protocol SHA-256: `34ae5cd40876b8774381fb778dcd6cc3a2a5b8ffe7bcebbe07202bb3096a1a0f`
- Census SHA-256: `d83054a85e074a1d01616b4532a57b6dca43706a42e31a9b0b195ed8293e24b9`

## Outcome-free census

Both write-once census executions were byte-identical. All eight entries reached a terminal `COMPLETED` census result. The census contains 60 GCD sites in total:

| Entry | Status | GCD sites |
|---|---|---:|
| `readers-writers` | `COMPLETED` | 5 |
| `coffee-can` | `COMPLETED` | 8 |
| `ewd840` | `COMPLETED` | 6 |
| `nbacg-guer01` | `COMPLETED` | 12 |
| `c1cs` | `COMPLETED` | 8 |
| `bosco` | `COMPLETED` | 7 |
| `slush-small` | `COMPLETED` | 14 |
| `buffered-random-access-file` | `COMPLETED` | 0 |

The zero-site storage entry remains in the frozen denominator and was not replaced. No mutation verdict, state relation, witness, review decision, or proposed invariant was observed before this census freeze.

The experiment receipt binds the unchanged discovery receipt and exact census bytes in addition to the original registry, protocol, manifest, policy, tools, and application artifacts. Mutation may start only after this census and experiment receipt are committed.
