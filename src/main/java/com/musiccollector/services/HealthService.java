package com.musiccollector.services;

import com.musiccollector.model.core.HealthDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HealthService {

    private final String version;

    public HealthService(@Value("${music-collector.version:dev}") String version) {
        this.version = version;
    }

    public HealthDto current() {
        return new HealthDto("ok", version, Instant.now());
    }
}
