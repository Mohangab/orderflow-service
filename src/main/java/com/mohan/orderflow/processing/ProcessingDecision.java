package com.mohan.orderflow.processing;

public record ProcessingDecision(boolean approved, String reason) {

    public static ProcessingDecision approve() {
        return new ProcessingDecision(true, null);
    }

    public static ProcessingDecision reject(String reason) {
        return new ProcessingDecision(false, reason);
    }
}
