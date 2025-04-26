package me.smt.filmaffinityjava;

/**
 * Callback interface for receiving the result or error of an asynchronous film info fetch operation.
 */
public interface RatingCallback { // Keep filename for now
    /**
     * Called when the film information is successfully fetched.
     * Note: This method might be called on a background thread.
     * Ensure UI updates are posted to the main thread.
     * @param filmInfo The fetched film information.
     */
    void onSuccess(FilmInfo filmInfo);

    /**
     * Called when an error occurs during the fetching or parsing process.
     * Note: This method might be called on a background thread.
     * Ensure UI updates are posted to the main thread.
     * @param e The exception that occurred.
     */
    void onError(Exception e);
} 