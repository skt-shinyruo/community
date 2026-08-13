# Deployment Tests

Deployment verification is grouped by the contract it owns:

- `contracts/compose/`: topology composition, isolation, ports and reset behavior.
- `contracts/database/`: current-state schemas, forward migrations and control-plane seeds.
- `contracts/config/`: Nacos, Kafka, observability and retired-configuration guards.
- `contracts/images/`: production image contracts.
- `smoke/`: checks that require an already running deployment.

Run every repeatable contract from the repository root:

```bash
./deploy/tests/run-contracts.sh
```

Pass one or more group names to narrow the run, for example
`./deploy/tests/run-contracts.sh compose config`. Smoke tests are deliberately excluded from this entry point.
