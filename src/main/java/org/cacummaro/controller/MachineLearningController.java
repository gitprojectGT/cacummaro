package org.cacummaro.controller;

import org.cacummaro.domain.Document;
import org.cacummaro.repository.DocumentRepository;
import org.cacummaro.service.classification.MachineLearningClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Machine Learning classifier operations
 */
@RestController
@RequestMapping("/api/v1/ml")
public class MachineLearningController {

    private static final Logger logger = LoggerFactory.getLogger(MachineLearningController.class);

    private final MachineLearningClassifier mlClassifier;
    private final DocumentRepository documentRepository;

    @Autowired
    public MachineLearningController(
            MachineLearningClassifier mlClassifier,
            DocumentRepository documentRepository) {
        this.mlClassifier = mlClassifier;
        this.documentRepository = documentRepository;
    }

    /**
     * Train the ML model on existing categorized documents
     *
     * GET /api/v1/ml/train
     *
     * @param maxDocuments Maximum number of documents to use for training (optional)
     * @return Training result with status and metrics
     */
    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> trainModel(
            @RequestParam(defaultValue = "1000") int maxDocuments) {

        if (!mlClassifier.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(createResponse(false, "ML Classifier is disabled. Set cacummaro.classification.ml.enabled=true", null));
        }

        try {
            logger.info("Starting ML model training with max {} documents", maxDocuments);

            // Fetch categorized documents from repository
            List<Document> allDocuments = documentRepository.findAll(PageRequest.of(0, maxDocuments)).getContent();

            // Filter documents that have categories
            List<Document> categorizedDocs = allDocuments.stream()
                    .filter(doc -> doc.getCategories() != null && !doc.getCategories().isEmpty())
                    .filter(doc -> doc.getPdfAttachmentName() != null)
                    .collect(Collectors.toList());

            if (categorizedDocs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createResponse(false, "No categorized documents found for training", null));
            }

            logger.info("Found {} categorized documents for training", categorizedDocs.size());

            // Train the model
            mlClassifier.train(categorizedDocs);

            // Prepare response
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("documentsProcessed", categorizedDocs.size());
            metrics.put("categoriesLearned", mlClassifier.getCategories().size());
            metrics.put("categories", mlClassifier.getCategories());

            logger.info("ML model training completed successfully");

            return ResponseEntity.ok(createResponse(true, "Model trained successfully", metrics));

        } catch (Exception e) {
            logger.error("ML model training failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createResponse(false, "Training failed: " + e.getMessage(), null));
        }
    }

    /**
     * Get ML classifier status
     *
     * GET /api/v1/ml/status
     *
     * @return ML classifier status and information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", mlClassifier.isEnabled());
        status.put("trained", mlClassifier.isModelTrained());
        status.put("classifierName", mlClassifier.getClassifierName());
        status.put("classifierVersion", mlClassifier.getClassifierVersion());

        if (mlClassifier.isModelTrained()) {
            status.put("categories", mlClassifier.getCategories());
            status.put("categoryCount", mlClassifier.getCategories().size());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Check if ML classifier is ready to use
     *
     * GET /api/v1/ml/ready
     *
     * @return Readiness status
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> checkReady() {
        boolean ready = mlClassifier.isEnabled() && mlClassifier.isModelTrained();

        Map<String, Object> response = new HashMap<>();
        response.put("ready", ready);
        response.put("enabled", mlClassifier.isEnabled());
        response.put("trained", mlClassifier.isModelTrained());

        if (ready) {
            response.put("message", "ML Classifier is ready");
            return ResponseEntity.ok(response);
        } else if (!mlClassifier.isEnabled()) {
            response.put("message", "ML Classifier is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        } else {
            response.put("message", "ML Classifier needs training");
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(response);
        }
    }

    private Map<String, Object> createResponse(boolean success, String message, Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }
}