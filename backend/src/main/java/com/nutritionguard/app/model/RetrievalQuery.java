package com.nutritionguard.app.model;

/**
 * Simple wrapper that carries the original user question together with the
 * {@link QueryUnderstandingResult}. It is passed to the retrieval components
 * so they can access the full semantic information without flattening it.
 */
public final class RetrievalQuery {
    private final String originalQuestion;
    private final QueryUnderstandingResult understanding;

    public RetrievalQuery(String originalQuestion, QueryUnderstandingResult understanding) {
        this.originalQuestion = originalQuestion;
        this.understanding = understanding;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public QueryUnderstandingResult getUnderstanding() {
        return understanding;
    }
}
