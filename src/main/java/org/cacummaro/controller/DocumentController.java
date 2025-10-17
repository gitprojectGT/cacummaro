package org.cacummaro.controller;

import org.cacummaro.domain.Document;
import org.cacummaro.domain.ProcessingStatus;
import org.cacummaro.dto.IngestRequest;
import org.cacummaro.dto.IngestResponse;
import org.cacummaro.repository.CategoryRepository;
import org.cacummaro.service.DocumentService;
import org.cacummaro.service.DocumentServiceException;
import org.cacummaro.service.EnhancedDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 1000;

    private final DocumentService documentService;
    private final EnhancedDocumentService enhancedDocumentService;
    private final CategoryRepository categoryRepository;

    @Autowired
    public DocumentController(DocumentService documentService, CategoryRepository categoryRepository) {
        this.documentService = documentService;
        this.categoryRepository = categoryRepository;
        // Cast to get access to enhanced features
        this.enhancedDocumentService = (EnhancedDocumentService) documentService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestUrl(@Valid @RequestBody IngestRequest request) {
        try {
            IngestResponse response = documentService.ingestUrl(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DocumentServiceException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<Document>> getAllDocuments(Pageable pageable) {
        Page<Document> documents = documentService.getAllDocuments(pageable);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocument(@PathVariable String id) {
        Optional<Document> document = documentService.getDocument(id);
        if (document.isPresent()) {
            return ResponseEntity.ok(document.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/documents/{id}/pdf")
    public ResponseEntity<?> getDocumentPdf(@PathVariable String id) {
        try {
            Optional<Document> optionalDocument = documentService.getDocument(id);
            if (optionalDocument.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Document document = optionalDocument.get();
            byte[] pdfData = documentService.getDocumentPdf(id);

            // Use the stored PDF attachment name for the download filename
            String filename = document.getPdfAttachmentName();
            if (filename == null || filename.isEmpty()) {
                filename = "document.pdf";
            }

            // Sanitize filename to ensure browser compatibility
            // Remove or replace characters that can cause issues in Content-Disposition header
            String safeFilename = filename
                    .replaceAll("[^a-zA-Z0-9._-]", "_")  // Replace special chars with underscore
                    .replaceAll("_+", "_");               // Replace multiple underscores with single

            // Ensure the filename ends with .pdf
            if (!safeFilename.toLowerCase().endsWith(".pdf")) {
                safeFilename += ".pdf";
            }

            // Limit filename length to 100 characters
            if (safeFilename.length() > 100) {
                String extension = ".pdf";
                safeFilename = safeFilename.substring(0, 100 - extension.length()) + extension;
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + safeFilename + "\"")
                    .body(pdfData);
        } catch (DocumentServiceException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/documents/{id}/status")
    public ResponseEntity<?> getProcessingStatus(@PathVariable String id) {
        ProcessingStatus status = enhancedDocumentService.getProcessingStatus(id);
        if (status != null) {
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id) {
        try {
            documentService.deleteDocument(id);
            return ResponseEntity.noContent().build();
        } catch (DocumentServiceException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/documents/{id}/reclassify")
    public ResponseEntity<?> reclassifyDocument(@PathVariable String id) {
        try {
            Document document = documentService.reclassifyDocument(id);
            return ResponseEntity.ok(document);
        } catch (DocumentServiceException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getAllCategories() {
        try {
            List<org.cacummaro.domain.Category> categories = categoryRepository.findAll();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve categories: " + e.getMessage()));
        }
    }

    @GetMapping("/categories/{name}/documents")
    public ResponseEntity<Page<Document>> getDocumentsByCategory(
            @PathVariable String name, Pageable pageable) {
        Page<Document> documents = documentService.getDocumentsByCategory(name, pageable);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Document>> searchDocuments(
            @RequestParam String q, Pageable pageable) {
        Page<Document> documents = documentService.searchDocuments(q, pageable);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/admin/rebuild-categories")
    public ResponseEntity<?> rebuildCategories() {
        try {
            // Get all documents
            Page<Document> allDocs = documentService.getAllDocuments(org.springframework.data.domain.PageRequest.of(0, MAX_PAGE_SIZE));

            int categoriesCreated = 0;
            java.util.Set<String> categoryNames = new java.util.HashSet<>();

            // Collect all unique category names from documents
            for (Document doc : allDocs.getContent()) {
                if (doc.getCategories() != null) {
                    for (org.cacummaro.domain.CategoryAssignment assignment : doc.getCategories()) {
                        categoryNames.add(assignment.getName());
                    }
                }
            }

            // Create or update category entities
            for (String categoryName : categoryNames) {
                java.util.Optional<org.cacummaro.domain.Category> existing = categoryRepository.findByName(categoryName);

                if (existing.isEmpty()) {
                    // Count documents in this category
                    long count = allDocs.getContent().stream()
                        .filter(doc -> doc.getCategories() != null &&
                                doc.getCategories().stream()
                                    .anyMatch(cat -> cat.getName().equals(categoryName)))
                        .count();

                    // Create category
                    org.cacummaro.domain.Category category = new org.cacummaro.domain.Category();
                    category.setName(categoryName);
                    category.setDescription(getCategoryDescription(categoryName));
                    category.setCreatedAt(java.time.Instant.now().toString());
                    category.setDocumentCount(count);
                    categoryRepository.save(category);
                    categoriesCreated++;
                }
            }

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Categories rebuilt successfully");
            response.put("categoriesCreated", categoriesCreated);
            response.put("totalCategories", categoryNames.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to rebuild categories: " + e.getMessage()));
        }
    }

    private String getCategoryDescription(String categoryName) {
        switch (categoryName.toLowerCase()) {
            case "technology":
                return "Documents related to technology, programming, software development, and tech news";
            case "technology update":
                return "Latest technology updates, product launches, and software releases";
            case "newspaper article":
                return "News articles, breaking news, and current events";
            case "simple article":
                return "General articles, blog posts, tutorials, and guides";
            case "unknown":
                return "Uncategorized documents";
            default:
                return "Documents categorized as " + categoryName;
        }
    }

    // Error response class
    public static class ErrorResponse {
        private final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}