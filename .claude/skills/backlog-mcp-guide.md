---
name: backlog-mcp-guide
description: Guide for using the Backlog.md MCP server for task management. Use this skill when you need to interact with project tasks using MCP tools instead of bash commands. Provides comprehensive examples and best practices for task creation, editing, viewing, and management through the MCP protocol.
color: purple
---

# Backlog.md MCP Server Guide

This skill provides comprehensive guidance on using the Backlog.md MCP (Model Context Protocol) server for task management.

## Why Use MCP Tools?

**✅ Advantages of MCP over bash CLI:**
- **Type-safe**: Structured JSON parameters with validation
- **Reliable**: Direct API calls, no shell escaping issues
- **Consistent**: Predictable response formats
- **AI-friendly**: Data returned in easily parseable structures
- **Multi-line friendly**: No need for shell quoting tricks (`$'...'`)

**⚠️ When to use bash CLI:**
- MCP server is unavailable (rare)
- Need features not exposed by MCP (very rare)

## Available MCP Tools

### Core Task Operations

| MCP Tool | Purpose | Bash Equivalent |
|----------|---------|-----------------|
| `mcp__backlog__task_create` | Create new tasks | `backlog task create` |
| `mcp__backlog__task_view` | View task details | `backlog task <id> --plain` |
| `mcp__backlog__task_list` | List tasks with filters | `backlog task list --plain` |
| `mcp__backlog__task_search` | Search tasks by content | `backlog search --plain` |
| `mcp__backlog__task_edit` | Edit task metadata/ACs/plan/notes | `backlog task edit` |
| `mcp__backlog__task_archive` | Archive a task | `backlog task archive` |

### Guidance Resources

| MCP Tool | Purpose |
|----------|---------|
| `mcp__backlog__get_workflow_overview` | Get workflow guidance |
| `mcp__backlog__get_task_creation_guide` | Get task creation best practices |
| `mcp__backlog__get_task_execution_guide` | Get implementation guidance |
| `mcp__backlog__get_task_completion_guide` | Get completion checklist |

### Document Operations

| MCP Tool | Purpose |
|----------|---------|
| `mcp__backlog__document_create` | Create documentation |
| `mcp__backlog__document_view` | View documentation |
| `mcp__backlog__document_list` | List all documents |
| `mcp__backlog__document_search` | Search documents |
| `mcp__backlog__document_update` | Update documentation |

---

## Common Operations

### 1. Creating a Task

**Basic task creation:**
```typescript
mcp__backlog__task_create({
  title: "Add user authentication system",
  description: "Implement a secure authentication system to allow users to register and login",
  acceptanceCriteria: [
    "Users can register with email and password",
    "Users can login with valid credentials",
    "Invalid login attempts show appropriate error messages"
  ],
  labels: ["authentication", "backend"],
  priority: "high"
})
```

**With all options:**
```typescript
mcp__backlog__task_create({
  title: "Add OAuth integration",
  description: "Integrate OAuth 2.0 for third-party authentication",
  acceptanceCriteria: [
    "Users can login with Google",
    "Users can login with GitHub",
    "OAuth tokens are securely stored"
  ],
  status: "To Do",
  assignee: ["@developer"],
  labels: ["auth", "oauth", "backend"],
  priority: "high",
  dependencies: ["task-5", "task-7"],
  parentTaskId: "task-3"
})
```

### 2. Viewing Tasks

**View single task:**
```typescript
mcp__backlog__task_view({ id: "task-7" })
```

**List all tasks:**
```typescript
mcp__backlog__task_list()
```

**List with filters:**
```typescript
mcp__backlog__task_list({
  status: "In Progress",
  assignee: "@developer",
  labels: ["backend"],
  limit: 20
})
```

**Search tasks:**
```typescript
mcp__backlog__task_search({
  query: "authentication",
  status: "To Do",
  priority: "high",
  limit: 10
})
```

### 3. Claiming a Task

When starting work on a task:
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  status: "In Progress",
  assignee: ["@sprint-developer"]
})
```

### 4. Managing Acceptance Criteria

**Add new ACs:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaAdd: ["New criterion", "Another criterion"]
})
```

**Check ACs (mark as complete):**
```typescript
// Mark single AC
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [1]
})

// Mark multiple ACs at once
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [1, 2, 3]
})
```

**Uncheck ACs:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaUncheck: [2, 3]
})
```

**Remove ACs:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaRemove: [4]
})
```

**Replace all ACs:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaSet: ["First AC", "Second AC", "Third AC"]
})
```

### 5. Implementation Plan

**Set plan (replaces existing):**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  planSet: "1. Research existing patterns\n2. Implement core functionality\n3. Add tests\n4. Validate against ACs"
})
```

**Append to plan:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  planAppend: ["5. Additional step", "6. Final verification"]
})
```

**Clear plan:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  planClear: true
})
```

### 6. Implementation Notes

**Set notes (replaces existing):**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  notesSet: "## Summary\nImplemented X using Y pattern\n\n## Changes\n- Added files A, B, C\n- Modified file D\n\n## Testing\n- Unit tests pass\n- Integration tests pass"
})
```

**Append to notes (progressive updates):**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  notesAppend: ["- Implemented core feature", "- Added validation layer"]
})
```

**Clear notes:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  notesClear: true
})
```

### 7. Updating Task Metadata

**Change status:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  status: "Done"
})
```

**Update assignee:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  assignee: ["@developer", "@reviewer"]
})
```

**Update labels:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  labels: ["backend", "api", "authentication"]
})
```

**Update priority:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  priority: "high"
})
```

**Update dependencies:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  dependencies: ["task-1", "task-2", "task-5"]
})
```

**Combine multiple updates:**
```typescript
mcp__backlog__task_edit({
  id: "task-7",
  status: "In Progress",
  assignee: ["@developer"],
  priority: "high",
  labels: ["urgent", "backend"]
})
```

### 8. Archiving Tasks

```typescript
mcp__backlog__task_archive({ id: "task-7" })
```

---

## Complete Workflow Example

Here's a complete task implementation workflow using only MCP tools:

```typescript
// 1. View available tasks
mcp__backlog__task_list({
  status: "To Do",
  limit: 10
})

// 2. View task details
mcp__backlog__task_view({ id: "task-7" })

// 3. Claim the task
mcp__backlog__task_edit({
  id: "task-7",
  status: "In Progress",
  assignee: ["@sprint-developer"]
})

// 4. Add implementation plan
mcp__backlog__task_edit({
  id: "task-7",
  planSet: "1. Research existing patterns\n2. Implement core functionality\n3. Add comprehensive tests\n4. Validate against all ACs\n5. Update documentation"
})

// 5. Work on the task (write code, tests, etc.)

// 6. Mark ACs as complete (progressively)
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [1]
})

mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [2]
})

// Or all at once:
// mcp__backlog__task_edit({
//   id: "task-7",
//   acceptanceCriteriaCheck: [1, 2, 3]
// })

// 7. Add implementation notes
mcp__backlog__task_edit({
  id: "task-7",
  notesSet: "## Summary\nImplemented authentication system using JWT tokens\n\n## Changes\n- Added: AuthService.java\n- Added: AuthResource.java\n- Modified: User.java\n\n## Testing\n- Unit tests: 15 new tests, all passing\n- Integration tests: AuthResourceTest added\n\n## Notes\n- Used HS256 algorithm for JWT\n- Token expiry set to 24 hours"
})

// 8. Mark task as done
mcp__backlog__task_edit({
  id: "task-7",
  status: "Done"
})
```

---

## Best Practices

### 1. Combine Operations When Possible

Instead of multiple calls:
```typescript
// ❌ Less efficient
mcp__backlog__task_edit({ id: "task-7", status: "In Progress" })
mcp__backlog__task_edit({ id: "task-7", assignee: ["@dev"] })
mcp__backlog__task_edit({ id: "task-7", priority: "high" })
```

Combine them:
```typescript
// ✅ More efficient
mcp__backlog__task_edit({
  id: "task-7",
  status: "In Progress",
  assignee: ["@dev"],
  priority: "high"
})
```

### 2. Use Arrays for Multiple Values

```typescript
// ✅ Correct
acceptanceCriteriaCheck: [1, 2, 3]
labels: ["backend", "api", "auth"]
assignee: ["@dev1", "@dev2"]

// ❌ Wrong
acceptanceCriteriaCheck: "1,2,3"  // String, not array
```

### 3. Multi-line Content

MCP tools handle newlines naturally:
```typescript
// ✅ Correct - just use \n in strings
planSet: "1. First step\n2. Second step\n3. Third step"

// No need for shell quoting tricks like $'...'
```

### 4. Incremental Updates

For notes and plan, prefer append for incremental updates:
```typescript
// ✅ Progressive documentation
mcp__backlog__task_edit({
  id: "task-7",
  notesAppend: ["- Completed user registration"]
})

// Later...
mcp__backlog__task_edit({
  id: "task-7",
  notesAppend: ["- Added email verification"]
})
```

### 5. Mark ACs Progressively

Don't wait until the end:
```typescript
// ✅ Mark ACs as you complete them
// After implementing registration
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [1]
})

// After implementing login
mcp__backlog__task_edit({
  id: "task-7",
  acceptanceCriteriaCheck: [2]
})
```

---

## Error Handling

MCP tools provide structured error responses:

```typescript
// If task doesn't exist:
// Error: Task not found: task-999

// If invalid status:
// Error: Invalid status. Must be one of: To Do, In Progress, Done

// If AC index out of range:
// Error: AC index 5 does not exist. Task has 3 ACs.
```

---

## Comparison: MCP vs Bash CLI

| Operation | MCP Tool | Bash CLI |
|-----------|----------|----------|
| Create task | `mcp__backlog__task_create({title: "...", description: "..."})` | `backlog task create "..." -d "..."` |
| View task | `mcp__backlog__task_view({id: "task-7"})` | `backlog task 7 --plain` |
| Edit task | `mcp__backlog__task_edit({id: "task-7", status: "Done"})` | `backlog task edit 7 -s "Done"` |
| Check AC | `mcp__backlog__task_edit({id: "task-7", acceptanceCriteriaCheck: [1]})` | `backlog task edit 7 --check-ac 1` |
| Add notes | `mcp__backlog__task_edit({id: "task-7", notesSet: "...newlines..."})` | `backlog task edit 7 --notes $'...\\n...'` |
| Search | `mcp__backlog__task_search({query: "auth"})` | `backlog search "auth" --plain` |

**MCP is cleaner, more reliable, and easier to use in AI agents!**

---

## Summary

- **Always prefer MCP tools** over bash commands when available
- **Combine operations** in single `task_edit` calls for efficiency
- **Use arrays** for multiple values (ACs, labels, assignee)
- **Mark ACs progressively** as you complete them
- **Append notes** for incremental documentation
- **MCP handles multi-line content** naturally with `\n`

The MCP server provides a robust, type-safe, and AI-friendly interface to Backlog.md that eliminates the complexities of shell command construction.
