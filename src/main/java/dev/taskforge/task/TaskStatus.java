package dev.taskforge.task;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED,
    TIMED_OUT;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS;

    static {
        var map = new EnumMap<TaskStatus, Set<TaskStatus>>(TaskStatus.class);

        map.put(PENDING, EnumSet.of(RUNNING, CANCELLED));
        map.put(RUNNING, EnumSet.of(COMPLETED, FAILED, TIMED_OUT, CANCELLED));
        map.put(FAILED, EnumSet.of(RETRYING, CANCELLED));
        map.put(TIMED_OUT, EnumSet.of(RETRYING, CANCELLED));
        map.put(RETRYING, EnumSet.of(RUNNING, CANCELLED));
        map.put(COMPLETED, EnumSet.noneOf(TaskStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(TaskStatus.class));

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(TaskStatus target) {
        if (target == null) {
            return false;
        }

        return ALLOWED_TRANSITIONS
                .getOrDefault(this, Set.of())
                .contains(target);
    }

    public boolean isAlwaysTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}