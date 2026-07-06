status: DONE

implementation_summary:
- Added CommentPageCache as the application-owned comment page cache port.
- Added RedisCommentPageCache with JSON payload storage, short TTL, per-post key index, poison-payload cleanup, and post-level eviction.
- Wired first-page root comment cache read-through behavior into CommentReadApplicationService.
- Preserved post readability validation before root comment cache hits so deleted/hidden posts cannot be served from cache.
- Evicted comment page cache after commit on comment create, update, and delete paths.
- Clamped post counter dirty snapshot flush batches to 1..500 and added the new content.counters.flush-batch-size config key.
- Updated Task 4 checklist state in the implementation plan.

tests:
- Red step: mvn test -pl :community-app -Dtest=CommentReadApplicationServiceTest,CommentApplicationServiceTest,PostCounterApplicationServiceTest,PostReadApplicationServiceTest
  outcome: failed at testCompile because RedisCommentPageCache and service constructor wiring were intentionally absent.
- Final: mvn test -pl :community-app -Dtest=CommentReadApplicationServiceTest,CommentApplicationServiceTest,PostCounterApplicationServiceTest,PostReadApplicationServiceTest,RedisCommentPageCacheTest
  outcome: BUILD SUCCESS; Tests run: 43, Failures: 0, Errors: 0, Skipped: 0.
- Review fix: mvn test -pl :community-app -Dtest=CommentReadApplicationServiceTest,CommentApplicationServiceTest,PostCounterApplicationServiceTest,PostReadApplicationServiceTest,RedisCommentPageCacheTest
  outcome: BUILD SUCCESS; Tests run: 43, Failures: 0, Errors: 0, Skipped: 0.

review:
- Initial review found that first-page comment cache hits bypassed post readability validation.
- Fixed by validating the post before cache lookup and adding a regression test.
- Re-review approved with no Critical, Important, or Minor findings.

concerns:
- None.
