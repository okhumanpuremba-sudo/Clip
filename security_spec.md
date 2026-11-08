# ClipForge AI Security Specification

## 1. Data Invariants
- All user data is strictly isolated under `/users/{userId}/...` where `request.auth != null && request.auth.uid == userId && incoming().userId == request.auth.uid`.
- Every collection (`projects`, `campaigns`, `clips`, `api_logs`) validates document IDs via `isValidId(id)` on single-document operations (`get`, `create`, `update`, `delete`) and enforces `isValid[Entity](incoming())` on `create` and `update`.
- Every `allow list` rule enforces `isSignedIn() && request.auth.uid == userId && resource.data.userId == request.auth.uid`.
- `createdAt` and `userId` are immutable on `update` (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
- `api_logs` are append-only (`allow update: if false;`) to preserve telemetry audit integrity.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read/Write**: Caller with `request.auth == null` reading `/users/alice/projects`.
2. **Cross-User Tenant Read**: Authenticated `bob` reading `/users/alice/projects/proj_1`.
3. **Identity Spoofing on Create**: Authenticated `alice` writing `userId: "bob"` inside `/users/alice/projects/proj_1`.
4. **Shadow/Ghost Field Injection on Create**: Payload containing `isAdmin: true` or `verified: true` not in `hasOnly(...)`.
5. **Shadow/Ghost Field Injection on Update**: Update modifying an unlisted field via `affectedKeys().hasOnly(...)`.
6. **Immutable Field Mutation**: Update attempting to overwrite `createdAt` or `userId`.
7. **Future Timestamp Spoofing**: Payload with `createdAt > request.time`.
8. **String Resource Exhaustion (Denial of Wallet)**: Injecting a 50KB string into `title` or `transcriptText`.
9. **Path ID Poisoning**: Document ID exceeding 128 chars or containing invalid special characters.
10. **Enum Poisoning**: Setting `reviewStatus` to `"HACKED"` instead of `['PROPOSED', 'APPROVED', 'REJECTED', 'EXPORTED']`.
11. **Invalid Clip Duration Bounds**: Setting `endSec <= startSec` or negative timestamps on a `ClipCandidate`.
12. **Audit Log Tampering**: Attempting to `update` an existing `api_logs` document.
