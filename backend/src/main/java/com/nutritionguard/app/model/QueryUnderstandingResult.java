package com.nutritionguard.app.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of the query understanding result.
 * All fields are final and set via constructor. No setters are provided.
 */
public final class QueryUnderstandingResult {
    private final String queryId;
    private final double confidence;
    private final List<Entity> entities;
    private final SearchContext searchContext;
    private final String originalQuestion;
    private final String intent;
    private final List<String> searchTerms;

    public QueryUnderstandingResult(String queryId,
                                   double confidence,
                                   List<Entity> entities,
                                   SearchContext searchContext,
                                   String originalQuestion,
                                   String intent,
                                   List<String> searchTerms) {
        this.queryId = Objects.requireNonNull(queryId);
        this.confidence = confidence;
        this.entities = entities == null ? Collections.emptyList() : Collections.unmodifiableList(entities);
        this.searchContext = searchContext == null ? new SearchContext(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()) : searchContext;
        this.originalQuestion = originalQuestion == null ? "" : originalQuestion;
        this.intent = intent == null ? "" : intent;
        this.searchTerms = searchTerms == null ? Collections.emptyList() : Collections.unmodifiableList(searchTerms);
        }
    /**
     * Create a minimal fallback result when parsing fails.
     */
    public static QueryUnderstandingResult minimal(String question) {
        return new QueryUnderstandingResult(
            java.util.UUID.randomUUID().toString(),
            0.0,
            java.util.Collections.emptyList(),
            new SearchContext(java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList()),
            question,
            "",
            java.util.Collections.emptyList()
        );
    }

    public String getQueryId() {
        return queryId;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public SearchContext getSearchContext() {
        return searchContext;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public String getIntent() {
        return intent;
    }

    public List<String> getSearchTerms() {
        return searchTerms;
    }

    /**
     * Simple immutable entity representation.
     */
    public static final class Entity {
        private final String type;
        private final String value;

        public Entity(String type, String value) {
            this.type = Objects.requireNonNull(type);
            this.value = Objects.requireNonNull(value);
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Immutable container for the extracted search context.
     */
    public static final class SearchContext {
        private final List<String> foods;
        private final List<String> nutrients;
        private final List<String> conditions;
        private final List<String> healthGoals;

        public SearchContext(List<String> foods,
                             List<String> nutrients,
                             List<String> conditions,
                             List<String> healthGoals) {
            this.foods = foods == null ? Collections.emptyList() : Collections.unmodifiableList(foods);
            this.nutrients = nutrients == null ? Collections.emptyList() : Collections.unmodifiableList(nutrients);
            this.conditions = conditions == null ? Collections.emptyList() : Collections.unmodifiableList(conditions);
            this.healthGoals = healthGoals == null ? Collections.emptyList() : Collections.unmodifiableList(healthGoals);
        }

        public List<String> getFoods() {
            return foods;
        }

        public List<String> getNutrients() {
            return nutrients;
        }

        public List<String> getConditions() {
            return conditions;
        }

        public List<String> getHealthGoals() {
            return healthGoals;
        }
    }
}
