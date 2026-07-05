# Task 3 Report

## Status

DONE

## Scope

Implemented the Task 3 command non-null hardening only for the owned content and social write-path application services:

- `CommentApplicationService`
- `PostPublishingApplicationService`
- `PostMediaApplicationService`
- `ModerationApplicationService`
- `BlockApplicationService`
- `FollowApplicationService`
- `LikeApplicationService`

Updated the matching owned tests listed in the brief.

## Contract Changes

Added `Objects.requireNonNull(command, "command must not be null")` at the public same-domain application entry boundary for:

- `CommentApplicationService.create(String, CreateCommentCommand)`
- `CommentApplicationService.update(UpdateCommentCommand)`
- `PostPublishingApplicationService.create(String, CreatePostCommand)`
- `PostMediaApplicationService.prepareUpload(PreparePostMediaUploadCommand)`
- `ModerationApplicationService.takeAction(TakeModerationActionCommand)`
- `BlockApplicationService.block(BlockCommand)`
- `BlockApplicationService.unblock(UnblockCommand)`
- `FollowApplicationService.follow(FollowCommand)`
- `FollowApplicationService.unfollow(UnfollowCommand)`
- `LikeApplicationService.setLike(SetLikeCommand)`

Field-level and business validation remained unchanged apart from removing redundant command-null tolerance.

## Helper Simplification

Removed redundant helper-level null branches from:

- `CommentApplicationService.createFromCommand(...)`
- `CommentApplicationService.updateFromCommand(...)`

No helper null-tolerance logic was added anywhere else.

## TDD Evidence

### Red

Ran the focused command from the brief after adding the new assertions:

```bash
cd backend
mvn test -pl :community-app -Dtest=CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostMediaApplicationServiceTest,ModerationApplicationServiceTest,BlockApplicationServiceTest,FollowApplicationServiceTest,LikeApplicationServiceTest
```

Result: failed as expected on the new null-contract assertions.

Observed failure modes before the fix:

- `CommentApplicationService` and `PostPublishingApplicationService` threw `IllegalArgumentException`
- `PostMediaApplicationService` folded null command into existing `BusinessException`
- `ModerationApplicationService`, `FollowApplicationService`, and `LikeApplicationService` threw JVM null dereference `NullPointerException` messages instead of the required message

### Green

Ran the same focused command after implementation:

```bash
cd backend
mvn test -pl :community-app -Dtest=CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostMediaApplicationServiceTest,ModerationApplicationServiceTest,BlockApplicationServiceTest,FollowApplicationServiceTest,LikeApplicationServiceTest
```

Result: `BUILD SUCCESS`, 81 tests run, 0 failures, 0 errors.

## Test Coverage Added

Added explicit null-command boundary tests for:

- comment create/update
- post create
- post media prepare upload
- moderation take action
- block/unblock
- follow/unfollow
- like set

## Self-Review

Reviewed the owned diff after the green run.

- Scope stayed within the owned services, owned tests, and required report file.
- Business validation and existing orchestration order were left unchanged.
- Public entry points now fail fast with the exact required message.
- Helper methods only rely on prior public entry validation where the brief required that cleanup.

## Concerns

None.
