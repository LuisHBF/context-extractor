# Context Extractor

> Assemble rich AI context files from your codebase, database, and notes — in one click.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-red?logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-green)

## Overview

Context Extractor is a desktop application for developers who work with large AI models such as Google Gemini. Modern AI models accept files directly as context, but assembling a useful context file manually — gathering source code, database schemas, sample data, and project notes — is tedious and error-prone.

Context Extractor automates this through a guided 5-step workflow. You select an agent prompt file, add file sources, optionally connect to a PostgreSQL database to extract schema definitions and sample rows, add free-form notes, and generate a structured XML file optimised for AI consumption. The XML uses `<![CDATA[` blocks throughout so the model never confuses your code with document structure.

When output exceeds the configured size limit the application automatically splits it into numbered part files (`context-part1.xml`, `context-part2.xml`, …), each independently valid, so you can upload them in sequence to stay within model input limits.

## Screenshots

_(screenshots coming soon)_

## Features

- **Guided 5-step workflow** — Agent → Files → Database → Additional Context → Review & Generate
- **Multiple file sources** — add any combination of scanned directories, individual files, and git diffs in a single session
- **Git Changes support** — attach a `git diff` (all changes, staged only, unstaged only, or vs base branch) as a dedicated `<codeChanges>` section with annotated line-by-line diffs for AI code review workflows
- **Drag-and-drop** — drag files and folders from Windows Explorer, IntelliJ, or VSCode directly onto the Files step
- **Recursive directory scanning** with a fully configurable exclusion list (names, extensions, directory names)
- **Full absolute file paths** — every `<file path="...">` in the generated XML uses the full filesystem path, preventing collisions between identically-named files in different directories
- **PostgreSQL integration** — browse schemas and tables; select DDL and/or sample data per table with custom `WHERE` / `ORDER BY` clauses
- **AI-readable XML output** — well-indented, generous whitespace, all dynamic content in `<![CDATA[` blocks, built-in usage instructions for the AI at the top of every file
- **Automatic file splitting** — configurable maximum file size; oversized output is split into valid part files
- **Preset system** — save and reload complete configurations (paths, DB credentials, table selections, git diff modes, settings) as JSON files
- **Dark mode** — toggle between light and dark themes in Settings
- **Settings panel** — configure max file size, exclusion patterns, output directory, presets directory, and theme
- **Unit tests** — JUnit 5 + Mockito test suite covering the exporter, file scanner, use cases, git diff runner, and persistence layer

---

## Getting Started

### Prerequisites

- Java 21 or later (`java -version`)
- Maven 3.8 or later (`mvn -version`)

### Download (pre-built)

A pre-built fat JAR is available in the [`dist/`](dist/) folder of this repository.
It requires **Java 21** to run:

```bash
java -jar dist/context-extractor-1.2.0.jar
```


### Run from source

```bash
git clone https://github.com/LuisHBF/content-extractor.git
cd content-extractor
mvn javafx:run
```

### Build a fat JAR

```bash
mvn package
java -jar target/context-extractor.jar
```

---

## How to Use

The application is a linear 5-step wizard, but every step is optional and you can jump to any step at any time by clicking the numbered buttons in the left sidebar.

### Step 1 — Agent

The Agent step lets you attach a **system prompt** or **behavioral instructions** file to your context. This file is placed inside the `<agent>` tag in the generated XML and is the first thing the AI reads.

1. Click **Browse…** to open a file picker.
2. Select any `.md`, `.txt`, or `.agent` file that contains your AI instructions.
3. The file content is displayed in the preview area below — scroll to read it in full.
4. Click **Next →** to proceed, or skip this step entirely if you have no agent file.

> **Tip:** An agent file might describe the AI's role ("You are a senior Java developer reviewing a pull request"), coding conventions, or response format requirements.

---

### Step 2 — Files

The Files step lets you build a collection of sources. You can mix directories, individual files, and git diffs in any combination. Each source appears as a card in the list and can be removed independently.

#### Add Directory

1. Click **+ Add Directory** to open a folder picker.
2. The scanner runs in the background. A progress indicator appears while scanning.
3. Once complete, the file count badge updates. Click the badge to expand and see the full list.
4. Use the checkboxes next to individual files to include or exclude them from the output.

#### Add Files

1. Click **+ Add Files** to open a multi-select file picker.
2. The selected files are added as an individual-file source.

#### Add Git Changes

1. Click **+ Add Git Changes** to open a folder picker.
2. Select the root directory of a git repository. If the folder is not a git repository, an error toast appears.
3. A card appears with a **mode selector** and a **↻ refresh** button. Four modes are available:
   - **All Changes** — `git diff HEAD` (everything not yet committed)
   - **Staged Only** — `git diff --cached`
   - **Unstaged Only** — `git diff`
   - **vs Base Branch** — `git diff <base>...HEAD` (all commits on the current branch vs its detected base)
4. The diff runs in the background. A spinner appears while fetching; on completion the badge shows the line count.
5. Change the mode dropdown at any time to re-fetch the diff automatically.
6. The diff is emitted as a dedicated `<codeChanges>` section in the output XML — separate from `<files>` — so the AI can clearly distinguish static snapshots from what changed.

#### Drag and drop

Drag files and folders from your file explorer and drop them anywhere on the Files step. Directories are scanned recursively; individual files are added as a single-file source.

> **Tip:** Configure which files are excluded in **Settings → File Exclusions**. By default, `node_modules`, `.git`, `target`, binary files (`.jar`, `.exe`, `.png`, …), and lock files are excluded.

---

### Step 3 — Database

The Database step connects to a PostgreSQL database and lets you include DDL statements and sample data for selected tables.

All fields are optional. If you skip this step, no `<database>` section is included in the output.

#### Connecting

1. Enter **Host**, **Port** (default `5432`), **Database**, **Username**, and **Password**.
2. Click **Test Connection**. A spinner appears while the connection is verified.
3. On success, a schema dropdown appears below. Select the schema you want to work with.

#### Selecting tables

After selecting a schema, the table list loads automatically. Each table row shows:

- **DDL checkbox** — include the `CREATE TABLE` statement in the output.
- **Data checkbox** — include sample rows in the output.
- **▶ expand arrow** — click to reveal per-table data export options:
  - **WHERE** — a SQL `WHERE` clause applied when fetching rows (e.g. `status = 'active'`). Do not include the `WHERE` keyword.
  - **ORDER BY** — a SQL `ORDER BY` clause (e.g. `created_at DESC`). Do not include the `ORDER BY` keyword.
  - **Row limit** — maximum number of rows to fetch (default `100`).

#### Apply to all

The **Default Export Config** panel at the top of the table list lets you set DDL, Data, and row limit defaults. Click **Apply to All** to push those defaults to every table in the list at once.

---

### Step 4 — Additional Context

A large, scrollable text area where you can type anything the AI should know that is not captured by the other steps:

- Business rules and domain concepts
- Known bugs or constraints
- Architectural decisions and rationale
- Explicit instructions to the AI ("Focus on the repository layer only", "Do not suggest changes to the public API")

A live character count is shown below the text area. This content is placed in the `<additionalContext>` tag at the end of the generated XML.

---

### Step 5 — Review & Generate

The Review step gives you a read-only summary of every selection made in the previous steps and lets you generate the final output file.

#### Reviewing your selections

Five summary cards display:
- **Agent** — path to the selected agent file
- **Files** — number of sources and total files (plus git diff count if any)
- **Database** — host/port/database, schema, and list of selected tables
- **Additional Context** — first 200 characters of your notes
- **Output** — configured max file size and output directory

#### Setting the output filename

Before generating, you can optionally enter a custom filename in the **Output File Name** field. The `.xml` extension is added automatically. If left blank, the file is saved as `context.xml`. This name is also stored when you save a preset.

#### Generating

1. Click **Generate AI Context**.
2. A progress bar tracks each stage: reading files, fetching DDL, fetching sample data, building XML, writing files.
3. On success, the output file paths are shown. Click **Open Downloads Folder** to open the output directory in your file explorer.
4. If generation fails, an error panel displays the cause.

#### Saving a preset

Click **Save Preset** to save your current configuration as a named JSON file. Enter a preset name and click **Save**. Presets are stored in the configured presets directory (see Settings) and appear in the **Load preset…** dropdown in the header.

Loading a preset from the dropdown auto-fills all steps with the saved values and re-scans directories and git diffs in the background.

---

### Settings

Access Settings at any time by clicking the **Settings** button in the top-right header. Changes apply immediately after clicking **Save Settings**.

| Setting | Description | Default |
|---|---|---|
| **Max XML File Size** | If the generated XML exceeds this size (in MB), it is split into multiple part files. | `6 MB` |
| **File Exclusions** | Patterns matched against file names and directory names during scanning. Click `×` to remove a tag; type and press Enter to add one. | See below |
| **Output Directory** | Where generated XML files are saved. Click **Browse…** to pick a folder. | Same directory as the running JAR (or `~/Downloads` when running from source) |
| **Presets Directory** | Where preset JSON files are stored and loaded from. Click **Browse…** to pick a folder. | `presets/` subfolder next to the JAR (or `~/.context-extractor/presets` from source) |

Default exclusion patterns: `node_modules`, `.git`, `target`, `build`, `.class`, `.jar`, `.exe`, `.png`, `.jpg`, `.jpeg`, `.gif`, `.ico`, `.svg`, `.woff`, `.ttf`, `.lock`, `.DS_Store`

Click **Reset to Defaults** to restore all settings to their original values.

---

## Output Format

The generated XML has a human-readable comment block at the top that explains the file structure to the AI. The comment conditionally includes a `<codeChanges>` explanation only when a git diff source was added. The rest of the document follows this shape:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
    Generated by Context Extractor ...
    HOW TO READ THIS FILE — explains each tag to the AI
-->
<context generated-at="2026-06-06T14:32:00" preset="my-preset" generator="context-extractor/1.2.0">

    <agent>
        <![CDATA[ ... system prompt content ... ]]>
    </agent>

    <files>
        <file path="com/example/domain/User.java">
            <![CDATA[ ... file content ... ]]>
        </file>
    </files>

    <!-- present only when a git diff source was added -->
    <codeChanges repository="/path/to/repo" mode="ALL_CHANGES">
        <![CDATA[
diff --git a/src/Main.java b/src/Main.java
...
        ]]>
    </codeChanges>

    <database schema="public">
        <table name="users">
            <ddl><![CDATA[ CREATE TABLE users (...); ]]></ddl>
            <data rows="10">
                <row>
                    <col name="id">1</col>
                    <col name="email">user@example.com</col>
                </row>
            </data>
        </table>
    </database>

    <additionalContext>
        <![CDATA[ ... developer notes ... ]]>
    </additionalContext>

</context>
```

---

## Project Structure

```
com.contextextractor
├── ContextExtractorApp.java          ← JavaFX Application entry point
├── Launcher.java                     ← Fat-JAR manifest entry point
│
├── presentation/
│   ├── MainController.java           ← Root layout, stepper navigation, preset loading
│   ├── ThemeManager.java             ← Light/dark theme switching
│   ├── components/
│   │   ├── StepperSidebar.java       ← Visual stepper sidebar component
│   │   ├── TagListEditor.java        ← Reusable chip/tag editor
│   │   └── ToastNotification.java    ← Transient overlay notifications
│   ├── steps/
│   │   ├── BaseStepController.java
│   │   ├── AgentStepController.java
│   │   ├── DirectoryStepController.java
│   │   ├── DatabaseStepController.java
│   │   ├── AdditionalContextStepController.java
│   │   └── ReviewStepController.java
│   └── settings/
│       └── SettingsController.java
│
├── application/
│   ├── GenerateContextUseCase.java
│   ├── LoadPresetUseCase.java
│   └── SavePresetUseCase.java
│
├── domain/
│   ├── model/                        ← Immutable Java records
│   │   ├── GitDiffMode.java          ← Enum: ALL_CHANGES, STAGED, UNSTAGED, BRANCH_BASE
│   │   └── ...
│   └── strategy/                     ← Strategy interfaces (ContextExportStrategy, DatabaseInspectorStrategy)
│
└── infrastructure/
    ├── export/XmlContextExporter.java
    ├── database/PostgresInspector.java
    ├── filesystem/RecursiveFileScanner.java
    ├── git/GitDiffRunner.java         ← Runs git diff via ProcessBuilder
    └── persistence/
        ├── PresetRepository.java
        └── SettingsRepository.java
```

## Architecture

**Strategy Pattern** — `ContextExportStrategy` and `DatabaseInspectorStrategy` are interfaces in the domain layer. Implementations live in infrastructure. Swapping to a Markdown exporter or a MySQL connector only requires a new implementation class.

**Use Case Pattern** — `application/` classes orchestrate domain logic without containing it. Controllers call use cases; use cases call strategies and repositories.

**Repository Pattern** — All persistence goes through repository classes. Domain models are persistence-agnostic Java records.

## Roadmap

- MySQL / MariaDB support
- Markdown and JSON export formats
- OpenAI / Claude-specific export optimisation
- Cloud storage preset sync

## License

MIT — see [LICENSE](LICENSE) for details.

---

*Built by [Luis Franco](https://github.com/LuisHBF)*
