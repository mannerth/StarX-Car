# AGENTS.md

## Project purpose and baseline

- StarX-Car is a Spring Boot backend for a ride-hailing course project.
- Runtime: Spring Boot 3.5.16, Spring Framework 6.2.x, Java 21, Maven Wrapper, MySQL, MyBatis-Plus.
- Root package: `org.mannerth.starxcar`. All application code must remain under this package so component scanning works.
- Treat `src/main/resources/application.yaml` as the runtime configuration source of truth and `create_sql.md` as the current database schema reference.
- The worktree may contain user changes. Read nearby code before editing, preserve unrelated changes, and never reset or revert user work.

## Source layout

```text
src/main/java/org/mannerth/starxcar/
  auth/         User account, login, registration and refresh-token domain
  car/          Vehicle domain
  passenger/    Passenger profile domain
  driver/       Driver, vehicle binding and work-status domain
  order/        Ride-order domain
    admin/      Administrator order-operation subdomain
    pricing/    Pricing-rule subdomain
    rating/     Order-rating subdomain
  infrastructure/ External system adapters, such as AMap and image hosting
  security/     JWT issuing/parsing, authentication filter, current-user helpers
  web/          ApiResponse, response advice, exception and security error handlers
  logging/      Trace ID, request logging, application startup logging
  config/       Security, JWT properties, OpenAPI configuration
  constant/     Shared enums and constants

Each business domain and order subdomain uses these responsibility packages:

  api/          HTTP controllers only
  application/  Services, transactions, schedulers and workflow orchestration
  domain/       Entities, enums, value objects, state machines and calculations
  infrastructure/ MyBatis mappers and persistence adapters
  request/      Input DTOs
  response/     Output DTOs
src/main/resources/
  application.yaml
src/test/java/org/mannerth/starxcar/
src/test/resources/application-test.yaml
```

### Placement rules

- Organize new business features by domain package, then by the responsibility packages above. Do not place Controllers, Services, entities, or Mappers directly in a domain root and do not create global `controller` or `service` packages.
- Preserve the dependency direction `api -> application -> domain`. Domain code must not depend on Spring MVC, Web, external HTTP clients, or persistence adapters. Application services may use infrastructure adapters to coordinate a use case.
- Put input models in `<domain>/request/` and output models in `<domain>/response/`. Use Java records for immutable DTOs unless a mutable DTO is necessary.
- Keep JPA out of this project. Use MyBatis-Plus entities and `BaseMapper` interfaces; place custom mapper XML under `src/main/resources/mapper/<domain>/` only when BaseMapper is insufficient.
- Keep controllers thin. Put multi-step business logic and transactions in a service. Use constructor injection only.
- Do not expose persistence entities when they contain secrets, internal state, or fields unsuitable for clients. In particular, never return `User.password`.
- Use `@Transactional` for a workflow that modifies multiple tables, for example user registration and account deletion.

## API contract and documentation: mandatory for every endpoint

Every new or changed HTTP endpoint must be documented in OpenAPI annotations and be visible correctly at `/swagger-ui.html`.

1. Add `@Tag(name = ..., description = ...)` to every controller.
2. Add `@Operation(summary = ..., description = ...)` to every handler method.
   - `summary` is a short imperative action, such as `Create and bind vehicle`.
   - `description` must state the caller/role, main behavior, side effects, and important failure conditions.
3. Add `@SecurityRequirement(name = "bearerAuth")` on every protected controller or endpoint. Do not put it on anonymous login, registration, or refresh endpoints.
4. Add `@ApiResponses` for meaningful outcomes. At minimum document success, `400`, `401`, `403` where applicable, `404` for lookups, and `409` for unique/constraint conflicts.
5. Add `@Schema(description = ..., example = ...)` to new public request and response DTO fields when the field name or format is not self-evident. Document formats such as phone numbers, license numbers, dates, IDs, and token fields.
6. Update the endpoint documentation when request fields, response fields, authorization, or status behavior changes.

### Response format

- Successful controller responses are automatically wrapped by `ApiResponseAdvice` as:

```json
{
  "code": 0,
  "message": "success",
  "traceId": "...",
  "data": {}
}
```

- Errors are produced by `GlobalExceptionHandler`, `RestAuthenticationEntryPoint`, or `RestAccessDeniedHandler` with the same envelope. `code` is the HTTP status for errors.
- Do not hand-wrap ordinary successful controller return values. Return the payload DTO/entity; the advice supplies the envelope. Return `ApiResponse.success(null)` only for a deliberately empty success response such as a delete endpoint.
- Use `ResponseStatusException` for clear, expected business failures. Let the global handler convert it to the common error response.
- Do not leak stack traces, SQL details, passwords, JWTs, refresh tokens, or internal exception messages to callers.

## Authentication and authorization

- Authentication is stateless Bearer JWT. Send `Authorization: Bearer <accessToken>`.
- `JwtService` signs and parses tokens; `JwtAuthenticationFilter` accepts only `token_type=access` tokens and establishes the current subject and role.
- Access and refresh tokens are distinct. Refresh endpoints must accept only `token_type=refresh`.
- Roles use `UserType` values and Spring Security authorities in `ROLE_<UPPERCASE_ROLE>` form. Protect role-specific endpoints with `@PreAuthorize`.
- Use `@AuthenticationPrincipal String principal` with `CurrentUser.id(principal)` for the authenticated user ID. Never trust a user ID from a request body or path when operating on "my" resources.
- Keep `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, and `/error` anonymous unless the API design intentionally changes.
- Passwords must be BCrypt hashes. Never compare plaintext passwords in a query and never log a password or token.

## Database and MyBatis-Plus

- The active datasource is MySQL (`com.mysql.cj.jdbc.Driver`), not Oracle. Do not reintroduce Oracle assumptions from older documentation.
- Existing schema uses `user`, `passenger`, `driver`, and `car` tables. Read `create_sql.md` before changing these entities or their constraints.
- Passenger and driver phone display fields are maintained by database triggers. Do not duplicate that masking logic in Java writes.
- Follow current MyBatis-Plus configuration: camel-case mapping, `IdType.AUTO` for auto-increment primary keys, and MyBatis stdout SQL logging in development.
- Use `@TableName`, `@TableId`, and explicit Java types matching the SQL schema. Be careful with nullable foreign keys such as `driver.car_id`.
- Catching broad database exceptions only to return a guessed message is discouraged. Prefer precondition checks and let `DataIntegrityViolationException` become the standard `409` response where appropriate.

## Logging and diagnostics

- `TraceIdFilter` accepts a safe incoming `X-Trace-Id` or generates one. Preserve it in the response header, `ApiResponse.traceId`, and MDC.
- `RequestLoggingFilter` logs method, path, status, duration, and authenticated user ID. Do not log query values, request/response bodies, passwords, JWTs, refresh tokens, or sensitive personal data.
- Use SLF4J (`LoggerFactory`), never `System.out.println` in application code.
- `ApplicationStartupLogger` logs the Swagger UI and OpenAPI URLs after the application is ready. Keep this behavior intact when changing server, context-path, or springdoc configuration.
- Add contextual information through structured log arguments rather than string concatenation.

## Configuration

- Required production environment variables include `SERVER_PORT`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRE`, `JWT_REFRESH_EXPIRE`, `AMAP_KEY`, and `UPLOAD_PATH`.
- `JWT_SECRET` is mandatory. Never commit a real secret or add an insecure production default.
- Add new configuration under a named prefix in `application.yaml`, bind it with `@ConfigurationProperties`, validate it, and give tests profile-specific values in `application-test.yaml`.
- Jackson is configured for non-null output, `yyyy-MM-dd HH:mm:ss`, and `Asia/Shanghai`; preserve these API conventions.
- Multipart limits are 20 MB per file and 50 MB per request. Change them only with an explicit product requirement.

## OpenAPI and dependency compatibility

- Swagger UI: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.
- `springdoc-openapi-starter-webmvc-ui` must stay on a Spring Boot 3.5 / Spring 6.2 compatible 2.8.x release. Do not downgrade it to 2.6.x: that causes `NoSuchMethodError` for `ControllerAdviceBean` when `/v3/api-docs` is requested.
- When changing controllers, exception advice, or springdoc dependencies, verify `/v3/api-docs` through MockMvc or a running application, not merely that the context starts.

## Build, test, and run

Use the Maven Wrapper, never a system Maven installation.

### Windows PowerShell with the workspace JDK 21

```powershell
$env:JAVA_HOME='D:\allwork\Course2025\dev\jdk21.0.5'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

### Unix-like shells

```bash
./mvnw test
./mvnw clean package
./mvnw spring-boot:run
```

- Run `mvnw.cmd test` after every meaningful code change. At minimum, compilation, JWT tests, application context startup, common error response behavior, and OpenAPI generation must pass.
- Do not rely on Java 17: the project compiles with `--release 21`.
- The test profile excludes real datasource/MyBatis auto-configuration and supplies mapper mocks. Do not make tests require a local MySQL instance unless adding a dedicated integration-test profile.

## Agent workflow

1. Inspect relevant controller, DTO, entity, mapper, service, configuration, test, and `create_sql.md` before implementation.
2. Reuse existing conventions and keep scope narrow. Do not perform unrelated formatting or dependency churn.
3. Before writing an endpoint, decide its route, request DTO, response DTO, role, transaction boundary, status codes, OpenAPI documentation, and tests.
4. Before editing, check `git status --short`; preserve dirty user changes.
5. Use `apply_patch` for manual edits. Prefer `rg` and `rg --files` for discovery.
6. After implementation, run focused tests and then the Maven test suite with JDK 21. Report verification limits honestly.
7. If an API or schema decision materially changes product behavior and cannot be inferred from existing code or `create_sql.md`, ask the user before inventing it.
