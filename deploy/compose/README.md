# Compose Fragments

This directory contains reusable Compose fragments. Operators use `deploy/deployment.sh`; the three supported
composition roots live under `deploy/stacks/`.

- `base.yml`: shared network and named-volume declarations.
- `infra/<capability>/{single,cluster}.yml`: topology-specific infrastructure services.
- `runtime/`: containerized backend, edge and development-tool services.
- `overlays/`: optional host-access and observability behavior.

Docker Compose resolves relative build and bind-mount paths from this `deploy/compose/` project-resource base when
the fragments are loaded through a Stack include. Keep paths relative to that base. Overlays appended by
`deployment.sh` use the injected `COMMUNITY_DEPLOY_ROOT`, because their base differs between Stack and legacy topology
composition. Run the compose contract group after moving an asset.
