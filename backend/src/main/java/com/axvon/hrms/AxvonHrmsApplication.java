package com.axvon.hrms;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
// Scheduling drives one job: the 1 January leave-balance rollover.
@SpringBootApplication @EnableScheduling
public class AxvonHrmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AxvonHrmsApplication.class, args);
        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║  AXVON HRMS v2.0 — RUNNING       ║");
        System.out.println("║  Author: Athul KV © 2025 AXVON   ║");
        System.out.println("╚══════════════════════════════════╝\n");
    }
}
