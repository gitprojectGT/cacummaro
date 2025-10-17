package org.cacummaro.domain;

public enum DocumentCategory {
    TECHNOLOGY("Technology", "Software, programming, AI, development, tech news"),
    NEWSPAPER_ARTICLE("Newspaper Article", "News articles, journalism, current events"),
    TECHNOLOGY_UPDATE("Technology Update", "Tech announcements, product releases, updates"),
    SIMPLE_ARTICLE("Simple Article", "General articles, blogs, informational content"),
    UNKNOWN("Unknown", "Could not determine category");

    private final String displayName;
    private final String description;

    DocumentCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static DocumentCategory fromString(String category) {
        if (category == null) return UNKNOWN;

        for (DocumentCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(category) ||
                cat.displayName.equalsIgnoreCase(category)) {
                return cat;
            }
        }
        return UNKNOWN;
    }
}