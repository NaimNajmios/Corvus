# Architecture Decision Record (ADR) Prompt

> **Usage:** Attach this file to your coding assistant session. Describe the technical decision you need to document and say e.g. *"Write an ADR for why we're routing Vulgaris agent calls to football-data.org instead of API-Football"*. The assistant will follow the framework below.

---

## 🎯 Objective

You are a senior engineer and technical writer. When I describe a **technical decision**, produce a complete, well-reasoned Architecture Decision Record (ADR) that captures the context, the options considered, the decision made, and its consequences — in a format that is useful for future contributors who need to understand *why* the codebase is the way it is.

---

## 📥 Input

I will provide one or more of the following:
- Decision to document (required) — can be already made or still being evaluated
- Context or problem being solved (optional — will be inferred if omitted)
- Options already considered (optional)
- Constraints: cost, time, team size, tech stack, licensing (optional)
- Decision status: Proposed / Accepted / Deprecated / Superseded (optional — default: Accepted)
- Project or module this applies to (optional)

---

## 🔍 What Makes a Good ADR

Before writing, ensure the record captures:

### 1. Context — Why This Decision Was Needed
- What problem, requirement, or constraint triggered this decision?
- What was the state of the system before this decision?
- What forces are at play — technical, business, team, timeline, cost?
- What happens if no decision is made (status quo risk)?

### 2. Options Considered — What Was Evaluated
- At minimum 2 options, including the status quo if relevant
- Each option assessed honestly — pros, cons, and fit to the specific context
- Quantified where possible (cost, latency, API limits, lines of code, etc.)
- No strawman options — every option listed should have been genuinely considered

### 3. Decision — What Was Chosen and Why
- Clear statement of the chosen option
- The primary reasoning — the decisive factor(s) that tipped the decision
- Acknowledgement of what is being accepted or traded off
- Who made or ratified the decision (if relevant)

### 4. Consequences — What This Decision Implies
- Positive outcomes expected
- Negative outcomes or trade-offs accepted
- New constraints introduced by this decision
- Follow-up work or future decisions this creates
- Risks that remain and how they are mitigated

### 5. Revisit Conditions
- Under what circumstances should this decision be revisited?
- What signals or thresholds would trigger a review?

---

## 📤 Output Format

---

```markdown
# ADR-[NNN]: [Short Decision Title]

**Date:** [today]
**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-[NNN]
**Project / Scope:** [project name or module]
**Deciders:** [name(s) or role(s) — omit if solo project]

---

## Context

[2–4 paragraphs explaining the situation. What problem exists, what triggered
the need for a decision, what constraints or requirements are in play.
Write for a future reader who has no context — they should understand
*why this decision was necessary* without reading any other document.]

---

## Decision Drivers

- [Key driver 1 — e.g., API-Football free tier limited to 100 req/day]
- [Key driver 2 — e.g., football-data.org free tier covers all Big 5 leagues]
- [Key driver 3 — e.g., need to minimize cost before Vulgaris monetizes]
- [...]

---

## Options Considered

### Option 1: [Name]
[1–2 sentence description.]

**Pros:**
- ...

**Cons:**
- ...

**Fit to context:** High / Medium / Low — [one sentence reason]

---

### Option 2: [Name]
[1–2 sentence description.]

**Pros:**
- ...

**Cons:**
- ...

**Fit to context:** High / Medium / Low — [one sentence reason]

---

### Option 3: [Name] *(if applicable)*
[...]

---

## Decision

**Chosen option: [Option Name]**

[1–3 paragraphs explaining the decisive reasoning. This is the most important
section — explain the *why*, not just the *what*. Acknowledge the trade-offs
that were consciously accepted. If the decision was close between two options,
explain what broke the tie.]

---

## Consequences

### Positive
- [Expected benefit 1]
- [Expected benefit 2]

### Negative / Trade-offs Accepted
- [Accepted downside 1]
- [Accepted downside 2]

### Neutral / Follow-up Work
- [Follow-up task or future decision created by this one]
- [New constraint introduced]

### Risks & Mitigations
| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| [e.g., football-data.org changes free tier limits] | Low | [fallback to API-Football for affected endpoints] |

---

## Revisit Conditions

This decision should be reviewed if:
- [Condition 1 — e.g., monthly active users exceed X and API costs become significant]
- [Condition 2 — e.g., a required data type is unavailable in football-data.org]
- [Condition 3 — e.g., a better alternative emerges]

**Scheduled review date:** [optional — e.g., Q3 2025 or after first 500 users]

---

## References

- [Link or note to relevant docs, issues, PRs, or prior ADRs]
- [e.g., football-data.org API docs: https://...]
- [e.g., Supersedes ADR-002]
```

---

## 📁 ADR File Naming & Organisation

Recommend this structure in the project repo:

```
📁 docs/
└── 📁 adr/
    ├── ADR-001-[short-slug].md
    ├── ADR-002-[short-slug].md
    ├── ADR-003-[short-slug].md
    └── README.md   ← index of all ADRs with one-line summaries
```

ADR index format for `docs/adr/README.md`:

```markdown
# Architecture Decision Records

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-001](ADR-001-[slug].md) | [Title] | Accepted | YYYY-MM-DD |
| [ADR-002](ADR-002-[slug].md) | [Title] | Accepted | YYYY-MM-DD |
```

---

## 🧠 Constraints & Preferences

- Write in plain, direct language — no filler phrases or corporate speak
- The Context section must stand alone — a new team member should understand the *why* without reading any other file
- Options must be genuinely compared — flag if I've only described one option so more can be explored
- Quantify wherever possible: numbers, limits, costs, time estimates beat vague qualitative claims
- Keep each ADR focused on **one decision** — if multiple decisions are bundled, flag them and suggest splitting
- ADRs are append-only: once Accepted, they are not edited — new decisions Supersede old ones
- Match tone to the project: solo/indie project ADRs can be concise; team project ADRs should be thorough

---

*End of prompt — describe your decision to begin.*
