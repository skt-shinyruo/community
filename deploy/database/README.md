# Deployment Database Assets

- `business/001_schema.sql`: canonical empty-volume schema for `community`, `community_oss` and `im_core`.
- `business/init/`: database and least-privilege user bootstrap.
- `business/seed/`: development-only reference identities.
- `mysql/`: MySQL server and client configuration.
- `nacos/`: control-plane database bootstrap assets.

Schema ownership and change rules are documented in `docs/handbook/data-and-storage.md`.
