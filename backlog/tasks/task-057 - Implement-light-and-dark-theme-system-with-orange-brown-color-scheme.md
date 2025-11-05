---
id: task-057
title: Implement light and dark theme system with orange/brown color scheme
status: In Progress
assignee:
  - '@assistant'
created_date: '2025-11-05 21:06'
updated_date: '2025-11-05 21:07'
labels:
  - frontend
  - ui
  - theme
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add support for light and dark themes with a toggle. Dark theme should use orange colors for fonts and UI details, and dark brown colors for backgrounds and components.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dark theme uses orange (#f97316 or similar) for text and UI accents
- [x] #2 Dark theme uses dark brown (#3e2723 or similar) for backgrounds and components
- [x] #3 Theme toggle component is implemented and accessible
- [x] #4 Theme preference is persisted to localStorage
- [x] #5 Theme changes apply smoothly across all pages and components
- [x] #6 System preference (prefers-color-scheme) is respected on first load
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Update index.css with orange/dark brown color scheme for dark theme
2. Create a ThemeContext and ThemeProvider to manage theme state
3. Add localStorage persistence and system preference detection
4. Create a ThemeToggle component (sun/moon icon button)
5. Integrate ThemeProvider in App.tsx
6. Add ThemeToggle to Layout component navigation bar
7. Test theme switching across all pages
<!-- SECTION:PLAN:END -->
