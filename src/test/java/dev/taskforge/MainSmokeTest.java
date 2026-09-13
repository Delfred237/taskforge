package dev.taskforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainSmokeTest {

    @Test
    void applicationNameShouldBeTaskForge() {
        assertEquals("TaskForge", Main.applicationName());
    }
}