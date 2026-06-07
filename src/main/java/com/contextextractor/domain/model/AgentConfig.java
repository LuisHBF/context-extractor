package com.contextextractor.domain.model;

import java.nio.file.Path;

public record AgentConfig(Path filePath, String content) {}
