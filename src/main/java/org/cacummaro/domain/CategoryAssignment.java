package org.cacummaro.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CategoryAssignment {

    @JsonProperty("name")
    private String name;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("classifier")
    private String classifier;

    public CategoryAssignment() {}

    public CategoryAssignment(String name, Double confidence, String classifier) {
        this.name = name;
        this.confidence = confidence;
        this.classifier = classifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }
}