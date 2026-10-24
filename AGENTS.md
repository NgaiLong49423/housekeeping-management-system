> **Document:** Agent Instructions\
> **File:** `AGENTS.md`\
> **Version:** v2.1.0\
> **Created:** 2026-06-29\
> **Last Updated:** 2026-10-24\
> **Status:** Template\

# Agent Entry Point

Read `.agents/POLICY.md` before repository work. Then inspect the relevant project documentation and code only for the task at hand.

## Canonical Sources

- Repository overview and project-specific usage: `README.md`
- Contribution and branch workflow: `CONTRIBUTING.md`
- Requirements: `docs/requirements/PRD.md` and `docs/requirements/SRS.md`
- Approved SQL Server schema contract: `docs/requirements/SQL_SERVER_DATA_DICTIONARY.md`
- Database starter: `database/README.md` and the scripts that apply to the selected database
- Machine-readable repository contract: `.agents/repo-contract.yml`
- Reusable Agent procedures: `.agents/skills/` and `.agents/workflows/`
- Java package structure and layer boundaries: `docs/architecture/java-package-structure.md`

`AGENTS.md` is the entry point. `.agents/POLICY.md` owns agent permissions and safety rules. `CONTRIBUTING.md` owns the contributor workflow. The repository contract summarizes paths and machine-checkable rules; it does not override these sources.

The approved application baseline is Java 21, Spring Boot, Spring MVC/Thymeleaf, Spring Data JPA, SQL Server and Flyway. Preserve the single Maven project and the approved 15-table dictionary. Do not apply the legacy MySQL reference DDL or rewrite applied Flyway migrations.

## Java Application Structure

For Java application work, read [`docs/architecture/java-package-structure.md`](docs/architecture/java-package-structure.md) and follow its package layout for all new code and migrated slices: `config/`, `controller/`, `dto/request/`, `dto/response/`, `entity/`, `repository/`, and `service/`; use `domain/` for suitable enums/value types. Keep layer responsibilities and the existing MVC boundary. This is the team's strong default, not a blanket instruction to rename untouched legacy classes. If a new class must sit outside the layout, keep the exception narrow and explain the reason in the change description.

## Repository Layout

- Application starter: `app/`
- Database starter: `database/`
- Project documentation: `docs/`
- Agent policy, contract, skills, workflows, and scripts: `.agents/`
- GitHub templates and Actions: `.github/`

## Protected Content

Never access, enumerate, search, parse, validate, create, edit, move, or delete files under `docs/diagrams/`. Exclude that subtree before selecting or traversing any inputs. Do not open or validate a link target inside it. Apply the same boundary to every skill, workflow, script, and CI job.

## Documentation

Use repository-relative links. Never add machine-local `file:///` links. Preserve each artifact's appropriate metadata format; do not apply document metadata blocks to skill frontmatter, workflows, or machine-readable configuration.
