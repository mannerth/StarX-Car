import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:starx_car_common/starx_car_common.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('starx_car_common/amap_route');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test(
    'nationwide POI search forwards pagination and parses page metadata',
    () async {
      MethodCall? invocation;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            invocation = call;
            return <String, Object>{
              'page': 1,
              'pageSize': 10,
              'hasMore': true,
              'records': <Map<String, Object>>[
                {
                  'poiId': 'B001',
                  'name': '杭州东站',
                  'address': '全福桥路2号',
                  'cityName': '杭州市',
                  'districtName': '上城区',
                  'distanceM': 1250,
                  'latitude': 30.2911,
                  'longitude': 120.2123,
                },
              ],
            };
          });

      final result = await const AmapSearchService().searchPoiPage(
        ' 火车站 ',
        near: const GeoPoint(latitude: 30.2741, longitude: 120.1551),
        page: 1,
        pageSize: 10,
      );

      expect(invocation?.method, 'searchPoiPage');
      expect(invocation?.arguments, {
        'keyword': '火车站',
        'page': 1,
        'pageSize': 10,
        'latitude': 30.2741,
        'longitude': 120.1551,
      });
      expect(
        (invocation?.arguments as Map<Object?, Object?>).containsKey('city'),
        isFalse,
      );
      expect(result.page, 1);
      expect(result.pageSize, 10);
      expect(result.hasMore, isTrue);
      expect(result.records.single.name, '杭州东站');
      expect(result.records.single.distanceM, 1250);
      expect(
        result.records.single.location.coordinateSystem,
        CoordinateSystem.gcj02,
      );
    },
  );
}
