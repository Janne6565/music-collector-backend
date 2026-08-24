package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.HealthApi;
import com.musiccollector.model.core.HealthDto;
import com.musiccollector.services.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    private final HealthService healthService;

    @Override
    public ResponseEntity<HealthDto> get() {
        return ResponseEntity.ok(healthService.current());
    }
}
