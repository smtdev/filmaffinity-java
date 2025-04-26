package me.smt.filmaffinityjava;

/**
 * Represents a single search result from Filmaffinity.
 */
public class SearchResult {
    private final String id;
    private final String title;
    private final String url;
    private final String rating; // Keep as String for now, might be "--"
    private final String year;
    private final String imageUrl;

    public SearchResult(String id, String title, String url, String rating, String year, String imageUrl) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.rating = rating;
        this.year = year;
        this.imageUrl = imageUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getRating() {
        return rating;
    }

    public String getYear() {
        return year;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", rating='" + rating + '\'' +
                ", year='" + year + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
} 