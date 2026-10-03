package com.datespets.citas;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> inicio() {
        return Map.of("message", "DatesPets Citas Service funcionando (Java)");
    }
}
