package com.tuoguan.backend.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeedCommandLineRunnerTest {

    @Mock
    private SeedService seedService;

    @Test
    void doesNothingWithoutSeedFlag() throws Exception {
        SeedCommandLineRunner runner = new SeedCommandLineRunner(seedService);

        runner.run("--server.port=8080");

        verify(seedService, never()).seed(anyString(), anyString(), anyString());
    }

    @Test
    void parsesArgumentsAndInvokesSeedService() throws Exception {
        SeedCommandLineRunner runner = new SeedCommandLineRunner(seedService);

        runner.run("--seed", "--seed.institution=测试机构", "--seed.phone=13800005099", "--seed.password=secret");

        verify(seedService).seed("测试机构", "13800005099", "secret");
    }

    @Test
    void throwsWhenRequiredArgumentIsMissing() {
        SeedCommandLineRunner runner = new SeedCommandLineRunner(seedService);

        assertThatThrownBy(() -> runner.run("--seed", "--seed.institution=测试机构", "--seed.phone=13800005099"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSeedClassArgumentsAndInvokesSeedService() throws Exception {
        SeedCommandLineRunner runner = new SeedCommandLineRunner(seedService);

        runner.run("--seed-class", "--seed-class.teacherPhone=13800005099", "--seed-class.name=三年级托管班");

        verify(seedService).seedClass("13800005099", "三年级托管班");
    }

    @Test
    void throwsWhenSeedClassArgumentIsMissing() {
        SeedCommandLineRunner runner = new SeedCommandLineRunner(seedService);

        assertThatThrownBy(() -> runner.run("--seed-class", "--seed-class.teacherPhone=13800005099"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
