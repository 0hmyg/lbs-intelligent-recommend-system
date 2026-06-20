package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SparkWsClient {
    private final SparkConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SparkWsClient(SparkConfig config) {
        this.config = config;
    }

    public String chat(String requestJson) throws Exception {
        System.out.println("[SparkWsClient] request=" + requestJson);
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder answer = new StringBuilder();
        StringBuilder raw = new StringBuilder();

        URI uri = new URI(config.getApiUrl());
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("[SparkWsClient] websocket opened");
                send(requestJson);
            }

            @Override
            public void onMessage(String message) {
                System.out.println("[SparkWsClient] onMessage=" + message);
                raw.append(message).append('\n');
                try {
                    JsonNode root = objectMapper.readTree(message);
                    int code = root.path("header").path("code").asInt(-1);
                    if (code != 0) {
                        answer.append(message);
                        latch.countDown();
                        close();
                        return;
                    }
                    JsonNode choices = root.path("payload").path("choices");
                    JsonNode text = choices.path("text");
                    if (text.isArray() && text.size() > 0) {
                        JsonNode first = text.get(0);
                        if (first.hasNonNull("content")) {
                            answer.append(first.path("content").asText(""));
                        }
                    }
                    int status = choices.path("status").asInt(2);
                    if (status == 2) {
                        latch.countDown();
                        close();
                    }
                } catch (Exception e) {
                    System.out.println("[SparkWsClient] parse error=" + e.getMessage());
                    answer.append(message);
                    latch.countDown();
                    close();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("[SparkWsClient] websocket closed code=" + code + " reason=" + reason);
                latch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("[SparkWsClient] websocket error=" + ex.getMessage());
                if (answer.length() == 0) {
                    answer.append(ex.getMessage() == null ? "ws error" : ex.getMessage());
                }
                latch.countDown();
            }
        };

        boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
        System.out.println("[SparkWsClient] connectBlocking=" + connected);
        latch.await(30, TimeUnit.SECONDS);
        try {
            client.close();
        } catch (Exception ignored) {}
        if (answer.length() == 0) {
            System.out.println("[SparkWsClient] empty response raw=" + raw);
            return "{\"answer\":\"星火接口未返回内容\",\"tags\":[\"post\"],\"keywords\":[\"post\"],\"intent\":\"recommend\",\"reason\":\"no response\"}";
        }
        return answer.toString();
    }
}
