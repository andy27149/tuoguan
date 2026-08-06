package com.tuoguan.backend.auth.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SeedCommandLineRunner implements CommandLineRunner {

    private final SeedService seedService;

    public SeedCommandLineRunner(SeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("--seed"))) {
            String institution = requireArg(args, "--seed.institution=");
            String phone = requireArg(args, "--seed.phone=");
            String password = requireArg(args, "--seed.password=");
            seedService.seed(institution, phone, password);
        }
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("--seed-class"))) {
            String teacherPhone = requireArg(args, "--seed-class.teacherPhone=");
            String className = requireArg(args, "--seed-class.name=");
            seedService.seedClass(teacherPhone, className);
        }
    }

    private String requireArg(String[] args, String prefix) {
        String value = Arrays.stream(args)
                .filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length()))
                .findFirst()
                .orElse(null);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + prefix);
        }
        return value;
    }
}
