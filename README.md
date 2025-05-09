[English Version](README-EN.md)

# Filmaffinity Java Scraper

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
<!-- Opcional: Añadir badges de JitPack una vez publicado -->
<!-- [![JitPack](https://jitpack.io/v/com.github.smtdev/filmaffinity-java.svg)](https://jitpack.io/#com.github.smtdev/filmaffinity-java) -->

Una librería Java simple para obtener información de películas y series haciendo scraping de la web de [Filmaffinity](https://www.filmaffinity.com/).

**Advertencia:** Esta librería utiliza web scraping, lo cual puede ser frágil y propenso a romperse si Filmaffinity cambia la estructura de su sitio web. Úsala con precaución y respeta los términos de servicio de Filmaffinity.

## Contenido

*   [Características](#características)
*   [Instalación](#instalación)
*   [Uso](#uso)
*   [Licencia](#licencia)
*   [Contribuir](#contribuir)

## Características

*   Obtiene información detallada de películas y series a partir de su URL de Filmaffinity.
*   **Búsqueda de películas/series por título (v1.1.0+).**
*   Extracción de datos como:
    *   Título
    *   Título Original
    *   Año
    *   Duración (por episodio para series)
    *   País
    *   Director(es) / Creador(es)
    *   Géneros / Temas
    *   Sinopsis
    *   Nota media (Rating)
    *   URL del Póster
    *   Tipo (Película / Serie de TV)
*   Manejo asíncrono de las peticiones de red.
*   Diseñado para ser robusto ante datos faltantes o pequeños cambios en el HTML.
*   Compatible con Java 8+.

## Instalación

### Usando JitPack (Recomendado)

Esta librería se puede añadir fácilmente a tu proyecto Gradle o Maven a través de [JitPack](https://jitpack.io/).

**Gradle:**

1.  Añade el repositorio de JitPack a tu archivo `build.gradle` raíz (o `settings.gradle` en versiones más nuevas):
    ```gradle
    // settings.gradle
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
            maven { url 'https://jitpack.io' } // Añadir JitPack
        }
    }
    ```
2.  Añade la dependencia en el archivo `build.gradle` de tu módulo:
    ```gradle
    dependencies {
        implementation 'com.github.smtdev:filmaffinity-java:Tag' // Reemplaza 'Tag' con la versión deseada (e.g., v1.0.0)
    }
    ```

**Maven:**

1.  Añade el repositorio de JitPack a tu `pom.xml`:
    ```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    ```
2.  Añade la dependencia:
    ```xml
    <dependency>
        <groupId>com.github.smtdev</groupId>
        <artifactId>filmaffinity-java</artifactId>
        <version>Tag</version> <!-- Reemplaza 'Tag' con la versión deseada -->
    </dependency>
    ```

*(Reemplaza `Tag` con el número de versión específico del release o tag de GitHub que quieras usar, por ejemplo, `v1.0.0`).*

## Uso

La clase principal es `FilmaffinityScraper`.

### Obtener Información Detallada (por URL)

Usa el método estático `fetchFilmInfo` para obtener los datos de forma asíncrona a partir de una URL específica de Filmaffinity.

**Ejemplo (Java):**

```java
import me.smt.filmaffinityjava.FilmInfo;
import me.smt.filmaffinityjava.FilmInfoCallback;
import me.smt.filmaffinityjava.FilmaffinityScraper;

public class MainFetch {

    public static void main(String[] args) {
        String pulpFictionUrl = "https://www.filmaffinity.com/es/film160882.html";
        String breakingBadUrl = "https://www.filmaffinity.com/es/film489970.html";

        System.out.println("Fetching info for Pulp Fiction...");
        FilmaffinityScraper.fetchFilmInfo(pulpFictionUrl, new FilmInfoCallback() {
            @Override
            public void onSuccess(FilmInfo filmInfo) {
                // IMPORTANTE: Este callback se ejecuta en un hilo secundario.
                // En aplicaciones con UI (como Android), necesitas postear
                // las actualizaciones de la UI al hilo principal.
                System.out.println("Success! Pulp Fiction Info:");
                System.out.println("  Title: " + filmInfo.getTitle());
                System.out.println("  Year: " + filmInfo.getYear());
                System.out.println("  Rating: " + filmInfo.getRating());
                System.out.println("  Type: " + filmInfo.getType());
                System.out.println("  Poster: " + filmInfo.getPosterUrl());
                System.out.println("  Directors: " + filmInfo.getDirectors());
                System.out.println("  Genres: " + filmInfo.getGenres());
                // System.out.println(filmInfo); // Imprime todos los datos (toString)
            }

            @Override
            public void onError(Exception e) {
                 // IMPORTANTE: Ejecutado en hilo secundario.
                System.err.println("Error fetching Pulp Fiction: " + e.getMessage());
                // e.printStackTrace(); // Descomentar para depuración
            }
        });

        System.out.println("Fetching info for Breaking Bad...");
         FilmaffinityScraper.fetchFilmInfo(breakingBadUrl, new FilmInfoCallback() {
            @Override
            public void onSuccess(FilmInfo filmInfo) {
                System.out.println("Success! Breaking Bad Info:");
                System.out.println("  Title: " + filmInfo.getTitle());
                System.out.println("  Rating: " + filmInfo.getRating());
                System.out.println("  Type: " + filmInfo.getType());
            }

            @Override
            public void onError(Exception e) {
                 System.err.println("Error fetching Breaking Bad: " + e.getMessage());
            }
        });

        // Necesario en una aplicación simple para esperar a los callbacks
        // En una app real (Android, servidor), esto no sería necesario
        // o se gestionaría de forma diferente.
        try {
            System.out.println("Waiting for fetch callbacks (example purposes)...");
            Thread.sleep(10000); // Espera 10 segundos (no hacer en producción)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Es MUY recomendable llamar a shutdownExecutor al salir de tu aplicación
            // para liberar los hilos del scraper de forma limpia.
             System.out.println("Shutting down scraper executor (Fetch Example)...");
            FilmaffinityScraper.shutdownExecutor();
        }
    }
}
```

**Importante (Fetch):**
*   El `FilmInfoCallback` se ejecuta en un hilo secundario. Si actualizas una interfaz de usuario (como en Android), debes asegurarte de hacerlo en el hilo principal (usando `runOnUiThread`, `Handler`, coroutines, etc.).
*   Recuerda llamar a `FilmaffinityScraper.shutdownExecutor()` cuando tu aplicación termine para liberar los recursos del pool de hilos.

### Búsqueda de Películas/Series (por Título - v1.1.0+)

A partir de la versión 1.1.0, puedes buscar películas o series por título usando `searchFilm`. Puedes proporcionar opcionalmente el año para refinar la búsqueda.

**Ejemplo (Java):**

```java
import me.smt.filmaffinityjava.FilmaffinityScraper;
import me.smt.filmaffinityjava.FilmSearchCallback;
import me.smt.filmaffinityjava.SearchResult;

import java.util.List;

public class MainSearch {

    public static void main(String[] args) {
        String searchQuery = "Pulp Fiction";
        Integer searchYear = 1994; // Opcional, puede ser null

        System.out.println("Buscando '" + searchQuery + "'...");
        FilmaffinityScraper.searchFilm(searchQuery, searchYear, new FilmSearchCallback() {
            @Override
            public void onSuccess(List<SearchResult> results) {
                // IMPORTANTE: Ejecutado en hilo secundario.
                if (results.isEmpty()) {
                    System.out.println("No se encontraron resultados para '" + searchQuery + "'.");
                } else {
                    System.out.println("Resultados encontrados:");
                    for (SearchResult result : results) {
                        System.out.println("  ID: " + result.getId());
                        System.out.println("  Título: " + result.getTitle());
                        System.out.println("  Año: " + result.getYear());
                        System.out.println("  Rating: " + result.getRating());
                        System.out.println("  URL: " + result.getUrl());
                        System.out.println("  Imagen: " + result.getImageUrl());
                        System.out.println("  ----");
                        // Puedes usar la URL del resultado para llamar a fetchFilmInfo si necesitas más detalles
                        // FilmaffinityScraper.fetchFilmInfo(result.getUrl(), anotherCallback);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                // IMPORTANTE: Ejecutado en hilo secundario.
                System.err.println("Error durante la búsqueda: " + e.getMessage());
            }
        });

        // Necesario en una aplicación simple para esperar a los callbacks
        // En una app real (Android, servidor), esto no sería necesario
        // o se gestionaría de forma diferente.
        try {
            System.out.println("Waiting for search callbacks (example purposes)...");
            Thread.sleep(5000); // Espera 5 segundos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // No olvides llamar a FilmaffinityScraper.shutdownExecutor() al final.
             System.out.println("Shutting down scraper executor (Search Example)...");
             FilmaffinityScraper.shutdownExecutor();
        }
    }
}
```

**Importante (Search):**
*   El `FilmSearchCallback` también se ejecuta en un hilo secundario. Gestiona las actualizaciones de UI de la misma forma que con `fetchFilmInfo`.
*   La búsqueda intenta encontrar la coincidencia más exacta posible, pero la naturaleza del scraping web puede hacerla frágil.
*   Recuerda llamar a `FilmaffinityScraper.shutdownExecutor()` al final.

## Licencia

Este proyecto está licenciado bajo la Licencia Apache 2.0. Consulta el archivo `LICENSE` para más detalles.

## Contribuir

Las contribuciones son bienvenidas. Si encuentras un error o tienes una sugerencia, por favor abre un issue. Si quieres contribuir con código, por favor abre un pull request.