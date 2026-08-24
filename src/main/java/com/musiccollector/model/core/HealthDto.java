package com.musiccollector.model.core;

import java.time.Instant;

public record HealthDto(String status, String version, Instant time) {}
