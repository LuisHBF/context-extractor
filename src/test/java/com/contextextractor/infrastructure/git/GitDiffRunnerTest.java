package com.contextextractor.infrastructure.git;

import com.contextextractor.domain.model.GitDiffMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class GitDiffRunnerTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("git-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    @Test
    @DisplayName("Returns false for isGitRepository when directory has no .git folder")
    void returnsFalseForNonGitDirectory() {
        assertFalse(GitDiffRunner.isGitRepository(tempDir));
    }

    @Test
    @DisplayName("Returns true for isGitRepository when directory is a valid git repo")
    void returnsTrueForGitRepository() throws Exception {
        initGitRepo(tempDir);

        assertTrue(GitDiffRunner.isGitRepository(tempDir));
    }

    @Test
    @DisplayName("getCurrentBranch returns a non-blank string for a valid git repo")
    void getCurrentBranchReturnsNonBlank() throws Exception {
        initGitRepo(tempDir);

        String branch = GitDiffRunner.getCurrentBranch(tempDir);

        assertNotNull(branch);
        assertFalse(branch.isBlank());
    }

    @Test
    @DisplayName("getCurrentBranch returns a non-null value even for a non-git directory")
    void getCurrentBranchReturnsNonNullForNonGitDir() {
        String branch = GitDiffRunner.getCurrentBranch(tempDir);

        assertNotNull(branch);
        assertFalse(branch.isBlank());
    }

    @Test
    @DisplayName("runDiff returns a DiffResult with non-null output for ALL_CHANGES mode")
    void runDiffReturnsResultForAllChanges() throws Exception {
        initGitRepo(tempDir);
        Files.writeString(tempDir.resolve("file.txt"), "initial");
        gitAdd(tempDir, "file.txt");
        gitCommit(tempDir, "initial commit");
        Files.writeString(tempDir.resolve("file.txt"), "modified");

        GitDiffRunner.DiffResult result = GitDiffRunner.runDiff(tempDir, GitDiffMode.ALL_CHANGES);

        assertNotNull(result.output());
        assertNotNull(result.branch());
        assertNotNull(result.resolvedCommand());
    }

    @Test
    @DisplayName("runDiff returns empty output when there are no changes")
    void runDiffReturnsEmptyWhenNoChanges() throws Exception {
        initGitRepo(tempDir);
        Files.writeString(tempDir.resolve("file.txt"), "content");
        gitAdd(tempDir, "file.txt");
        gitCommit(tempDir, "initial");

        GitDiffRunner.DiffResult result = GitDiffRunner.runDiff(tempDir, GitDiffMode.ALL_CHANGES);

        assertTrue(result.output().isBlank());
    }

    @Test
    @DisplayName("runDiff for STAGED mode only shows staged changes")
    void runDiffStagedShowsOnlyStagedChanges() throws Exception {
        initGitRepo(tempDir);
        Files.writeString(tempDir.resolve("a.txt"), "original");
        gitAdd(tempDir, "a.txt");
        gitCommit(tempDir, "initial");

        Files.writeString(tempDir.resolve("a.txt"), "staged change");
        gitAdd(tempDir, "a.txt");
        Files.writeString(tempDir.resolve("b.txt"), "unstaged new file");

        GitDiffRunner.DiffResult result = GitDiffRunner.runDiff(tempDir, GitDiffMode.STAGED);

        assertTrue(result.output().contains("staged change"));
        assertFalse(result.output().contains("unstaged new file"));
    }

    @Test
    @DisplayName("runDiff resolvedCommand contains the git diff command string")
    void resolvedCommandContainsGitDiff() throws Exception {
        initGitRepo(tempDir);
        Files.writeString(tempDir.resolve("f.txt"), "x");
        gitAdd(tempDir, "f.txt");
        gitCommit(tempDir, "init");

        GitDiffRunner.DiffResult result = GitDiffRunner.runDiff(tempDir, GitDiffMode.ALL_CHANGES);

        assertTrue(result.resolvedCommand().startsWith("git diff"));
    }

    @Test
    @DisplayName("detectDefaultBranch returns a non-null branch name")
    void detectDefaultBranchReturnsNonNull() throws Exception {
        initGitRepo(tempDir);
        Files.writeString(tempDir.resolve("f.txt"), "x");
        gitAdd(tempDir, "f.txt");
        gitCommit(tempDir, "init");

        String branch = GitDiffRunner.detectDefaultBranch(tempDir);

        assertNotNull(branch);
        assertFalse(branch.isBlank());
    }

    @Test
    @DisplayName("DiffResult record holds output, branch, and resolvedCommand correctly")
    void diffResultRecordHoldsValues() {
        GitDiffRunner.DiffResult result = new GitDiffRunner.DiffResult("diff output", "main", "git diff HEAD");

        assertEquals("diff output", result.output());
        assertEquals("main", result.branch());
        assertEquals("git diff HEAD", result.resolvedCommand());
    }

    private void initGitRepo(Path dir) throws Exception {
        exec(dir, "git", "init");
        exec(dir, "git", "config", "user.email", "test@test.com");
        exec(dir, "git", "config", "user.name", "Test");
    }

    private void gitAdd(Path dir, String file) throws Exception {
        exec(dir, "git", "add", file);
    }

    private void gitCommit(Path dir, String message) throws Exception {
        exec(dir, "git", "commit", "-m", message);
    }

    private void exec(Path dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().readAllBytes();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd));
        }
    }
}
