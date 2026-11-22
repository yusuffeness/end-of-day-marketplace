# Contributing

## Branching

- The project follows GitHub Flow. `main` is protected and always deployable.
- Create a short-lived branch from `main` for every change, using one of these prefixes:
  - `feat/...` for new features
  - `fix/...` for bug fixes
  - `docs/...` for documentation
  - `test/...` for tests
  - `chore/...` for tooling and maintenance
  - `refactor/...` for refactoring without behavior changes

## Commit messages

- Follow [Conventional Commits](https://www.conventionalcommits.org/).
- Write in English and in the imperative mood.
- Start the summary with a lowercase letter.
- Keep the first line at 72 characters or fewer.

Examples:

```text
feat(order): lock listings in id order before decrementing stock
fix(pickup): reject delivery outside the pickup window
docs: describe local setup
```

## Pull requests

- Every change reaches `main` through a pull request.
- The description explains what changed, why, and how it was tested.
- Do not merge until CI is green.
- Use squash merge.
- Delete the branch after it is merged.

## Code style

- Java: Spotless with palantir-java-format. Run `./mvnw spotless:apply` before merging.
- SQL: write keywords in UPPERCASE.
- Python: format and lint with ruff.

## Database rules

- Table and column names are Turkish, exactly as defined in the project report, and must not be renamed.
- New database objects follow these naming patterns:
  - Triggers: `trg_<table>_before_insert`, `trg_<table>_before_update`
  - Stored procedures: `proc_<verb>_<noun>`
  - Events: `evt_<verb>_<noun>`
  - Views: `v_<noun>`
- Schema changes are made only through a new Flyway migration. Never edit a migration that has already been applied.

## Language

- Repository content (code, comments, file names, documentation, commit messages) is in English.
- User interface text and user-facing error messages are in Turkish.

## Secrets

- Never commit `.env`. Keep real credentials there and placeholders only in `.env.example`.
