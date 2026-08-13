# Deployment Images

- `backend/`: shared Spring Boot service image definition.
- `frontend/`: production frontend image definition, Nginx runtime configuration and entrypoint scripts.
- `garage-init/`: Garage bootstrap image definition.

Build contexts are declared by the Compose runtime and infrastructure fragments; keep Dockerfile `COPY` paths aligned
with those contexts.
