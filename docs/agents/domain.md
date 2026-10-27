# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

**Layout: single-context.** One `GLOSSARY.md` and one `docs/adr/` at the repo root, shared by the whole codebase. `agent/`, `agent-runtime/`, `knowledge/` and the other Maven modules are build units, not separate domain contexts, so they do not get their own glossary or `docs/adr/`.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root — the domain vocabulary. It does not exist yet: `/domain-modeling` creates it lazily, with the first term that actually gets resolved.
- **`docs/adr/`** — architecture decision records for the whole repo. Read the ADRs that touch the area you're about to work in. The directory is created with the first ADR.
- **`docs/decisions/`** — the pre-existing engineering decision ledger (feature/defect trade-offs with evidence and verification status). It is a separate, older convention, not an ADR store: when you touch an area covered there, read the relevant record as well.

Do not pre-create `GLOSSARY.md`, `docs/adr/`, or any per-module glossary. If a file doesn't exist, **proceed silently**: don't flag its absence, and don't suggest creating it upfront.

**Legacy names:** the current skills only recognise `GLOSSARY.md` and `GLOSSARY-MAP.md`. If an old `CONTEXT.md` or `CONTEXT-MAP.md` shows up, treat it as the pre-rename name, rename it to the new one, and fix the references — never read both.

## File structure

Single-context repo (this one):

```
/
├── GLOSSARY.md
├── docs/
│   ├── adr/            ← created with the first ADR
│   │   ├── 0001-....md
│   │   └── 0002-....md
│   ├── decisions/      ← existing engineering decision ledger
│   └── agents/         ← these consumer rules
├── agent/
├── agent-runtime/
└── knowledge/
```

No `GLOSSARY-MAP.md` and no `src/<context>/docs/adr/`: those belong to multi-context repos.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_
