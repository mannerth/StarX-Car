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
  bool _selectingPickup = true;
  OrderQuote? _quote;
  bool _loading = false;
  bool _locating = false;
  GeoPoint _mapCenter = _defaultPoint;
  AmapLocationService? _locationService;
  CancelToken? _cancelToken;

  @override
  void dispose() {
    _cancelToken?.cancel('booking page disposed');
    final locationService = _locationService;
    if (locationService != null) unawaited(locationService.dispose());
    super.dispose();
  }

  Place _place(GeoPoint point, String label) =>
      Place(name: label, location: point);

  Future<void> _selectFromSearch() async {
    final place = await showDialog<Place>(
      context: context,
      builder: (_) => _PlaceSearchDialog(near: _defaultPoint),
    );
    if (place == null || !mounted) return;
    setState(() {
      if (_selectingPickup) {
        _pickup = place;
      } else {
        _destination = place;
      }
      _quote = null;
    });
  }

  Future<void> _selectFromMap(GeoPoint point) async {
    final selectingPickup = _selectingPickup;
    final fallback = _place(point, selectingPickup ? '上车点' : '目的地');
    setState(() {
      if (selectingPickup) {
        _pickup = fallback;
      } else {
        _destination = fallback;
      }
      _quote = null;
    });
    try {
      final place = await const AmapSearchService().reverseGeocode(point);
      if (!mounted || place == null) return;
      setState(() {
        if (selectingPickup) {
          _pickup = place;
        } else {
          _destination = place;
        }
      });
    } on Object catch (_) {
      // The coordinate remains usable when reverse geocoding is unavailable.
    }
  }

  Future<void> _useCurrentLocation() async {
    if (_locating) return;
    setState(() => _locating = true);
    final service = _locationService ??= AmapLocationService();
    try {
      final update = await service.locateOnce();
      if (!mounted) return;
      final point = update.point;
      if (point == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(update.errorMessage ?? '无法获取当前位置，请检查定位权限和 GPS。'),
          ),
        );
        return;
      }
      final selectingPickup = _selectingPickup;
      setState(() {
        _mapCenter = point;
        final place = _place(point, '当前位置');
        if (selectingPickup) {
          _pickup = place;
        } else {
          _destination = place;
        }
        _quote = null;
      });
    } on TimeoutException {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('定位超时，请检查 GPS 后重试。')));
      }
    } on Object catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('定位失败，请检查定位权限和 GPS。')));
      }
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  Future<void> _quoteRide() async {
    if (_pickup == null || _destination == null) return;
    final validationError = _validatePlaces(_pickup!, _destination!);
    if (validationError != null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(validationError)));
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
      if (error.cancelled) return;
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
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
      if (error.cancelled) return;
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (identical(_cancelToken, cancelToken)) _cancelToken = null;
      if (mounted) setState(() => _loading = false);
    }
  }

  void _cancelRequest() => _cancelToken?.cancel('user cancelled');

  @override
  Widget build(BuildContext context) {
    final markers = [
      _pickup,
      _destination,
    ].whereType<Place>().map((place) => place.location).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('即时叫车')),
      body: Column(
        children: [
          Expanded(
            child: AmapMapView(
              initialPoint: _mapCenter,
              markers: markers,
              onTap: _selectFromMap,
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () =>
                            setState(() => _selectingPickup = true),
                        child: Text(_pickup == null ? '选择上车点' : '上车点已选择'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () =>
                            setState(() => _selectingPickup = false),
                        child: Text(_destination == null ? '选择目的地' : '目的地已选择'),
                      ),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Expanded(
                      child: TextButton.icon(
                        onPressed: _selectFromSearch,
                        icon: const Icon(Icons.search),
                        label: const Text('搜索地点'),
                      ),
                    ),
                    Expanded(
                      child: TextButton.icon(
                        onPressed: _locating ? null : _useCurrentLocation,
                        icon: _locating
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.my_location),
                        label: Text(_locating ? '定位中' : '使用当前位置'),
                      ),
                    ),
                  ],
                ),
                if (_quote != null)
                  Text(
                    '预估 ¥${_quote!.fare.totalFare} · ${(_quote!.estimatedDistanceM / 1000).toStringAsFixed(1)} 公里',
                  ),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton(
                        onPressed:
                            _loading || _pickup == null || _destination == null
                            ? null
                            : _quoteRide,
                        child: Text(_loading ? '处理中…' : '获取报价'),
                      ),
                    ),
                    if (_loading)
                      IconButton(
                        tooltip: '取消请求',
                        onPressed: _cancelRequest,
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
        ],
      ),
    );
  }
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

class _PlaceSearchDialog extends StatefulWidget {
  const _PlaceSearchDialog({required this.near});

  final GeoPoint near;

  @override
  State<_PlaceSearchDialog> createState() => _PlaceSearchDialogState();
}

class _PlaceSearchDialogState extends State<_PlaceSearchDialog> {
  final _controller = TextEditingController();
  List<Place> _results = const [];
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final keyword = _controller.text.trim();
    if (keyword.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await const AmapSearchService().searchPoi(
        keyword,
        near: widget.near,
      );
      if (mounted) setState(() => _results = results);
    } on Object catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('搜索地点'),
    content: SizedBox(
      width: double.maxFinite,
      height: 360,
      child: Column(
        children: [
          TextField(
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _search(),
            decoration: InputDecoration(
              hintText: '输入地点名称',
              suffixIcon: IconButton(
                tooltip: '搜索',
                onPressed: _loading ? null : _search,
                icon: const Icon(Icons.search),
              ),
            ),
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_error!, textAlign: TextAlign.center),
            ),
          Expanded(
            child: _results.isEmpty
                ? const Center(child: Text('输入关键词搜索高德地点'))
                : ListView.builder(
                    itemCount: _results.length,
                    itemBuilder: (_, index) {
                      final place = _results[index];
                      return ListTile(
                        leading: const Icon(Icons.location_on_outlined),
                        title: Text(place.name),
                        subtitle: place.address == null
                            ? null
                            : Text(place.address!),
                        onTap: () => Navigator.pop(context, place),
                      );
                    },
                  ),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('关闭'),
      ),
    ],
  );
}
