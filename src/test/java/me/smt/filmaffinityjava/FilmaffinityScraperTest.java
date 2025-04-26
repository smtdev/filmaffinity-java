package me.smt.filmaffinityjava;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
} 