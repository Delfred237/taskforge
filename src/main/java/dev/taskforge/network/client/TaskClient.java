package dev.taskforge.network.client;

import dev.taskforge.network.protocol.MessageCodec;
import dev.taskforge.network.protocol.Request;
import dev.taskforge.network.protocol.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public final class TaskClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final MessageCodec codec;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public TaskClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.codec = new MessageCodec();
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public Response send(Request request) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("Client is not connected");
        }
        out.println(codec.encode(request));
        String responseLine = in.readLine();
        if (responseLine == null) {
            throw new IOException("Server closed connection");
        }
        return codec.decode(responseLine, Response.class);
    }

    public Request createRequest(dev.taskforge.network.protocol.Action action, String payload) {
        return new Request(UUID.randomUUID().toString(), action, payload);
    }

    @Override
    public void close() throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
    }
}