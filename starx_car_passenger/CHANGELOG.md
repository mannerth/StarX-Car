# Changelog

## Unreleased

- Android Gradle 配置关闭 Kotlin 增量缓存并改用进程内编译，修复 Windows 下工程目录与 Pub 缓存分属不同盘符时 `image_picker_android` 编译失败的问题。

- 个人资料与乘客资料 Provider 改为依赖当前认证会话；退出或切换账号后会使旧缓存失效，防止新会话短暂展示上一乘客的资料或手机号。

- 个人资料编辑移除手工头像 URL 输入，改用系统相册选择图片并随 `PUT /api/users/me` Multipart 表单上传；个人中心展示后端保存的头像 URL，并保留首字默认头像。

- 注册与个人中心适配真实姓名、头像地址和账号状态字段；公共用户资料模型同步读取新版响应。

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
