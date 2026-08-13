# Deployment Database Assets

- `business/current-state/`: empty-volume current schema for `community`, `community_oss` and `im_core`.
- `business/migrations/`: immutable forward migrations for existing `community` volumes.
- `business/init/`: database and least-privilege user bootstrap.
- `business/seed/`: development-only reference identities.
- `mysql/`: MySQL server and client configuration.
- `nacos/` and `xxl-job/`: control-plane database bootstrap assets.

Schema ownership and change rules are documented in `docs/handbook/data-and-storage.md`.
