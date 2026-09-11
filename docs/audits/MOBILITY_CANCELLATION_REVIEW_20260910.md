# Cancellation cross-review

Second-agent read-only review verified atomic suppression including two pending publications. Five SQLite tests exercise production SQL. It found:

1. Retryable server rejections could exhaust cancellation after waiting for publication. Patched to preserve CANCEL on both retryable rejection and transport failure regardless of attempt count; retry backoff remains bounded.
2. Resolved expectedVersion 0→N made duplicates appear conflicting. Patched comparison accepts original version-zero cancellation replay only when all other owner/request/type/payload fields match.
3. Version conflict remains a terminal conflict requiring the user to review a fresh projection and cancel again. Automatic rebase with a fresh command identity is not implemented. This remains P1 and prevents complete cancellation-recovery certification.

Physical process-death and remote cancellation/acceptance race tests remain pending.
