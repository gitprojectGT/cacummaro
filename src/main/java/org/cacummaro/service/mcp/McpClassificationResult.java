package org.cacummaro.service.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MCP Classification Result
 */
public class McpClassificationResult {

    @JsonProperty("categories")
    private List<CategoryPrediction> categories;

    @JsonProperty("model")
    private String model;

    @JsonProperty("confidence")
    private double confidence;

    public McpClassificationResult() {
    }

    public List<CategoryPrediction> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryPrediction> categories) {
        this.categories = categories;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public static class CategoryPrediction {
        @JsonProperty("name")
        private String name;

        @JsonProperty("confidence")
        private double confidence;

        @JsonProperty("reasoning")
        private String reasoning;

        public CategoryPrediction() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }
    }
}