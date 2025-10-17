package org.cacummaro.domain;

public enum ProcessingStep {
    URL_VERIFICATION("Verifying URL accessibility", "🔍"),
    PDF_CONVERSION("Converting web page to PDF", "📄"),
    STORAGE("Storing in CouchDB with UUID", "💾"),
    CONTENT_ANALYSIS("Analyzing PDF content", "🤖"),
    CATEGORIZATION("Categorizing document", "🏷️"),
    COMPLETED("Processing completed", "✅");

    private final String description;
    private final String icon;

    ProcessingStep(String description, String icon) {
        this.description = description;
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return icon + " " + description;
    }
}