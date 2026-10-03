package com.datespets.mascotas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.datespets")
public class MascotasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MascotasApplication.class, args);
    }
}
