package org.cacummaro.service.classification;

import org.cacummaro.domain.CategoryAssignment;
import org.cacummaro.domain.Document;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@ConfigurationProperties(prefix = "cacummaro.classification.rule-based")
public class RuleBasedClassifier implements Classifier {

    private static final String CLASSIFIER_NAME = "rule-based";
    private static final String CLASSIFIER_VERSION = "v1.0";

    private Map<String, List<String>> categoryKeywords = new HashMap<>();
    private Map<String, List<String>> categoryDomains = new HashMap<>();
    private double confidenceThreshold = 0.7;

    public RuleBasedClassifier() {
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        // Finance keywords
        categoryKeywords.put("finance", Arrays.asList(
                "bank", "banking", "finance", "financial", "investment", "money",
                "loan", "credit", "debt", "mortgage", "insurance", "trading",
                "stocks", "portfolio", "crypto", "cryptocurrency", "bitcoin"
        ));

        // Technology keywords
        categoryKeywords.put("technology", Arrays.asList(
                "technology", "tech", "software", "programming", "development",
                "coding", "computer", "ai", "artificial intelligence", "machine learning",
                "cloud", "api", "database", "framework", "javascript", "python", "java"
        ));

        // News keywords
        categoryKeywords.put("news", Arrays.asList(
                "news", "breaking", "report", "article", "journalism", "politics",
                "government", "election", "policy", "current events"
        ));

        // Science keywords
        categoryKeywords.put("science", Arrays.asList(
                "science", "research", "study", "experiment", "biology", "chemistry",
                "physics", "medicine", "health", "medical", "scientific"
        ));

        // Business keywords
        categoryKeywords.put("business", Arrays.asList(
                "business", "company", "corporate", "enterprise", "startup",
                "entrepreneur", "management", "strategy", "marketing", "sales"
        ));

        // Domain-based classification
        categoryDomains.put("news", Arrays.asList(
                "cnn.com", "bbc.com", "reuters.com", "ap.org", "nytimes.com",
                "washingtonpost.com", "theguardian.com"
        ));

        categoryDomains.put("technology", Arrays.asList(
                "github.com", "stackoverflow.com", "techcrunch.com", "wired.com",
                "ars-technica.com", "theverge.com", "hacker-news.com"
        ));

        categoryDomains.put("finance", Arrays.asList(
                "bloomberg.com", "cnbc.com", "marketwatch.com", "wsj.com",
                "investing.com", "yahoo.com/finance"
        ));
    }

    @Override
    public List<CategoryAssignment> classify(Document document) {
        List<CategoryAssignment> assignments = new ArrayList<>();

        // Combine all text for analysis
        String combinedText = buildCombinedText(document).toLowerCase();
        String domain = extractDomain(document.getUrl());

        for (String category : categoryKeywords.keySet()) {
            double confidence = calculateConfidence(category, combinedText, domain);

            if (confidence >= confidenceThreshold) {
                assignments.add(new CategoryAssignment(
                        category,
                        confidence,
                        getClassifierName() + "-" + getClassifierVersion()
                ));
            }
        }

        // Sort by confidence descending
        assignments.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        return assignments;
    }

    private String buildCombinedText(Document document) {
        StringBuilder text = new StringBuilder();

        if (document.getTitle() != null) {
            text.append(document.getTitle()).append(" ");
        }

        if (document.getDescription() != null) {
            text.append(document.getDescription()).append(" ");
        }

        if (document.getUrl() != null) {
            text.append(document.getUrl()).append(" ");
        }

        if (document.getMetaTags() != null) {
            document.getMetaTags().values().forEach(value ->
                text.append(value).append(" "));
        }

        return text.toString();
    }

    private String extractDomain(String url) {
        if (url == null) return "";

        try {
            // Simple domain extraction
            String domain = url.replaceAll("^https?://", "")
                               .replaceAll("^www\\.", "")
                               .replaceAll("/.*$", "");
            return domain.toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    private double calculateConfidence(String category, String text, String domain) {
        double keywordScore = 0.0;
        double domainScore = 0.0;

        // Check keywords
        List<String> keywords = categoryKeywords.get(category);
        if (keywords != null) {
            int matches = 0;
            for (String keyword : keywords) {
                if (text.contains(keyword.toLowerCase())) {
                    matches++;
                }
            }
            keywordScore = (double) matches / keywords.size();
        }

        // Check domain
        List<String> domains = categoryDomains.get(category);
        if (domains != null && !domain.isEmpty()) {
            for (String categoryDomain : domains) {
                if (domain.contains(categoryDomain.toLowerCase())) {
                    domainScore = 1.0;
                    break;
                }
            }
        }

        // Weighted combination: 70% keywords, 30% domain
        return (keywordScore * 0.7) + (domainScore * 0.3);
    }

    @Override
    public String getClassifierName() {
        return CLASSIFIER_NAME;
    }

    @Override
    public String getClassifierVersion() {
        return CLASSIFIER_VERSION;
    }

    // Configuration properties getters and setters
    public Map<String, List<String>> getCategoryKeywords() {
        return categoryKeywords;
    }

    public void setCategoryKeywords(Map<String, List<String>> categoryKeywords) {
        this.categoryKeywords = categoryKeywords;
    }

    public Map<String, List<String>> getCategoryDomains() {
        return categoryDomains;
    }

    public void setCategoryDomains(Map<String, List<String>> categoryDomains) {
        this.categoryDomains = categoryDomains;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
}