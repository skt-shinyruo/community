# Task 1 Report: Failing Nacos Deployment Contracts

## Scope

Only Task 1 contract tests were changed. No Compose, environment, Nacos seed, or MySQL bootstrap production files were modified.

## Files Changed

- `deploy/tests/topology_single_cluster.sh`
  - Requires the rendered single and all three cluster Nacos services to use `nacos/nacos-server:v3.1.2-slim`.
  - Requires all services to receive non-empty `NACOS_AUTH_TOKEN`, `NACOS_AUTH_IDENTITY_KEY`, and `NACOS_AUTH_IDENTITY_VALUE` values from the topology env file.
  - Removes each auth variable in turn and requires Compose rendering to reject it as required.
  - Requires the Nacos v3 readiness endpoint, a `code=0` check, and port `9848` health semantics in rendered services.

- `deploy/tests/nacos_config_seed.sh`
  - Executes the real seed script with a controlled `curl`/`sleep` path.
  - Returns HTTP-successful `{"code":1}` followed by `{"code":0}` and requires two readiness calls to `/nacos/v3/admin/core/state/readiness` before all 17 configs are published.
  - Rejects use of `/nacos/actuator/health`.

- `deploy/tests/nacos_schema_contract.sh`
  - Adds a Nacos 3.1.2 schema baseline and compatibility migration contract.
  - Requires `config_info_gray`, `publish_type`, `gray_name`, and `ext_info` across the baseline/migration.
  - Runs the real `001_bootstrap.sh` with a fake MySQL client reporting an existing `config_info` table, and verifies that the migration input is still executed.

- `deploy/tests/development_clean_break_contract.sh`
  - Rejects production deployment references to Nacos 2.3.2 or `/nacos/actuator/health`.
  - Includes the new schema contract in the clean-break suite.

## Verification

Syntax and diff validation passed:

```text
bash -n deploy/tests/topology_single_cluster.sh deploy/tests/nacos_config_seed.sh \
  deploy/tests/nacos_schema_contract.sh deploy/tests/development_clean_break_contract.sh
git diff --check
```

Expected RED failures against the pre-implementation deployment state:

| Contract | Result | Expected failure |
| --- | --- | --- |
| `deploy/tests/topology_single_cluster.sh` | exit 1 | `nacos must receive NACOS_AUTH_TOKEN` |
| `deploy/tests/nacos_config_seed.sh` | exit 1 | `seed script must retry readiness until the response body contains code=0` |
| `deploy/tests/nacos_schema_contract.sh` | exit 1 | `Nacos schema baseline must match nacos/nacos-server:v3.1.2-slim` |
| `deploy/tests/development_clean_break_contract.sh` | exit 1 | `retired Nacos 2.3.2 runtime or health endpoint remains` |

These failures are intentional and map to the missing Task 2-4 production changes: Nacos 2.3.2 image/auth values, actuator-only health checks, seed readiness that accepts any HTTP 2xx response, and no Nacos 3.1 compatibility migration.

## Concerns

- The contracts intentionally remain RED until Tasks 2-4 update the Compose/environment files, seed script, Nacos schema baseline, and bootstrap migration.
- The schema contract does not require Docker; it exercises the bootstrap script with a controlled MySQL client. Task 5 must still verify fresh and upgrade paths using real MySQL and Nacos containers.
