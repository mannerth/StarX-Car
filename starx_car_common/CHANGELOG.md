## Unreleased

- Rewrite README to document the implemented session, HTTP, token-refresh, secure-storage and model capabilities, compatibility pinning, boundaries, validation and pending map work.

- Fix the Dio request-header constant used by the shared API client so all three Flutter projects compile.
- Pin `flutter_secure_storage` to the Android-template-compatible 9.x line; 10.x pulls in `jni` 1.x, whose Gradle script is incompatible with the current build.
- Override `path_provider_android` to 2.2.22 for the same Gradle compatibility reason; this removes the remaining Android `jni` plugin from the generated plugin graph.

- Add encrypted session persistence, role-neutral account profile model, API exception model and Dio HTTP transport.
- Add automatic Bearer token injection and single-flight refresh/retry support for 401 responses.

- Document the responsibility boundary of the shared package: common infrastructure and role-neutral models only; passenger and driver workflows remain in their respective apps.
- Document the GCJ-02 coordinate convention and the boundary for future real-time location support.

## 0.0.1

- Initialize the local shared package for the passenger and driver Android apps.
- Add common API response, authentication session, and map location models.
