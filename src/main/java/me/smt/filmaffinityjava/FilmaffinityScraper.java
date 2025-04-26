package me.smt.filmaffinityjava;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String BASE_URL = "https://www.filmaffinity.com";
    private static final String SEARCH_URL_SIMPLE_TEMPLATE = BASE_URL + "/es/search.php?stype=title&stext=%s";
    private static final String SEARCH_URL_EXACT_TEMPLATE = BASE_URL + "/es/search.php?stype=title&stext=%s&em=1";
    private static final String SEARCH_URL_ADVANCED_TEMPLATE = BASE_URL + "/es/advsearch.php?stext=%s&stype[]=title&country=&genre=&fromyear=%d&toyear=%d";
    private static final String FILM_URL_REGEX = ".*/film(\\d+)\\.html.*";
    private static final Pattern FILM_ID_PATTERN = Pattern.compile(FILM_URL_REGEX);

    /**
     * Asynchronously searches for films or series on Filmaffinity by title.
     *
     * @param query The search query (title).
     * @param year Optional year to refine the search (can be null).
     * @param callback The callback to handle the list of results or an error.
     */
    public static void searchFilm(String query, Integer year, FilmSearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("Search query cannot be null or empty."));
            return;
        }
        if (callback == null) {
            System.err.println("FilmaffinityScraper: Warning - Callback is null, search result will be lost for query: " + query);
            return;
        }

        executorService.execute(() -> {
            try {
                String encodedQuery;
                try {
                    encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    // Should not happen with UTF-8
                    callback.onError(new ScraperException("Failed to encode query: " + query, e));
                    return;
                }

                // --- Try Exact Match First ---
                String exactSearchUrl = String.format(SEARCH_URL_EXACT_TEMPLATE, encodedQuery);
                Connection.Response response = Jsoup.connect(exactSearchUrl)
                        .userAgent(DEFAULT_USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .followRedirects(true) // Important!
                        .execute();

                String finalUrl = response.url().toString();
                Matcher filmUrlMatcher = FILM_ID_PATTERN.matcher(finalUrl);

                if (filmUrlMatcher.matches()) {
                    // Redirected to a film page!
                    Document filmDoc = response.parse(); // Parse the redirected page
                    SearchResult result = parseBasicFilmInfoFromDoc(filmDoc, finalUrl);
                    if (result != null) {
                        callback.onSuccess(Collections.singletonList(result));
                        return; // Found exact match, stop searching
                    }
                    // If parsing failed for some reason, continue to next search method
                }

                // --- Try Advanced Search if Year Provided ---
                if (year != null) {
                    String advancedSearchUrl = String.format(SEARCH_URL_ADVANCED_TEMPLATE, encodedQuery, year, year);
                    Document resultsDoc = Jsoup.connect(advancedSearchUrl)
                            .userAgent(DEFAULT_USER_AGENT)
                            .timeout(TIMEOUT_MILLIS)
                            .get();

                    Elements movieCards = resultsDoc.select("ul.fa-list-group div.movie-card");
                    List<SearchResult> results = new ArrayList<>();
                    for (Element card : movieCards) {
                        SearchResult result = parseMovieCardElement(card);
                        if (result != null) {
                            results.add(result);
                        }
                    }

                    callback.onSuccess(results);
                    return; // Found results (or empty list) via advanced search, stop searching
                }

                // --- Fallback to Simple Search ---
                String simpleSearchUrl = String.format(SEARCH_URL_SIMPLE_TEMPLATE, encodedQuery);
                Document simpleResultsDoc = Jsoup.connect(simpleSearchUrl)
                        .userAgent(DEFAULT_USER_AGENT)
                        .timeout(TIMEOUT_MILLIS)
                        .get();

                // Use the same selector as advanced search, assuming structure is similar
                Elements simpleMovieCards = simpleResultsDoc.select("ul.fa-list-group div.movie-card");

                // Fallback selector based on python script if the first yields no results
                if (simpleMovieCards.isEmpty()) {
                     simpleMovieCards = simpleResultsDoc.select("div.d-flex"); // Or adjust based on actual simple search page structure
                }

                List<SearchResult> simpleResults = new ArrayList<>();
                for (Element card : simpleMovieCards) {
                    SearchResult result = parseMovieCardElement(card);
                    if (result != null) {
                        simpleResults.add(result);
                    }
                }

                callback.onSuccess(simpleResults); // Report results from simple search

            } catch (IOException e) {
                System.err.println("IOException during search for query: " + query + " - " + e.getMessage());
                callback.onError(new ScraperException("Network error during search for: " + query, e));
            } catch (Exception e) {
                System.err.println("Unexpected error during search for query: " + query + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                callback.onError(new ScraperException("Unexpected error during search for: " + query, e));
            }
        });
    }

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
             // Check if the string CONTAINS "Serie", case-insensitive
             if (typeString.toLowerCase().contains("serie")) {
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
     * Parses basic information from a film page Document into a SearchResult.
     * Used primarily for the exact match search scenario.
     * @param doc The Jsoup Document of the film page.
     * @param filmUrl The final URL of the film page.
     * @return SearchResult or null if essential info is missing.
     */
    static SearchResult parseBasicFilmInfoFromDoc(Document doc, String filmUrl) {
        if (doc == null) return null;
        try {
            String id = extractFilmIdFromUrl(filmUrl);
            if (id == null) return null; // Cannot create result without ID

            String title = getText(doc, "h1#main-title span[itemprop=name]");
            if (title == null || title.isEmpty()) return null; // Title is essential

            String year = getText(doc, "dd[itemprop=datePublished]");
            year = (year != null) ? year : EMPTY_STRING;

            String rating = getText(doc, "#movie-rat-avg");
            rating = (rating != null && !rating.isEmpty()) ? rating : "--"; // Default to --

            String posterUrl = getAttr(doc, "#movie-main-image-container img", "src");
            if (posterUrl == null || posterUrl.isEmpty()) {
                String ogPosterUrl = getAttr(doc, "meta[property=og:image]", "content");
                posterUrl = (ogPosterUrl != null) ? ogPosterUrl : EMPTY_STRING;
            }

            return new SearchResult(id, title, filmUrl, rating, year, posterUrl);
        } catch (Exception e) {
            System.err.println("Error parsing basic info from film page: " + filmUrl + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the numeric film ID from a Filmaffinity film URL.
     * @param url The film URL.
     * @return The numeric ID as a String, or null if not found.
     */
    static String extractFilmIdFromUrl(String url) {
        if (url == null) return null;
        Matcher matcher = FILM_ID_PATTERN.matcher(url);
        if (matcher.matches() && matcher.groupCount() >= 1) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parses a 'movie-card' div element from search results into a SearchResult.
     * @param cardElement The div.movie-card element.
     * @return SearchResult or null if parsing fails.
     */
    static SearchResult parseMovieCardElement(Element cardElement) {
        if (cardElement == null) return null;
        try {
            String id = cardElement.attr("data-movie-id"); // Get ID from data attribute
            if (id == null || id.isEmpty()) return null;

            Element titleLink = cardElement.selectFirst(".mc-title a");
            String title = (titleLink != null) ? titleLink.text() : EMPTY_STRING;
            String url = (titleLink != null) ? titleLink.absUrl("href") : EMPTY_STRING;

            String year = getText(cardElement, ".mc-year");
            year = (year != null) ? year : EMPTY_STRING;

            String rating = getText(cardElement, ".fa-avg-rat-box .avg");
            rating = (rating != null && !rating.isEmpty()) ? rating : "--";

            String imageUrl = "";
            Element img = cardElement.selectFirst(".mc-poster img");
            if (img != null) {
                imageUrl = img.absUrl("src"); // Try src first (might be placeholder)
                String srcset = img.attr("data-srcset");
                if (srcset != null && !srcset.isEmpty()) {
                    // Try to get a better resolution from srcset (e.g., large)
                    String[] sources = srcset.split(",");
                    for (String source : sources) {
                        String trimmedSource = source.trim();
                        if (trimmedSource.contains("large")) {
                            imageUrl = trimmedSource.split("\\s+")[0]; // Get the URL part
                            break;
                        }
                         // Fallback to medium if large not found
                         else if (trimmedSource.contains("mmed")) {
                            imageUrl = trimmedSource.split("\\s+")[0];
                         }
                    }
                }
                 // Ensure URL is absolute
                 if (!imageUrl.startsWith("http")) {
                    // Try to construct absolute URL if it looks relative (needs BASE_URL)
                    if (imageUrl.startsWith("/")) {
                         imageUrl = BASE_URL + imageUrl;
                    } else {
                         imageUrl = EMPTY_STRING; // Invalid relative URL
                    }
                 }
            }
            imageUrl = (imageUrl != null) ? imageUrl : EMPTY_STRING;


            return new SearchResult(id, title, url, rating, year, imageUrl);
        } catch (Exception e) {
            System.err.println("Error parsing movie card element: " + e.getMessage());
            return null;
        }
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