package com.contextextractor.infrastructure.git;

import com.contextextractor.domain.model.GitDiffMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class GitDiffRunner {

    /**
     * Carries the raw diff output, the active branch name at the time of the run,
     * and the exact git command string that was executed.
     */
    public record DiffResult(String output, String branch, String resolvedCommand) {}

    /**
     * Returns {@code true} if {@code directory} is inside a git repository,
     * determined by running {@code git rev-parse --git-dir}.
     */
    public static boolean isGitRepository(Path directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--git-dir");
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs the git diff corresponding to {@code mode} inside {@code repoRoot}.
     * For {@link GitDiffMode#BRANCH_BASE} the base branch is auto-detected via
     * {@link #detectDefaultBranch(Path)}. Returns the diff output, the current
     * branch name, and the resolved command string.
     */
    public static DiffResult runDiff(Path repoRoot, GitDiffMode mode) throws Exception {
        String[] cmd;
        String resolvedCommand;
        if (mode == GitDiffMode.BRANCH_BASE) {
            String base = detectDefaultBranch(repoRoot);
            cmd = new String[]{"git", "diff", base + "...HEAD"};
            resolvedCommand = "git diff " + base + "...HEAD";
        } else {
            cmd = mode.command();
            resolvedCommand = mode.commandHint();
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return new DiffResult(output, getCurrentBranch(repoRoot), resolvedCommand);
    }

    /**
     * Returns the name of the currently checked-out branch, or {@code "detached HEAD"}
     * if in detached HEAD state, or {@code "unknown"} if the command fails.
     */
    public static String getCurrentBranch(Path directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            if (out.isBlank()) return "unknown";
            return "HEAD".equals(out) ? "detached HEAD" : out;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Detects the default base branch for the repository at {@code directory}.
     * Resolution order:
     * <ol>
     *   <li>{@code git symbolic-ref refs/remotes/origin/HEAD} (e.g. {@code origin/main} → {@code main})</li>
     *   <li>{@code git rev-parse --verify main}</li>
     *   <li>Falls back to {@code master}</li>
     * </ol>
     */
    public static String detectDefaultBranch(Path directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "symbolic-ref", "refs/remotes/origin/HEAD");
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && out.contains("/")) return out.substring(out.lastIndexOf('/') + 1);
        } catch (Exception ignored) {}
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--verify", "main");
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            if (pb.start().waitFor() == 0) return "main";
        } catch (Exception ignored) {}
        return "master";
    }
}
