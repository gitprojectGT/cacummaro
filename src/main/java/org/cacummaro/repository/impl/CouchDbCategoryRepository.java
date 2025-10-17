package org.cacummaro.repository.impl;

import org.cacummaro.domain.Category;
import org.cacummaro.repository.CategoryRepository;
import org.ektorp.CouchDbConnector;
import org.ektorp.support.CouchDbRepositorySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CouchDbCategoryRepository extends CouchDbRepositorySupport<Category> implements CategoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(CouchDbCategoryRepository.class);

    public CouchDbCategoryRepository(CouchDbConnector db) {
        super(Category.class, db);
    }

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            category.setId("category|" + category.getName());
        }
        add(category);
        return category;
    }

    @Override
    public Optional<Category> findById(String id) {
        try {
            return Optional.of(get(id));
        } catch (org.ektorp.DocumentNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Category> findByName(String name) {
        return findById("category|" + name);
    }

    @Override
    public List<Category> findAll() {
        try {
            // Get all documents and filter by ID prefix
            return db.queryView(createQuery("_all_docs")
                    .startKey("category|")
                    .endKey("category|\uffff")
                    .includeDocs(true), Category.class);
        } catch (Exception e) {
            logger.error("Failed to query categories: {}", e.getMessage(), e);
            return new java.util.ArrayList<>();
        }
    }

    @Override
    public void delete(String id) {
        try {
            Category category = get(id);
            remove(category);
        } catch (org.ektorp.DocumentNotFoundException e) {
            // Category doesn't exist, nothing to delete
            logger.debug("Category not found for deletion: {}", id);
        }
    }

    @Override
    public boolean exists(String id) {
        return contains(id);
    }

    @Override
    public void incrementDocumentCount(String categoryName) {
        Optional<Category> categoryOpt = findByName(categoryName);
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            category.setDocumentCount(category.getDocumentCount() + 1);
            update(category);
        }
    }

    @Override
    public void decrementDocumentCount(String categoryName) {
        Optional<Category> categoryOpt = findByName(categoryName);
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            long newCount = Math.max(0, category.getDocumentCount() - 1);
            category.setDocumentCount(newCount);
            update(category);
        }
    }
}