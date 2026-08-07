package org.mannerth.starxcar.driver

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "starx_car_driver/foreground"
        private const val START = "start"
        private const val STOP = "stop"
        private const val RUNTIME_PERMISSIONS_REQUEST = 2402
    }

    private var pendingStartResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    START -> {
                        requestPermissionsAndStart(result)
                    }
                    STOP -> {
                        val intent = Intent(this, DriverForegroundService::class.java)
                            .setAction(DriverForegroundService.ACTION_STOP)
                        startService(intent)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun requestPermissionsAndStart(result: MethodChannel.Result) {
        if (pendingStartResult != null) {
            result.error("PERMISSION_REQUEST_IN_PROGRESS", "正在请求上线所需权限", null)
            return
        }

        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            // Android 12+ requires coarse and fine location to be requested together.
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isNotEmpty()) {
            pendingStartResult = result
            requestPermissions(
                permissions.distinct().toTypedArray(),
                RUNTIME_PERMISSIONS_REQUEST,
            )
            return
        }
        startDriverForegroundService(result)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RUNTIME_PERMISSIONS_REQUEST) return
        val result = pendingStartResult ?: return
        pendingStartResult = null

        val hasPreciseLocation =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasNotifications =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPreciseLocation || !hasNotifications) {
            result.error(
                "RUNTIME_PERMISSIONS",
                "请一次允许精确定位和通知权限后再上线",
                null,
            )
            return
        }
        startDriverForegroundService(result)
    }

    private fun startDriverForegroundService(result: MethodChannel.Result) {
        try {
            val intent = Intent(this, DriverForegroundService::class.java)
                .setAction(DriverForegroundService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            result.success(null)
        } catch (error: SecurityException) {
            result.error("FOREGROUND_PERMISSION", "缺少前台定位服务权限", error.message)
        } catch (error: Exception) {
            result.error("FOREGROUND_SERVICE", "无法启动前台定位服务", error.message)
        }
    }
}
