package org.cacummaro.domain;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

public class ProcessingStatus {
    private String documentId;
    private ProcessingStep currentStep;
    private List<ProcessingStepResult> completedSteps;
    private boolean completed;
    private boolean failed;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;

    public ProcessingStatus() {
        this.completedSteps = new ArrayList<>();
        this.startedAt = Instant.now();
        this.completed = false;
        this.failed = false;
    }

    public ProcessingStatus(String documentId) {
        this();
        this.documentId = documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public ProcessingStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(ProcessingStep currentStep) {
        this.currentStep = currentStep;
    }

    public List<ProcessingStepResult> getCompletedSteps() {
        return completedSteps;
    }

    public void addCompletedStep(ProcessingStepResult stepResult) {
        this.completedSteps.add(stepResult);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed) {
            this.completedAt = Instant.now();
        }
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
        if (failed) {
            this.completedAt = Instant.now();
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public static class ProcessingStepResult {
        private ProcessingStep step;
        private boolean success;
        private String message;
        private Instant completedAt;
        private Object data; // For storing step-specific data

        public ProcessingStepResult() {
            this.completedAt = Instant.now();
        }

        public ProcessingStepResult(ProcessingStep step, boolean success, String message) {
            this();
            this.step = step;
            this.success = success;
            this.message = message;
        }

        // Getters and setters
        public ProcessingStep getStep() { return step; }
        public void setStep(ProcessingStep step) { this.step = step; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
}