package org.cacummaro.repository;

import org.cacummaro.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(String id);

    Optional<Category> findByName(String name);

    List<Category> findAll();

    void delete(String id);

    boolean exists(String id);

    void incrementDocumentCount(String categoryName);

    void decrementDocumentCount(String categoryName);
}