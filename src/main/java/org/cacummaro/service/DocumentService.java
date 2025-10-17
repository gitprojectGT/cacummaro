package org.cacummaro.service;

import org.cacummaro.domain.Document;
import org.cacummaro.dto.IngestRequest;
import org.cacummaro.dto.IngestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DocumentService {

    IngestResponse ingestUrl(IngestRequest request) throws DocumentServiceException;

    Optional<Document> getDocument(String id);

    Page<Document> getAllDocuments(Pageable pageable);

    Page<Document> getDocumentsByCategory(String categoryName, Pageable pageable);

    Page<Document> searchDocuments(String query, Pageable pageable);

    byte[] getDocumentPdf(String id) throws DocumentServiceException;

    void deleteDocument(String id) throws DocumentServiceException;

    Document reclassifyDocument(String id) throws DocumentServiceException;
}