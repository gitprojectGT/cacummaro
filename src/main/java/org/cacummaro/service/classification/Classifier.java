package org.cacummaro.service.classification;

import org.cacummaro.domain.CategoryAssignment;
import org.cacummaro.domain.Document;

import java.util.List;

public interface Classifier {

    List<CategoryAssignment> classify(Document document);

    String getClassifierName();

    String getClassifierVersion();
}