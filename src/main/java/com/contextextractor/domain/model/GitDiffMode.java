package com.contextextractor.domain.model;

public enum GitDiffMode {

    ALL_CHANGES("All Changes",    new String[]{"git", "diff", "HEAD"},     "git diff HEAD"),
    STAGED     ("Staged Only",    new String[]{"git", "diff", "--cached"}, "git diff --cached"),
    UNSTAGED   ("Unstaged Only",  new String[]{"git", "diff"},             "git diff"),
    BRANCH_BASE("vs Base Branch", null,                                    "git diff <base>...HEAD");

    private final String displayName;
    private final String[] command;
    private final String commandHint;

    GitDiffMode(String displayName, String[] command, String commandHint) {
        this.displayName = displayName;
        this.command = command;
        this.commandHint = commandHint;
    }

    public String[] command()     { return command; }
    public String displayName()   { return displayName; }
    public String commandHint()   { return commandHint; }

    @Override
    public String toString() { return displayName; }
}
