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
        if (Arrays.stream(args).noneMatch(arg -> arg.equals("--seed"))) {
            return;
        }
        String institution = requireArg(args, "--seed.institution=");
        String phone = requireArg(args, "--seed.phone=");
        String password = requireArg(args, "--seed.password=");
        seedService.seed(institution, phone, password);
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
