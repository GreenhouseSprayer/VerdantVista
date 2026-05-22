# AGENTS.md

## Overview
This document provides essential knowledge for AI coding agents to be productive in the VerdantVista codebase. It outlines the architecture, workflows, conventions, and integration points specific to this project.

---

## Big Picture Architecture

### Project Structure
The VerdantVista workspace consists of multiple sub-projects:
- **app**: Modern Kotlin-based Android application module.
- **iNaturalistAndroid**: Original Android application for iNaturalist.org.
- **iNaturalistAPI**: Node.js-based API backend.
- **iNaturalist**: Shared libraries and configurations for the Android app.
- **Library**: Additional shared code.
- **urlImageViewHelper**: Utility for image handling.

### Key Components
- **app**: Implements the modern UI and logic using Kotlin. Key directories:
  - `src/main/java/Verdant/Vista`: Main application code (MVVM).
- **iNaturalistAndroid**: Implements the original mobile app UI and logic. Key directories:
  - `src/main/java`: Contains Android app source code.
  - `res/values`: Configuration files like `config.xml`.
- **iNaturalistAPI**: Provides RESTful services documented with Swagger/OpenAPI. Key directories:
  - `lib/`: Core API logic and utilities.
  - `test/`: Unit and integration tests.
  - `docker/`: Docker configurations for deployment.

### Data Flow
- The Android app communicates with the API for data retrieval and submission.
- The API interacts with PostgreSQL and Elasticsearch for data storage and querying.

---

## Technical Paradigms

### Dependency Management
- **Android**: Uses Gradle with Version Catalogs (`gradle/libs.versions.toml`). Dependencies are defined centrally and referenced in `build.gradle.kts` files.
- **API**: Uses `npm` for package management. Dependencies are listed in `package.json`.

### Concurrency & State Management (Android)
- **Kotlin Coroutines**: Preferred for asynchronous operations (network calls, database access).
- **MVVM Pattern**: ViewModels (`androidx.lifecycle.ViewModel`) hold state using `LiveData` or Kotlin `Flow`.
- **Repository Pattern**: Centralizes data access logic, abstracting between Retrofit (API) and Room (Local DB).

### Authentication Flow
- **API**: Uses JWT (JSON Web Tokens) with HMAC-SHA512. The `Authorization` header is expected to contain the token.
- **Android App**: Communicates with the API using a specific `User-Agent` header for identification. Secure endpoints require the `Authorization` header with a valid user/application JWT.

### Error Handling
- **Android**: repositories typically use `try-catch` blocks and log errors via `Log.e`. Network failures are handled gracefully with fallbacks to local cache (e.g., `DiscoveryDao`).
- **API**: Uses standard Express middleware and `util.js` for consistent response formatting and error handling.

---

## Developer Workflows

### Building the Android App
1. Install the latest [Android Studio](https://developer.android.com/studio).
2. Copy `config.example.xml` to `config.xml` in `iNaturalist/src/main/res/values`.
3. Copy `google-services.example.json` to `google-services.json` in `iNaturalist/`.
4. Install the [Android NDK](https://developer.android.com/ndk/downloads/index.html).
5. Build the project using Android Studio.

### Running the API
1. Install dependencies: `npm install`.
2. Copy `config_example.js` to `config.js` and configure database connections.
3. Start the server: `NODE_ENV=development node app.js`.

### Testing
- **Android App**: Use Android Studio's built-in testing tools.
- **API**:
  - Run all tests: `npm test`.
  - Filter tests: `NODE_ENV=test ./node_modules/mocha/bin/_mocha --recursive --fgrep <pattern>`.
  - With Docker: `docker compose -f docker-compose.test.yml up -d`.

### Linting
- Run ESLint for the API: `npm run eslint`.

---

## Project-Specific Conventions

### Android App
- Use `config.xml` for app-specific configurations.
- Follow Android's MVVM (Model-View-ViewModel) architecture.

### API
- Use Swagger/OpenAPI for documenting endpoints.
- Place reusable utilities in `lib/`.
- Write tests in `test/` using Mocha.

---

## Integration Points

### External Dependencies
- **Android App**:
  - Google Maps API
  - Firebase
  - Retrofit & OkHttp
  - Room Persistence Library
- **API**:
  - PostgreSQL
  - Elasticsearch
  - Docker for containerization

### Cross-Component Communication
- The Android app communicates with the API via RESTful endpoints.
- Use Swagger UI to explore and test API endpoints.

---

## Examples

### Android App
- Example of querying projects:
  ```java
  Cursor c = getActivity().getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION,
      "(id = " + projectId + ")", null, Project.DEFAULT_SORT_ORDER);
  ```

### API
- Example of a test case:
  ```javascript
  it("should allow observation with valid project rules", () => {
    const project = buildProject({ rules: ["valid"] });
    const observation = { valid: true };
    expect(Project.collectionProjectRulesAllowObservation(project, observation)).to.be.true;
  });
  ```

---

## Updating Documentation
- Android: Update `README.md` in `iNaturalistAndroid/`.
- API: Edit `lib/views/swagger_v*.yml.ejs` for Swagger documentation.

---

This document is a living guide. Update it as the project evolves.
