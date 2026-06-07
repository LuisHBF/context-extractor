package com.contextextractor.application;

import com.contextextractor.domain.model.Preset;
import com.contextextractor.infrastructure.persistence.PresetRepository;

public class SavePresetUseCase {

    private final PresetRepository repository;

    public SavePresetUseCase(PresetRepository repository) {
        this.repository = repository;
    }

    public void save(Preset preset) {
        repository.save(preset);
    }
}
