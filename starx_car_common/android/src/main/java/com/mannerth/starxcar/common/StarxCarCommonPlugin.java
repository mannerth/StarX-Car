package com.mannerth.starxcar.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.Path;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/** Native bridge for AMap Android route search and runtime location permission. */
public final class StarxCarCommonPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler,
        ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    private static final String CHANNEL = "starx_car_common/amap_route";
    private static final String PERMISSION_CHANNEL = "starx_car_common/location_permission";
    private static final int LOCATION_PERMISSION_REQUEST = 4401;
    private MethodChannel channel;
    private MethodChannel permissionChannel;
    private Context context;
    private Activity activity;
    private MethodChannel.Result pendingPermissionResult;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler(this);
        permissionChannel = new MethodChannel(binding.getBinaryMessenger(), PERMISSION_CHANNEL);
        permissionChannel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) channel.setMethodCallHandler(null);
        channel = null;
        if (permissionChannel != null) permissionChannel.setMethodCallHandler(null);
        permissionChannel = null;
        context = null;
        activity = null;
        pendingPermissionResult = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                              @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            return false;
        }
        MethodChannel.Result result = pendingPermissionResult;
        pendingPermissionResult = null;
        if (result == null || activity == null) {
            return false;
        }
        boolean granted = hasLocationPermission();
        if (granted) {
            result.success("granted");
            return true;
        }
        boolean permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        result.success(permanentlyDenied ? "permanentlyDenied" : "denied");
        return true;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if ("searchPoi".equals(call.method)) {
            searchPoi(call, result, false);
            return;
        }
        if ("searchPoiPage".equals(call.method)) {
            searchPoi(call, result, true);
            return;
        }
        if ("reverseGeocode".equals(call.method)) {
            reverseGeocode(call, result);
            return;
        }
        if ("isLocationGranted".equals(call.method)) {
            result.success(hasLocationPermission());
            return;
        }
        if ("requestLocation".equals(call.method)) {
            requestLocation(result);
            return;
        }
        if (!"planDriving".equals(call.method) && !"planWalking".equals(call.method)) {
            result.notImplemented();
            return;
        }
        if (!(call.arguments instanceof Map)) {
            result.error("INVALID_ARGUMENT", "路线参数格式无效", null);
            return;
        }
        Map<?, ?> args = (Map<?, ?>) call.arguments;
        try {
            double originLat = number(args.get("originLat"));
            double originLng = number(args.get("originLng"));
            double destinationLat = number(args.get("destinationLat"));
            double destinationLng = number(args.get("destinationLng"));
            MapsInitializer.updatePrivacyShow(context, true, true);
            MapsInitializer.updatePrivacyAgree(context, true);
            RouteSearch search = new RouteSearch(context);
            boolean walking = "planWalking".equals(call.method);
            search.setRouteSearchListener(new RouteSearch.OnRouteSearchListener() {
                @Override
                public void onDriveRouteSearched(DriveRouteResult routeResult, int code) {
                    if (walking || code != AMapException.CODE_AMAP_SUCCESS || routeResult == null
                            || routeResult.getPaths() == null || routeResult.getPaths().isEmpty()) {
                        if (!walking) result.error("AMAP_ROUTE", "高德驾车路线规划失败", code);
                        return;
                    }
                    result.success(toRouteResponse(routeResult.getPaths().get(0)));
                }

                @Override
                public void onWalkRouteSearched(WalkRouteResult routeResult, int code) {
                    if (!walking || code != AMapException.CODE_AMAP_SUCCESS || routeResult == null
                            || routeResult.getPaths() == null || routeResult.getPaths().isEmpty()) {
                        if (walking) result.error("AMAP_WALK", "高德步行路线规划失败", code);
                        return;
                    }
                    result.success(toRouteResponse(routeResult.getPaths().get(0)));
                }

                @Override
                public void onBusRouteSearched(com.amap.api.services.route.BusRouteResult value, int code) {
                }

                @Override
                public void onRideRouteSearched(com.amap.api.services.route.RideRouteResult value, int code) {
                }
            });
            RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                    new LatLonPoint(originLat, originLng),
                    new LatLonPoint(destinationLat, destinationLng));
            if (walking) {
                RouteSearch.WalkRouteQuery query = new RouteSearch.WalkRouteQuery(
                        fromAndTo, RouteSearch.WALK_DEFAULT);
                query.setExtensions(RouteSearch.EXTENSIONS_ALL);
                search.calculateWalkRouteAsyn(query);
            } else {
                RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                        fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
                query.setExtensions(RouteSearch.EXTENSIONS_ALL);
                search.calculateDriveRouteAsyn(query);
            }
        } catch (AMapException exception) {
            result.error("AMAP_ROUTE", "高德路线服务初始化失败", exception.getErrorCode());
        } catch (IllegalArgumentException exception) {
            result.error("INVALID_ARGUMENT", "路线坐标无效", null);
        }
    }

    private void requestLocation(MethodChannel.Result result) {
        if (hasLocationPermission()) {
            result.success("granted");
            return;
        }
        if (activity == null) {
            result.error("NO_ACTIVITY", "当前无法请求定位权限", null);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            result.success("granted");
            return;
        }
        if (pendingPermissionResult != null) {
            result.error("BUSY", "已有定位权限请求进行中", null);
            return;
        }
        pendingPermissionResult = result;
        // Android 12+ requires coarse and fine in the same request to offer precise location.
        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    private boolean hasLocationPermission() {
        if (activity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void searchPoi(MethodCall call, MethodChannel.Result result, boolean paged) {
        if (!(call.arguments instanceof Map)) {
            result.error("INVALID_ARGUMENT", "POI 搜索参数格式无效", null);
            return;
        }
        Map<?, ?> args = (Map<?, ?>) call.arguments;
        try {
            String keyword = String.valueOf(args.get("keyword")).trim();
            if (keyword.isEmpty()) throw new IllegalArgumentException("keyword");
            String city = args.get("city") == null ? "" : String.valueOf(args.get("city"));
            int page = args.get("page") instanceof Number ? ((Number) args.get("page")).intValue() : 0;
            int pageSize = args.get("pageSize") instanceof Number ? ((Number) args.get("pageSize")).intValue() : 20;
            if (page < 0 || pageSize < 1 || pageSize > 50) throw new IllegalArgumentException("page");
            PoiSearch.Query query = new PoiSearch.Query(keyword, "", city);
            query.setPageSize(pageSize);
            query.setPageNum(page);
            query.setDistanceSort(true);
            query.setCityLimit(!city.isEmpty());
            Double lat = optionalNumber(args.get("latitude"));
            Double lng = optionalNumber(args.get("longitude"));
            if (lat != null && lng != null) {
                LatLonPoint center = new LatLonPoint(lat, lng);
                query.setLocation(center);
            }
            PoiSearch search = new PoiSearch(context, query);
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult poiResult, int code) {
                    if (code != AMapException.CODE_AMAP_SUCCESS || poiResult == null) {
                        result.error("AMAP_POI", "高德 POI 搜索失败", code);
                        return;
                    }
                    List<Map<String, Object>> values = new ArrayList<>();
                    if (poiResult.getPois() != null) {
                        for (com.amap.api.services.core.PoiItem item : poiResult.getPois()) {
                            LatLonPoint point = item.getLatLonPoint();
                            if (point == null) continue;
                            Map<String, Object> value = new HashMap<>();
                            value.put("poiId", item.getPoiId());
                            value.put("name", item.getTitle());
                            value.put("address", item.getSnippet());
                            value.put("cityName", item.getCityName());
                            value.put("districtName", item.getAdName());
                            if (lat != null && lng != null && item.getDistance() >= 0) {
                                value.put("distanceM", item.getDistance());
                            }
                            value.put("latitude", point.getLatitude());
                            value.put("longitude", point.getLongitude());
                            values.add(value);
                        }
                    }
                    if (!paged) {
                        result.success(values);
                        return;
                    }
                    Map<String, Object> response = new HashMap<>();
                    response.put("page", page);
                    response.put("pageSize", pageSize);
                    response.put("hasMore", page + 1 < poiResult.getPageCount());
                    response.put("records", values);
                    result.success(response);
                }

                @Override
                public void onPoiItemSearched(com.amap.api.services.core.PoiItem value, int code) {
                }
            });
            search.searchPOIAsyn();
        } catch (AMapException exception) {
            result.error("AMAP_POI", "高德 POI 服务初始化失败", exception.getErrorCode());
        } catch (IllegalArgumentException exception) {
            result.error("INVALID_ARGUMENT", "POI 搜索参数无效", null);
        }
    }

    private void reverseGeocode(MethodCall call, MethodChannel.Result result) {
        if (!(call.arguments instanceof Map)) {
            result.error("INVALID_ARGUMENT", "逆地理编码参数格式无效", null);
            return;
        }
        Map<?, ?> args = (Map<?, ?>) call.arguments;
        try {
            double latitude = number(args.get("latitude"));
            double longitude = number(args.get("longitude"));
            GeocodeSearch search = new GeocodeSearch(context);
            search.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(RegeocodeResult value, int code) {
                    if (code != AMapException.CODE_AMAP_SUCCESS || value == null
                            || value.getRegeocodeAddress() == null) {
                        result.error("AMAP_GEOCODE", "高德逆地理编码失败", code);
                        return;
                    }
                    RegeocodeAddress address = value.getRegeocodeAddress();
                    Map<String, Object> response = new HashMap<>();
                    response.put("name", address.getFormatAddress());
                    response.put("address", address.getFormatAddress());
                    String cityName = address.getCity();
                    if (cityName == null || cityName.isEmpty()) cityName = address.getProvince();
                    response.put("cityName", cityName);
                    response.put("districtName", address.getDistrict());
                    result.success(response);
                }

                @Override
                public void onGeocodeSearched(GeocodeResult value, int code) {
                }
            });
            RegeocodeQuery query = new RegeocodeQuery(
                    new LatLonPoint(latitude, longitude), 100, GeocodeSearch.AMAP);
            query.setExtensions(GeocodeSearch.EXTENSIONS_ALL);
            search.getFromLocationAsyn(query);
        } catch (AMapException exception) {
            result.error("AMAP_GEOCODE", "高德逆地理编码服务初始化失败", exception.getErrorCode());
        } catch (IllegalArgumentException exception) {
            result.error("INVALID_ARGUMENT", "逆地理编码坐标无效", null);
        }
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("coordinate");
        return ((Number) value).doubleValue();
    }

    private static Double optionalNumber(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static Map<String, Object> toRouteResponse(Path path) {
        List<List<Double>> points = new ArrayList<>();
        List<LatLonPoint> pathPoints = path.getPolyline();
        if (pathPoints != null) addPoints(points, pathPoints);
        if (points.isEmpty() && path instanceof DrivePath) {
            List<DriveStep> steps = ((DrivePath) path).getSteps();
            if (steps != null) {
                for (DriveStep step : steps) {
                    if (step != null && step.getPolyline() != null) addPoints(points, step.getPolyline());
                }
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("distanceM", Math.round(path.getDistance()));
        response.put("durationS", path.getDuration());
        response.put("points", points);
        return response;
    }

    private static void addPoints(List<List<Double>> target, List<LatLonPoint> source) {
        for (LatLonPoint point : source) {
            if (point == null) continue;
            List<Double> value = new ArrayList<>(2);
            value.add(point.getLatitude());
            value.add(point.getLongitude());
            target.add(value);
        }
    }
}

