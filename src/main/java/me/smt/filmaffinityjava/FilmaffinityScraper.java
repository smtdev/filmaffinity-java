package me.smt.filmaffinityjava;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Scraper utility to fetch movie/series information from Filmaffinity.
 * Remember to call {@link #shutdownExecutor()} when your application exits
 * to release background threads.
 */
public class FilmaffinityScraper {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT_MILLIS = 15000; // 15 seconds timeout
    private static final String EMPTY_STRING = "";

    /**
     * Asynchronously fetches film or series information from its Filmaffinity URL.
     * Handles potential network errors and parsing issues gracefully.
     *
     * @param contentUrl The full URL of the movie/series page on Filmaffinity. Must not be null or empty.
     * @param callback The callback to handle the success (with FilmInfo) or error result. Must not be null.
     */
    public static void fetchFilmInfo(String contentUrl, FilmInfoCallback callback) {
        // --- Input Validation ---
        if (contentUrl == null || contentUrl.trim().isEmpty()) {
             if (callback != null) callback.onError(new IllegalArgumentException("Content URL cannot be null or empty."));
             return;
        }
         if (callback == null) {
            System.err.println("FilmaffinityScraper: Warning - Callback is null, result will be lost for URL: " + contentUrl);
            return; // Cannot proceed without a callback
         }


        executorService.execute(() -> {
            try {
                // --- Network Fetch ---
                Document doc = Jsoup.connect(contentUrl)
                        .userAgent(DEFAULT_USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .get();

                // --- Call the Parsing Logic ---
                FilmInfo filmInfo = parseFilmInfo(doc, contentUrl);

                // --- Report Success ---
                callback.onSuccess(filmInfo);

            } catch (ScraperException e) {
                 System.err.println("ScraperException parsing content from: " + contentUrl + " - " + e.getMessage());
                 callback.onError(e);
            } catch (IOException e) {
                System.err.println("IOException fetching URL: " + contentUrl + " - " + e.getMessage());
                callback.onError(new ScraperException("Network error fetching URL: " + contentUrl, e));
            } catch (Exception e) {
                System.err.println("Unexpected error during fetchFilmInfo for " + contentUrl + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                callback.onError(new ScraperException("Unexpected error processing URL: " + contentUrl, e));
            }
        });
    }

    /**
     * Parses the film information from a pre-fetched Jsoup Document.
     * Made package-private for testability.
     * @param doc The Jsoup Document of the Filmaffinity page.
     * @param sourceUrl The original URL (used for error messages).
     * @return FilmInfo object with the parsed data.
     * @throws ScraperException if essential data (like title) cannot be parsed.
     */
    static FilmInfo parseFilmInfo(Document doc, String sourceUrl) throws ScraperException {
        if (doc == null) {
            throw new ScraperException("Input Document is null for URL: " + sourceUrl);
        }
        try {
            // --- Data Extraction with Null Safety ---
            String title = getText(doc, "h1#main-title span[itemprop=name]");
            if (title == null || title.isEmpty()) {
                throw new ScraperException("Could not extract title from: " + sourceUrl);
            }

            String originalTitle = findDlData(doc, "Título original");
            originalTitle = (originalTitle != null) ? originalTitle : EMPTY_STRING;

            String year = getText(doc, "dd[itemprop=datePublished]");
            year = (year != null) ? year : EMPTY_STRING;

            String duration = findDlData(doc, "Duración");
            duration = (duration != null) ? duration : EMPTY_STRING;

            String country = findDlData(doc, "País");
            country = (country != null) ? country : EMPTY_STRING;

            String synopsis = getText(doc, "dd[itemprop=description]");
            synopsis = (synopsis != null) ? synopsis : EMPTY_STRING;

            String rating = getText(doc, "#movie-rat-avg");
            if (rating == null || rating.isEmpty()) {
                System.err.println("FilmaffinityScraper: Warning - Could not extract rating for URL: " + sourceUrl);
                rating = EMPTY_STRING;
            }

            String posterUrl = getAttr(doc, "#movie-main-image-container img", "src");
            if (posterUrl == null || posterUrl.isEmpty()) {
                String ogPosterUrl = getAttr(doc, "meta[property=og:image]", "content");
                posterUrl = (ogPosterUrl != null) ? ogPosterUrl : EMPTY_STRING;
            }

            List<String> directors = getCredits(doc, "Dirección");
            List<String> genres = getGenres(doc);
            FilmInfo.Type type = determineType(doc);

            // --- Create Result Object ---
            return new FilmInfo(
                    title, originalTitle, year, duration, country,
                    directors, genres, synopsis, rating, posterUrl, type
            );
        } catch (Exception e) {
            // Wrap unexpected parsing errors
             throw new ScraperException("Error parsing content from URL: " + sourceUrl, e);
        }
    }

    /** Determines the content type (Movie/TV Show) based on page elements. */
    private static FilmInfo.Type determineType(Document doc) {
         String typeString = getText(doc, "h1#main-title .movie-type .type");
         if (typeString != null) {
             if ("Serie".equalsIgnoreCase(typeString)) {
                 return FilmInfo.Type.TV_SHOW;
             } else {
                 // Consider other potential types if Filmaffinity adds them (e.g., "Corto")
                 return FilmInfo.Type.MOVIE;
             }
         } else {
             // Fallback to OG meta tag
             String ogType = getAttr(doc, "meta[property=og:type]", "content");
             if ("video.tv_show".equalsIgnoreCase(ogType)) {
                  return FilmInfo.Type.TV_SHOW;
             } else if ("video.movie".equalsIgnoreCase(ogType)) {
                  return FilmInfo.Type.MOVIE;
             }
             // Default assumption if nothing else is found
             // Could potentially check URL structure here as another fallback if needed
             return FilmInfo.Type.MOVIE;
         }
    }


    // --- Helper Methods (with minor null safety improvements) ---

    /** Safely gets text from an element selected by CSS query. Returns null if not found. */
    private static String getText(Element parent, String cssSelector) {
        if (parent == null) return null;
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.text().trim() : null;
    }

    /** Safely gets an attribute value from an element selected by CSS query. Returns null if not found. */
     private static String getAttr(Element parent, String cssSelector, String attributeKey) {
        if (parent == null) return null;
        Element el = parent.selectFirst(cssSelector);
        return (el != null) ? el.attr(attributeKey) : null;
    }

    /** Finds data in a definition list (<dl>) by matching the <dt> text. Returns null if not found. */
    private static String findDlData(Document doc, String dtText) {
        if (doc == null || dtText == null) return null;
        Elements dtElements = doc.select("dl.movie-info dt");
        for (Element dt : dtElements) {
            // Use containsIgnoreCase for slightly more flexible matching
            if (dt.text().trim().equalsIgnoreCase(dtText.trim())) {
                Element dd = dt.nextElementSibling();
                if (dd != null) {
                    if ("País".equalsIgnoreCase(dtText.trim())) {
                         // Prioritize ownText to avoid including potential hidden text from child nodes (like flags)
                         String ownText = dd.ownText().trim();
                         return !ownText.isEmpty() ? ownText : dd.text().trim(); // Fallback to full text if ownText is empty
                    }
                    return dd.text().trim();
                }
            }
        }
        return null; // Explicitly return null if not found
    }

     /** Extracts names from credit lists (Director, Guion, Reparto). Returns an empty list if not found. */
    private static List<String> getCredits(Document doc, String dtText) {
         if (doc == null || dtText == null) return Collections.emptyList();
         Elements dtElements = doc.select("dl.movie-info dt");
         for (Element dt : dtElements) {
            if (dt.text().trim().equalsIgnoreCase(dtText.trim())) {
                 Element dd = dt.nextElementSibling();
                 if (dd != null) {
                     Elements nameElements = dd.select(".credits span[itemprop=name], .credits span > a, .credits > span > span[itemprop=name]");
                      if (!nameElements.isEmpty()) {
                          return nameElements.stream()
                                  .map(Element::text)
                                  .filter(name -> !name.trim().isEmpty())
                                  .distinct()
                                  .collect(Collectors.toList());
                      } else {
                          // Fallback for simple text credits (like Música: Varios)
                          Element creditDiv = dd.selectFirst(".credits");
                          if (creditDiv != null) {
                               String creditText = creditDiv.text().trim();
                               if (!creditText.isEmpty()) {
                                    // Simple split by comma, assuming basic structure
                                    String[] names = creditText.split("\\s*,\\s*");
                                    List<String> nameList = new ArrayList<>();
                                    for (String name : names) {
                                        if (!name.trim().isEmpty()) {
                                            nameList.add(name.trim());
                                        }
                                    }
                                     return nameList;
                               }
                          }
                      }
                 }
                 return Collections.emptyList();
             }
         }
         return Collections.emptyList();
    }

     /** Extracts genres and topics. Returns an empty list if not found. */
    private static List<String> getGenres(Document doc) {
          if (doc == null) return Collections.emptyList();
          Element dd = doc.selectFirst("dd.card-genres");
          if (dd != null) {
             // Use a simpler selector to capture ALL links within the dd element
             Elements genreLinks = dd.select("a"); // Select all 'a' tags within dd.card-genres
             if (!genreLinks.isEmpty()) {
                 return genreLinks.stream()
                         .map(Element::text)
                         .filter(genre -> !genre.trim().isEmpty())
                         .distinct()
                         .collect(Collectors.toList());
             }
          }
          // Fallback logic removed for now as it was commented out and potentially unreliable
          return Collections.emptyList();
     }


    /**
     * Shuts down the internal executor service, attempting graceful termination first.
     * It's recommended to call this when your application is shutting down
     * to ensure background threads are released properly.
     */
    public static void shutdownExecutor() {
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a reasonable time for existing tasks to finish
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("FilmaffinityScraper: Tasks did not complete in 5s, forcing shutdown...");
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a bit for tasks to respond to cancellation
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS))
                    System.err.println("FilmaffinityScraper: Executor service did not terminate even after forced shutdown.");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread was interrupted during awaitTermination
            System.err.println("FilmaffinityScraper: Shutdown interrupted, forcing immediate shutdown.");
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Custom exception class for scraper-specific errors.
     */
    public static class ScraperException extends IOException {
        public ScraperException(String message) {
            super(message);
        }

        public ScraperException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 