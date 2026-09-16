package dev.taskforge.network.protocol;

public record Request(
        String requestId,
        Action action,
        String payload
) {
    public Request {
        if (action == null) {
            action = Action.UNKNOWN;
        }
        if (payload == null) {
            payload = "";
        }
    }
}