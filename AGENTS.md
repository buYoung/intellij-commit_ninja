# AGENTS.md

## 3. Core Behaviors & Patterns

- **Progressive disclosure**: `SKILL.md` files define activation, scope, workflow, output contract, and routing. Large domain rules move into package-local `references/`, and entry points tell agents which reference to read at each decision point instead of loading every file by default.
- **Frontmatter-driven discovery**: Skill activation is controlled by YAML `name` and especially `description`. Descriptions carry trigger phrases, exclusions, explicit-invocation boundaries, and routing to sibling variants such as `task-brief-creator-caveman`.
- **Capability-local evidence**: A skill's templates, validators, examples, evals, licenses, and update notes stay under the same `skills/<skill-name>/` package. Cross-skill repository material belongs only in root docs or release scripts; do not create shared evidence areas for a single package's behavior.
- **Workflow gates as contracts**: Complex skills encode numbered stages, halt conditions, role splits, decision tables, output schemas, and termination metadata. Preserve these transitions in `task-brief-creator`, `feature-design-doc`, `iterative-self-review`, `delegated-review-loop`, `orchestration`, and `linear-issue-*` because downstream agents rely on them for when to ask, stop, validate, or hand off.
- **Structural validation only**: Python validators check filenames, headings, section order, checklist shape, frontmatter, references, and duplicate/empty sections with explicit exit codes. They intentionally avoid judging content quality; semantic judgment remains in the skill workflow, reviewer, or user decision step.
- **Release metadata chain**: Release state flows through `package.json`, `.release-it.json`, `scripts/release/*`, changelog files, `.github/workflows/release.yml`, `.claude-plugin/marketplace.json`, and README tables. A version bump or bundle change is unsafe unless each public metadata surface agrees.
- **Agent isolation patterns**: Review and orchestration skills distinguish main-agent, sub-agent, reviewer, and worker responsibilities. Keep path-passing, clean-context, no-prior-findings, and user-judged halt contracts explicit when editing those skills.

## 4. Conventions

- **Skill package naming**: Use `kebab-case` for skill directories, and keep the directory name aligned with the `name` frontmatter value. Variants keep the base name visible, such as `task-brief-creator-caveman`.
- **Frontmatter shape**: Installable `SKILL.md` files start with YAML frontmatter. Keep `name` lowercase kebab-case, make `description` the complete activation surface, and add `license` when package-local license material requires it.
- **Entry point structure**: Keep `SKILL.md` navigational rather than encyclopedic. Use sections for triggers, workflow, output contract, reference routing, helper scripts, guardrails, and scope boundaries; push detailed domain rules into `references/`.
- **Reference naming**: Use topic-oriented filenames that match local package schemes: `snake_case` for AGENTS generator specs, hyphenated workflow topics for brief skills, and numbered prefixes for JetBrains plugin references.
- **Relative package links**: Link package-owned material with relative paths like `references/template.md` or `scripts/validate_brief.py`. Avoid installed absolute paths in repository-authored skill docs.
- **Artifact placement**: Put reusable Markdown templates in `references/`, structural checks in `scripts/`, sample outputs in `examples/`, regression fixtures in `evals/`, and design/revision notes in `updates/`.
- **Python validator style**: Use standard-library modules, uppercase contract constants, compiled regexes, explicit exit codes, small parsing/validation functions, and `Report` or dataclass report objects for user-facing output.
- **Node release script style**: Use CommonJS, `node:` imports, uppercase root/path constants, `fail(message)` helpers, explicit argument validation, and targeted writes to release-owned files.
- **Documentation language**: Repository artifacts are English by default unless intentionally localized, such as `CHANGELOG.ko.md`; keep code, paths, command names, frontmatter keys, and exact user-provided strings unchanged.

## 5. Working Agreements

- Respond in Korean unless the user explicitly requests another language; keep technical terms, code blocks, file paths, identifiers, and exact logs unchanged.
- Ask the user before introducing tests, lint, formatter setups, or related automation; add them only on explicit request.
- Build context by reviewing related usages, flows, patterns, and likely impact before editing.
- Fix the underlying cause, not only the visible symptom; inspect affected flows and apply the narrowest complete change that resolves the root issue.
- Check side effects across callers, shared abstractions, public skill contracts, output formats, trigger behavior, bundle availability, and release metadata; report relevant impact and compatibility risks.
- Ask actively when user decisions are needed for scope, behavior, packaging, or tradeoffs.
- New functions, scripts, or modules should be single-purpose and colocated with the owning skill package or release workflow.
- External dependencies are allowed only when necessary; explain why any added dependency is required.
- Preserve user-owned custom sections when updating generated `AGENTS.md` files; refresh only standard managed sections.

## 6. user custom
- Absolute rule for `fable5.md`: for any work involving `fable5.md`, read `fable5.md` first and treat its current contents as the source of truth. Do not skip this rule for convenience.
- Absolute rule for `codemap-search`: actively use `codemap-search` for code exploration and repository navigation. Prefer it over generic Read, Grep, Find, shell search, or broad file-reading workflows whenever it is available and suitable; do not skip this rule for convenience.
