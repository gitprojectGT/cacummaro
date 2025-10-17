package org.cacummaro.repository.impl;

import org.apache.commons.io.IOUtils;
import org.cacummaro.domain.Document;
import org.cacummaro.repository.DocumentRepository;
import org.ektorp.AttachmentInputStream;
import org.ektorp.CouchDbConnector;
import org.ektorp.support.CouchDbRepositorySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CouchDbDocumentRepository extends CouchDbRepositorySupport<Document> implements DocumentRepository {

    private static final Logger logger = LoggerFactory.getLogger(CouchDbDocumentRepository.class);

    public CouchDbDocumentRepository(CouchDbConnector db) {
        super(Document.class, db);
    }

    @Override
    public Document save(Document document) {
        if (document.getId() == null) {
            document.setId("doc|" + java.util.UUID.randomUUID().toString());
            add(document);
        } else if (document.getRevision() == null) {
            add(document);
        } else {
            update(document);
        }
        return document;
    }

    @Override
    public Optional<Document> findById(String id) {
        try {
            return Optional.of(get(id));
        } catch (org.ektorp.DocumentNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Document> findAll() {
        // Get all documents and filter by type to exclude Category documents
        List<Document> allDocs = getAll();
        return allDocs.stream()
                .filter(doc -> "document".equals(doc.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public Page<Document> findAll(Pageable pageable) {
        List<Document> allDocs = findAll();
        return createPageFromList(allDocs, pageable);
    }

    @Override
    public List<Document> findByCategory(String categoryName) {
        // Filter documents by category in-memory since CouchDB views are not configured
        List<Document> allDocs = findAll();
        return allDocs.stream()
                .filter(doc -> doc.getCategories() != null &&
                        doc.getCategories().stream()
                                .anyMatch(cat -> categoryName.equals(cat.getName())))
                .collect(Collectors.toList());
    }

    @Override
    public Page<Document> findByCategory(String categoryName, Pageable pageable) {
        List<Document> docs = findByCategory(categoryName);
        return createPageFromList(docs, pageable);
    }

    @Override
    public List<Document> search(String query) {
        List<Document> allDocs = findAll();
        return allDocs.stream()
                .filter(doc -> matchesQuery(doc, query))
                .collect(Collectors.toList());
    }

    @Override
    public Page<Document> search(String query, Pageable pageable) {
        List<Document> docs = search(query);
        return createPageFromList(docs, pageable);
    }

    @Override
    public void delete(String id) {
        try {
            Document doc = get(id);
            remove(doc);
        } catch (org.ektorp.DocumentNotFoundException e) {
            // Document doesn't exist, nothing to delete
            logger.debug("Document not found for deletion: {}", id);
        }
    }

    @Override
    public boolean exists(String id) {
        return contains(id);
    }

    @Override
    public void saveAttachment(String documentId, String attachmentName, byte[] data, String contentType) {
        try {
            Document doc = get(documentId);
            AttachmentInputStream attachment = new AttachmentInputStream(
                    attachmentName,
                    new ByteArrayInputStream(data),
                    contentType,
                    data.length
            );
            db.createAttachment(documentId, doc.getRevision(), attachment);
        } catch (org.ektorp.DocumentNotFoundException e) {
            throw new RuntimeException("Document not found: " + documentId, e);
        }
    }

    @Override
    public byte[] getAttachment(String documentId, String attachmentName) {
        try (AttachmentInputStream attachment = db.getAttachment(documentId, attachmentName)) {
            // Use Apache Commons IO to properly read the stream
            byte[] bytes = IOUtils.toByteArray(attachment);
            logger.debug("Retrieved attachment {} from document {}, size: {} bytes",
                        attachmentName, documentId, bytes.length);
            return bytes;
        } catch (IOException e) {
            logger.error("Failed to read attachment {} from document {}: {}",
                        attachmentName, documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve attachment: " + attachmentName + " from document: " + documentId, e);
        } catch (org.ektorp.DocumentNotFoundException e) {
            logger.error("Document {} not found", documentId);
            throw new RuntimeException("Document not found: " + documentId, e);
        } catch (Exception e) {
            logger.error("Unexpected error retrieving attachment {} from document {}: {}",
                        attachmentName, documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve attachment: " + attachmentName + " from document: " + documentId, e);
        }
    }

    @Override
    public void deleteAttachment(String documentId, String attachmentName) {
        try {
            Document doc = get(documentId);
            db.deleteAttachment(documentId, doc.getRevision(), attachmentName);
        } catch (org.ektorp.DocumentNotFoundException e) {
            throw new RuntimeException("Document not found: " + documentId, e);
        }
    }

    private Page<Document> createPageFromList(List<Document> docs, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), docs.size());

        if (start > docs.size()) {
            return new PageImpl<>(List.of(), pageable, docs.size());
        }

        List<Document> pageContent = docs.subList(start, end);
        return new PageImpl<>(pageContent, pageable, docs.size());
    }

    private boolean matchesQuery(Document doc, String query) {
        String lowerQuery = query.toLowerCase();
        return (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(lowerQuery)) ||
               (doc.getDescription() != null && doc.getDescription().toLowerCase().contains(lowerQuery)) ||
               (doc.getUrl() != null && doc.getUrl().toLowerCase().contains(lowerQuery));
    }
}