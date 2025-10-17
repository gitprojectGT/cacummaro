package org.cacummaro.service;

import org.cacummaro.domain.Document;
import org.cacummaro.domain.DocumentCategory;
import org.cacummaro.domain.CategoryAssignment;
import org.cacummaro.service.classification.MachineLearningClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EnhancedClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedClassificationService.class);
    private static final Map<DocumentCategory, List<Pattern>> CATEGORY_PATTERNS = new HashMap<>();

    private final MachineLearningClassifier mlClassifier;

    @Autowired
    public EnhancedClassificationService(MachineLearningClassifier mlClassifier) {
        this.mlClassifier = mlClassifier;
    }

    static {
        // Technology patterns
        CATEGORY_PATTERNS.put(DocumentCategory.TECHNOLOGY, Arrays.asList(
            Pattern.compile("\\b(software|programming|developer|coding|algorithm|javascript|python|java|react|angular|vue|node\\.js|api|database|cloud|aws|azure|docker|kubernetes|artificial intelligence|machine learning|AI|ML|blockchain|cryptocurrency|bitcoin|ethereum)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(github|stackoverflow|tech|technology|framework|library|open source|backend|frontend|fullstack|devops|ci/cd|microservices|rest|graphql)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // Newspaper Article patterns
        CATEGORY_PATTERNS.put(DocumentCategory.NEWSPAPER_ARTICLE, Arrays.asList(
            Pattern.compile("\\b(breaking|news|reported|journalist|correspondent|reuters|associated press|CNN|BBC|times|post|herald|tribune|gazette|today|daily)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(politics|government|election|president|minister|congress|parliament|senate|house|policy|legislation|investigation)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(according to sources|officials said|statement|press conference|interview|exclusive|developing story)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // Technology Update patterns
        CATEGORY_PATTERNS.put(DocumentCategory.TECHNOLOGY_UPDATE, Arrays.asList(
            Pattern.compile("\\b(update|upgrade|release|version|patch|changelog|new features|announcement|launched|beta|alpha|rollout)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(apple|google|microsoft|facebook|meta|amazon|tesla|spotify|netflix|uber|twitter|instagram|tiktok|whatsapp)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(iOS|android|windows|mac|chrome|firefox|safari|edge|app store|play store|product launch|keynote)\\b", Pattern.CASE_INSENSITIVE)
        ));

        // Simple Article patterns
        CATEGORY_PATTERNS.put(DocumentCategory.SIMPLE_ARTICLE, Arrays.asList(
            Pattern.compile("\\b(how to|guide|tutorial|tips|advice|learn|beginner|step by step|introduction|overview|basics)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(blog|article|post|content|writing|author|published|lifestyle|health|travel|food|culture|sports|entertainment)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(opinion|review|analysis|commentary|thoughts|perspective|experience|personal|story|essay)\\b", Pattern.CASE_INSENSITIVE)
        ));
    }

    public List<CategoryAssignment> classifyDocument(Document document) {
        List<CategoryAssignment> allAssignments = new ArrayList<>();

        // Try ML classifier first if enabled and trained
        if (mlClassifier.isEnabled() && mlClassifier.isModelTrained()) {
            logger.debug("Using ML classifier for document {}", document.getId());
            List<CategoryAssignment> mlAssignments = mlClassifier.classify(document);
            if (!mlAssignments.isEmpty()) {
                logger.debug("ML classifier returned {} assignments", mlAssignments.size());
                allAssignments.addAll(mlAssignments);
            }
        }

        // Fallback to rule-based classification
        String content = buildContentString(document);
        Map<DocumentCategory, Double> scores = new HashMap<>();

        for (DocumentCategory category : DocumentCategory.values()) {
            if (category == DocumentCategory.UNKNOWN) continue;

            List<Pattern> patterns = CATEGORY_PATTERNS.get(category);
            if (patterns != null) {
                double score = calculateCategoryScore(content, patterns);
                scores.put(category, score);
            }
        }

        // Find the category with highest score
        DocumentCategory bestCategory = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(DocumentCategory.UNKNOWN);

        double bestScore = scores.getOrDefault(bestCategory, 0.0);

        // If no significant match found, classify as unknown
        if (bestScore < 0.1) {
            bestCategory = DocumentCategory.UNKNOWN;
            bestScore = 1.0;
        }

        CategoryAssignment ruleBasedAssignment = new CategoryAssignment();
        ruleBasedAssignment.setName(bestCategory.getDisplayName());
        ruleBasedAssignment.setConfidence(Math.min(bestScore, 1.0));
        ruleBasedAssignment.setClassifier("enhanced-rule-based-v1.0");

        allAssignments.add(ruleBasedAssignment);

        // Merge and deduplicate assignments by category name
        Map<String, CategoryAssignment> mergedAssignments = new HashMap<>();
        for (CategoryAssignment assignment : allAssignments) {
            String categoryName = assignment.getName();
            if (!mergedAssignments.containsKey(categoryName)) {
                mergedAssignments.put(categoryName, assignment);
            } else {
                // Keep the assignment with higher confidence
                CategoryAssignment existing = mergedAssignments.get(categoryName);
                if (assignment.getConfidence() > existing.getConfidence()) {
                    mergedAssignments.put(categoryName, assignment);
                }
            }
        }

        List<CategoryAssignment> finalAssignments = new ArrayList<>(mergedAssignments.values());
        finalAssignments.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        logger.debug("Final classification for document {}: {} categories", document.getId(), finalAssignments.size());
        return finalAssignments;
    }

    private String buildContentString(Document document) {
        StringBuilder content = new StringBuilder();

        if (document.getTitle() != null) {
            content.append(document.getTitle()).append(" ");
        }

        if (document.getDescription() != null) {
            content.append(document.getDescription()).append(" ");
        }

        if (document.getMetaTags() != null) {
            document.getMetaTags().values().forEach(value ->
                content.append(value).append(" "));
        }

        if (document.getUrl() != null) {
            content.append(document.getUrl()).append(" ");
        }

        return content.toString().toLowerCase();
    }

    private double calculateCategoryScore(String content, List<Pattern> patterns) {
        int totalMatches = 0;
        int totalWords = content.split("\\s+").length;

        for (Pattern pattern : patterns) {
            long matches = pattern.matcher(content).results().count();
            totalMatches += matches;
        }

        // Score based on match density and absolute match count
        double density = (double) totalMatches / Math.max(totalWords, 1);
        double absoluteScore = Math.min(totalMatches * 0.1, 1.0);

        return Math.max(density * 2, absoluteScore);
    }

    public DocumentCategory getCategoryEnum(String categoryName) {
        return DocumentCategory.fromString(categoryName);
    }

    public List<DocumentCategory> getAllCategories() {
        return Arrays.stream(DocumentCategory.values())
            .filter(cat -> cat != DocumentCategory.UNKNOWN)
            .collect(Collectors.toList());
    }
}