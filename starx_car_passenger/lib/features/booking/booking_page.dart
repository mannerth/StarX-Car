import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:starx_car_common/starx_car_common.dart';

import '../../core/providers.dart';
import '../orders/passenger_orders_page.dart';

class PassengerBookingPage extends ConsumerStatefulWidget {
  const PassengerBookingPage({super.key});

  @override
  ConsumerState<PassengerBookingPage> createState() =>
      _PassengerBookingPageState();
}

class _PassengerBookingPageState extends ConsumerState<PassengerBookingPage> {
  static const _defaultPoint = GeoPoint(
    latitude: 39.916527,
    longitude: 116.397128,
  );

  Place? _pickup;
  Place? _destination;
  OrderQuote? _quote;
  AmapDrivingRoute? _route;
  bool _loading = false;
  bool _locating = false;
  bool _routing = false;
  String? _routeError;
  GeoPoint _mapCenter = _defaultPoint;
  bool _hasSearchCenter = false;
  String? _searchCity;
  AmapLocationService? _locationService;
  CancelToken? _cancelToken;
  int _routeRequest = 0;

  @override
  void initState() {
    super.initState();
    // 登录后首次进入叫车页先请求定位权限，再发起首次定位；
    // 避免高德定位 SDK 因未授权直接失败而需要用户手动去系统设置开启。
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _requestPermissionAndLocate(),
    );
  }

  Future<void> _requestPermissionAndLocate() async {
    final status = await const AmapPermissionService().requestLocation();
    if (!mounted) return;
    switch (status) {
      case LocationPermissionStatus.granted:
        await _locate(initial: true);
      case LocationPermissionStatus.denied:
        _showMessage('未获得定位权限，将无法自动定位当前位置，可在页面右下角重试。');
      case LocationPermissionStatus.permanentlyDenied:
        _showMessage('定位权限已被永久拒绝，请到系统设置中开启定位权限。');
    }
  }

  @override
  void dispose() {
    _routeRequest++;
    _cancelToken?.cancel('booking page disposed');
    final locationService = _locationService;
    if (locationService != null) unawaited(locationService.dispose());
    super.dispose();
  }

  Future<void> _choosePlace({required bool pickup}) async {
    final place = await Navigator.of(context).push<Place>(
      MaterialPageRoute<Place>(
        builder: (_) => _PlaceSearchPage(
          title: pickup ? '选择上车地' : '选择目的地',
          near: _hasSearchCenter ? _mapCenter : null,
          city: _searchCity,
        ),
      ),
    );
    if (place == null || !mounted) return;
    _setPlace(place, pickup: pickup);
  }

  void _setPlace(Place place, {required bool pickup}) {
    setState(() {
      if (pickup) {
        _pickup = place;
        _searchCity ??= place.cityName;
      } else {
        _destination = place;
      }
      _mapCenter = place.location;
      _hasSearchCenter = true;
      _quote = null;
    });
    unawaited(_planRoute());
  }

  Future<void> _locate({bool initial = false}) async {
    if (_locating) return;
    setState(() => _locating = true);
    final service = _locationService ??= AmapLocationService();
    try {
      final update = await service.locateOnce();
      if (!mounted) return;
      final point = update.point;
      if (point == null) {
        _showMessage(update.errorMessage ?? '无法获取当前位置，请检查定位权限和 GPS。');
        return;
      }
      Place place = Place(name: '当前位置', location: point);
      try {
        place = await const AmapSearchService().reverseGeocode(point) ?? place;
      } on Object {
        // A coordinate is still useful when an address lookup is unavailable.
      }
      if (!mounted) return;
      if (initial && (_pickup != null || _destination != null)) return;
      setState(() {
        _mapCenter = point;
        _hasSearchCenter = true;
        _searchCity = place.cityName;
        _pickup ??= place;
        _quote = null;
      });
      unawaited(_planRoute());
    } on TimeoutException {
      if (mounted) _showMessage('定位超时，请检查 GPS 后重试。');
    } on Object {
      if (mounted) _showMessage('定位失败，请检查定位权限和 GPS。');
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  Future<void> _planRoute() async {
    final pickup = _pickup;
    final destination = _destination;
    final request = ++_routeRequest;
    if (pickup == null || destination == null) {
      if (mounted) {
        setState(() {
          _route = null;
          _routeError = null;
          _routing = false;
        });
      }
      return;
    }
    final validationError = _validatePlaces(pickup, destination);
    if (validationError != null) {
      setState(() {
        _route = null;
        _routeError = validationError;
        _routing = false;
      });
      return;
    }
    setState(() {
      _routing = true;
      _routeError = null;
    });
    try {
      final route = await const AmapRouteService().planDriving(
        origin: pickup.location,
        destination: destination.location,
      );
      if (!mounted || request != _routeRequest) return;
      setState(() => _route = route);
    } on Object {
      if (!mounted || request != _routeRequest) return;
      setState(() {
        _route = null;
        _routeError = '推荐路线加载失败，可重试或重新选择地点。';
      });
    } finally {
      if (mounted && request == _routeRequest) setState(() => _routing = false);
    }
  }

  Future<void> _quoteRide() async {
    if (_pickup == null || _destination == null) return;
    final validationError = _validatePlaces(_pickup!, _destination!);
    if (validationError != null) {
      _showMessage(validationError);
      return;
    }
    final cancelToken = CancelToken();
    _cancelToken = cancelToken;
    setState(() => _loading = true);
    try {
      final quote = await ref
          .read(passengerOrderRepositoryProvider)
          .quote(
            pickup: _pickup!,
            destination: _destination!,
            cancelToken: cancelToken,
          );
      if (mounted) setState(() => _quote = quote);
    } on ApiException catch (error) {
      if (!error.cancelled && mounted) _showMessage(error.message);
    } finally {
      if (identical(_cancelToken, cancelToken)) _cancelToken = null;
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createRide() async {
    if (_pickup == null || _destination == null) return;
    final cancelToken = CancelToken();
    _cancelToken = cancelToken;
    setState(() => _loading = true);
    try {
      final created = await ref
          .read(passengerOrderRepositoryProvider)
          .create(
            pickup: _pickup!,
            destination: _destination!,
            cancelToken: cancelToken,
          );
      if (!mounted) return;
      ref.invalidate(passengerOrdersProvider);
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => PassengerOrderDetailPage(orderNo: created.orderNo),
        ),
      );
      if (mounted) setState(() => _quote = null);
    } on ApiException catch (error) {
      if (!error.cancelled && mounted) _showMessage(error.message);
    } finally {
      if (identical(_cancelToken, cancelToken)) _cancelToken = null;
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final markers = [
      _pickup,
      _destination,
    ].whereType<Place>().map((place) => place.location).toList();
    final routePoints = _route?.points ?? const <GeoPoint>[];
    final viewportPoints = routePoints.isNotEmpty ? routePoints : markers;
    return Scaffold(
      appBar: AppBar(title: const Text('即时叫车')),
      body: Column(
        children: [
          Material(
            color: Theme.of(context).colorScheme.surface,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: Column(
                children: [
                  _PlaceSelector(
                    label: '上车地',
                    place: _pickup,
                    color: Colors.green.shade700,
                    onTap: () => _choosePlace(pickup: true),
                  ),
                  const Divider(height: 1, indent: 32),
                  _PlaceSelector(
                    label: '目的地',
                    place: _destination,
                    color: Colors.red.shade700,
                    onTap: () => _choosePlace(pickup: false),
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            child: Stack(
              children: [
                Positioned.fill(
                  child: AmapMapView(
                    initialPoint: _mapCenter,
                    markers: markers,
                    routePoints: routePoints,
                    viewportPoints: viewportPoints,
                  ),
                ),
                Positioned(
                  right: 12,
                  bottom: 16,
                  child: FloatingActionButton.small(
                    heroTag: 'passenger-current-location',
                    tooltip: '定位到当前位置',
                    onPressed: _locating ? null : _locate,
                    child: _locating
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.my_location),
                  ),
                ),
                if (_routing) const LinearProgressIndicator(),
              ],
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (_routeError != null)
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            _routeError!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                        if (_pickup != null &&
                            _destination != null &&
                            _validatePlaces(_pickup!, _destination!) == null)
                          TextButton(
                            onPressed: _planRoute,
                            child: const Text('重试'),
                          ),
                      ],
                    )
                  else if (_route != null)
                    Text(
                      '推荐路线 · ${(_route!.distanceM / 1000).toStringAsFixed(1)} 公里 · 约 ${(_route!.durationS / 60).ceil()} 分钟',
                    ),
                  if (_quote != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        '预估 ¥${_quote!.fare.totalFare}',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton(
                          onPressed:
                              _loading ||
                                  _pickup == null ||
                                  _destination == null
                              ? null
                              : _quoteRide,
                          child: Text(_loading ? '处理中...' : '获取报价'),
                        ),
                      ),
                      if (_loading)
                        IconButton(
                          tooltip: '取消请求',
                          onPressed: () =>
                              _cancelToken?.cancel('user cancelled'),
                          icon: const Icon(Icons.close),
                        ),
                      if (_quote != null) ...[
                        const SizedBox(width: 8),
                        Expanded(
                          child: FilledButton.tonal(
                            onPressed: _loading ? null : _createRide,
                            child: const Text('确认叫车'),
                          ),
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PlaceSelector extends StatelessWidget {
  const _PlaceSelector({
    required this.label,
    required this.place,
    required this.color,
    required this.onTap,
  });

  final String label;
  final Place? place;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: SizedBox(
      height: 58,
      child: Row(
        children: [
          Icon(Icons.circle, size: 12, color: color),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: Theme.of(context).textTheme.labelMedium),
                const SizedBox(height: 2),
                Text(
                  place?.name ?? '点击选择$label',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: place == null
                        ? Theme.of(context).colorScheme.onSurfaceVariant
                        : null,
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right),
        ],
      ),
    ),
  );
}

String? _validatePlaces(Place pickup, Place destination) {
  if (pickup.location.coordinateSystem != CoordinateSystem.gcj02 ||
      destination.location.coordinateSystem != CoordinateSystem.gcj02) {
    return '地点坐标不是高德 GCJ-02，无法创建行程。';
  }
  if (pickup.location.latitude == destination.location.latitude &&
      pickup.location.longitude == destination.location.longitude) {
    return '上车点和目的地不能相同。';
  }
  return null;
}

class _PlaceSearchPage extends StatefulWidget {
  const _PlaceSearchPage({required this.title, this.near, this.city});

  final String title;
  final GeoPoint? near;
  final String? city;

  @override
  State<_PlaceSearchPage> createState() => _PlaceSearchPageState();
}

class _PlaceSearchPageState extends State<_PlaceSearchPage> {
  final _controller = TextEditingController();
  List<Place> _results = const [];
  Timer? _searchDebounce;
  bool _loading = false;
  String? _error;
  String? _searchedKeyword;
  int _searchRequest = 0;

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchRequest++;
    _controller.dispose();
    super.dispose();
  }

  void _scheduleSearch(String value) {
    _searchDebounce?.cancel();
    final keyword = value.trim();
    final request = ++_searchRequest;
    setState(() {
      _results = const [];
      _error = null;
      _searchedKeyword = null;
      _loading = false;
    });
    if (keyword.isEmpty) return;
    _searchDebounce = Timer(
      const Duration(milliseconds: 350),
      () => _search(keyword, request),
    );
  }

  void _searchNow() {
    _searchDebounce?.cancel();
    final keyword = _controller.text.trim();
    final request = ++_searchRequest;
    if (keyword.isEmpty) {
      _scheduleSearch('');
      return;
    }
    FocusScope.of(context).unfocus();
    _search(keyword, request);
  }

  Future<void> _search(String keyword, int request) async {
    if (!mounted || request != _searchRequest) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await const AmapSearchService().searchPoi(
        keyword,
        near: widget.near,
        //city: widget.city,
      );
      if (!mounted || request != _searchRequest) return;
      setState(() {
        _results = results;
        _searchedKeyword = keyword;
        _loading = false;
      });
    } on Object {
      if (!mounted || request != _searchRequest) return;
      setState(() {
        _error = '地点搜索失败，请检查网络后重试。';
        _loading = false;
      });
    }
  }

  void _clearQuery() {
    _controller.clear();
    _scheduleSearch('');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: TextField(
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.search,
            onChanged: _scheduleSearch,
            onSubmitted: (_) => _searchNow(),
            decoration: InputDecoration(
              hintText: '输入地点名称',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _controller.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: '清空',
                      onPressed: _clearQuery,
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
        ),
        if (_loading) const LinearProgressIndicator(),
        Expanded(
          child: _error != null
              ? _SearchMessage(
                  icon: Icons.cloud_off_outlined,
                  message: _error!,
                  actionLabel: '重试',
                  onAction: _searchNow,
                )
              : _results.isEmpty
              ? _SearchMessage(
                  icon: _searchedKeyword == null
                      ? Icons.search
                      : Icons.location_off_outlined,
                  message: _emptyMessage(),
                )
              : ListView.separated(
                  itemCount: _results.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (_, index) {
                    final place = _results[index];
                    final details = _placeDetails(place);
                    return ListTile(
                      leading: const Icon(Icons.location_on_outlined),
                      title: Text(place.name),
                      subtitle: details.isEmpty
                          ? null
                          : Text(
                              details,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                      onTap: () => Navigator.pop(context, place),
                    );
                  },
                ),
        ),
      ],
    ),
  );

  String _emptyMessage() {
    if (_loading) return '正在搜索相关地点...';
    final keyword = _controller.text.trim();
    if (keyword.isEmpty) return '输入地点名称或地址';
    if (_searchedKeyword != null) return '未找到“$_searchedKeyword”相关地点';
    return '正在查找相关地点...';
  }

  String _placeDetails(Place place) {
    final location = <String>[
      if (place.cityName?.isNotEmpty == true) place.cityName!,
      if (place.districtName?.isNotEmpty == true) place.districtName!,
    ].join(' · ');
    final details = <String>[
      if (location.isNotEmpty) location,
      if (place.address?.isNotEmpty == true) place.address!,
      if (place.distanceM != null) _distance(place.distanceM!),
    ];
    return details.join(' · ');
  }

  String _distance(int meters) =>
      meters < 1000 ? '$meters 米' : '${(meters / 1000).toStringAsFixed(1)} 公里';
}

class _SearchMessage extends StatelessWidget {
  const _SearchMessage({
    required this.icon,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 32,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 8),
            TextButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    ),
  );
}

