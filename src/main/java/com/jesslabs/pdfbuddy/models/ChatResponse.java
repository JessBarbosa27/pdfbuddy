package com.jesslabs.pdfbuddy.models;

public class ChatResponse {
    private String answer;

    // Constructor
    public ChatResponse(String answer) {
        this.answer = answer;
    }

    // Getter
    public String getAnswer() {
        return answer;
    }
}