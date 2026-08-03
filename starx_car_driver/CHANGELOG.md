# Changelog

## Unreleased

- Android Gradle 配置关闭 Kotlin 增量缓存并改用进程内编译，修复 Windows 下工程目录与 Pub 缓存分属不同盘符时 `image_picker_android` 编译失败的问题。

- 基础资料、司机资料和当前车辆 Provider 现在依赖司机认证会话；退出或切换司机后旧缓存会失效，避免展示上一账号的数据。

- 司机个人中心新增头像展示和相册选图上传；头像文件通过 `PUT /api/users/me` 与当前资料一次提交，由后端图床服务保存 URL，取消手工头像 URL 编辑。

- 司机注册和资料页适配真实姓名、头像、身份证号码、驾驶证有效期、审核状态与工作状态。
- 车辆页面改用拆分车牌、登记归属地、核定载客数和出厂年份；不再提交旧版 `carNumber` 请求字段。
- 资料编辑将性别改为中文二选一，驾驶证有效期改用日期选择器，车辆出厂年份改用年份下拉选择；可选证件和日期字段可清空。

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
