## Summary

- 请简要描述本次变更。

## DDD Tactical Layering Check

- [ ] Controller / Listener / Job only call same-domain `*ApplicationService`
- [ ] `ApplicationService` owns use-case orchestration, transactions, idempotency, and cross-domain collaboration
- [ ] Domain code does not depend on `application`, `infrastructure`, `controller`, HTTP DTOs, mapper/dataobject types, or `api.*`
- [ ] Mapper, Redis, MQ, Spring event, outbox, and dataobject code stays in `infrastructure`
- [ ] No new `UseCase`, raw `Service`, `FacadeService`, `CommandService`, or `ActionService` application entry style
- [ ] Cross-domain synchronous collaboration uses foreign owner-domain `api.query` / `api.action` / `api.model`
- [ ] Cross-domain asynchronous collaboration uses `contracts.event`
- [ ] Architecture docs and ArchUnit tests were updated when architecture rules changed

## Verification

- [ ] `mvn -pl community-app -am -Dtest=DddLayeringArchTest,DomainBoundaryArchTest,InfraBoundaryArchTest,ControllerBoundaryArchTest,DtoBoundaryArchTest,ListenerBoundaryArchTest test`
- [ ] `mvn -pl community-app -am test`
