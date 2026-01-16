package org.shareandimprove.cjj;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsAPIHandler implements HttpHandler {
    private static final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        
        if ("GET".equals(requestMethod)) {
            try {
                // Get the latest log entry
                List<Map<String, Object>> results = DBHandler.SQL("SELECT * FROM INDEX_LOG ORDER BY id DESC LIMIT 1");
                
                Map<String, Object> responseData = new HashMap<>();
                if (!results.isEmpty()) {
                    responseData = results.get(0);
                }
                
                String jsonResponse = gson.toJson(responseData);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
