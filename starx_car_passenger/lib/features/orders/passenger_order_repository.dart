import 'package:starx_car_common/starx_car_common.dart';

/// Passenger-only access to immediate order and trip endpoints.
///
/// This repository deliberately contains no polling timer or page state; those
/// belong to the passenger order/trip controllers so their lifecycle can be
/// stopped when a page is hidden or the app enters the background.
class PassengerOrderRepository {
  const PassengerOrderRepository(this._api);

  final ApiClient _api;

  Future<OrderQuote> quote({
    required Place pickup,
    required Place destination,
    CancelToken? cancelToken,
  }) async => OrderQuote.fromJson(
    await _api.post(
          '/orders/quote',
          data: {
            'pickup': pickup.toJson(),
            'destination': destination.toJson(),
          },
          cancelToken: cancelToken,
        )
        as Map<String, dynamic>,
  );

  Future<OrderCreated> create({
    required Place pickup,
    required Place destination,
    CancelToken? cancelToken,
  }) async => OrderCreated.fromJson(
    await _api.post(
          '/orders',
          data: {
            'pickup': pickup.toJson(),
            'destination': destination.toJson(),
          },
          cancelToken: cancelToken,
        )
        as Map<String, dynamic>,
  );

  Future<OrderPage> list({
    int page = 1,
    int size = 20,
    OrderStatus? status,
    CancelToken? cancelToken,
  }) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null && status != OrderStatus.unknown) {
      query['status'] = orderStatusToJson(status);
    }
    final data = await _api.get(
      '/passengers/me/orders',
      queryParameters: query,
      cancelToken: cancelToken,
    );
    return OrderPage.fromJson(data as Map<String, dynamic>);
  }

  Future<OrderDetail> detail(
    String orderNo, {
    CancelToken? cancelToken,
  }) async => OrderDetail.fromJson(
    await _api.get('/orders/$orderNo', cancelToken: cancelToken)
        as Map<String, dynamic>,
  );

  Future<void> cancel(String orderNo, {String? reason}) async => _api.post(
    '/orders/$orderNo/cancel',
    data: reason == null || reason.trim().isEmpty
        ? null
        : {'reason': reason.trim()},
  );

  Future<DriverLocation> driverLocation(
    String orderNo, {
    CancelToken? cancelToken,
  }) async => DriverLocation.fromJson(
    await _api.get('/orders/$orderNo/driver-location', cancelToken: cancelToken)
        as Map<String, dynamic>,
  );

  Future<RouteContext> routeContext(
    String orderNo, {
    CancelToken? cancelToken,
  }) async => RouteContext.fromJson(
    await _api.get('/orders/$orderNo/route-context', cancelToken: cancelToken)
        as Map<String, dynamic>,
  );

  Future<OrderRating> rating(
    String orderNo, {
    CancelToken? cancelToken,
  }) async => OrderRating.fromJson(
    await _api.get('/orders/$orderNo/rating', cancelToken: cancelToken)
        as Map<String, dynamic>,
  );

  Future<OrderRating> submitRating(
    String orderNo, {
    required int rating,
    String? content,
  }) async => OrderRating.fromJson(
    await _api.post(
          '/orders/$orderNo/rating',
          data: {
            'rating': rating,
            if (content != null && content.trim().isNotEmpty)
              'content': content.trim(),
          },
        )
        as Map<String, dynamic>,
  );

  Future<RatingPage> myRatings({
    int page = 1,
    int size = 20,
    CancelToken? cancelToken,
  }) async => RatingPage.fromJson(
    await _api.get(
          '/passengers/me/ratings',
          queryParameters: {'page': page, 'size': size},
          cancelToken: cancelToken,
        )
        as Map<String, dynamic>,
  );
}

class OrderCreated {
  const OrderCreated({
    required this.orderNo,
    required this.status,
    required this.quote,
  });

  final String orderNo;
  final OrderStatus status;
  final OrderQuote quote;

  factory OrderCreated.fromJson(Map<String, dynamic> json) => OrderCreated(
    orderNo: json['orderNo'] as String,
    status: orderStatusFromJson(json['status']),
    quote: OrderQuote.fromJson(json['quote'] as Map<String, dynamic>),
  );
}


/// A passenger's rating of the driver for one completed order.
class OrderRating {
  const OrderRating({
    required this.rated,
    this.rating,
    this.content,
    this.createdAt,
  });

  final bool rated;
  final int? rating;
  final String? content;
  final DateTime? createdAt;

  factory OrderRating.fromJson(Map<String, dynamic> json) => OrderRating(
    rated: json['rated'] as bool? ?? false,
    rating: (json['rating'] as num?)?.toInt(),
    content: json['content'] as String?,
    createdAt: json['createdAt'] == null
        ? null
        : DateTime.tryParse(json['createdAt'].toString()),
  );
}

/// One submitted rating shown in the passenger's rating history.
class RatingSummary {
  const RatingSummary({
    required this.orderNo,
    this.rating,
    this.content,
    this.createdAt,
  });

  final String orderNo;
  final int? rating;
  final String? content;
  final DateTime? createdAt;

  factory RatingSummary.fromJson(Map<String, dynamic> json) => RatingSummary(
    orderNo: json['orderNo'] as String,
    rating: (json['rating'] as num?)?.toInt(),
    content: json['content'] as String?,
    createdAt: json['createdAt'] == null
        ? null
        : DateTime.tryParse(json['createdAt'].toString()),
  );
}

/// Paginated rating history returned by `/passengers/me/ratings`.
class RatingPage {
  const RatingPage({
    required this.page,
    required this.size,
    required this.total,
    required this.records,
  });

  final int page;
  final int size;
  final int total;
  final List<RatingSummary> records;

  factory RatingPage.fromJson(Map<String, dynamic> json) => RatingPage(
    page: (json['page'] as num).toInt(),
    size: (json['size'] as num).toInt(),
    total: (json['total'] as num).toInt(),
    records: ((json['records'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(RatingSummary.fromJson)
        .toList(growable: false)),
  );
}
