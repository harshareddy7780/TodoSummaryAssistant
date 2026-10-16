package com.todoapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class CohereService {

    @Value("${cohere.api.key}")
    private String cohereApiKey;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CohereService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public String summarizeText(String text) throws IOException {

        if (text == null || text.trim().isEmpty()) {
            return "No pending todos to summarize.";
        }

        String url = "https://api.cohere.ai/v2/chat";

        ObjectNode requestJson = objectMapper.createObjectNode();
        requestJson.put("model", "command-a-03-2025");
        requestJson.put("temperature", 0.3);
        requestJson.put("max_tokens", 300);

        ArrayNode messages = requestJson.putArray("messages");

        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put(
                "content",
                "Summarize the following pending todo tasks into a clear, " +
                "concise paragraph. Mention the main tasks and priorities. " +
                "Do not invent tasks that are not listed.\n\n" +
                "Todo tasks:\n" + text
        );

        RequestBody body = RequestBody.create(
                requestJson.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + cohereApiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            String responseBody = response.body() != null
                    ? response.body().string()
                    : "";

            if (!response.isSuccessful()) {
                throw new IOException(
                        "Cohere Chat API returned HTTP "
                                + response.code() + ": " + responseBody
                );
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode content = jsonNode.path("message").path("content");

            if (content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText())
                            && item.hasNonNull("text")) {
                        return item.get("text").asText().trim();
                    }
                }
            }

            throw new IOException(
                    "Cohere returned no text content in its response."
            );
        }
    }
}