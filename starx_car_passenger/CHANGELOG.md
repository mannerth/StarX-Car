# Changelog

## Unreleased

- Add passenger phone-number display and update flow through `/api/passengers/me`, including refresh of the backend-maintained masked number.

- Rewrite README with the implemented passenger authentication/profile workflow, Riverpod structure, backend endpoint mapping, Android configuration, validation commands and remaining business scope.

- Split the Flutter entry-file implementation into app, core providers, authentication feature and profile feature layers.
- Fix template-test imports and all analyzer errors introduced during the initial client implementation.
- Pin the Android path-provider implementation to avoid the incompatible `jni` Gradle plugin during Android builds.
- Delay disposal of profile-dialog text controllers until dialog exit animations have finished, preventing focused fields from using a disposed controller.

- Add Riverpod-managed passenger login, registration, encrypted session restoration/logout and API error presentation.
- Add passenger personal center for viewing/updating base profile and changing password with current-password verification.
- Enable Android local HTTP development traffic for the configurable backend API address.
- Replace the Flutter counter template test with a passenger app-root smoke test.

- Configure the Android package ID as `org.mannerth.starxcar.passenger` and connect the release build to the local shared signing certificate.
- Document the passenger-app responsibility boundary, including ride-hailing, trip, payment and evaluation workflows, and its dependency on `starx_car_common`.
