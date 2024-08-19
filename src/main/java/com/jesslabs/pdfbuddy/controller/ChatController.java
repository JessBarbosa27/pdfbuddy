package com.jesslabs.pdfbuddy.controller;

import com.jesslabs.pdfbuddy.models.ChatRequest;
import com.jesslabs.pdfbuddy.models.ChatResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
public class ChatController {

    private String documentText = "";

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        try (InputStream pdfStream = file.getInputStream()) {
            documentText = extractTextFromPDF(pdfStream);
            return "PDF uploaded and processed successfully.";
        } catch (IOException e) {
            return "Error processing the PDF: " + e.getMessage();
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest chatRequest) {
        String query = chatRequest.getQuery();
        String apiKey = chatRequest.getApiKey();

        try {
            String gptResponse = getGPT3Response(query, documentText, apiKey);
            return ResponseEntity.ok(new ChatResponse(gptResponse));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                // Handle 429 Too Many Requests
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("You've exceeded your current quota. Please check your plan and billing details.");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // Handle 401 Unauthorized (Invalid API Key)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid API key. Please check your key and try again.");
            } else {
                // Handle other errors
                return ResponseEntity.status(e.getStatusCode())
                        .body("An error occurred: " + e.getMessage());
            }
        }
    }

    private String extractTextFromPDF(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        }
    }

    private String getGPT3Response(String query, String documentText, String apiKey) {
        String prompt = "Based on the following document content, answer the query: " +
                documentText + "\n\nQuery: " + query;
        return sendRequestToGPT3(prompt, apiKey);
    }

    private String sendRequestToGPT3(String prompt, String apiKey) {
        String url = "https://api.openai.com/v1/chat/completions";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        JSONObject request = new JSONObject();
        request.put("model", "gpt-3.5-turbo");
        request.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", "You are a helpful assistant."))
                .put(new JSONObject().put("role", "user").put("content", prompt))
        );

        HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        JSONObject jsonResponse = new JSONObject(response.getBody());
        return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}