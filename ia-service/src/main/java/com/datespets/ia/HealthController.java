package com.datespets.ia;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> inicio() {
        return Map.of("message", "DatesPets IA Service funcionando (Java)");
    }
}
