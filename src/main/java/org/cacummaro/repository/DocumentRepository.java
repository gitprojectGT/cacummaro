package org.cacummaro.repository;

import org.cacummaro.domain.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(String id);

    List<Document> findAll();

    Page<Document> findAll(Pageable pageable);

    List<Document> findByCategory(String categoryName);

    Page<Document> findByCategory(String categoryName, Pageable pageable);

    List<Document> search(String query);

    Page<Document> search(String query, Pageable pageable);

    void delete(String id);

    boolean exists(String id);

    void saveAttachment(String documentId, String attachmentName, byte[] data, String contentType);

    byte[] getAttachment(String documentId, String attachmentName);

    void deleteAttachment(String documentId, String attachmentName);
}