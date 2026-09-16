package dev.taskforge.network.protocol;

public record Response(
        String requestId,
        boolean success,
        String message,
        String payload
) {
    public static Response ok(String requestId, String message, String payload) {
        return new Response(requestId, true, message, payload == null ? "" : payload);
    }

    public static Response error(String requestId, String message) {
        return new Response(requestId, false, message, "");
    }
}