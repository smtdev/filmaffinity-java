package me.smt.filmaffinityjava;

import java.util.List;

/**
 * Callback interface for asynchronous search results.
 */
public interface FilmSearchCallback {
    /**
     * Called when the search is successful.
     * @param results A list of SearchResult objects found.
     */
    void onSuccess(List<SearchResult> results);

    /**
     * Called when an error occurs during the search.
     * @param e The exception that occurred.
     */
    void onError(Exception e);
} 