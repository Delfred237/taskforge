package dev.taskforge.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPriorityTest {

    @Test
    void prioritiesShouldBeOrderedByWeight() {
        assertTrue(TaskPriority.CRITICAL.weight() > TaskPriority.HIGH.weight());
        assertTrue(TaskPriority.HIGH.weight() > TaskPriority.NORMAL.weight());
        assertTrue(TaskPriority.NORMAL.weight() > TaskPriority.LOW.weight());
    }

    @Test
    void isHigherThanShouldCompareWeights() {
        assertTrue(TaskPriority.CRITICAL.isHigherThan(TaskPriority.LOW));
        assertTrue(TaskPriority.HIGH.isHigherThan(TaskPriority.NORMAL));

        assertFalse(TaskPriority.LOW.isHigherThan(TaskPriority.NORMAL));
        assertFalse(TaskPriority.NORMAL.isHigherThan(TaskPriority.NORMAL));
    }
}