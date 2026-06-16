# Observability Contracts

This directory contains machine-readable observability contracts used by static tests and implementation reviews.

The human-readable source of truth is `docs/handbook/observability.md`. These files intentionally use one value per line so Bash tests can consume them without adding `yq`, `jq`, or a custom parser.

Rules:

- Blank lines are ignored by tests.
- Lines beginning with `#` are comments.
- New metrics should use families from `metric-families.txt`.
- New metric dimensions should use `allowed-metric-dimensions.txt`.
- Values in `forbidden-observability-fields.txt` must not be used as metric dimensions or log labels.
- Runtime events should use categories from `stable-event-categories.txt`.
