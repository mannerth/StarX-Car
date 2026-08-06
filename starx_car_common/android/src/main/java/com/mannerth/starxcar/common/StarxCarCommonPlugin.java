package com.mannerth.starxcar.common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RouteSearch;
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
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/** Native bridge for AMap Android driving route search. */
public final class StarxCarCommonPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
    private static final String CHANNEL = "starx_car_common/amap_route";
    private MethodChannel channel;
    private Context context;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if ("searchPoi".equals(call.method)) {
            searchPoi(call, result);
            return;
        }
        if ("reverseGeocode".equals(call.method)) {
            reverseGeocode(call, result);
            return;
        }
        if (!"planDriving".equals(call.method)) {
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
            search.setRouteSearchListener(new RouteSearch.OnRouteSearchListener() {
                @Override
                public void onDriveRouteSearched(DriveRouteResult routeResult, int code) {
                    if (code != AMapException.CODE_AMAP_SUCCESS || routeResult == null
                            || routeResult.getPaths() == null || routeResult.getPaths().isEmpty()) {
                        result.error("AMAP_ROUTE", "高德驾车路线规划失败", code);
                        return;
                    }
                    result.success(toResponse(routeResult.getPaths().get(0)));
                }

                @Override
                public void onBusRouteSearched(com.amap.api.services.route.BusRouteResult value, int code) {
                }

                @Override
                public void onWalkRouteSearched(com.amap.api.services.route.WalkRouteResult value, int code) {
                }

                @Override
                public void onRideRouteSearched(com.amap.api.services.route.RideRouteResult value, int code) {
                }
            });
            RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                    new LatLonPoint(originLat, originLng),
                    new LatLonPoint(destinationLat, destinationLng));
            RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                    fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
            query.setExtensions(RouteSearch.EXTENSIONS_ALL);
            search.calculateDriveRouteAsyn(query);
        } catch (AMapException exception) {
            result.error("AMAP_ROUTE", "高德路线服务初始化失败", exception.getErrorCode());
        } catch (IllegalArgumentException exception) {
            result.error("INVALID_ARGUMENT", "路线坐标无效", null);
        }
    }

    private void searchPoi(MethodCall call, MethodChannel.Result result) {
        if (!(call.arguments instanceof Map)) {
            result.error("INVALID_ARGUMENT", "POI 搜索参数格式无效", null);
            return;
        }
        Map<?, ?> args = (Map<?, ?>) call.arguments;
        try {
            String keyword = String.valueOf(args.get("keyword")).trim();
            if (keyword.isEmpty()) throw new IllegalArgumentException("keyword");
            String city = args.get("city") == null ? "" : String.valueOf(args.get("city"));
            PoiSearch.Query query = new PoiSearch.Query(keyword, "", city);
            query.setPageSize(20);
            query.setPageNum(0);
            query.setDistanceSort(true);
            Double lat = optionalNumber(args.get("latitude"));
            Double lng = optionalNumber(args.get("longitude"));
            PoiSearch search = new PoiSearch(context, query);
            if (lat != null && lng != null) {
                LatLonPoint center = new LatLonPoint(lat, lng);
                query.setLocation(center);
                search.setBound(new PoiSearch.SearchBound(center, 5000, true));
            }
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
                            value.put("latitude", point.getLatitude());
                            value.put("longitude", point.getLongitude());
                            values.add(value);
                        }
                    }
                    result.success(values);
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

    private static Map<String, Object> toResponse(DrivePath path) {
        List<List<Double>> points = new ArrayList<>();
        List<LatLonPoint> pathPoints = path.getPolyline();
        if (pathPoints != null) addPoints(points, pathPoints);
        if (points.isEmpty() && path.getSteps() != null) {
            for (DriveStep step : path.getSteps()) {
                if (step != null && step.getPolyline() != null) addPoints(points, step.getPolyline());
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
