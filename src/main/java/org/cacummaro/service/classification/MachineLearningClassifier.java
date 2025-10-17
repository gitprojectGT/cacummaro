package org.cacummaro.service.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cacummaro.domain.CategoryAssignment;
import org.cacummaro.domain.Document;
import org.cacummaro.repository.DocumentRepository;
import org.cacummaro.service.mcp.McpClientService;
import org.cacummaro.service.mcp.McpClassificationResult;
import org.cacummaro.service.pdf.PdfTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Machine Learning Classifier using TF-IDF and Cosine Similarity
 *
 * This classifier extracts text from PDFs and uses TF-IDF (Term Frequency-Inverse Document Frequency)
 * vectorization with cosine similarity to classify documents into categories.
 */
@Service
public class MachineLearningClassifier implements Classifier {

    private static final Logger logger = LoggerFactory.getLogger(MachineLearningClassifier.class);
    private static final String CLASSIFIER_NAME = "ml-tfidf";
    private static final String CLASSIFIER_VERSION = "v1.0";

    @Value("${cacummaro.classification.ml.enabled:false}")
    private boolean enabled;

    @Value("${cacummaro.classification.ml.confidence-threshold:0.6}")
    private double confidenceThreshold;

    @Value("${cacummaro.classification.ml.model-path:./ml-model.json}")
    private String modelPath;

    @Value("${cacummaro.classification.ml.min-document-frequency:2}")
    private int minDocumentFrequency;

    @Value("${cacummaro.classification.ml.max-features:1000}")
    private int maxFeatures;

    private final PdfTextExtractor textExtractor;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final McpClientService mcpClientService;

    // TF-IDF model components
    private Map<String, Integer> vocabulary; // word -> index
    private Map<String, Double> inverseDocumentFrequency; // word -> IDF score
    private Map<String, Map<String, Double>> categoryVectors; // category -> (word -> TF-IDF)
    private Set<String> stopWords;
    private boolean modelTrained = false;

    @Autowired
    public MachineLearningClassifier(
            PdfTextExtractor textExtractor,
            DocumentRepository documentRepository,
            ObjectMapper objectMapper,
            McpClientService mcpClientService) {
        this.textExtractor = textExtractor;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.mcpClientService = mcpClientService;
        this.vocabulary = new HashMap<>();
        this.inverseDocumentFrequency = new HashMap<>();
        this.categoryVectors = new HashMap<>();
        this.stopWords = initializeStopWords();
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            logger.info("ML Classifier is disabled. Set cacummaro.classification.ml.enabled=true to enable");
            return;
        }

        // Try to load existing model
        try {
            loadModel();
            logger.info("ML Classifier model loaded successfully from {}", modelPath);
        } catch (IOException e) {
            logger.warn("No existing ML model found at {}. Model will need to be trained.", modelPath);
        }
    }

    @Override
    public List<CategoryAssignment> classify(Document document) {
        if (!enabled) {
            logger.debug("ML Classifier is disabled, returning empty classification");
            return Collections.emptyList();
        }

        if (!modelTrained) {
            logger.warn("ML model not trained yet, returning empty classification");
            return Collections.emptyList();
        }

        try {
            // Extract text from PDF
            byte[] pdfData = documentRepository.getAttachment(document.getId(), document.getPdfAttachmentName());
            String pdfText = textExtractor.extractText(pdfData);

            // Combine metadata and PDF text
            String fullText = buildFullText(document, pdfText);

            List<CategoryAssignment> allAssignments = new ArrayList<>();

            // Try MCP classifier first if enabled
            if (mcpClientService.isEnabled()) {
                try {
                    logger.debug("Calling MCP server for document classification: {}", document.getId());
                    McpClassificationResult mcpResult = mcpClientService.classifyDocument(
                        document.getId(),
                        document.getTitle(),
                        document.getDescription(),
                        fullText
                    );

                    // Convert MCP results to CategoryAssignments
                    if (mcpResult.getCategories() != null && !mcpResult.getCategories().isEmpty()) {
                        for (McpClassificationResult.CategoryPrediction prediction : mcpResult.getCategories()) {
                            CategoryAssignment assignment = new CategoryAssignment(
                                prediction.getName(),
                                prediction.getConfidence(),
                                "mcp-" + (mcpResult.getModel() != null ? mcpResult.getModel() : "ai")
                            );
                            allAssignments.add(assignment);
                        }
                        logger.info("MCP classification returned {} categories for document {}",
                                   allAssignments.size(), document.getId());
                    }
                } catch (Exception e) {
                    logger.warn("MCP classification failed, falling back to TF-IDF: {}", e.getMessage());
                }
            }

            // Run TF-IDF classification (always as backup or enhancement)
            Map<String, Double> documentVector = computeTfIdfVector(fullText);

            // Calculate similarity with each category
            Map<String, Double> categoryScores = new HashMap<>();
            for (Map.Entry<String, Map<String, Double>> entry : categoryVectors.entrySet()) {
                String category = entry.getKey();
                Map<String, Double> categoryVector = entry.getValue();
                double similarity = cosineSimilarity(documentVector, categoryVector);
                categoryScores.put(category, similarity);
            }

            // Filter by confidence threshold and create assignments
            List<CategoryAssignment> tfidfAssignments = categoryScores.entrySet().stream()
                    .filter(e -> e.getValue() >= confidenceThreshold)
                    .map(e -> new CategoryAssignment(
                            e.getKey(),
                            e.getValue(),
                            getClassifierName() + "-" + getClassifierVersion()
                    ))
                    .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                    .collect(Collectors.toList());

            allAssignments.addAll(tfidfAssignments);

            // Merge and deduplicate by category name (keep highest confidence)
            Map<String, CategoryAssignment> mergedAssignments = new HashMap<>();
            for (CategoryAssignment assignment : allAssignments) {
                String categoryName = assignment.getName();
                if (!mergedAssignments.containsKey(categoryName)) {
                    mergedAssignments.put(categoryName, assignment);
                } else {
                    CategoryAssignment existing = mergedAssignments.get(categoryName);
                    if (assignment.getConfidence() > existing.getConfidence()) {
                        mergedAssignments.put(categoryName, assignment);
                    }
                }
            }

            List<CategoryAssignment> finalAssignments = new ArrayList<>(mergedAssignments.values());
            finalAssignments.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

            logger.debug("ML Classification result for {}: {} categories (MCP: {}, TF-IDF: {})",
                        document.getId(), finalAssignments.size(),
                        allAssignments.size() - tfidfAssignments.size(), tfidfAssignments.size());
            return finalAssignments;

        } catch (Exception e) {
            logger.error("ML Classification failed for document {}: {}", document.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Train the ML model on existing documents
     *
     * @param trainingDocuments Documents with known categories for training
     * @throws IOException if training fails
     */
    public void train(List<Document> trainingDocuments) throws IOException {
        logger.info("Starting ML model training with {} documents", trainingDocuments.size());

        // Group documents by category
        Map<String, List<String>> categoryTexts = new HashMap<>();

        for (Document doc : trainingDocuments) {
            if (doc.getCategories() == null || doc.getCategories().isEmpty()) {
                continue;
            }

            try {
                // Get the primary category
                String category = doc.getCategories().get(0).getName();

                // Extract text from PDF
                byte[] pdfData = documentRepository.getAttachment(doc.getId(), doc.getPdfAttachmentName());
                String pdfText = textExtractor.extractText(pdfData);
                String fullText = buildFullText(doc, pdfText);

                categoryTexts.computeIfAbsent(category, k -> new ArrayList<>()).add(fullText);

            } catch (Exception e) {
                logger.warn("Failed to process document {} for training: {}", doc.getId(), e.getMessage());
            }
        }

        if (categoryTexts.isEmpty()) {
            throw new IOException("No valid training documents found");
        }

        logger.info("Training on {} categories: {}", categoryTexts.size(), categoryTexts.keySet());

        // Build vocabulary from all documents
        buildVocabulary(categoryTexts);

        // Calculate IDF scores
        calculateIDF(categoryTexts);

        // Create TF-IDF vectors for each category
        for (Map.Entry<String, List<String>> entry : categoryTexts.entrySet()) {
            String category = entry.getKey();
            List<String> texts = entry.getValue();

            // Combine all texts for the category
            String categoryText = String.join(" ", texts);
            Map<String, Double> categoryVector = computeTfIdfVector(categoryText);

            categoryVectors.put(category, categoryVector);
            logger.debug("Created TF-IDF vector for category: {} ({} features)", category, categoryVector.size());
        }

        modelTrained = true;
        logger.info("ML model training completed successfully");

        // Save the model
        saveModel();
    }

    private void buildVocabulary(Map<String, List<String>> categoryTexts) {
        Map<String, Integer> wordFrequency = new HashMap<>();

        // Count document frequency for each word
        for (List<String> texts : categoryTexts.values()) {
            for (String text : texts) {
                Set<String> uniqueWords = new HashSet<>(tokenize(text));
                for (String word : uniqueWords) {
                    wordFrequency.merge(word, 1, Integer::sum);
                }
            }
        }

        // Filter words by document frequency and limit vocabulary size
        vocabulary = wordFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= minDocumentFrequency)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(maxFeatures)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> vocabulary.size(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        logger.info("Built vocabulary with {} terms (min freq: {}, max features: {})",
                   vocabulary.size(), minDocumentFrequency, maxFeatures);
    }

    private void calculateIDF(Map<String, List<String>> categoryTexts) {
        int totalDocuments = categoryTexts.values().stream().mapToInt(List::size).sum();

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> texts : categoryTexts.values()) {
            for (String text : texts) {
                Set<String> uniqueWords = new HashSet<>(tokenize(text));
                for (String word : uniqueWords) {
                    if (vocabulary.containsKey(word)) {
                        documentFrequency.merge(word, 1, Integer::sum);
                    }
                }
            }
        }

        // Calculate IDF = log(total_documents / document_frequency)
        inverseDocumentFrequency = documentFrequency.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Math.log((double) totalDocuments / e.getValue())
                ));

        logger.debug("Calculated IDF scores for {} terms", inverseDocumentFrequency.size());
    }

    private Map<String, Double> computeTfIdfVector(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Integer> termFrequency = new HashMap<>();

        // Count term frequencies
        for (String token : tokens) {
            if (vocabulary.containsKey(token)) {
                termFrequency.merge(token, 1, Integer::sum);
            }
        }

        // Calculate TF-IDF
        Map<String, Double> tfidfVector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            double idf = inverseDocumentFrequency.getOrDefault(term, 0.0);
            double tfidf = tf * idf;
            tfidfVector.put(term, tfidf);
        }

        // Normalize the vector
        double magnitude = Math.sqrt(tfidfVector.values().stream()
                .mapToDouble(v -> v * v)
                .sum());

        if (magnitude > 0) {
            tfidfVector.replaceAll((k, v) -> v / magnitude);
        }

        return tfidfVector;
    }

    private double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        double dotProduct = 0.0;

        for (Map.Entry<String, Double> entry : vector1.entrySet()) {
            String term = entry.getKey();
            if (vector2.containsKey(term)) {
                dotProduct += entry.getValue() * vector2.get(term);
            }
        }

        return dotProduct; // Vectors are already normalized
    }

    private List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Convert to lowercase and split on non-word characters
        return Arrays.stream(text.toLowerCase()
                        .split("[\\W_]+"))
                .filter(word -> word.length() > 2) // Minimum word length
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toList());
    }

    private String buildFullText(Document document, String pdfText) {
        StringBuilder fullText = new StringBuilder();

        if (document.getTitle() != null) {
            fullText.append(document.getTitle()).append(" ");
        }

        if (document.getDescription() != null) {
            fullText.append(document.getDescription()).append(" ");
        }

        if (document.getMetaTags() != null) {
            document.getMetaTags().values().forEach(value ->
                    fullText.append(value).append(" "));
        }

        if (pdfText != null && !pdfText.isEmpty()) {
            fullText.append(pdfText);
        }

        return fullText.toString();
    }

    private Set<String> initializeStopWords() {
        return new HashSet<>(Arrays.asList(
                "the", "and", "for", "are", "but", "not", "you", "all", "can", "her",
                "was", "one", "our", "out", "day", "get", "has", "him", "his", "how",
                "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did",
                "its", "let", "put", "say", "she", "too", "use", "any", "may", "with",
                "this", "that", "from", "they", "were", "been", "have", "what", "your"
        ));
    }

    private void saveModel() throws IOException {
        ModelData modelData = new ModelData();
        modelData.vocabulary = vocabulary;
        modelData.inverseDocumentFrequency = inverseDocumentFrequency;
        modelData.categoryVectors = categoryVectors;
        modelData.trained = modelTrained;

        File file = new File(modelPath);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, modelData);
        logger.info("ML model saved to {}", modelPath);
    }

    private void loadModel() throws IOException {
        File file = new File(modelPath);
        if (!file.exists()) {
            throw new IOException("Model file not found: " + modelPath);
        }

        ModelData modelData = objectMapper.readValue(file, ModelData.class);
        this.vocabulary = modelData.vocabulary;
        this.inverseDocumentFrequency = modelData.inverseDocumentFrequency;
        this.categoryVectors = modelData.categoryVectors;
        this.modelTrained = modelData.trained;

        logger.debug("Loaded model: {} categories, {} vocabulary terms",
                    categoryVectors.size(), vocabulary.size());
    }

    @Override
    public String getClassifierName() {
        return CLASSIFIER_NAME;
    }

    @Override
    public String getClassifierVersion() {
        return CLASSIFIER_VERSION;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isModelTrained() {
        return modelTrained;
    }

    public Set<String> getCategories() {
        return new HashSet<>(categoryVectors.keySet());
    }

    // Inner class for model persistence
    private static class ModelData {
        public Map<String, Integer> vocabulary;
        public Map<String, Double> inverseDocumentFrequency;
        public Map<String, Map<String, Double>> categoryVectors;
        public boolean trained;
    }
}