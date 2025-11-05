---
id: task-046
title: Initialize React frontend with Vite and shadcn/ui
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 16:55'
updated_date: '2025-11-05 17:08'
labels:
  - frontend
  - ui
  - sprint-5
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up the frontend infrastructure for the sync agent dashboard using Vite + React 18 + shadcn/ui (Tailwind CSS). This provides the foundation for building the modern SPA that will serve as the operations dashboard.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Vite project initialized with React 18 and TypeScript
- [x] #2 Tailwind CSS configured and working
- [x] #3 shadcn/ui components library installed and configured
- [x] #4 Basic routing setup with React Router
- [x] #5 Development server runs and hot-reload works
- [x] #6 Build process produces optimized static assets
- [x] #7 Static assets can be served from Quarkus backend
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Check Node.js/npm environment availability
2. Create frontend directory structure (frontend/ or ui/)
3. Initialize Vite project with React + TypeScript template
4. Install and configure Tailwind CSS with PostCSS
5. Install and configure shadcn/ui component library
6. Set up React Router v6 for SPA routing
7. Configure Maven/Quarkus to serve static frontend assets
8. Create build pipeline that copies assets to src/main/resources/META-INF/resources
9. Test development server with hot-reload
10. Test production build and verify Quarkus serves static files
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Successfully initialized the React frontend infrastructure for the Keycloak Kafka Sync Agent dashboard using Vite + React 19 + TypeScript + shadcn/ui + Tailwind CSS 3.

## What Was Implemented

### Frontend Setup
- Created `/frontend` directory with Vite project structure
- Configured React 19.1.1 with TypeScript 5.9.3
- Set up Tailwind CSS 3.4.0 with PostCSS and Autoprefixer
- Installed and configured shadcn/ui component library with:
  - CSS variables for theming (light/dark mode support)
  - Path aliases (@/* for imports)
  - Utility function (cn) for class merging
  - Component infrastructure in `src/components/ui`

### Routing
- Installed React Router DOM v6
- Created basic routing structure with:
  - Dashboard page at `/`
  - 404 Not Found page for unmatched routes
  - BrowserRouter configuration

### Build Pipeline
- Added frontend-maven-plugin (v1.15.1) to pom.xml
- Configured automatic Node.js (v22.19.0) and npm (10.9.3) installation
- Set up build phases:
  1. `generate-resources`: Install Node, npm dependencies, build frontend
  2. `process-resources`: Copy dist files to `target/classes/META-INF/resources`
- Frontend assets are now bundled with Quarkus JAR and served at root path

### Files Modified/Created
- `pom.xml`: Added frontend build plugins
- `.gitignore`: Added frontend build artifacts
- `frontend/` directory: Complete Vite + React + Tailwind setup
- `frontend/src/pages/`: Dashboard and NotFound pages
- `frontend/src/lib/`: Utility functions
- `frontend/components.json`: shadcn/ui configuration

## Testing
- ✅ Production build succeeds: `npm run build`
- ✅ Maven build integrates frontend: `./mvnw clean compile`
- ✅ Assets copied to `target/classes/META-INF/resources/`
- ✅ Quarkus will serve static files from classpath

## Next Steps
- Task-047: Implement backend API endpoints for UI data
- Task-048: Implement TanStack Query for data management
- Task-049: Create Dashboard page with actual content
<!-- SECTION:NOTES:END -->
