package com.contextextractor.infrastructure.export;

import com.contextextractor.domain.model.AppSettings;
import com.contextextractor.domain.model.ContextPayload;
import com.contextextractor.domain.model.ScannedFile;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.domain.strategy.ContextExportStrategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlContextExporter implements ContextExportStrategy {

    private static final String HR = "\u2500".repeat(77);
    private static final Pattern HUNK_PATTERN =
            Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".class", ".jar", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".svg", ".woff", ".ttf", ".exe", ".pdf");

    private enum FileStatus { ADDED, DELETED, MODIFIED }
    private enum DiffLineType { ADDED, REMOVED, CONTEXT }

    private record DiffLine(DiffLineType type, String content) {}
    private record DiffHunk(int oldStart, int oldCount, int newStart, int newCount, List<DiffLine> lines) {}
    private record ParsedFileDiff(String path, FileStatus status, List<DiffHunk> hunks) {}
    private record AnnotatedLine(int lineNumber, char marker, String content) {}

    @Override
    public List<Path> export(ContextPayload payload, Path outputDirectory) throws IOException {
        List<Map.Entry<String, String>> allFileEntries = new ArrayList<>(payload.files().entrySet());
        byte[] singleBytes = buildPartXml(payload, allFileEntries, true, true).getBytes(StandardCharsets.UTF_8);
        long maxBytes = (long) payload.settings().maxXmlSizeMb() * 1024 * 1024;

        if (singleBytes.length <= maxBytes) {
            Path outputFile = resolveOutputPath(outputDirectory, resolveBaseName(payload) + ".xml");
            Files.write(outputFile, singleBytes);
            return List.of(outputFile);
        }
        return splitExport(payload, allFileEntries, outputDirectory, maxBytes);
    }

    private List<Path> splitExport(ContextPayload payload,
                                    List<Map.Entry<String, String>> allFileEntries,
                                    Path outputDirectory, long maxBytes) throws IOException {
        long overhead = buildPartXml(payload, List.of(), true, true).getBytes(StandardCharsets.UTF_8).length;
        List<List<Map.Entry<String, String>>> parts = partitionFiles(allFileEntries, overhead, maxBytes);
        String baseName = resolveBaseName(payload);

        List<Path> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == parts.size() - 1;
            String xml = buildPartXml(payload, parts.get(i), isFirst, isLast);
            String filename = parts.size() == 1 ? baseName + ".xml" : baseName + "-part" + (i + 1) + ".xml";
            Path outputFile = resolveOutputPath(outputDirectory, filename);
            Files.writeString(outputFile, xml, StandardCharsets.UTF_8);
            result.add(outputFile);
        }
        return result;
    }

    private List<List<Map.Entry<String, String>>> partitionFiles(
            List<Map.Entry<String, String>> fileEntries, long overhead, long maxBytes) {
        List<List<Map.Entry<String, String>>> parts = new ArrayList<>();
        List<Map.Entry<String, String>> currentPart = new ArrayList<>();
        long currentSize = overhead;

        for (Map.Entry<String, String> entry : fileEntries) {
            long fileSize = buildFileXml(entry).getBytes(StandardCharsets.UTF_8).length;
            if (!currentPart.isEmpty() && currentSize + fileSize > maxBytes) {
                parts.add(currentPart);
                currentPart = new ArrayList<>();
                currentSize = overhead;
            }
            currentPart.add(entry);
            currentSize += fileSize;
        }
        if (!currentPart.isEmpty() || parts.isEmpty()) parts.add(currentPart);
        return parts;
    }

    private String buildPartXml(ContextPayload payload,
                                 List<Map.Entry<String, String>> fileEntries,
                                 boolean includeDatabase,
                                 boolean includeAdditionalContext) {
        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String presetName = Objects.nonNull(payload.presetName()) ? payload.presetName() : "unsaved";
        boolean hasCodeChanges = Objects.nonNull(payload.codeChanges()) && !payload.codeChanges().isEmpty();
        boolean emitCodeChanges = hasCodeChanges && includeAdditionalContext;

        StringBuilder sb = new StringBuilder();
        sb.append(buildXmlPreamble(generatedAt, presetName, emitCodeChanges));
        sb.append(buildAgentSection(payload));
        sb.append("    <files>\n");
        for (Map.Entry<String, String> entry : fileEntries) {
            sb.append(buildFileXml(entry));
        }
        sb.append("\n    </files>\n\n");
        if (emitCodeChanges) sb.append(buildCodeChangesSection(payload));
        if (includeDatabase) sb.append(buildDatabaseSection(payload));
        if (includeAdditionalContext) sb.append(buildAdditionalContextSection(payload));
        sb.append("</context>\n");
        return sb.toString();
    }

    private String buildXmlPreamble(String generatedAt, String presetName, boolean hasCodeChanges) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + buildContextGuideComment(hasCodeChanges)
                + "<context"
                + " generated-at=\"" + generatedAt + "\""
                + " preset=\"" + escapeAttr(presetName) + "\""
                + " generator=\"context-extractor/" + AppSettings.VERSION + "\">\n\n";
    }

    private String buildContextGuideComment(boolean hasCodeChanges) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append("    Generated by Context Extractor \u2014 a developer tool for assembling AI context files.\n\n");
        sb.append("    HOW TO READ THIS FILE\n");
        sb.append("    ").append(HR).append("\n");
        sb.append("    This document bundles everything a developer has chosen to share with you\n");
        sb.append("    into a single structured file. Read all sections before responding.\n\n");
        sb.append("    <agent>\n");
        sb.append("        The system prompt or role instructions written by the developer.\n");
        sb.append("        This defines your persona, task, and constraints. Treat it as your\n");
        sb.append("        primary directive for the entire conversation.\n\n");
        sb.append("    <files>\n");
        sb.append("        Source code and text files from a project directory. Each <file>\n");
        sb.append("        has a path attribute (relative to the scanned root directory) and\n");
        sb.append("        its full content in a CDATA block. Use the path to understand the\n");
        sb.append("        module structure and package layout of the project.\n\n");
        if (hasCodeChanges) {
            sb.append("    <codeChanges repository=\"...\" branch=\"...\" mode=\"...\">\n");
            sb.append("        Contains one <file path=\"...\" status=\"...\"> per changed file.\n");
            sb.append("        Each line follows the format:  {lineNum} \u2502 {marker} \u2502 {content}\n");
            sb.append("        Marker values:\n");
            sb.append("          (space)  unchanged \u2014 present in both old and new version\n");
            sb.append("          +        added     \u2014 present only in the new version\n");
            sb.append("          -        removed   \u2014 present only in the old version\n");
            sb.append("        Line numbers: '+' and ' ' lines use the new-file number;\n");
        sb.append("        '-' lines use the old-file number.\n");
            sb.append("        The status attribute is MODIFIED, ADDED, or DELETED.\n");
            sb.append("        The branch attribute names the branch active when the context\n");
            sb.append("        was generated. The mode attribute indicates the diff scope:\n");
            sb.append("          ALL_CHANGES   \u2014 all uncommitted changes vs. last commit\n");
            sb.append("          STAGED        \u2014 only staged changes\n");
            sb.append("          UNSTAGED      \u2014 only unstaged changes\n");
            sb.append("          BRANCH_BASE   \u2014 this branch vs. its detected base branch\n");
            sb.append("                          (useful for reviewing all commits on a feature branch)\n");
            sb.append("        Prioritise your analysis on these changes over the static\n");
            sb.append("        snapshots in <files>.\n\n");
        }
        sb.append("    <database schema=\"...\">\n");
        sb.append("        PostgreSQL database information for the named schema. Each <table>\n");
        sb.append("        may contain:\n");
        sb.append("          <ddl>   \u2014 the CREATE TABLE statement with all column types,\n");
        sb.append("                    constraints, and indexes. Use this to understand the\n");
        sb.append("                    exact data model.\n");
        sb.append("          <data>  \u2014 sample rows as <row> elements with <col name=\"\">.\n");
        sb.append("                    Use these to understand realistic data shapes and values.\n\n");
        sb.append("    <additionalContext>\n");
        sb.append("        Free-form notes written by the developer: business rules, known\n");
        sb.append("        issues, architectural decisions, or explicit instructions to you.\n");
        sb.append("        Treat this as high-priority context that can override or extend\n");
        sb.append("        anything else in this file.\n\n");
        sb.append("    CDATA BLOCKS\n");
        sb.append("        All dynamic content (code, DDL, free text) is wrapped in\n");
        sb.append("        <![CDATA[ ... ]]> to prevent XML parsing conflicts. The content\n");
        sb.append("        inside should be interpreted literally, not as XML.\n\n");
        sb.append("    MULTI-PART FILES\n");
        sb.append("        If the output was split due to size limits, you will receive\n");
        sb.append("        context-part1.xml, context-part2.xml, etc. Each part is a valid\n");
        sb.append("        XML document. Process all parts before responding \u2014 the <files>\n");
        sb.append("        section is distributed across parts while <agent>, <database>,\n");
        sb.append("        and <additionalContext> appear only in the relevant part.\n");
        sb.append("    ").append(HR).append("\n");
        sb.append("-->\n");
        return sb.toString();
    }

    private String buildAgentSection(ContextPayload payload) {
        if (Objects.isNull(payload.agentContent()) || payload.agentContent().isBlank()) return "";
        return "    <agent>\n"
                + "        <![CDATA[\n"
                + escapeCdata(payload.agentContent())
                + "\n        ]]>\n"
                + "    </agent>\n\n";
    }

    private String buildCodeChangesSection(ContextPayload payload) {
        if (Objects.isNull(payload.codeChanges()) || payload.codeChanges().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ScannedFile entry : payload.codeChanges()) {
            String[] parts = entry.relativePath().split("\\|", 3);
            String mode   = parts.length > 0 ? parts[0] : "";
            String branch = parts.length >= 3 ? parts[1] : "";
            String repo   = parts.length >= 3 ? parts[2] : (parts.length >= 2 ? parts[1] : "");
            Path repoPath = repo.isBlank() ? Path.of(".") : Path.of(repo);

            sb.append("    <codeChanges repository=\"").append(escapeAttr(repo)).append("\"");
            if (!branch.isBlank()) sb.append(" branch=\"").append(escapeAttr(branch)).append("\"");
            sb.append(" mode=\"").append(escapeAttr(mode)).append("\">\n\n");

            for (ParsedFileDiff fd : parseDiff(entry.content())) {
                String annotated = buildAnnotatedContent(fd, repoPath);
                sb.append("        <file path=\"").append(escapeAttr(fd.path()))
                  .append("\" status=\"").append(fd.status().name()).append("\">\n")
                  .append("            <![CDATA[\n")
                  .append(escapeCdata(annotated.stripTrailing()))
                  .append("\n            ]]>\n")
                  .append("        </file>\n\n");
            }

            sb.append("    </codeChanges>\n\n");
        }
        return sb.toString();
    }

    private List<ParsedFileDiff> parseDiff(String rawDiff) {
        List<ParsedFileDiff> result = new ArrayList<>();
        if (Objects.isNull(rawDiff) || rawDiff.isBlank()) return result;

        String currentPath = null;
        FileStatus currentStatus = FileStatus.MODIFIED;
        List<DiffHunk> currentHunks = null;
        List<DiffLine> currentHunkLines = null;
        int hunkOldStart = 0, hunkOldCount = 0, hunkNewStart = 0, hunkNewCount = 0;

        String normalized = rawDiff.replace("\r\n", "\n").replace("\r", "\n");
        for (String line : normalized.split("\n", -1)) {
            if (line.startsWith("warning:") || line.startsWith("\\ ")) continue;

            if (line.startsWith("diff --git ")) {
                if (Objects.nonNull(currentHunkLines) && Objects.nonNull(currentHunks)) {
                    currentHunks.add(new DiffHunk(hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount, currentHunkLines));
                }
                if (Objects.nonNull(currentPath)) {
                    result.add(new ParsedFileDiff(currentPath, currentStatus, currentHunks));
                }
                // Reset all per-file state — must happen before processing each new diff --git header
                int bIdx = line.lastIndexOf(" b/");
                currentPath = bIdx >= 0 ? line.substring(bIdx + 3) : line;
                currentStatus = FileStatus.MODIFIED;
                currentHunks = new ArrayList<>();
                currentHunkLines = null;
                hunkOldStart = 0; hunkOldCount = 0; hunkNewStart = 0; hunkNewCount = 0;
            } else if (line.startsWith("--- /dev/null")) {
                currentStatus = FileStatus.ADDED;
            } else if (line.startsWith("+++ /dev/null")) {
                currentStatus = FileStatus.DELETED;
            } else if (line.startsWith("@@")) {
                if (Objects.nonNull(currentHunkLines) && Objects.nonNull(currentHunks)) {
                    currentHunks.add(new DiffHunk(hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount, currentHunkLines));
                }
                Matcher m = HUNK_PATTERN.matcher(line);
                if (m.find()) {
                    hunkOldStart = Integer.parseInt(m.group(1));
                    hunkOldCount = Objects.nonNull(m.group(2)) ? Integer.parseInt(m.group(2)) : 1;
                    hunkNewStart = Integer.parseInt(m.group(3));
                    hunkNewCount = Objects.nonNull(m.group(4)) ? Integer.parseInt(m.group(4)) : 1;
                    currentHunkLines = new ArrayList<>();
                } else {
                    currentHunkLines = null;
                }
            } else if (Objects.nonNull(currentHunkLines)) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    currentHunkLines.add(new DiffLine(DiffLineType.ADDED, line.substring(1)));
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    currentHunkLines.add(new DiffLine(DiffLineType.REMOVED, line.substring(1)));
                } else if (line.startsWith(" ")) {
                    currentHunkLines.add(new DiffLine(DiffLineType.CONTEXT, line.substring(1)));
                }
            }
        }

        if (Objects.nonNull(currentHunkLines) && Objects.nonNull(currentHunks)) {
            currentHunks.add(new DiffHunk(hunkOldStart, hunkOldCount, hunkNewStart, hunkNewCount, currentHunkLines));
        }
        if (Objects.nonNull(currentPath)) {
            result.add(new ParsedFileDiff(currentPath, currentStatus, currentHunks));
        }

        return result;
    }

    private String buildAnnotatedContent(ParsedFileDiff fileDiff, Path repoPath) {
        if (isBinaryFile(fileDiff.path())) return "Binary file \u2014 diff not shown.";
        return formatAnnotatedLines(buildAnnotatedLines(fileDiff, repoPath));
    }

    private List<AnnotatedLine> buildAnnotatedLines(ParsedFileDiff fileDiff, Path repoPath) {
        return switch (fileDiff.status()) {
            case ADDED    -> buildAddedAnnotations(fileDiff, repoPath);
            case DELETED  -> buildDeletedAnnotations(fileDiff);
            case MODIFIED -> buildModifiedAnnotations(fileDiff, repoPath);
        };
    }

    private List<AnnotatedLine> buildAddedAnnotations(ParsedFileDiff fileDiff, Path repoPath) {
        try {
            List<String> fileLines = Files.readAllLines(repoPath.resolve(fileDiff.path()), StandardCharsets.UTF_8);
            List<AnnotatedLine> result = new ArrayList<>(fileLines.size());
            for (int i = 0; i < fileLines.size(); i++) {
                result.add(new AnnotatedLine(i + 1, '+', fileLines.get(i)));
            }
            return result;
        } catch (IOException e) {
            List<AnnotatedLine> result = new ArrayList<>();
            for (DiffHunk hunk : fileDiff.hunks()) {
                int pos = hunk.newStart();
                for (DiffLine dl : hunk.lines()) {
                    if (dl.type() == DiffLineType.ADDED) {
                        result.add(new AnnotatedLine(pos++, '+', dl.content()));
                    }
                }
            }
            return result;
        }
    }

    private List<AnnotatedLine> buildDeletedAnnotations(ParsedFileDiff fileDiff) {
        List<AnnotatedLine> result = new ArrayList<>();
        for (DiffHunk hunk : fileDiff.hunks()) {
            int pos = hunk.oldStart();
            for (DiffLine dl : hunk.lines()) {
                if (dl.type() == DiffLineType.REMOVED) {
                    result.add(new AnnotatedLine(pos++, '-', dl.content()));
                }
            }
        }
        return result;
    }

    private List<AnnotatedLine> buildModifiedAnnotations(ParsedFileDiff fileDiff, Path repoPath) {
        List<String> fileLines;
        try {
            fileLines = Files.readAllLines(repoPath.resolve(fileDiff.path()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return buildFallbackAnnotations(fileDiff);
        }

        int N = fileLines.size();
        boolean[] isAdded = new boolean[N + 2];
        Map<Integer, List<String>> removedBefore = new HashMap<>();

        for (DiffHunk hunk : fileDiff.hunks()) {
            int pos = hunk.newStart();
            for (DiffLine dl : hunk.lines()) {
                switch (dl.type()) {
                    case CONTEXT -> pos++;
                    case ADDED -> {
                        if (pos >= 1 && pos <= N) isAdded[pos] = true;
                        pos++;
                    }
                    case REMOVED -> removedBefore
                            .computeIfAbsent(pos, k -> new ArrayList<>())
                            .add(dl.content());
                }
            }
        }

        List<AnnotatedLine> result = new ArrayList<>(N);
        for (int i = 1; i <= N; i++) {
            List<String> removed = removedBefore.get(i);
            if (Objects.nonNull(removed)) {
                for (String c : removed) result.add(new AnnotatedLine(i, '-', c));
            }
            result.add(new AnnotatedLine(i, isAdded[i] ? '+' : ' ', fileLines.get(i - 1)));
        }
        List<String> trailing = removedBefore.get(N + 1);
        if (Objects.nonNull(trailing)) {
            for (String c : trailing) result.add(new AnnotatedLine(N + 1, '-', c));
        }
        return result;
    }

    private List<AnnotatedLine> buildFallbackAnnotations(ParsedFileDiff fileDiff) {
        List<AnnotatedLine> result = new ArrayList<>();
        for (DiffHunk hunk : fileDiff.hunks()) {
            int oldPos = hunk.oldStart();
            int newPos = hunk.newStart();
            for (DiffLine dl : hunk.lines()) {
                switch (dl.type()) {
                    case REMOVED -> { result.add(new AnnotatedLine(oldPos, '-', dl.content())); oldPos++; }
                    case ADDED   -> { result.add(new AnnotatedLine(newPos, '+', dl.content())); newPos++; }
                    case CONTEXT -> { result.add(new AnnotatedLine(newPos, ' ', dl.content())); oldPos++; newPos++; }
                }
            }
        }
        return result;
    }

    private String formatAnnotatedLines(List<AnnotatedLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (AnnotatedLine line : lines) {
            sb.append(String.format("%4d \u2502 %s \u2502 %s\n", line.lineNumber(), line.marker(), line.content()));
        }
        return sb.toString();
    }

    private boolean isBinaryFile(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return false;
        return BINARY_EXTENSIONS.contains(path.substring(dot).toLowerCase());
    }

    private String buildDatabaseSection(ContextPayload payload) {
        if (Objects.isNull(payload.databaseConfig())) return "";
        if (payload.tableDdl().isEmpty() && payload.tableData().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("    <database schema=\"")
          .append(escapeAttr(payload.databaseConfig().schema())).append("\">\n\n");
        for (TableConfig tableConfig : payload.tables()) {
            sb.append(buildTableXml(tableConfig, payload));
        }
        sb.append("    </database>\n\n");
        return sb.toString();
    }

    private String buildTableXml(TableConfig tableConfig, ContextPayload payload) {
        boolean hasDdl = tableConfig.exportDdl() && payload.tableDdl().containsKey(tableConfig.tableName());
        boolean hasData = tableConfig.exportData() && payload.tableData().containsKey(tableConfig.tableName());
        if (!hasDdl && !hasData) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("        <table name=\"").append(escapeAttr(tableConfig.tableName())).append("\">\n\n");
        if (hasDdl) {
            sb.append("            <ddl>\n")
              .append("                <![CDATA[\n")
              .append(escapeCdata(payload.tableDdl().get(tableConfig.tableName())))
              .append("\n                ]]>\n")
              .append("            </ddl>\n\n");
        }
        if (hasData) {
            sb.append(buildDataSection(payload.tableData().get(tableConfig.tableName())));
        }
        sb.append("        </table>\n\n");
        return sb.toString();
    }

    private String buildDataSection(List<Map<String, String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("            <data rows=\"").append(rows.size()).append("\">\n");
        for (Map<String, String> row : rows) {
            sb.append("                <row>\n");
            for (Map.Entry<String, String> column : row.entrySet()) {
                sb.append("                    <col name=\"")
                  .append(escapeAttr(column.getKey())).append("\">")
                  .append(escapeContent(column.getValue()))
                  .append("</col>\n");
            }
            sb.append("                </row>\n");
        }
        sb.append("            </data>\n\n");
        return sb.toString();
    }

    private String buildAdditionalContextSection(ContextPayload payload) {
        if (Objects.isNull(payload.additionalContext()) || payload.additionalContext().isBlank()) return "";
        return "    <additionalContext>\n"
                + "        <![CDATA[\n"
                + escapeCdata(payload.additionalContext())
                + "\n        ]]>\n"
                + "    </additionalContext>\n\n";
    }

    private String buildFileXml(Map.Entry<String, String> entry) {
        return "\n        <file path=\"" + escapeAttr(entry.getKey()) + "\">\n"
                + "            <![CDATA[\n"
                + escapeCdata(entry.getValue())
                + "\n            ]]>\n"
                + "        </file>\n";
    }

    private String resolveBaseName(ContextPayload payload) {
        return (Objects.nonNull(payload.outputFileName()) && !payload.outputFileName().isBlank())
                ? payload.outputFileName().replaceAll("[^a-zA-Z0-9_\\-]", "_")
                : "context";
    }

    private Path resolveOutputPath(Path directory, String filename) {
        Path outputPath = directory.resolve(filename);
        if (Files.exists(outputPath)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            int dot = filename.lastIndexOf('.');
            String base = dot >= 0 ? filename.substring(0, dot) : filename;
            String ext = dot >= 0 ? filename.substring(dot) : "";
            outputPath = directory.resolve(base + "-" + timestamp + ext);
        }
        return outputPath;
    }

    private String escapeCdata(String s) {
        if (Objects.isNull(s)) return "";
        return s.replace("]]>", "]]]]><![CDATA[>");
    }

    private String escapeAttr(String s) {
        if (Objects.isNull(s)) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private String escapeContent(String s) {
        if (Objects.isNull(s)) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
