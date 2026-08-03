# NextBench Android Agent Guide

This file is the operating manual for AI agents and engineers working in this repository. Read it before editing code. It describes the product goal, the current architecture, the backend contract, the expected quality bar, and the workflow required to change the project without breaking cross-platform behavior.

## 1. Project Goal

Build and maintain a production-quality native Android client for NextBench, a verified student marketplace and campus community.

The Android app must:

- Reuse the live NextBench backend and remain interoperable with the website.
- Support the same users, posts, products, stories, chats, clubs, notifications, profiles, and social relationships.
- Feel native and significantly more polished than a literal website port.
- Be smooth enough for repeated daily use: stable scrolling, responsive gestures, optimistic actions, good loading states, and no avoidable layout shifts.
- Prioritize trust, safety, privacy, and predictable behavior for a student audience.
- Stay understandable and maintainable. Do not introduce complexity unless it solves a real problem or follows an established project pattern.

The target experience is the craft and responsiveness of mature social and publishing apps, while retaining NextBench's own visual identity and campus focus.

### Explicit Product Constraint

Do not build an admin panel in the Android app. Administration and moderation dashboards are managed by the website. Android may expose ordinary user-facing report, block, safety, and moderation-result states, but not an admin console.

## 2. Repositories and Sources of Truth

### Android repository

- Local path: `/Users/yashsingh/nextbench`
- Public GitHub repository: `https://github.com/yashclouded/nextbench`
- Main branch: `main`

### Website reference repository

- Local path: `/Users/yashsingh/nextbench-1`
- GitHub: `https://github.com/maryamfatimadesigns-ux/nextbench-1`

The website is not a visual template that Android must copy. It is the primary reference for product behavior, Firestore field names, Cloud Function call shapes, business rules, and security constraints.

### Decision precedence

When sources disagree, use this order:

1. Deployed Firebase behavior and current Firestore security rules.
2. Current website implementation and callable contracts in `nextbench-1`.
3. Existing working Android behavior and repository tests.
4. The approved Android design spec at `docs/specs/2026-07-27-nextbench-android-design.md`.
5. New design judgment consistent with the product goal.

Do not assume the design spec's proposed folder structure is current. The repository evolved after that document was written. The actual structure described below is authoritative.

## 3. Technology Baseline

- Kotlin
- Jetpack Compose and Material 3 with a custom design system
- MVVM with immutable UI state and unidirectional data flow
- Kotlin Coroutines and Flow
- Hilt dependency injection
- Navigation Compose
- Firebase Auth, Firestore, Functions, Storage, Messaging
- Cloudinary unsigned uploads for supported media flows
- Coil for image loading
- Media3 for playback
- DataStore for local preferences where applicable
- Gradle Kotlin DSL and version catalog
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`
- JDK 17

Do not replace these technologies or add a new framework without a concrete need and a repository-wide reason.

## 4. Actual Project Structure

```text
nextbench/
  app/
    src/main/kotlin/com/nextbench/app/
      MainActivity.kt             Android entry point and external intents
      NbAppShell.kt               Persistent app chrome and intent coordination
      navigation/                 Routes, NavHost, bottom navigation
      auth/                       Login, signup, organization signup, route gates
      feed/                       Community feed and stories
      create/                     Post composer and post media preparation
      marketplace/                Search/browse, detail, sell/edit, wishlist
      chat/                       Direct inbox and direct conversations
      clubs/                      Club discovery, chat, joining, settings
      notifications/              In-app notification list
      profile/                    Own profile, public profile, settings
      post/                       Post detail and replies
      search/                     Unified discovery search
      invite/                     Referral and invite flows
      verification/               Student verification flow
      legal/                      Terms and privacy screens
      share/                      Native Android share target
      ui/                         App-specific shared UI such as splash
    src/test/                     JVM state, mapper, route, and contract tests
  core/
    common/                       Shared errors, results, dispatchers, formatters
    designsystem/                 Theme, tokens, icons, motion, base components
  data/
    model/                        Firestore-compatible domain/data models
    firebase/                     Firebase providers, repositories, callables, uploads
  functions-android/              Isolated Android push Functions codebase
  docs/
    SETUP.md                      Local Firebase, Cloudinary, and signing setup
    specs/                        Product and architecture specifications
  firebase.json                  Only the isolated `android-push` codebase
  gradle/libs.versions.toml       Dependency and plugin versions
```

Features currently live as packages inside the `app` module, not separate Gradle feature modules. Keep new work in the existing layout unless a split clearly reduces real build or ownership problems.

## 5. Module Responsibilities

### `app`

Owns Android presentation and platform integration:

- Compose screens and reusable feature UI
- ViewModels and immutable UI state
- Navigation and route guards
- Android intents, permissions, notifications, recording, and file preparation
- User-facing error copy

ViewModels should coordinate work. They should not contain raw Firestore queries or reconstruct backend schemas.

### `core:designsystem`

Owns the visual language:

- `NbTheme` and `NbColors`
- `NbDimens`
- `NbMotion`
- `NbIcons`
- `NbLogo`
- Buttons, fields, cards, bottom sheets, avatars, badges, skeletons, and empty states

Use these tokens and components before adding feature-local styling. Do not hardcode a second design system inside a screen. A feature-local value is acceptable for a genuinely unique fixed format, such as a message-bubble corner.

### `core:common`

Owns small platform-independent helpers. Keep it free of feature UI and Firebase implementation details.

### `data:model`

Owns shared data shapes such as `UserData`, `Post`, `Product`, `Message`, `ChatRoom`, `Club`, and `Story`.

Models must remain compatible with Firestore and the website. Prefer nullable/defaulted fields and defensive mapping when old and new documents coexist.

### `data:firebase`

Owns the backend boundary:

- Firebase providers and `FirestoreRefs`
- Repository reads and writes
- Firestore snapshot mapping
- Callable Cloud Function wrappers in `NbFunctions`
- Cloudinary uploads
- Backend-specific validation and compatibility behavior

Screens must not call Firebase SDKs directly. Add or extend a repository or `NbFunctions` method instead.

### `functions-android`

This is an isolated Firebase Functions codebase for Android push-token registration and direct-message delivery. It does not own the website's main Functions deployment or Firestore rules.

Do not copy the website backend into this directory. Do not deploy or modify Firestore rules from this repository.

## 6. Application Flow

### Entry and shell

`MainActivity` initializes edge-to-edge Compose, theme preference, and incoming Android intents. `NbAppShell` owns the persistent top and bottom chrome and coordinates deep links, share intents, push permissions, and presence. `NbNavHost` owns destinations.

The app begins at Splash, resolves onboarding/session state, then enters the relevant public, authentication, verification, or signed-in route.

### Navigation

Routes are string-backed sealed objects in `navigation/NbRoute.kt`. Use the route builders such as `NbRoute.post(id)` and `NbRoute.messages(id)` so path segments are encoded consistently.

Access policy lives in `auth/AuthGate.kt`:

- Public: feed, marketplace browsing, search, legal pages, authentication.
- Signed in: profile, notifications, clubs, wishlist, public profile details, invites.
- Verified: create/sell, direct messages, chat rooms, edits, and native share-to-chat.

Do not work around a route guard inside a screen.

### Feature data flow

The standard flow is:

```text
Compose event
  -> ViewModel method
  -> repository/callable/media helper
  -> Result or Flow
  -> StateFlow update
  -> Compose renders immutable state
```

For realtime data, repositories expose `Flow`. ViewModels collect inside `viewModelScope`, map failures to stable UI state, and cancel or replace jobs when the active user or route identity changes.

## 7. Firebase and Website Compatibility

The Android app consumes Firebase project `nextbench-a11ed`. It is another client of the same backend, not a separate product database.

Before changing a Firestore read, write, or callable:

1. Inspect the relevant website code.
2. Inspect `/Users/yashsingh/nextbench-1/firestore.rules`.
3. Inspect existing Android repository contract tests.
4. Preserve legacy field aliases when Android already supports them.
5. Add a focused contract test for the exact payload or mapper change.

Useful website references include:

- `src/hooks/useChatEngine.ts`
- `src/components/chat/`
- `src/lib/firebase.ts`
- `src/lib/linkPreview.ts`
- `functions/src/`
- `firestore.rules`

### Important current backend invariants

- Firestore document and field names must match the website exactly.
- Server-owned timestamps use `FieldValue.serverTimestamp()`.
- User-specific room state uses arrays such as `unreadBy`, `mutedBy`, `archivedBy`, `pinnedBy`, and `deletedBy`.
- Do not casually add `updatedAt` to per-user flag mutations. Some rules allow only a narrow field set, and changing sort timestamps for private state can reorder rooms for every participant.
- Direct and club message types are compatible with `text`, `voice`, `video`, and `file`.
- Image messages intentionally omit an explicit `type = "image"` field because the current deployed message validation accepts image content but rejects that explicit type. `explicitAttachmentMessageType` centralizes this compatibility behavior.
- Club member message metadata updates must follow the deployed rule's allowed field set. Do not combine fields merely because a batch update appears convenient.
- Direct optimistic text messages use a stable Android client ID as both the client identifier and Firestore document ID. Retries must be idempotent and remote snapshots reconcile through `clientMessageId`.
- A Firestore message document ID must match the repository's allowed character/length contract. Do not pass arbitrary user text into a document path.
- Link previews use the website's SSRF-hardened `getLinkPreview` callable. Never fetch arbitrary third-party HTML directly from the Android device to build previews.
- Public profile reads and sensitive aggregate stats may be routed through trusted callables because direct Firestore reads can be blocked by rules.

If a write fails with `PERMISSION_DENIED`, first compare it against deployed rules and the website payload. Do not weaken security or add random fields until the mismatch is understood.

## 8. Configuration and Secrets

The following are local-only and must never be committed:

- `.env` and `.env.*`, except `.env.example`
- `app/google-services.json`
- `local.properties`
- Release keystores (`*.jks`, `*.keystore`)
- Signing passwords or aliases
- Firebase Admin credentials
- Cloudinary API secrets
- Any third-party private token

The repository already ignores these files. Verify ignore behavior before every commit that touches configuration.

Client-safe local values may be supplied through ignored `.env`, `local.properties`, or the environment. Supported names include:

- `CLOUDINARY_CLOUD_NAME` or `VITE_CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_UPLOAD_PRESET` or `VITE_CLOUDINARY_UPLOAD_PRESET`
- `GIPHY_API_KEY`
- `GOOGLE_WEB_CLIENT_ID` as an intentional override
- Release signing variables documented in `docs/SETUP.md`

Debug UI compilation works without `google-services.json`, but Firebase-backed behavior deliberately reports that Firebase is not configured. Release packaging fails early when Firebase, Cloudinary, or release signing is missing.

Never paste real secret values into source, tests, docs, commit messages, logs, or issue text.

## 9. Design and Interaction Rules

The Android app should be recognizably NextBench but should not imitate the website layout mechanically.

### Visual system

- Use `NbTheme.colors`, not arbitrary color constants.
- Use `NbDimens` for standard spacing, radii, avatar sizes, and chrome dimensions.
- Use `NbIcons` instead of direct Material icons when the design system provides an equivalent.
- Use `NbLogo` for brand presentation.
- Keep cards at 8dp radius or below unless an existing component or a domain-specific shape requires otherwise.
- Avoid nested cards and excessive floating surfaces.
- Keep operational screens compact, scannable, and focused.
- Support light and dark themes for every new state.

### Motion and performance

- Use `NbMotion` for standard screen and interaction timing.
- Prefer transform/alpha animation over layout-heavy animation.
- Give lists stable keys.
- Avoid doing blocking I/O, bitmap work, media inspection, or Firebase calls on the main thread.
- Keep fixed-format UI dimensions stable so loading states and content do not shift controls.
- Preserve scroll position unless a deliberate action requires navigation.
- Test keyboard and system inset behavior for composers.
- Do not add decorative animation that competes with reading, browsing, or messaging.

### Expected states

Every networked screen should account for:

- Initial loading
- Empty data
- Content
- Recoverable error
- In-progress mutation
- Success or navigation result
- Permission/configuration failure where relevant

Do not leave a button active during a duplicate submission. Do not silently discard user-authored text or selected media after a failed upload.

### Accessibility

- Provide content descriptions for actionable icons.
- Keep touch targets near the platform-standard 44-48dp range.
- Do not encode state using color alone.
- Respect text scaling and avoid fixed heights around variable text.
- Check contrast in light and dark themes.

## 10. Feature Ownership and Behavior Notes

### Feed and stories

- `feed/CommunityScreen.kt` owns the mixed community feed presentation.
- Feed content can include posts and marketplace recommendations.
- Stories live in `feed/StoryUi.kt` and `StoryViewModel.kt` because the story tray is part of the community journey.
- Feed chrome intentionally hides while scrolling down and returns near the top or on reverse navigation behavior. Preserve this space-saving interaction.

### Search

- Search is a main bottom-navigation destination.
- The search screen is useful before a query: recommendations, trending content, books/products, users, posts, and discovery sections belong there as data allows.
- Do not recreate a separate mobile Market tab; marketplace items are discoverable through feed/search and detail routes.

### Chat

- Direct chat is split between `MessagesViewModel`/`MessagesScreen` and `ChatRoomViewModel`/`ChatRoomScreen`.
- Keep message operations server-compatible and idempotent.
- Pending/failed optimistic messages must not expose actions that require a server-backed message.
- Replies, forwarding, selection, reactions, deletes, read receipts, voice, attachments, and link previews share the `Message` contract.
- Tapping a profile identity should open the public profile route.

### Clubs

- Club chat shares message concepts with direct chat but has different membership and role rules.
- Do not assume direct-chat Firestore permissions apply to clubs.
- Only club leads/co-leads may perform role-restricted operations.
- Leadership transfer and richer role management remain areas for continued parity work.

### Profiles

- Own and public profiles have different repositories and permissions.
- Relationship counts and mutual information may require trusted callable data.
- Profile settings belong in the profile journey, not in a separate admin surface.

### Push notifications

- `NbMessagingService` receives Android FCM messages and builds deep-link intents.
- Token synchronization is performed after authentication.
- Changes to push delivery can require both Android changes and the isolated `functions-android` codebase. Test both when applicable.

## 11. Coding Conventions

- Follow the style of the surrounding file.
- Prefer small, cohesive functions and immutable state.
- Keep state derivations as computed properties or pure internal helpers when they are testable without Android runtime dependencies.
- Keep Android-only parsing and media access behind small helpers; extract pure normalization logic for JVM tests.
- Use `Result` at repository boundaries where the project already does so.
- Map backend errors to concise user-facing messages in the ViewModel or feature layer.
- Add comments only for non-obvious compatibility or lifecycle invariants.
- Default to ASCII unless the file already requires Unicode content, such as reaction emoji.
- Do not perform unrelated refactors while implementing a feature.
- Do not introduce an abstraction solely to reduce a few lines of straightforward code.

## 12. Testing and Verification

### Fast targeted checks

Run the smallest relevant test while iterating, for example:

```bash
./gradlew :app:testDebugUnitTest --tests com.nextbench.app.chat.ChatStateTest
./gradlew :data:firebase:testDebugUnitTest --tests com.nextbench.data.firebase.ChatRepositoryContractTest
./gradlew :app:compileDebugKotlin
```

### Required full Android verification

Before committing a completed Android slice, run:

```bash
./gradlew \
  :data:firebase:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  --console=plain
```

On the current development machine, the known JDK 17 path is:

```text
/Users/yashsingh/Library/Java/JavaVirtualMachines/ms-17.0.14/Contents/Home
```

Use it through `JAVA_HOME=...` when the shell default is incompatible.

### Functions verification

When changing `functions-android`:

```bash
npm test --prefix functions-android
```

Only deploy when explicitly requested and after confirming the active Firebase project and codebase.

### Manual verification

Automated tests do not replace device checks for:

- Scrolling and animation smoothness
- Keyboard/inset behavior
- Notifications and deep links
- Camera, microphone, picker, and document permissions
- Voice recording/playback
- Native share intents
- Dark mode
- Offline/reconnect behavior
- TalkBack and large text

If a device or emulator was not available, state that clearly rather than claiming the UI was fully verified.

## 13. Git and Delivery Rules

The user requires atomic commits.

Before editing:

```bash
git status --short
git log -5 --oneline --decorate
```

Assume unrelated dirty files belong to the user or another active slice. Never reset, discard, or overwrite them. Work with existing changes if they intersect your task.

For each completed slice:

1. Keep the diff limited to one coherent behavior.
2. Run targeted tests during implementation.
3. Run full verification before the commit when practical.
4. Run `git diff --check`.
5. Confirm secrets are ignored and absent from the diff.
6. Stage only the files belonging to the slice.
7. Create a descriptive conventional commit.
8. Push `main` immediately after the verified commit.

Examples:

```text
feat(chat): add secure link previews
fix(profile): load relationship stats through callable
test(search): cover mixed discovery ranking
docs: add agent project guide
```

Do not mix documentation, unrelated cleanup, and feature behavior in one commit. Do not amend or rewrite published commits unless explicitly requested.

## 14. Definition of Done

A feature is not done merely because the happy path compiles. A completed slice should satisfy the applicable items below:

- Behavior matches the live website/backend contract.
- UI follows the existing design system and route structure.
- Loading, empty, error, disabled, and retry states are handled.
- User input survives recoverable failures.
- Authentication and verification rules are enforced.
- Realtime listeners and Android resources are cleaned up with lifecycle changes.
- Focused tests cover pure logic and backend payload/mapping changes.
- Full unit tests, lint, and debug assembly pass.
- No secrets or generated local files are staged.
- The change is committed atomically and pushed.
- Any unverified device-specific behavior is reported honestly.

## 15. Current Product Direction

The project already contains substantial working vertical slices for authentication, verification, feed, stories, posts, marketplace, direct chat, clubs, notifications, search, invites, own/public profiles, and profile settings.

High-value remaining work includes:

- Complete and device-test native share-target behavior.
- Add recent searches and richer club results to unified discovery.
- Finish club leadership transfer and role management.
- Improve accessibility through TalkBack, large-text, and contrast testing.
- Add Baseline Profiles and run device performance profiling.
- Validate push delivery across foreground/background/killed states.
- Complete release signing and Play-ready release validation.
- Continue UI refinement only where it improves clarity, speed, or perceived quality.

This list is directional, not permission to skip repository inspection. Always check current code and recent commits before assuming a gap still exists.

## 16. Common Failure Modes to Avoid

- Copying website UI instead of adapting behavior to native Android.
- Inventing a new Firestore schema from Kotlin models.
- Treating `PERMISSION_DENIED` as a reason to weaken or bypass rules.
- Committing `.env`, `google-services.json`, `local.properties`, or signing material.
- Adding mobile admin screens.
- Calling Firebase directly from Composables.
- Starting a second design-token system in a feature package.
- Losing drafts or temporary files on a retryable error.
- Triggering duplicate sends from enabled controls or non-idempotent retries.
- Changing shared room timestamps during private per-user actions.
- Fetching link-preview HTML directly on device.
- Replacing stable list keys with indices.
- Marking work complete without lint/assembly or without disclosing skipped device tests.
- Resetting a dirty worktree or reverting changes that were not yours.

## 17. First Steps for Any New Agent

1. Read this file, `README.md`, `docs/SETUP.md`, and the relevant feature code.
2. Check `git status` and recent commits.
3. Locate the equivalent website behavior and Firestore rule before changing backend interactions.
4. Identify the smallest coherent vertical slice.
5. State what you are changing before editing.
6. Implement through existing ViewModel/repository/design-system patterns.
7. Add tests proportional to risk.
8. Verify, inspect the diff, commit atomically, and push.

The standard is production discipline without unnecessary architecture. Preserve backend compatibility, keep the UI native and polished, and leave the repository easier to understand than you found it.
