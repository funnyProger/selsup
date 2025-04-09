package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Queue<Long> requestTimestamps = new LinkedList<>();
    private final Object lock = new Object();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create?pg=milk";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        throttle();

        String documentJson = objectMapper.writeValueAsString(document);
        String base64Document = Base64.getEncoder().encodeToString(documentJson.getBytes());

        CrptRequestBody body = new CrptRequestBody("MANUAL", base64Document, "milk", "LP_INTRODUCE_GOODS", signature);
        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to send request: " + response.statusCode() + " - " + response.body());
        }
    }

    private void throttle() {
        synchronized (lock) {
            long currentTime = Instant.now().toEpochMilli();
            long intervalInMillis = timeUnit.toMillis(1);

            while (requestTimestamps.size() >= requestLimit) {
                long earliest = requestTimestamps.peek();
                long elapsed = currentTime - earliest;
                if (elapsed < intervalInMillis) {
                    try {
                        Thread.sleep(intervalInMillis - elapsed);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    requestTimestamps.poll();
                }
                currentTime = Instant.now().toEpochMilli();
            }
            requestTimestamps.add(currentTime);
        }
    }

    public static class Document {
        public String description;
        public String productionDate;
        public String tnvedCode;
        public String producerInn;
    }

    private static class CrptRequestBody {
        public String document_format;
        public String product_document;
        public String product_group;
        public String type;
        public String signature;

        public CrptRequestBody(String document_format, String product_document, String product_group, String type, String signature) {
            this.document_format = document_format;
            this.product_document = product_document;
            this.product_group = product_group;
            this.type = type;
            this.signature = signature;
        }
    }
} 
