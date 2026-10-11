# Public Fund frontend design

## Direction
A civic funding workspace for service providers, administrators and verification officers. The installed taste skill guided the audit, visual direction and public-page composition. Its dashboard guidance selects the installed official Fluent UI foundation. Design variance 4, motion intensity 4, visual density 5.

Blue actions, ink typography, cool neutral surfaces, self-hosted Manrope and Phosphor icons. The original supplied logo is retained; the served WebP is a smaller encoding of the original asset. No invented metrics, official endorsement claims or decorative status indicators.

## Active styling
Only src/styles/foundation.css, workspace.css and public.css are imported. Older page styles and override sheets remain on disk but are not loaded. This removes their conflicting geometry and colour rules.

- Full-window app canvas; fixed navigation rail, utility header and responsive drawer.
- Panels 20px, nested panels 16px, fields/buttons 12px, status labels pill-shaped.
- Overview metrics link to real workflows. The status distribution has a text legend and count/share toggle. The officer overview shows actual recent submissions.
- Account pages share AuthLayout; public theme switching is available. The guide pairs responsibilities with the actual phase lifecycle and FAQ.
- Tables remain semantic and can be horizontally scrolled by keyboard on narrow screens.
- Native modal dialogs contain focus, make background content inert and support Escape. Mobile navigation contains focus and restores page scrolling on dismissal/resizing.
- Persistent dark mode, skeletons, upload progress, confirmation/error states and reduced-motion support.
- Workspace pages load on demand to reduce the account-page download. Lazy loading also respects the saved theme.

## Preserved contract
All route paths, role restrictions, API methods/URLs/payloads, authentication integration, credentials, budgets, validations and workflow transitions remain. Provider payment navigation is project-specific; evidence navigation remains project > phase > submissions. The centered square upload, less-than-25-MB limit, progress and confirmation remain. Full name appears once in the utility header. Staff navigation has no guide link. Breadcrumbs are display-only and Back navigates.

See REDESIGN-AUDIT.md for the page/workflow inventory.

## Verification
- npm run build
- npm run test:smoke (22 routes, role restrictions, theme persistence, filters, shared components; waits for lazy modules)
- npm run test:layout (shell and reveal behavior)
- node scripts/project-flows-test.mjs (project/payment isolation and file validation)
- npm run lint (13 pre-existing effect/export warnings; no errors)
- scripts/visual-redesign-test.mjs captures desktop/mobile and dark-mode pages, payment navigation and upload feedback using mocked API data.
- scripts/workflow-redesign-test.mjs checks all routes including fallback pages across 390/1024/1440px, light/dark, plus modal focus, approval/rejection/proposal/application payloads and empty/error/loading states. No live backend writes.

Browser tests use an external Playwright installation via PLAYWRIGHT_MODULE and optional VISUAL_BASE_URL. WORKFLOW_ONLY=1 runs just the interactive checks; ROUTE_WIDTHS=1440 narrows the viewport sweep. Screenshots and Lighthouse results are in artifacts/redesign. Lighthouse's Windows launcher can report a temporary-directory cleanup error after successfully saving its audit; inspect the report timestamp and scores rather than treating that exit code as an audit result.

The saved login audit after optimisation reports performance 86, accessibility 100, LCP 2.3s and CLS 0 under Lighthouse's simulated mobile conditions. The original supplied PNG remains unchanged. Its served WebP is 30 KB rather than 255 KB. The initial JavaScript chunk is 404 KB rather than 597 KB, with workspace pages loaded separately. Backend source files, the API client, authentication context and role guard were verified unchanged by SHA-256 comparison.
