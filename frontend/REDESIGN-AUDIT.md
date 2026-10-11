# Frontend overhaul audit

## Scope and direction
An entirely new presentation layer for the existing React application. Public Fund retains its supplied logo and product name. Trust-first civic workspace: ink typography, blue actions, cool neutral canvas, restrained motion. Taste skill dials: DESIGN_VARIANCE 4, MOTION_INTENSITY 4, VISUAL_DENSITY 5. Fluent UI remains the sole product component foundation; Phosphor remains the icon family.

## Existing inventory
- Public: login (provider/staff fallback), provider registration, forgotten password, token/password reset, how-it-works guide, access denied and not found.
- Provider: overview and application records, funding application form, named projects with phase proposals/start/completion request, evidence upload with progress and 25 MB limit, project/phase evidence history and download, project payment directory and detail, notifications with read actions.
- Administrator: overview with application decisions/project creation, complete application register, project and phase management with approve/reject, payment creation/release, phase conclusion and project completion confirmations, payment register, audit register.
- Officer: overview, evidence file opening and approve/reject comments, project phase monitoring.
- Shared: role guards, API authorization, search/status filters, skeletons, persistent dark mode, back navigation, display-only breadcrumbs, mobile navigation, single personal name display, provider-only guide navigation.

## Audit findings
The existing Manrope/blue treatment is spread across index.css, page-level CSS and three later override sheets. Geometry and typography have conflicting owners. Repeated metric and action cards dominate over task context. Account pages and recovery pages have separate compositions. Tables and modals have inconsistent spacing. Existing mobile menu lacks a focus trap. Keep the working data/state logic and replace the presentation composition and active stylesheet stack.

## Preservation contract
All route paths, role gates, API methods/URLs/request payloads, form names/order, data calculations, validation and allowed actions remain. No backend changes. No fabricated metrics, official seals or government affiliation claims. Retain supplied logo on public account pages instead of adding decorative stock imagery. SEO baseline is a Vite app with a product title/description, no route metadata or structured data to migrate.

## New foundation
Full-window canvas with a fixed navigation rail, compact utility header and spacious page headings. One radius scale: 20px panels, 12px controls, pill status labels. Task panels use real record counts. Shared account layout, shared page heading, accessible modal wrapper, unified filter toolbar. Semantic status colours are information, not decorative accents. Dark mode uses the same hierarchy. Motion communicates entry, focus, pending work and action feedback; reduced-motion removes movement.
