package com.example.store.controller;

import com.example.store.service.ApiKeyService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal-only endpoint for generating HMAC-signed API keys.
 *
 * <p>Mapped under {@code /internal/keys} which should NOT be exposed through the K8s Ingress. This endpoint is excluded
 * from API key authentication (see SecurityConfig) so that administrators can generate keys without already having one.
 *
 * <p>Access control in production is enforced at the network level:
 *
 * <ul>
 *   <li>K8s Ingress only routes /api/** and /actuator/health/**
 *   <li>/internal/** is only reachable from within the cluster (pod-to-pod)
 * </ul>
 */
@RestController
@RequestMapping("/internal/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * Generates a new HMAC-signed API key for the given client.
     *
     * @param clientId unique identifier for the API consumer
     * @return the generated API key
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateKey(@RequestParam String clientId) {
        String key = apiKeyService.generateKey(clientId);
        return ResponseEntity.ok(Map.of("apiKey", key, "clientId", clientId));
    }
}
