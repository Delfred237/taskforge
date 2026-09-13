package dev.taskforge.task;

public class IllegalTaskStateTransitionException extends RuntimeException {

    public IllegalTaskStateTransitionException(TaskStatus from, TaskStatus to, TaskId taskId) {
        super("Illegal task state transition from " + from + " to " + to + " for task " + taskId);
    }
}