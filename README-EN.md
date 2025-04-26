[Versión en Español](README.md)

# Filmaffinity Java Scraper

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
<!-- Optional: Add JitPack badges once published -->
<!-- [![JitPack](https://jitpack.io/v/com.github.smtdev/filmaffinity-java.svg)](https://jitpack.io/#com.github.smtdev/filmaffinity-java) -->

A simple Java library to fetch movie and TV show information by scraping the [Filmaffinity](https://www.filmaffinity.com/) website.

**Warning:** This library uses web scraping, which can be fragile and prone to breaking if Filmaffinity changes its website structure. Use it with caution and respect Filmaffinity's terms of service.

## Contents

*   [Features](#features)
*   [Installation](#installation)
*   [Usage](#usage)
*   [License](#license)
*   [Contributing](#contributing)

## Features

*   Fetches detailed information for movies and TV shows given their Filmaffinity URL.
*   Extracts data such as:
    *   Title
    *   Original Title
    *   Year
    *   Duration (per episode for series)
    *   Country
    *   Director(s) / Creator(s)
    *   Genres / Topics
    *   Synopsis
    *   Average Rating
    *   Poster URL
    *   Type (Movie / TV Show)
*   Asynchronous handling of network requests.
*   Designed to be robust against missing data or minor HTML changes.
*   Java 8+ compatible.

## Installation

### Using JitPack (Recommended)

This library can be easily added to your Gradle or Maven project via [JitPack](https://jitpack.io/).

**Gradle:**

1.  Add the JitPack repository to your root `build.gradle` (or `settings.gradle` in newer versions):
    ```gradle
    // settings.gradle
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
            maven { url 'https://jitpack.io' } // Add JitPack
        }
    }
    ```
2.  Add the dependency in your module's `build.gradle` file:
    ```gradle
    dependencies {
        implementation 'com.github.smtdev:filmaffinity-java:Tag' // Replace 'Tag' with the desired version (e.g., v1.0.0)
    }
    ```

**Maven:**

1.  Add the JitPack repository to your `pom.xml`:
    ```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    ```
2.  Add the dependency:
    ```xml
    <dependency>
        <groupId>com.github.smtdev</groupId>
        <artifactId>filmaffinity-java</artifactId>
        <version>Tag</version> <!-- Replace 'Tag' with the desired version -->
    </dependency>
    ```

*(Replace `Tag` with the specific release version or GitHub tag you want to use, e.g., `v1.0.0`).*

## Usage

The main class is `FilmaffinityScraper`. Use the static `fetchFilmInfo` method to fetch data asynchronously.

**Example (Java):**

```java
import me.smt.filmaffinityjava.FilmInfo;
import me.smt.filmaffinityjava.FilmInfoCallback;
import me.smt.filmaffinityjava.FilmaffinityScraper;

public class Main {

    public static void main(String[] args) {
        String pulpFictionUrl = "https://www.filmaffinity.com/en/film160882.html"; // Use English URL if preferred

        System.out.println("Fetching info for Pulp Fiction...");
        FilmaffinityScraper.fetchFilmInfo(pulpFictionUrl, new FilmInfoCallback() {
            @Override
            public void onSuccess(FilmInfo filmInfo) {
                // IMPORTANT: This callback executes on a background thread.
                // In UI applications (like Android), you need to post
                // UI updates to the main thread.
                System.out.println("Success! Pulp Fiction Info:");
                System.out.println("  Title: " + filmInfo.getTitle());
                System.out.println("  Year: " + filmInfo.getYear());
                System.out.println("  Rating: " + filmInfo.getRating());
                System.out.println("  Type: " + filmInfo.getType());
                // ... access other fields ...
            }

            @Override
            public void onError(Exception e) {
                 // IMPORTANT: Executed on a background thread.
                System.err.println("Error fetching Pulp Fiction: " + e.getMessage());
            }
        });

        // Required in a simple application to wait for callbacks.
        // In a real app (Android, server), this wouldn't be needed
        // or would be handled differently.
        try {
            System.out.println("Waiting for callbacks (example purposes)...");
            Thread.sleep(10000); // Wait 10 seconds (don't do this in production)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // It's HIGHLY recommended to call shutdownExecutor when your application exits
            // to release the scraper's threads cleanly.
             System.out.println("Shutting down scraper executor...");
            FilmaffinityScraper.shutdownExecutor();
        }
    }
}
```

**Important:**
*   The `FilmInfoCallback` executes on a background thread. If you update a User Interface (like in Android), ensure you do it on the main thread (using `runOnUiThread`, `Handler`, coroutines, etc.).
*   Remember to call `FilmaffinityScraper.shutdownExecutor()` when your application terminates to release thread pool resources.

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.

## Contributing

Contributions are welcome. If you find a bug or have a suggestion, please open an issue. If you want to contribute code, please open a pull request. 