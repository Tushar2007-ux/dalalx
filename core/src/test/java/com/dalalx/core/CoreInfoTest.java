package com.dalalx.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Smoke test: confirms JUnit 5 + AssertJ are wired into the core module. */
class CoreInfoTest {

    @Test
    void reportsModuleName() {
        assertThat(CoreInfo.name()).isEqualTo("DalalX Core");
    }
}
