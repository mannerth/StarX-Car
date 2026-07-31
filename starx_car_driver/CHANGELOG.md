# Changelog

## Unreleased

- Add display and edit flow for driver username, email and gender through `/api/users/me`; the profile now exposes all currently supported account and driver fields.

- Rewrite README with the implemented driver authentication/profile/vehicle workflow, modal-controller safety, API mapping, Android signing behavior, validation commands and pending business scope.

- Split the Flutter entry-file implementation into app, core providers, authentication and driver-home feature layers.
- Fix the malformed initial widget tree, update template-test imports and clear analyzer errors.
- Pin the Android path-provider implementation to avoid the incompatible `jni` Gradle plugin during Android builds.
- Allow debug APK assembly without local release signing credentials; release signing remains applied whenever `android/key.properties` is present.
- Move driver dialog text-controller ownership into stateful dialog widgets so controllers remain valid through focus and route-exit animations.

- Add Riverpod-managed driver login, registration, encrypted session restoration/logout and API error presentation.
- Add driver personal center for account, phone number, driving licence and password updates.
- Add create-and-bind and edit flows for the driver's currently bound vehicle.
- Enable Android local HTTP development traffic for the configurable backend API address.
- Replace the Flutter counter template test with a driver app-root smoke test.

- Configure the Android package ID as `org.mannerth.starxcar.driver` and connect the release build to the local shared signing certificate.
- Document the driver-app responsibility boundary, including work status, order handling, navigation, location reporting and income workflows, and its dependency on `starx_car_common`.
