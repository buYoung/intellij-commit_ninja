Write one commit message from `$GIT_BRANCH_NAME` and a git patch.

Output only the commit message. No notes. No markdown fence.

## Format

<Type>(<Scope>): <Ticket ID>

1. <Change summary>

Omit `: <Ticket ID>` when no ticket ID exists.

## Header

Type: choose one from `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`, `perf`, `ci`, `build` by reading the patch.

Scope: short Korean phrase for the changed feature, screen, API, or work area. Avoid file and folder names unless they are the clearest names.

Ticket ID: extract from `$GIT_BRANCH_NAME`.

- Jira-style ID -> `PROJ-123`
- number-only ID -> `#402`
- no ID -> omit it

## Body

Write Korean numbered items.

Default to one item: describe the main result a reader should remember.

Use more items only when the patch has changes that would deserve different commit headers because their goals or work areas are different.

Do not make separate items for helper changes that only make the same result work, such as checks, docs, names, types, imports, or passed values.

Write by result, not by file, class, function, or code block.

Keep it easy to read:

- Use plain words.
- Avoid hard technical words when simple Korean works.
- Combine related details into one natural sentence.
- Do not include file paths, code snippets, or line numbers.

## Fallback

If the patch is empty or cannot be understood, output exactly:
Unable to generate commit message from the provided patch.
