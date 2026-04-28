package com.manager.controller;

import com.manager.config.GeminiConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final GeminiConfig geminiConfig;

    public DebugController(GeminiConfig geminiConfig) {
        this.geminiConfig = geminiConfig;
    }

    @GetMapping("/gemini-models")
    public ResponseEntity<String> listGeminiModels() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiConfig.getApiKey() + "&pageSize=200")
                    .get()
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                return ResponseEntity.status(resp.code()).body(body);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }
}
