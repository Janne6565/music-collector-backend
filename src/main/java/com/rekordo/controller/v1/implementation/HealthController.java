package com.rekordo.controller.v1.implementation;

import com.rekordo.controller.v1.schema.HealthApi;
import com.rekordo.model.core.HealthDto;
import com.rekordo.services.HealthService;
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
