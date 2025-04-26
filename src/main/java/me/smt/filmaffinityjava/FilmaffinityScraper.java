package me.smt.filmaffinityjava;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects; // Import Objects for requireNonNullElse
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
            Document doc = null;
            try {
                // --- Network Fetch ---
                doc = Jsoup.connect(contentUrl)
                        .userAgent(DEFAULT_USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .get();

                // --- Data Extraction with Null Safety ---
                String title = getText(doc, "h1#main-title span[itemprop=name]");
                // Title is considered essential, fail if not found
                if (title == null || title.isEmpty()) {
                    callback.onError(new ScraperException("Could not extract title from: " + contentUrl));
                    return;
                }

                String originalTitle = Objects.requireNonNullElse(findDlData(doc, "Título original"), EMPTY_STRING);
                String year = Objects.requireNonNullElse(getText(doc, "dd[itemprop=datePublished]"), EMPTY_STRING);
                String duration = Objects.requireNonNullElse(findDlData(doc, "Duración"), EMPTY_STRING);
                String country = Objects.requireNonNullElse(findDlData(doc, "País"), EMPTY_STRING);
                String synopsis = Objects.requireNonNullElse(getText(doc, "dd[itemprop=description]"), EMPTY_STRING);
                // Rating is also quite important, handle its potential absence carefully
                String rating = getText(doc, "#movie-rat-avg");
                 if (rating == null || rating.isEmpty()) {
                    System.err.println("FilmaffinityScraper: Warning - Could not extract rating for URL: " + contentUrl);
                    rating = EMPTY_STRING; // Assign empty instead of null
                 }


                String posterUrl = getAttr(doc, "#movie-main-image-container img", "src");
                if (posterUrl == null || posterUrl.isEmpty()) {
                    posterUrl = Objects.requireNonNullElse(getAttr(doc, "meta[property=og:image]", "content"), EMPTY_STRING);
                }

                List<String> directors = getCredits(doc, "Dirección"); // Already returns empty list if fails
                List<String> genres = getGenres(doc); // Already returns empty list if fails

                // --- Determine Type ---
                FilmInfo.Type type = determineType(doc);

                // --- Create Result Object ---
                FilmInfo filmInfo = new FilmInfo(
                        title, originalTitle, year, duration, country,
                        directors, genres, synopsis, rating, posterUrl, type
                );

                // --- Report Success ---
                callback.onSuccess(filmInfo);

            } catch (IOException e) {
                // --- Network Error Handling ---
                System.err.println("IOException fetching or parsing URL: " + contentUrl + " - " + e.getMessage());
                callback.onError(new ScraperException("Network error fetching URL: " + contentUrl, e));
            } catch (Exception e) {
                // --- General Parsing/Runtime Error Handling ---
                System.err.println("Unexpected error extracting film info from " + contentUrl + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                // e.printStackTrace(); // Uncomment for detailed debugging if needed
                callback.onError(new ScraperException("Error parsing content from URL: " + contentUrl, e));
            }
        });
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
                     // Prioritize extracting names from links or specific spans
                     Elements nameElements = dd.select(".credits span[itemprop=name], .credits span > a, .credits > span > span[itemprop=name]"); // Refined selector
                      if (!nameElements.isEmpty()) {
                          return nameElements.stream()
                                  .map(Element::text)
                                  .filter(name -> name != null && !name.trim().isEmpty())
                                  .distinct() // Avoid duplicates if structure is weird
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
                 // Found the dt but couldn't extract from dd, return empty
                 return Collections.emptyList();
             }
         }
         return Collections.emptyList(); // dtText not found
    }

     /** Extracts genres and topics. Returns an empty list if not found. */
    private static List<String> getGenres(Document doc) {
          if (doc == null) return Collections.emptyList();
          Element dd = doc.selectFirst("dd.card-genres");
          if (dd != null) {
             // Selects links within the main genre span and subsequent topic links
             Elements genreLinks = dd.select("span[itemprop=genre] a, a[href*=movietopic]");
             if (!genreLinks.isEmpty()) {
                 return genreLinks.stream()
                         .map(Element::text)
                         .filter(genre -> genre != null && !genre.trim().isEmpty())
                         .distinct()
                         .collect(Collectors.toList());
             }
          }
          // Fallback (less reliable) - Consider removing if too noisy
          /*
          String metaKeywords = getAttr(doc, "meta[name=keywords]", "content");
          if (metaKeywords != null && !metaKeywords.isEmpty()) {
             String[] keywords = metaKeywords.split(",\\s*");
             List<String> genresFromMeta = new ArrayList<>();
             for (String keyword : keywords) {
                 if (!keyword.matches("\\d{4}") && keyword.length() > 2) {
                      genresFromMeta.add(keyword.trim());
                 }
             }
             if (!genresFromMeta.isEmpty()) return genresFromMeta;
          }
          */
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