package com.rekordo.controller.v1.schema;

import com.rekordo.model.core.HealthDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public interface HealthApi {

    @GetMapping
    @Operation(summary = "Liveness probe carrying the running build")
    @ApiResponse(responseCode = "200", description = "Service is up")
    ResponseEntity<HealthDto> get();
}
