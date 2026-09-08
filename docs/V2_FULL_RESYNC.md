# W1 NFC Reader v2 — Full Re-Sync contract

Full Re-Sync is an explicit advanced History action. It is not the normal update path.

## Product behavior

- Normal NFC contact remains Live-only.
- Normal History Sync All keeps choosing `INITIAL_FULL` for incomplete families and `INCREMENTAL` for COMPLETE families.
- Full Re-Sync becomes available only when Hour, Day and Month each already have an authoritative COMPLETE baseline.
- Full Re-Sync traverses Hour, Day and Month in that order and uses `FULL_RESYNC` for each family.
- The action may take as long as the initial full synchronization and therefore requires explicit confirmation.
- Existing local history is never deleted before the reread. Matching observations confirm known periods; differing observations remain preserved through the existing revision/conflict model.

## Safety invariants

Full Re-Sync does not introduce a new meter command or transport shortcut. Every family retains the established safety shell:

1. Reset/default normalization.
2. Protected and verified Live/default preflight.
3. Family-specific boundary selection and guard.
4. Selected traversal with immediate persistence of each accepted observation.
5. Completion only from supported full-traversal terminal/ring evidence.
6. Final default restore and verified Live/default state before COMPLETE.

A logical NFC-V reconnect does not prove default application state. A following family starts only when the previous family completed or at least restored and verified default state. A later partial or failed Full Re-Sync does not erase an already authoritative COMPLETE baseline.

`KNOWN_RECORD_REACHED` remains an incremental-only completion reason and is not a successful Full Re-Sync stop.
