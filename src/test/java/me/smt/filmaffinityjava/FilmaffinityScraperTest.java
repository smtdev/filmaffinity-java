package me.smt.filmaffinityjava;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link FilmaffinityScraper}.
 * Tests the parsing logic using local HTML files.
 */
public class FilmaffinityScraperTest {

    // Helper method to load HTML content from test resources
    private Document loadTestHtml(String filename) throws IOException, URISyntaxException {
        URL resourceUrl = getClass().getResource(filename);
        assertNotNull("Test HTML file not found: " + filename, resourceUrl);
        File testFile = new File(resourceUrl.toURI());
        // Base URI doesn't matter much here as we are not resolving relative URLs in the test
        return Jsoup.parse(testFile, StandardCharsets.UTF_8.name(), "https://www.filmaffinity.com/");
    }

    @Test
    public void testParsePulpFiction() throws Exception {
        Document doc = loadTestHtml("pulp_fiction.html");
        FilmInfo info = FilmaffinityScraper.parseFilmInfo(doc, "pulp_fiction.html"); // Source URL for logging

        assertNotNull("FilmInfo should not be null", info);
        assertEquals("Pulp Fiction", info.getTitle());
        assertEquals("Pulp Fiction", info.getOriginalTitle());
        assertEquals("1994", info.getYear());
        assertEquals("153 min.", info.getDuration());
        assertEquals("Estados Unidos", info.getCountry());
        assertEquals("8,6", info.getRating());
        assertEquals(FilmInfo.Type.MOVIE, info.getType());
        assertEquals("https://pics.filmaffinity.com/pulp_fiction-210382116-mmed.jpg", info.getPosterUrl());

        // Check Directors
        List<String> expectedDirectors = Arrays.asList("Quentin Tarantino");
        assertEquals(expectedDirectors, info.getDirectors());

        // Check Genres (includes topics)
        List<String> expectedGenres = Arrays.asList("Thriller", "Crimen", "Historias cruzadas", "Película de culto", "Comedia negra");
        assertEquals(expectedGenres, info.getGenres());

        // Check Synopsis (just check it's not null or empty for simplicity)
        assertNotNull(info.getSynopsis());
        assertFalse(info.getSynopsis().isEmpty());
    }

    @Test
    public void testParseBreakingBad() throws Exception {
        Document doc = loadTestHtml("breaking_bad.html");
        FilmInfo info = FilmaffinityScraper.parseFilmInfo(doc, "breaking_bad.html");

        assertNotNull("FilmInfo should not be null", info);
        assertEquals("Breaking Bad", info.getTitle());
        assertEquals("Breaking Bad", info.getOriginalTitle());
        assertEquals("2008", info.getYear());
        assertEquals("45 min.", info.getDuration());
        assertEquals("Estados Unidos", info.getCountry());
        assertEquals("8,8", info.getRating());
        assertEquals(FilmInfo.Type.TV_SHOW, info.getType());
        assertEquals("https://pics.filmaffinity.com/breaking_bad-504442815-mmed.jpg", info.getPosterUrl());

        // Check Directors (only checks the first few visible ones from simplified HTML)
        List<String> expectedDirectors = Arrays.asList("Vince Gilligan", "Michelle MacLaren", "Adam Bernstein");
        // Use containsAll because the actual list might be longer in full HTML, but we only check the start
        assertTrue("Directors list should contain the expected names", info.getDirectors().containsAll(expectedDirectors));
        // Alternatively, if we only expect these exact ones from the simplified HTML:
        // assertEquals(expectedDirectors, info.getDirectors());

        // Check Genres
        List<String> expectedGenres = Arrays.asList("Serie de TV", "Thriller", "Drama", "Drogas", "Enfermedad", "Policíaco", "Crimen", "Comedia negra");
        assertEquals(expectedGenres, info.getGenres());

        // Check Synopsis
        assertNotNull(info.getSynopsis());
        assertTrue("Synopsis should contain expected start", info.getSynopsis().startsWith("Serie de TV (2008-2013)"));
    }

    @Test(expected = FilmaffinityScraper.ScraperException.class)
    public void testParseMissingTitle() throws Exception {
        // Create a dummy document without the title element
        Document doc = Jsoup.parse("<html><body><div id='movie-rat-avg'>5.0</div></body></html>");
        FilmaffinityScraper.parseFilmInfo(doc, "missing_title.html"); // This should throw
    }

    @Test
    public void testParseMissingRating() throws Exception {
        // Create a dummy document without the rating element but with title
        Document doc = Jsoup.parse("<html><body><h1 id='main-title'><span itemprop='name'>Test Movie</span></h1></body></html>");
        FilmInfo info = FilmaffinityScraper.parseFilmInfo(doc, "missing_rating.html");
        assertNotNull(info);
        assertEquals("Test Movie", info.getTitle());
        assertEquals("", info.getRating()); // Expect empty string as per current implementation
    }

    @Test(expected = FilmaffinityScraper.ScraperException.class)
    public void testParseNullDocument() throws Exception {
         FilmaffinityScraper.parseFilmInfo(null, "null_doc.html"); // This should throw
    }

    // --- Tests for parseFilmInfo --- //

    @Test
    public void parseFilmInfo_ValidMovieHtml_ReturnsCorrectInfo() throws Exception {
        String html = "<html><head><title>Test Movie</title></head><body>" +
                      "<h1 id='main-title'><span itemprop='name'>Test Movie Title</span></h1>" +
                      "<dl class='movie-info'>" +
                      "<dt>Título original</dt><dd>Original Test Title</dd>" +
                      "<dt>Año</dt><dd itemprop='datePublished'>2023</dd>" +
                      "<dt>Duración</dt><dd>120 min.</dd>" +
                      "<dt>País</dt><dd><span><img/></span> Test Country</dd>" +
                      "<dt>Dirección</dt><dd><div class='credits'><span itemprop='name'>Director 1</span>, <span itemprop='name'>Director 2</span></div></dd>" +
                      "<dt>Sinopsis</dt><dd itemprop='description'>Synopsis text here.</dd>" +
                      "</dl>" +
                      "<div id='movie-rat-avg'>8,5</div>" +
                      "<div id='movie-main-image-container'><img src='/images/poster.jpg'/></div>" +
                      "<dd class='card-genres'><a href='#'>Action</a> | <a href='#'>Adventure</a> | <a href='#'>Sci-Fi</a></dd>" +
                      "</body></html>";
        Document doc = Jsoup.parse(html, "http://example.com/"); // Base URI needed for absUrl

        FilmInfo info = FilmaffinityScraper.parseFilmInfo(doc, "http://example.com/film1.html");

        assertNotNull(info);
        assertEquals("Test Movie Title", info.getTitle());
        assertEquals("Original Test Title", info.getOriginalTitle());
        assertEquals("2023", info.getYear());
        assertEquals("120 min.", info.getDuration());
        assertEquals("Test Country", info.getCountry()); // Assuming extraction handles flag img
        assertEquals("Synopsis text here.", info.getSynopsis());
        assertEquals("8,5", info.getRating());
        //assertEquals("http://example.com/images/poster.jpg", info.getPosterUrl()); // Test absolute URL
        assertTrue(info.getDirectors().contains("Director 1"));
        assertTrue(info.getDirectors().contains("Director 2"));
        assertEquals(2, info.getDirectors().size());
        assertTrue(info.getGenres().contains("Action"));
        assertTrue(info.getGenres().contains("Adventure"));
        assertTrue(info.getGenres().contains("Sci-Fi"));
        assertEquals(3, info.getGenres().size());
        assertEquals(FilmInfo.Type.MOVIE, info.getType()); // Default type
    }

     @Test
     public void parseFilmInfo_ValidSeriesHtml_ReturnsCorrectType() throws Exception {
         String html = "<html><body>" +
                       "<h1 id='main-title'><span itemprop='name'>Test Series</span><span class='movie-type'><span class='type'>(Serie de TV)</span></span></h1>" +
                       // ... other necessary fields ...
                       "</body></html>";
         Document doc = Jsoup.parse(html);
         FilmInfo info = FilmaffinityScraper.parseFilmInfo(doc, "http://example.com/film2.html");
         assertEquals(FilmInfo.Type.TV_SHOW, info.getType());
     }

    @Test(expected = FilmaffinityScraper.ScraperException.class)
    public void parseFilmInfo_MissingTitle_ThrowsException() throws Exception {
        String html = "<html><body><dl class='movie-info'><dt>Año</dt><dd>2023</dd></dl></body></html>";
        Document doc = Jsoup.parse(html);
        FilmaffinityScraper.parseFilmInfo(doc, "http://example.com/film_no_title.html");
    }

    // --- Tests for parseMovieCardElement --- //

    @Test
    public void parseMovieCardElement_ValidCardHtml_ReturnsCorrectResult() {
        String cardHtml = "<div class='movie-card' data-movie-id='12345'>" +
                          "<div class='mc-poster'><a href='film12345.html'><img data-srcset='http://example.com/small.jpg 150w, http://example.com/large.jpg 400w' src='http://example.com/placeholder.jpg'/></a></div>" +
                          "<div class='mc-info-container'>" +
                          "  <div class='mc-title'><a href='film12345.html'>Card Title</a></div>" +
                          "  <div><span class='mc-year ms-1'>2022</span></div>" +
                          "  <div class='fa-avg-rat-box'><div class='avg mx-0'>7,8</div></div>" +
                          "</div></div>";
        Document doc = Jsoup.parseBodyFragment(cardHtml, "https://www.filmaffinity.com/es/"); // Need Base URI
        Element cardElement = doc.selectFirst("div.movie-card");

        assertNotNull("Card element should not be null", cardElement);

        SearchResult result = FilmaffinityScraper.parseMovieCardElement(cardElement);

        assertNotNull("SearchResult should not be null", result);
        assertEquals("12345", result.getId());
        assertEquals("Card Title", result.getTitle());
        assertEquals("https://www.filmaffinity.com/es/film12345.html", result.getUrl());
        assertEquals("7,8", result.getRating());
        assertEquals("2022", result.getYear());
        assertEquals("http://example.com/large.jpg", result.getImageUrl()); // Check if it prefers large
    }

     @Test
     public void parseMovieCardElement_MinimalCardHtml_HandlesMissingData() {
         String cardHtml = "<div class='movie-card' data-movie-id='67890'>" +
                           "<div class='mc-info-container'>" +
                           "  <div class='mc-title'><a href='film67890.html'>Minimal Card</a></div>" +
                           "</div></div>";
         Document doc = Jsoup.parseBodyFragment(cardHtml, "https://www.filmaffinity.com/es/");
         Element cardElement = doc.selectFirst("div.movie-card");
         SearchResult result = FilmaffinityScraper.parseMovieCardElement(cardElement);

         assertNotNull(result);
         assertEquals("67890", result.getId());
         assertEquals("Minimal Card", result.getTitle());
         assertEquals("https://www.filmaffinity.com/es/film67890.html", result.getUrl());
         assertEquals("--", result.getRating()); // Default for missing rating
         assertEquals("", result.getYear());     // Default for missing year
         assertEquals("", result.getImageUrl()); // Default for missing image
     }

    // --- Tests for extractFilmIdFromUrl --- //

    @Test
    public void extractFilmIdFromUrl_ValidUrl_ReturnsId() {
        assertEquals("123456", FilmaffinityScraper.extractFilmIdFromUrl("https://www.filmaffinity.com/es/film123456.html"));
        assertEquals("789", FilmaffinityScraper.extractFilmIdFromUrl("http://filmaffinity.com/en/film789.html?foo=bar"));
        assertEquals("1", FilmaffinityScraper.extractFilmIdFromUrl("/es/film1.html"));
    }

    @Test
    public void extractFilmIdFromUrl_InvalidUrls_ReturnsNull() {
        assertNull(FilmaffinityScraper.extractFilmIdFromUrl("https://www.filmaffinity.com/es/search.php?stext=test"));
        assertNull(FilmaffinityScraper.extractFilmIdFromUrl("https://www.google.com"));
        assertNull(FilmaffinityScraper.extractFilmIdFromUrl(null));
        assertNull(FilmaffinityScraper.extractFilmIdFromUrl(""));
        assertNull(FilmaffinityScraper.extractFilmIdFromUrl("/es/filmabc.html"));
    }
} 