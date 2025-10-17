package org.cacummaro.service.obsidian;

import org.cacummaro.domain.CategoryAssignment;
import org.cacummaro.domain.Document;
import org.cacummaro.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ObsidianNoteServiceImpl implements ObsidianNoteService {

    private static final Logger logger = LoggerFactory.getLogger(ObsidianNoteServiceImpl.class);
    private static final int MAX_TITLE_LENGTH = 50;

    @Value("${cacummaro.obsidian.vault-path:./obsidian-vault}")
    private String vaultPath;

    @Value("${cacummaro.obsidian.enabled:true}")
    private boolean enabled;

    private final DocumentRepository documentRepository;

    @Autowired
    public ObsidianNoteServiceImpl(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public String createNote(Document document, String noteContent) throws ObsidianNoteException {
        return createNote(document, noteContent, null);
    }

    @Override
    public String createNote(Document document, String noteContent, String customMetaTag) throws ObsidianNoteException {
        if (!enabled) {
            logger.info("Obsidian integration is disabled");
            return null;
        }

        validateDocument(document);
        ensureVaultDirectoryExists();
        ensurePdfsDirectoryExists();

        String fileName = generateFileName(document);
        String noteMarkdown = generateNoteContent(document, noteContent, customMetaTag);

        try {
            // Write the note file
            Path notePath = Paths.get(vaultPath, fileName);

            // Security check: ensure the path is within the vault directory
            Path normalizedVaultPath = Paths.get(vaultPath).toAbsolutePath().normalize();
            Path normalizedNotePath = notePath.toAbsolutePath().normalize();

            if (!normalizedNotePath.startsWith(normalizedVaultPath)) {
                throw new ObsidianNoteException("Invalid note path: path traversal attempt detected");
            }

            Files.write(notePath, noteMarkdown.getBytes());

            // Export PDF to local filesystem
            exportPdfToVault(document);

            logger.info("Created Obsidian note: {}", fileName);
            return fileName;

        } catch (IOException e) {
            logger.error("Failed to create Obsidian note for document {}", document.getId(), e);
            throw new ObsidianNoteException("Failed to write note file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteNote(String noteFileName) throws ObsidianNoteException {
        if (!enabled) {
            return;
        }

        try {
            Path notePath = Paths.get(vaultPath, noteFileName);

            // Security check: ensure the path is within the vault directory
            Path normalizedVaultPath = Paths.get(vaultPath).toAbsolutePath().normalize();
            Path normalizedNotePath = notePath.toAbsolutePath().normalize();

            if (!normalizedNotePath.startsWith(normalizedVaultPath)) {
                throw new ObsidianNoteException("Invalid note path: path traversal attempt detected");
            }

            if (Files.exists(notePath)) {
                Files.delete(notePath);
                logger.info("Deleted Obsidian note: {}", noteFileName);
            }

        } catch (IOException e) {
            logger.error("Failed to delete Obsidian note: {}", noteFileName, e);
            throw new ObsidianNoteException("Failed to delete note file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean noteExists(String noteFileName) {
        if (!enabled) {
            return false;
        }

        try {
            Path notePath = Paths.get(vaultPath, noteFileName);
            return Files.exists(notePath);
        } catch (Exception e) {
            logger.warn("Error checking if note exists: {}", noteFileName, e);
            return false;
        }
    }

    private void validateDocument(Document document) throws ObsidianNoteException {
        if (document == null) {
            throw new ObsidianNoteException("Document cannot be null");
        }
        if (document.getId() == null) {
            throw new ObsidianNoteException("Document ID cannot be null");
        }
        if (document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            throw new ObsidianNoteException("Document title cannot be empty");
        }
    }

    private void ensureVaultDirectoryExists() throws ObsidianNoteException {
        try {
            Path vaultDir = Paths.get(vaultPath);
            if (!Files.exists(vaultDir)) {
                Files.createDirectories(vaultDir);
                logger.info("Created Obsidian vault directory: {}", vaultPath);
            }
        } catch (IOException e) {
            throw new ObsidianNoteException("Failed to create vault directory: " + e.getMessage(), e);
        }
    }

    private void ensurePdfsDirectoryExists() throws ObsidianNoteException {
        try {
            Path pdfsDir = Paths.get(vaultPath, "pdfs");
            if (!Files.exists(pdfsDir)) {
                Files.createDirectories(pdfsDir);
                logger.info("Created PDFs directory: {}", pdfsDir);
            }
        } catch (IOException e) {
            throw new ObsidianNoteException("Failed to create PDFs directory: " + e.getMessage(), e);
        }
    }

    private void exportPdfToVault(Document document) throws ObsidianNoteException {
        try {
            // Get PDF from CouchDB
            String pdfAttachmentName = document.getPdfAttachmentName();
            if (pdfAttachmentName == null || pdfAttachmentName.isEmpty()) {
                logger.warn("No PDF attachment found for document {}", document.getId());
                return;
            }

            byte[] pdfData = documentRepository.getAttachment(document.getId(), pdfAttachmentName);

            // Sanitize document ID for Windows file paths (replace | with -)
            String sanitizedId = document.getId().replace("|", "-");

            // Write PDF to vault/pdfs directory with sanitized document ID as filename
            Path pdfPath = Paths.get(vaultPath, "pdfs", sanitizedId + ".pdf");

            // Security check
            Path normalizedVaultPath = Paths.get(vaultPath).toAbsolutePath().normalize();
            Path normalizedPdfPath = pdfPath.toAbsolutePath().normalize();

            if (!normalizedPdfPath.startsWith(normalizedVaultPath)) {
                throw new ObsidianNoteException("Invalid PDF path: path traversal attempt detected");
            }

            Files.write(pdfPath, pdfData);
            logger.info("Exported PDF to vault: {}", pdfPath);

        } catch (Exception e) {
            logger.error("Failed to export PDF for document {}", document.getId(), e);
            // Don't throw exception - note creation should succeed even if PDF export fails
        }
    }

    private String generateFileName(Document document) {
        // Sanitize title for filename
        String sanitizedTitle = document.getTitle()
                .replaceAll("[^a-zA-Z0-9\\s\\-_]", "")
                .replaceAll("\\s+", "_")
                .toLowerCase();

        // Limit length and add timestamp to avoid conflicts
        if (sanitizedTitle.length() > MAX_TITLE_LENGTH) {
            sanitizedTitle = sanitizedTitle.substring(0, MAX_TITLE_LENGTH);
        }

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .format(Instant.parse(document.getFetchedAt()).atZone(java.time.ZoneOffset.UTC));

        return String.format("%s_%s.md", sanitizedTitle, timestamp);
    }

    private String generateNoteContent(Document document, String noteContent, String customMetaTag) {
        StringBuilder markdown = new StringBuilder();

        // Frontmatter
        markdown.append("---\n");
        markdown.append("source_url: ").append(document.getUrl()).append("\n");
        markdown.append("title: ").append(document.getTitle()).append("\n");
        markdown.append("pdf_id: ").append(document.getId()).append("\n");
        markdown.append("fetchedAt: ").append(document.getFetchedAt()).append("\n");

        // Add categories as tags
        if (document.getCategories() != null && !document.getCategories().isEmpty()) {
            List<String> categoryNames = document.getCategories().stream()
                    .map(CategoryAssignment::getName)
                    .collect(Collectors.toList());
            categoryNames.add("page-snapshot");
            markdown.append("tags: [").append(String.join(", ", categoryNames)).append("]\n");
        } else {
            markdown.append("tags: [page-snapshot]\n");
        }

        markdown.append("---\n\n");

        // Title
        markdown.append("# ").append(document.getTitle()).append("\n\n");

        // Description
        if (document.getDescription() != null && !document.getDescription().trim().isEmpty()) {
            markdown.append("## Description\n");
            markdown.append(document.getDescription()).append("\n\n");
        }

        // Custom note content from meta tag or provided content
        if (noteContent != null && !noteContent.trim().isEmpty()) {
            markdown.append("## Notes\n");
            markdown.append(noteContent).append("\n\n");
        } else if (customMetaTag != null && document.getMetaTags() != null) {
            String metaContent = document.getMetaTags().get(customMetaTag);
            if (metaContent != null && !metaContent.trim().isEmpty()) {
                markdown.append("## Notes\n");
                markdown.append(metaContent).append("\n\n");
            }
        }

        // PDF download link (sanitize ID for Windows paths)
        String sanitizedId = document.getId().replace("|", "-");
        markdown.append("## Resources\n");
        markdown.append("[Download PDF](../pdfs/").append(sanitizedId).append(".pdf)\n\n");

        // Categories
        if (document.getCategories() != null && !document.getCategories().isEmpty()) {
            markdown.append("## Categories\n");
            for (CategoryAssignment category : document.getCategories()) {
                markdown.append("- ").append(category.getName())
                        .append(" (confidence: ").append(String.format("%.2f", category.getConfidence()))
                        .append(")\n");
            }
            markdown.append("\n");
        }

        // Metadata
        markdown.append("## Metadata\n");
        markdown.append("- **Fetched:** ").append(document.getFetchedAt()).append("\n");
        if (document.getCanonicalUrl() != null) {
            markdown.append("- **Canonical URL:** ").append(document.getCanonicalUrl()).append("\n");
        }
        if (document.getSizeBytes() != null) {
            markdown.append("- **PDF Size:** ").append(formatFileSize(document.getSizeBytes())).append("\n");
        }

        return markdown.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}