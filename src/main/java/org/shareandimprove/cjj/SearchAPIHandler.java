package org.shareandimprove.cjj;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An {@link HttpHandler} for processing search API requests.
 * Supports both SQL-based keyword search and AI-powered vector semantic search.
 */
public class SearchAPIHandler implements HttpHandler {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();

        if ("POST".equals(requestMethod) && "/api/search".equals(path)) {
            handleSearch(exchange);
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        String queryString = exchange.getRequestURI().getQuery();
        String queryParam = extractQueryParam(queryString);

        if (queryParam == null || queryParam.isEmpty()) {
            sendResponse(exchange, 400, "Missing or empty 'q' query parameter.");
            return;
        }

        try {
            List<Map<String, Object>> results;

            // Heuristic: If query contains advanced operators, use SQL search.
            // Otherwise, attempt Vector Search if configured.
            boolean isAdvancedQuery = queryParam.contains(":") || queryParam.contains("(") || 
                                      queryParam.contains(" AND ") || queryParam.contains(" OR ");
            
            if (isAdvancedQuery) {
                Log.println("Executing SQL Search: " + queryParam);
                String sqlQuery = QueryHandler.query2SQL(queryParam);
                results = DBHandler.SQL(sqlQuery);
            } else {
                // Try Vector Search
                try {
                    String aiKey = Config.getAiKey();
                    if (aiKey != null && !aiKey.isEmpty() && !aiKey.contains("...")) {
                         Log.println("Executing Vector Search: " + queryParam);
                        results = performVectorSearch(queryParam);
                    } else {
                        // Fallback if AI not configured
                         Log.println("Vector search not configured, falling back to SQL: " + queryParam);
                        String sqlQuery = QueryHandler.query2SQL(queryParam);
                        results = DBHandler.SQL(sqlQuery);
                    }
                } catch (Exception e) {
                    Log.println("Vector search failed (" + e.getMessage() + "), falling back to SQL.");
                    String sqlQuery = QueryHandler.query2SQL(queryParam);
                    results = DBHandler.SQL(sqlQuery);
                }
            }
            
            // Clean up results (remove large embedding data before sending to client)
            for (Map<String, Object> row : results) {
                row.remove("Embedding"); 
                // Ensure nulls are handled for frontend
                row.putIfAbsent("Description", "");
                row.putIfAbsent("Subject", "Unknown");
                row.putIfAbsent("Year", "");
            }

            String jsonResponse = gson.toJson(results);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 200, jsonResponse);

        } catch (Exception e) {
            e.printStackTrace(); // Log full trace
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
    
    // --- Vector Search Logic ---

    private List<Map<String, Object>> performVectorSearch(String query) throws Exception {
        // 1. Get embedding for the query
        List<Double> queryVector = fetchEmbedding(query);
        if (queryVector == null || queryVector.isEmpty()) {
            throw new Exception("Failed to generate query embedding.");
        }

        // 2. Load all file embeddings from DB
        // Optimization: In a real app, cache this or use a Vector DB.
        List<Map<String, Object>> allFiles = DBHandler.SQL("SELECT * FROM FILE");
        
        List<Map<String, Object>> scoredFiles = new ArrayList<>();

        for (Map<String, Object> file : allFiles) {
            Object embeddingObj = file.get("Embedding");
            if (embeddingObj != null) {
                String embeddingJson = embeddingObj.toString();
                if (!embeddingJson.isEmpty()) {
                    List<Double> fileVector = gson.fromJson(embeddingJson, new TypeToken<List<Double>>(){}.getType());
                    if (fileVector != null && fileVector.size() == queryVector.size()) {
                        double similarity = cosineSimilarity(queryVector, fileVector);
                        file.put("score", similarity);
                        scoredFiles.add(file);
                    }
                }
            }
        }

        // 3. Sort by score descending
        scoredFiles.sort((m1, m2) -> Double.compare((Double) m2.get("score"), (Double) m1.get("score")));

        // 4. Return top 50
        return scoredFiles.stream().limit(50).collect(Collectors.toList());
    }

    private List<Double> fetchEmbedding(String text) throws IOException, InterruptedException {
        String host = Config.getAiHost();
        String key = Config.getAiKey();
        String model = Config.getAiModel();

        if (!host.endsWith("/embeddings")) {
             // Handle base URL vs full URL
             if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
             if (!host.endsWith("/v1")) host += "/v1";
             host += "/embeddings";
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", text);
        requestBody.put("model", model);

        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("AI API Error: " + response.statusCode() + " " + response.body());
        }

        // Parse response: { "data": [ { "embedding": [...] } ] }
        Map<String, Object> responseMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
        List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
        if (data != null && !data.isEmpty()) {
            return (List<Double>) data.get(0).get("embedding");
        }
        return Collections.emptyList();
    }

    private double cosineSimilarity(List<Double> v1, List<Double> v2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // --- Helper Methods ---

    private String extractQueryParam(String queryString) {
        if (queryString != null) {
            for (String param : queryString.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "q".equals(pair[0])) {
                    return pair[1]; // Already decoded by Java URI in most cases, but verify
                }
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
