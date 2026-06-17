package com.contextextractor.application;

import com.contextextractor.domain.model.Preset;
import com.contextextractor.infrastructure.persistence.PresetRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LoadPresetUseCase {

    private final PresetRepository repository;

    public LoadPresetUseCase(PresetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public List<Preset> loadAll() {
        return repository.loadAll();
    }

    public Optional<Preset> load(String name) {
        return repository.loadByName(name);
    }
}
