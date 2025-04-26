package me.smt.filmaffinityjava;

import java.util.List;
import java.util.Objects;

/**
 * Represents the information scraped for a film or series from Filmaffinity.
 */
public class FilmInfo {

    public enum Type { MOVIE, TV_SHOW, UNKNOWN } // Enum for type safety

    private final String title;
    private final String originalTitle;
    private final String year;
    private final String duration; // For series, this is likely episode duration
    private final String country;
    private final List<String> directors; // Includes creators for series in the main list for now
    private final List<String> genres;
    private final String synopsis;
    private final String rating;
    private final String posterUrl;
    private final Type type; // Added type field

    // Updated Constructor
    public FilmInfo(String title, String originalTitle, String year, String duration, String country,
                    List<String> directors, List<String> genres, String synopsis, String rating, String posterUrl, Type type) {
        this.title = title;
        this.originalTitle = originalTitle;
        this.year = year;
        this.duration = duration;
        this.country = country;
        this.directors = directors; // Keep creators mixed in for simplicity, or separate later
        this.genres = genres;
        this.synopsis = synopsis;
        this.rating = rating;
        this.posterUrl = posterUrl;
        this.type = type;
    }

    // Getters (including new getType)
    public String getTitle() { return title; }
    public String getOriginalTitle() { return originalTitle; }
    public String getYear() { return year; }
    public String getDuration() { return duration; }
    public String getCountry() { return country; }
    public List<String> getDirectors() { return directors; }
    public List<String> getGenres() { return genres; }
    public String getSynopsis() { return synopsis; }
    public String getRating() { return rating; }
    public String getPosterUrl() { return posterUrl; }
    public Type getType() { return type; } // Getter for type

    @Override
    public String toString() {
        return "FilmInfo{" +
                "type=" + type + // Added type
                ", title='" + title + '\'' +
                ", originalTitle='" + originalTitle + '\'' +
                ", year='" + year + '\'' +
                ", duration='" + duration + '\'' +
                ", country='" + country + '\'' +
                ", directors=" + directors +
                ", genres=" + genres +
                ", synopsis='" + (synopsis != null ? synopsis.substring(0, Math.min(synopsis.length(), 50)) + "..." : "null") + '\'' +
                ", rating='" + rating + '\'' +
                ", posterUrl='" + posterUrl + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilmInfo filmInfo = (FilmInfo) o;
        // Added type to equality check
        return type == filmInfo.type &&
               Objects.equals(title, filmInfo.title) &&
               Objects.equals(year, filmInfo.year) &&
               Objects.equals(originalTitle, filmInfo.originalTitle);
    }

    @Override
    public int hashCode() {
        // Added type to hash code
        return Objects.hash(type, title, originalTitle, year);
    }
} 