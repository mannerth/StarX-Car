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
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    START -> {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    2402,
                                )
                                result.error(
                                    "NOTIFICATION_PERMISSION",
                                    "请允许司机端通知后再上线",
                                    null,
                                )
                                return@setMethodCallHandler
                            }
                            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                                PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                    2403,
                                )
                                result.error(
                                    "LOCATION_PERMISSION",
                                    "请允许定位权限后再上线",
                                    null,
                                )
                                return@setMethodCallHandler
                            }
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
}
