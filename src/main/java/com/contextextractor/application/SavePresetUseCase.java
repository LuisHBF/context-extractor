package com.contextextractor.application;

import com.contextextractor.domain.model.Preset;
import com.contextextractor.infrastructure.persistence.PresetRepository;

import java.util.Objects;

public class SavePresetUseCase {

    private final PresetRepository repository;

    public SavePresetUseCase(PresetRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public void save(Preset preset) {
        repository.save(preset);
    }
}
