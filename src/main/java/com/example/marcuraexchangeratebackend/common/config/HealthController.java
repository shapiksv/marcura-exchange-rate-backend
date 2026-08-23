package com.example.marcuraexchangeratebackend.common.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Health check endpoint")
public class HealthController {

    @GetMapping
    @Operation(summary = "Health check", description = "Returns the health status of the application")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "marcura-exchange-rate-backend"
        );
    }
}
