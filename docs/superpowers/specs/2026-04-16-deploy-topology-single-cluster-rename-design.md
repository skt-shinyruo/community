# Deploy Topology `single`/`cluster` Rename Design Spec

**Date:** 2026-04-16
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Background

The repository currently exposes two local deployment topology names:

- `dev`
- `ha`

These names are not ideal:

- `dev` describes usage intent, not topology shape
- `ha` describes a capability target, not the actual local topology shape
- the pair is asymmetric, which makes operator UX and documentation less clear

The current stacks are better described as:

1. a single-node local development topology
2. a local multi-instance / clustered rehearsal topology

The user explicitly chose a full hard cutover to new names:

- `single`
- `cluster`

The user also explicitly rejected keeping `dev` / `ha` compatibility aliases.

That means the repository should move to a single naming vocabulary everywhere:

- CLI
- env files
- compose filenames
- nginx config filenames
- default compose project names
- user-facing docs

Old topology names must stop working after this change.

---

## 2. Goals

### 2.1 Primary goals

- Replace local topology names `dev` and `ha` with `single` and `cluster`
- Make the naming consistent across operator, filenames, env examples, and docs
- Keep existing topology behavior unchanged except for naming
- Preserve current topology semantics:
  - `single` remains the lightweight single-node local stack
  - `cluster` remains the local multi-replica / clustered rehearsal stack

### 2.2 UX goals

- Make topology names describe topology shape, not mixed concepts
- Ensure commands read naturally:
  - `./deploy/deployment.sh up --topology single`
  - `./deploy/deployment.sh up --topology cluster`
- Make compose project names equally explicit:
  - `community-single`
  - `community-cluster`

---

## 3. Non-goals

- Do not redesign the stack composition itself
- Do not add topology aliases for backward compatibility
- Do not change service counts, middleware bootstrap behavior, or network wiring
- Do not revisit the `full` / `infra` scope design
- Do not change Spring `dev` profile usage inside application runtime config unless required by this rename

---

## 4. Decision

Adopt a repository-wide hard rename:

- `dev` -> `single`
- `ha` -> `cluster`

This rename applies to:

- `--topology` accepted values
- default env file lookup
- default compose project names
- topology-specific compose filenames
- topology-specific nginx config filenames
- env example filenames
- user-facing documentation

Old names:

- `--topology dev`
- `--topology ha`

will no longer be accepted.

Old file paths such as:

- `deploy/.env.dev.example`
- `deploy/.env.ha.example`
- `deploy/compose.infra.mysql.dev.yml`
- `deploy/compose.infra.mysql.ha.yml`
- `deploy/nginx/nginx.dev.conf`
- `deploy/nginx/nginx.ha.conf`

will be replaced by the new naming scheme.

---

## 5. Rename matrix

### 5.1 CLI and defaults

- `--topology single|cluster`
- default topology: `cluster`
- default project names:
  - `community-single`
  - `community-cluster`

### 5.2 Env files

- `deploy/.env.single.example`
- `deploy/.env.cluster.example`

Default runtime env lookup:

- `single` -> `deploy/.env.single`
- `cluster` -> `deploy/.env.cluster`

Hard-cut behavior:

- remove `deploy/.env` as a supported default path
- remove `deploy/.env.example` as the old compatibility alias doc path

### 5.3 Compose files

Infra:

- `compose.infra.mysql.single.yml`
- `compose.infra.mysql.cluster.yml`
- `compose.infra.redis.single.yml`
- `compose.infra.redis.cluster.yml`
- `compose.infra.kafka.single.yml`
- `compose.infra.kafka.cluster.yml`
- `compose.infra.elasticsearch.single.yml`
- `compose.infra.elasticsearch.cluster.yml`
- `compose.infra.nacos.single.yml`
- `compose.infra.nacos.cluster.yml`
- `compose.infra.xxl-job.single.yml`
- `compose.infra.xxl-job.cluster.yml`
- `compose.infra.mock-data-studio-bootstrap.single.yml`
- `compose.infra.mock-data-studio-bootstrap.cluster.yml`

Runtime:

- `compose.runtime.services.single.yml`
- `compose.runtime.services.cluster.yml`
- `compose.runtime.frontend-nginx.single.yml`
- `compose.runtime.frontend-nginx.cluster.yml`
- `compose.runtime.mock-data-studio.single.yml`
- `compose.runtime.mock-data-studio.cluster.yml`

### 5.4 Nginx configs

- `deploy/nginx/nginx.single.conf`
- `deploy/nginx/nginx.cluster.conf`

---

## 6. Proposed design

### 6.1 Operator behavior

[deploy/deployment.sh](/home/feng/code/project/community/deploy/deployment.sh) will be updated so that:

- help text only advertises `single|cluster`
- topology validation only accepts `single|cluster`
- `TOPOLOGY="cluster"` becomes the default
- default env lookup uses:
  - `deploy/.env.single`
  - `deploy/.env.cluster`
- default project names become:
  - `community-single`
  - `community-cluster`
- file assembly uses only `*.single.yml` and `*.cluster.yml`

Examples after the rename:

```bash
./deploy/deployment.sh up --topology single
./deploy/deployment.sh up --topology single --scope infra
./deploy/deployment.sh up --topology cluster
./deploy/deployment.sh config --topology cluster
```

Examples that must fail after the rename:

```bash
./deploy/deployment.sh up --topology dev
./deploy/deployment.sh up --topology ha
```

### 6.2 File layout

The `deploy/` tree will remain topology-split in the same way as today, but names will be rewritten from `dev` / `ha` to `single` / `cluster`.

No topology behavior changes are introduced by this step:

- single-node compose files remain single-node
- clustered compose files remain clustered
- shared files such as `compose.yml`, `compose.observability.yml`, and `compose.infra.mailhog.yml` stay shared

### 6.3 Documentation model

All user-facing docs will describe the two topologies as:

- `single`: 单机开发拓扑
- `cluster`: 本地多副本 / 集群演练拓扑

Docs that currently mention `dev` / `ha` will be rewritten so the repository exposes only one vocabulary.

This includes:

- repository root README
- `deploy/README.md`
- `docs/DEPLOYMENT.md`
- `docs/OBSERVABILITY.md`
- `docs/LOCAL_HA.md`
- `backend/README.md`
- `tools/im-load/README.md`
- other current operational docs that instruct users to run local stacks

### 6.4 Startup validation and hints

User-facing operator hints and startup validation guidance must be updated to refer to:

- `deploy/.env.single`
- `deploy/.env.cluster`

Any message that still points users at only `deploy/.env.dev`, `deploy/.env.ha`, or `deploy/.env` becomes inconsistent after the rename.

### 6.5 Breaking-change policy

This is an intentional breaking change.

The repository will not preserve compatibility aliases in:

- CLI accepted topology values
- env example file names
- default env file lookup
- compose filenames
- nginx config filenames
- docs examples

Rationale:

- the user explicitly requested a hard switch
- the rename is still local-operator-facing, not an external API contract
- leaving aliases behind would keep two naming systems alive and dilute the purpose of the cleanup

---

## 7. Impacted files and areas

### 7.1 Required code and config edits

- [deploy/deployment.sh](/home/feng/code/project/community/deploy/deployment.sh)
- all `deploy/compose.*.(dev|ha).yml` files
- [deploy/nginx/nginx.dev.conf](/home/feng/code/project/community/deploy/nginx/nginx.dev.conf)
- [deploy/nginx/nginx.ha.conf](/home/feng/code/project/community/deploy/nginx/nginx.ha.conf)
- [deploy/.env.dev.example](/home/feng/code/project/community/deploy/.env.dev.example)
- [deploy/.env.ha.example](/home/feng/code/project/community/deploy/.env.ha.example)
- [deploy/.env.example](/home/feng/code/project/community/deploy/.env.example)
- any user-facing runtime message that mentions old env paths

### 7.2 Required documentation edits

- [README.md](/home/feng/code/project/community/README.md)
- [backend/README.md](/home/feng/code/project/community/backend/README.md)
- [deploy/README.md](/home/feng/code/project/community/deploy/README.md)
- [docs/DEPLOYMENT.md](/home/feng/code/project/community/docs/DEPLOYMENT.md)
- [docs/OBSERVABILITY.md](/home/feng/code/project/community/docs/OBSERVABILITY.md)
- [docs/LOCAL_HA.md](/home/feng/code/project/community/docs/LOCAL_HA.md)
- [docs/LOAD_TESTING.md](/home/feng/code/project/community/docs/LOAD_TESTING.md)
- [docs/ARCHITECTURE.md](/home/feng/code/project/community/docs/ARCHITECTURE.md)
- [docs/SYSTEM_DESIGN.md](/home/feng/code/project/community/docs/SYSTEM_DESIGN.md)
- [docs/SECURITY.md](/home/feng/code/project/community/docs/SECURITY.md)
- [tools/im-load/README.md](/home/feng/code/project/community/tools/im-load/README.md)
- [deploy/observability/kibana/README.md](/home/feng/code/project/community/deploy/observability/kibana/README.md)

Historical specs and plans are not part of the operator surface and do not need bulk rewrites unless directly referenced by current docs.

---

## 8. Verification strategy

The rename is complete only if all of the following hold:

### 8.1 Operator verification

- `./deploy/deployment.sh --help` shows only `single|cluster`
- `./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example`
- `./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example`
- `./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example`
- `./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example`

### 8.2 Negative verification

- `./deploy/deployment.sh config --topology dev ...` fails with unsupported topology
- `./deploy/deployment.sh config --topology ha ...` fails with unsupported topology

### 8.3 Service-shape verification

Rendered `single` compose should contain representative names such as:

- `mysql`
- `redis`
- `kafka`
- `elasticsearch`
- `nacos`
- `community-app`
- `community-gateway`

Rendered `cluster` compose should contain representative names such as:

- `mysql-primary`
- `redis-1`
- `kafka-1`
- `elasticsearch-1`
- `nacos-1`
- `community-app-1`
- `community-gateway-1`

### 8.4 Application verification

The existing Redis topology regression test remains the main behavior check:

```bash
mvn -f backend/pom.xml -pl community-app -am \
  -Dtest=RedisTopologyProfileTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

This change does not alter Redis behavior directly, but it guards against accidental deployment-config regressions while the operator and docs are being renamed.

---

## 9. Risks and mitigations

### 9.1 Risk: stale references remain

Because this rename spans filenames, CLI values, and docs, stale `dev` / `ha` references are the main failure mode.

Mitigation:

- use repository-wide search for current docs and deploy files
- verify help text and rendered compose outputs after rename
- explicitly run negative tests for old topology values

### 9.2 Risk: shell and docs drift

If `deployment.sh` changes but docs or env examples keep old names, the repository becomes harder to use than before.

Mitigation:

- treat doc updates as part of the required implementation, not optional cleanup
- include doc paths in the same change set

### 9.3 Risk: compose file rename breaks operator assembly

Hard-renaming the topology-specific compose files can break `deployment.sh` if any path is missed.

Mitigation:

- verify all four rendered combinations after rename
- keep shared file names untouched

---

## 10. Final recommendation

Execute a full hard cutover from `dev` / `ha` to `single` / `cluster`.

Specifically:

- rename CLI topology values
- rename topology-specific compose files and nginx configs
- rename env examples and default env lookup
- rename default compose project names
- update all active user-facing docs
- do not keep compatibility aliases

This gives the repository one coherent local deployment vocabulary:

- `single` for the lightweight single-node topology
- `cluster` for the local multi-replica / clustered rehearsal topology

That is the clearest long-term model and matches the user's explicit decision.
