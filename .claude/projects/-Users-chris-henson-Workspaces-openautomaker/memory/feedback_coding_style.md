---
name: Coding style — early return
description: User prefers early return / guard clause pattern over nested conditionals
type: feedback
---

Prefer early returns (guard clauses) over nested if/else.

**Why:** User's stated style preference.

**How to apply:** When writing or refactoring methods, check preconditions up front and return early rather than wrapping the happy path in a conditional block.
