# Runtime Configuration Assets

This directory owns configuration mounted into running containers:

- `nacos/`: non-secret Nacos seed templates and their publisher.
- `nginx/`: single and cluster ingress templates.
- `garage/`: single and cluster Garage node configuration.

Credentials and signing keys stay in the Stack env files or a secret manager, never in these templates.
