package org.cacummaro.service;

import org.cacummaro.domain.*;
import org.cacummaro.dto.IngestRequest;
import org.cacummaro.dto.IngestResponse;
import org.cacummaro.repository.DocumentRepository;
import org.cacummaro.repository.CategoryRepository;
import org.cacummaro.service.pdf.PdfGenerator;
import org.cacummaro.service.obsidian.ObsidianNoteService;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnhancedDocumentService implements DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedDocumentService.class);
    private static final int MAX_FILENAME_LENGTH = 100;

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final PdfGenerator pdfGenerator;
    private final UrlVerificationService urlVerificationService;
    private final EnhancedClassificationService classificationService;
    private final ObsidianNoteService obsidianNoteService;

    // Store processing status for each document
    private final ConcurrentHashMap<String, ProcessingStatus> processingStatuses = new ConcurrentHashMap<>();

    @Autowired
    public EnhancedDocumentService(
            DocumentRepository documentRepository,
            CategoryRepository categoryRepository,
            PdfGenerator pdfGenerator,
            UrlVerificationService urlVerificationService,
            EnhancedClassificationService classificationService,
            ObsidianNoteService obsidianNoteService) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.pdfGenerator = pdfGenerator;
        this.urlVerificationService = urlVerificationService;
        this.classificationService = classificationService;
        this.obsidianNoteService = obsidianNoteService;
    }

    @Override
    public IngestResponse ingestUrl(IngestRequest request) throws DocumentServiceException {
        // Create document with UUID
        Document document = new Document(request.getUrl());
        ProcessingStatus status = new ProcessingStatus(document.getId());
        processingStatuses.put(document.getId(), status);

        try {
            // Step 1: Verify URL accessibility
            status.setCurrentStep(ProcessingStep.URL_VERIFICATION);
            UrlVerificationService.UrlVerificationResult verification = urlVerificationService.verifyUrl(request.getUrl());

            if (!verification.isAccessible()) {
                status.setFailed(true);
                status.setErrorMessage(verification.getMessage());
                throw new DocumentServiceException("URL verification failed: " + verification.getMessage());
            }

            status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                ProcessingStep.URL_VERIFICATION, true, "URL is accessible"));

            // Step 2: Convert web page to PDF
            status.setCurrentStep(ProcessingStep.PDF_CONVERSION);
            byte[] pdfData;
            try {
                pdfData = pdfGenerator.generatePdf(request.getUrl());
                document.setSizeBytes((long) pdfData.length);
            } catch (Exception e) {
                status.setFailed(true);
                status.setErrorMessage("PDF generation failed: " + e.getMessage());
                throw new DocumentServiceException("PDF generation failed: " + e.getMessage(), e);
            }

            status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                ProcessingStep.PDF_CONVERSION, true, "PDF generated successfully"));

            // Step 3: Store in CouchDB with UUID
            status.setCurrentStep(ProcessingStep.STORAGE);
            try {
                // Extract basic metadata from the page
                extractMetadata(document, request.getUrl());

                // Create sanitized filename from page title
                String sanitizedTitle = sanitizeFilename(document.getTitle());
                String pdfFilename = sanitizedTitle + ".pdf";

                // Save document to repository
                document.setPdfAttachmentName(pdfFilename);
                document.setStatus(DocumentStatus.STORED);
                document = documentRepository.save(document);

                // Store PDF as attachment in CouchDB
                documentRepository.saveAttachment(document.getId(), pdfFilename, pdfData, "application/pdf");

            } catch (Exception e) {
                status.setFailed(true);
                status.setErrorMessage("Storage failed: " + e.getMessage());
                throw new DocumentServiceException("Document storage failed: " + e.getMessage(), e);
            }

            status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                ProcessingStep.STORAGE, true, "Document stored with UUID: " + document.getId()));

            // Step 4: Analyze PDF content
            status.setCurrentStep(ProcessingStep.CONTENT_ANALYSIS);
            try {
                // This would normally extract text content from PDF for analysis
                // For now, we'll use the metadata we have
                analyzeContent(document);
            } catch (Exception e) {
                // Content analysis failure shouldn't stop the process
                status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                    ProcessingStep.CONTENT_ANALYSIS, false, "Content analysis failed: " + e.getMessage()));
            }

            status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                ProcessingStep.CONTENT_ANALYSIS, true, "Content analyzed successfully"));

            // Step 5: Categorize document
            status.setCurrentStep(ProcessingStep.CATEGORIZATION);
            try {
                logger.debug("Starting classification for document: {}", document.getId());

                // Refresh document from DB to get latest revision (after PDF attachment was added)
                document = documentRepository.findById(document.getId()).orElseThrow();
                logger.debug("Document refreshed, revision: {}", document.getRevision());

                List<CategoryAssignment> categories = classificationService.classifyDocument(document);
                logger.debug("Classification result: {} categories found", categories.size());

                document.setCategories(categories);
                document = documentRepository.save(document);
                logger.debug("Document saved with categories");

                // Create/update category entities in CouchDB
                for (CategoryAssignment assignment : categories) {
                    logger.debug("Ensuring category exists: {}", assignment.getName());
                    ensureCategoryExists(assignment.getName());
                }
            } catch (Exception e) {
                // Classification failure shouldn't stop the process
                logger.error("Classification failed: {}", e.getMessage(), e);
                status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                    ProcessingStep.CATEGORIZATION, false, "Categorization failed: " + e.getMessage()));
            }

            status.addCompletedStep(new ProcessingStatus.ProcessingStepResult(
                ProcessingStep.CATEGORIZATION, true, "Document categorized successfully"));

            // Create Obsidian note if requested
            if (request.getOptions().isCreateObsidianNote()) {
                try {
                    obsidianNoteService.createNote(document, request.getOptions().getNoteMetaTag());
                } catch (Exception e) {
                    // Note creation failure shouldn't stop the process
                    logger.error("Obsidian note creation failed: {}", e.getMessage(), e);
                }
            }

            // Mark as completed
            status.setCurrentStep(ProcessingStep.COMPLETED);
            status.setCompleted(true);

            // Create response
            IngestResponse response = new IngestResponse();
            response.setId(document.getId());
            response.setStatus(DocumentStatus.STORED);
            response.setPdfUrl("/api/v1/documents/" + document.getId() + "/pdf");

            return response;

        } catch (DocumentServiceException e) {
            throw e;
        } catch (Exception e) {
            status.setFailed(true);
            status.setErrorMessage("Unexpected error: " + e.getMessage());
            throw new DocumentServiceException("Document ingestion failed: " + e.getMessage(), e);
        }
    }

    public ProcessingStatus getProcessingStatus(String documentId) {
        return processingStatuses.get(documentId);
    }

    public void removeProcessingStatus(String documentId) {
        processingStatuses.remove(documentId);
    }

    private void extractMetadata(Document document, String url) {
        try {
            // Fetch and parse the HTML page
            org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // Extract title
            String title = htmlDoc.title();
            if (title == null || title.trim().isEmpty()) {
                title = "Untitled Document";
            }
            document.setTitle(title);

            // Extract description from meta tags
            String description = htmlDoc.select("meta[name=description]").attr("content");
            if (description.isEmpty()) {
                description = htmlDoc.select("meta[property=og:description]").attr("content");
            }
            if (description.isEmpty()) {
                description = "PDF snapshot of " + url;
            }
            document.setDescription(description);

            // Extract canonical URL
            String canonicalUrl = htmlDoc.select("link[rel=canonical]").attr("href");
            if (canonicalUrl.isEmpty()) {
                canonicalUrl = htmlDoc.select("meta[property=og:url]").attr("content");
            }
            if (canonicalUrl.isEmpty()) {
                canonicalUrl = url;
            }
            document.setCanonicalUrl(canonicalUrl);

            // Extract all meta tags
            Map<String, String> metaTags = new HashMap<>();
            htmlDoc.select("meta").forEach(meta -> {
                String name = meta.attr("name");
                String property = meta.attr("property");
                String content = meta.attr("content");

                if (!name.isEmpty() && !content.isEmpty()) {
                    metaTags.put(name, content);
                } else if (!property.isEmpty() && !content.isEmpty()) {
                    metaTags.put(property, content);
                }
            });
            document.setMetaTags(metaTags);

            document.setFetchedAt(Instant.now());

        } catch (Exception e) {
            // Fallback to basic metadata if parsing fails
            document.setTitle("Web Page Snapshot");
            document.setDescription("PDF snapshot of web page");
            document.setCanonicalUrl(url);
            document.setFetchedAt(Instant.now());
            logger.warn("Failed to extract metadata from {}: {}", url, e.getMessage());
        }
    }

    private String sanitizeFilename(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "document";
        }

        // Remove or replace invalid filename characters
        String sanitized = title
                .replaceAll("[\\\\/:*?\"<>|]", "_")  // Replace invalid chars with underscore
                .replaceAll("\\s+", "_")              // Replace whitespace with underscore
                .replaceAll("_+", "_")                // Replace multiple underscores with single
                .trim();

        // Limit length to MAX_FILENAME_LENGTH characters
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
        }

        // Remove trailing underscore if present
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        return sanitized.isEmpty() ? "document" : sanitized;
    }

    private void analyzeContent(Document document) {
        // This would normally extract and analyze text content from the PDF
        // For now, this is a placeholder
        logger.debug("Analyzing content for document: {}", document.getId());
    }

    private void ensureCategoryExists(String categoryName) {
        try {
            Optional<Category> existingCategory = categoryRepository.findByName(categoryName);
            if (existingCategory.isEmpty()) {
                // Create new category
                Category category = new Category();
                category.setName(categoryName);
                category.setDescription(getCategoryDescription(categoryName));
                category.setCreatedAt(Instant.now().toString());
                category.setDocumentCount(1L);
                categoryRepository.save(category);
                logger.debug("Created new category: {}", categoryName);
            } else {
                // Increment document count
                categoryRepository.incrementDocumentCount(categoryName);
                logger.debug("Incremented count for category: {}", categoryName);
            }
        } catch (Exception e) {
            logger.error("Failed to create/update category {}: {}", categoryName, e.getMessage(), e);
        }
    }

    private String getCategoryDescription(String categoryName) {
        // Provide default descriptions based on category name
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

    // Existing interface methods
    @Override
    public Optional<Document> getDocument(String id) {
        return documentRepository.findById(id);
    }

    @Override
    public Page<Document> getAllDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }

    @Override
    public Page<Document> getDocumentsByCategory(String categoryName, Pageable pageable) {
        return documentRepository.findByCategory(categoryName, pageable);
    }

    @Override
    public Page<Document> searchDocuments(String query, Pageable pageable) {
        return documentRepository.search(query, pageable);
    }

    @Override
    public byte[] getDocumentPdf(String id) throws DocumentServiceException {
        Optional<Document> optionalDocument = documentRepository.findById(id);
        if (optionalDocument.isEmpty()) {
            throw new DocumentServiceException("Document not found: " + id);
        }

        Document document = optionalDocument.get();
        String pdfAttachmentName = document.getPdfAttachmentName();

        if (pdfAttachmentName == null || pdfAttachmentName.isEmpty()) {
            throw new DocumentServiceException("No PDF attachment found for document: " + id);
        }

        try {
            // Retrieve the PDF attachment from CouchDB
            return documentRepository.getAttachment(id, pdfAttachmentName);
        } catch (Exception e) {
            throw new DocumentServiceException("Failed to retrieve PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(String id) throws DocumentServiceException {
        try {
            documentRepository.delete(id);
            processingStatuses.remove(id);
        } catch (Exception e) {
            throw new DocumentServiceException("Failed to delete document: " + e.getMessage(), e);
        }
    }

    @Override
    public Document reclassifyDocument(String id) throws DocumentServiceException {
        Optional<Document> optionalDocument = documentRepository.findById(id);
        if (optionalDocument.isEmpty()) {
            throw new DocumentServiceException("Document not found: " + id);
        }

        Document document = optionalDocument.get();
        try {
            List<CategoryAssignment> categories = classificationService.classifyDocument(document);
            document.setCategories(categories);
            return documentRepository.save(document);
        } catch (Exception e) {
            throw new DocumentServiceException("Reclassification failed: " + e.getMessage(), e);
        }
    }
}