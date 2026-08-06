import '../map/geo_point.dart';
import '../map/place.dart';

/// Order states returned by the backend. [unknown] keeps older clients
/// readable when the server adds a new terminal or intermediate state.
enum OrderStatus {
  pending,
  accepted,
  driverArrived,
  inService,
  completed,
  cancelled,
  unknown,
}

OrderStatus orderStatusFromJson(Object? value) {
  switch (value?.toString()) {
    case 'PENDING':
      return OrderStatus.pending;
    case 'ACCEPTED':
      return OrderStatus.accepted;
    case 'DRIVER_ARRIVED':
      return OrderStatus.driverArrived;
    case 'IN_SERVICE':
      return OrderStatus.inService;
    case 'COMPLETED':
      return OrderStatus.completed;
    case 'CANCELLED':
      return OrderStatus.cancelled;
    default:
      return OrderStatus.unknown;
  }
}

String orderStatusToJson(OrderStatus value) {
  switch (value) {
    case OrderStatus.pending:
      return 'PENDING';
    case OrderStatus.accepted:
      return 'ACCEPTED';
    case OrderStatus.driverArrived:
      return 'DRIVER_ARRIVED';
    case OrderStatus.inService:
      return 'IN_SERVICE';
    case OrderStatus.completed:
      return 'COMPLETED';
    case OrderStatus.cancelled:
      return 'CANCELLED';
    case OrderStatus.unknown:
      throw StateError('未知订单状态不能用于请求筛选。');
  }
}

DateTime? _dateTime(Object? value) =>
    value == null ? null : DateTime.tryParse(value.toString());

String _decimal(Object? value) => value?.toString() ?? '0';

/// A money breakdown. Decimal values stay as strings to avoid double
/// rounding; the UI can format them without recalculating the server result.
class FareQuote {
  const FareQuote({
    required this.baseFare,
    required this.distanceFare,
    required this.drivingTimeFare,
    required this.waitingFare,
    required this.totalFare,
  });

  final String baseFare;
  final String distanceFare;
  final String drivingTimeFare;
  final String waitingFare;
  final String totalFare;

  factory FareQuote.fromJson(Map<String, dynamic> json) => FareQuote(
    baseFare: _decimal(json['baseFare']),
    distanceFare: _decimal(json['distanceFare']),
    drivingTimeFare: _decimal(json['drivingTimeFare']),
    waitingFare: _decimal(json['waitingFare']),
    totalFare: _decimal(json['totalFare']),
  );
}

class OrderDriver {
  const OrderDriver({
    required this.driverId,
    this.displayPhone,
    this.carNumber,
  });

  final int driverId;
  final String? displayPhone;
  final String? carNumber;

  factory OrderDriver.fromJson(Map<String, dynamic> json) => OrderDriver(
    driverId: (json['driverId'] as num).toInt(),
    displayPhone: json['displayPhone'] as String?,
    carNumber: json['carNumber'] as String?,
  );
}

class OrderSummary {
  const OrderSummary({
    required this.orderNo,
    required this.status,
    required this.pickupName,
    required this.destinationName,
    required this.totalFare,
    required this.createdTime,
    required this.estimatedFare,
  });

  final String orderNo;
  final OrderStatus status;
  final String pickupName;
  final String destinationName;
  final String totalFare;
  final DateTime? createdTime;
  final bool estimatedFare;

  factory OrderSummary.fromJson(Map<String, dynamic> json) => OrderSummary(
    orderNo: json['orderNo'] as String,
    status: orderStatusFromJson(json['status']),
    pickupName: json['pickupName'] as String,
    destinationName: json['destinationName'] as String,
    totalFare: _decimal(json['totalFare']),
    createdTime: _dateTime(json['createdTime']),
    estimatedFare: json['estimatedFare'] as bool? ?? true,
  );
}

class OrderPage {
  const OrderPage({
    required this.page,
    required this.size,
    required this.total,
    required this.records,
  });

  final int page;
  final int size;
  final int total;
  final List<OrderSummary> records;

  factory OrderPage.fromJson(Map<String, dynamic> json) => OrderPage(
    page: (json['page'] as num).toInt(),
    size: (json['size'] as num).toInt(),
    total: (json['total'] as num).toInt(),
    records: ((json['records'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(OrderSummary.fromJson)
        .toList(growable: false)),
  );
}

class DriverLocation {
  const DriverLocation({
    required this.point,
    this.accuracyM,
    this.speedMps,
    this.bearing,
    this.recordedTime,
    this.receivedTime,
    required this.stale,
  });

  final GeoPoint point;
  final double? accuracyM;
  final double? speedMps;
  final double? bearing;
  final DateTime? recordedTime;
  final DateTime? receivedTime;
  final bool stale;

  factory DriverLocation.fromJson(Map<String, dynamic> json) => DriverLocation(
    point: GeoPoint(
      latitude: double.parse(_decimal(json['latitude'])),
      longitude: double.parse(_decimal(json['longitude'])),
    ),
    accuracyM: _number(json['accuracyM']),
    speedMps: _number(json['speedMps']),
    bearing: _number(json['bearing']),
    recordedTime: _dateTime(json['recordedTime']),
    receivedTime: _dateTime(json['receivedTime']),
    stale: json['stale'] as bool? ?? true,
  );
}

double? _number(Object? value) =>
    value == null ? null : double.tryParse(value.toString());

class OrderQuote {
  const OrderQuote({
    required this.pricingRuleId,
    required this.pricingRuleVersion,
    required this.estimatedDistanceM,
    required this.estimatedDurationS,
    required this.fare,
  });

  final int pricingRuleId;
  final int pricingRuleVersion;
  final int estimatedDistanceM;
  final int estimatedDurationS;
  final FareQuote fare;

  factory OrderQuote.fromJson(Map<String, dynamic> json) => OrderQuote(
    pricingRuleId: (json['pricingRuleId'] as num).toInt(),
    pricingRuleVersion: (json['pricingRuleVersion'] as num).toInt(),
    estimatedDistanceM: (json['estimatedDistanceM'] as num).toInt(),
    estimatedDurationS: (json['estimatedDurationS'] as num).toInt(),
    fare: FareQuote.fromJson(json['fare'] as Map<String, dynamic>),
  );
}

/// A server-recorded order transition shown in an order timeline.
///
/// Event values remain strings so an older client can render newly added
/// server event/operator types instead of failing to parse the order detail.
class OrderEvent {
  const OrderEvent({
    this.eventType,
    this.fromStatus,
    this.toStatus,
    this.operatorType,
    this.createdTime,
    this.remark,
  });

  final String? eventType;
  final String? fromStatus;
  final String? toStatus;
  final String? operatorType;
  final DateTime? createdTime;
  final String? remark;

  factory OrderEvent.fromJson(Map<String, dynamic> json) => OrderEvent(
    eventType: json['eventType']?.toString(),
    fromStatus: json['fromStatus']?.toString(),
    toStatus: json['toStatus']?.toString(),
    operatorType: json['operatorType']?.toString(),
    createdTime: _dateTime(json['createdTime']),
    remark: json['remark']?.toString(),
  );
}

class OrderDetail {
  const OrderDetail({
    required this.orderNo,
    required this.status,
    required this.pickup,
    required this.destination,
    required this.estimatedDistanceM,
    required this.estimatedDurationS,
    required this.billingDistanceM,
    this.actualDurationS,
    this.waitingDurationS,
    required this.fare,
    required this.estimatedFare,
    this.driver,
    this.createdTime,
    this.acceptedTime,
    this.driverArrivedTime,
    this.serviceStartedTime,
    this.completedTime,
    this.cancelledTime,
    this.cancelledBy,
    this.cancelReason,
    this.events = const [],
  });

  final String orderNo;
  final OrderStatus status;
  final Place pickup;
  final Place destination;
  final int estimatedDistanceM;
  final int estimatedDurationS;
  final int billingDistanceM;
  final int? actualDurationS;
  final int? waitingDurationS;
  final FareQuote fare;
  final bool estimatedFare;
  final OrderDriver? driver;
  final DateTime? createdTime;
  final DateTime? acceptedTime;
  final DateTime? driverArrivedTime;
  final DateTime? serviceStartedTime;
  final DateTime? completedTime;
  final DateTime? cancelledTime;
  final String? cancelledBy;
  final String? cancelReason;
  final List<OrderEvent> events;

  factory OrderDetail.fromJson(Map<String, dynamic> json) => OrderDetail(
    orderNo: json['orderNo'] as String,
    status: orderStatusFromJson(json['status']),
    pickup: _place(json, 'pickup'),
    destination: _place(json, 'destination'),
    estimatedDistanceM: (json['estimatedDistanceM'] as num).toInt(),
    estimatedDurationS: (json['estimatedDurationS'] as num).toInt(),
    billingDistanceM: (json['billingDistanceM'] as num).toInt(),
    actualDurationS: (json['actualDurationS'] as num?)?.toInt(),
    waitingDurationS: (json['waitingDurationS'] as num?)?.toInt(),
    fare: FareQuote.fromJson(json['fare'] as Map<String, dynamic>),
    estimatedFare: json['estimatedFare'] as bool? ?? true,
    driver: (json['driver'] as Map<String, dynamic>?) == null
        ? null
        : OrderDriver.fromJson(json['driver'] as Map<String, dynamic>),
    createdTime: _dateTime(json['createdTime']),
    acceptedTime: _dateTime(json['acceptedTime']),
    driverArrivedTime: _dateTime(json['driverArrivedTime']),
    serviceStartedTime: _dateTime(json['serviceStartedTime']),
    completedTime: _dateTime(json['completedTime']),
    cancelledTime: _dateTime(json['cancelledTime']),
    cancelledBy: json['cancelledBy'] as String?,
    cancelReason: json['cancelReason'] as String?,
    events: (json['events'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((event) => OrderEvent.fromJson(Map<String, dynamic>.from(event)))
        .toList(growable: false),
  );
}

Place _place(Map<String, dynamic> json, String prefix) => Place(
  name: json['${prefix}Name'] as String,
  address: json['${prefix}Address'] as String?,
  amapPoiId: json['${prefix}PoiId'] as String?,
  location: GeoPoint(
    latitude: double.parse(_decimal(json['${prefix}Lat'])),
    longitude: double.parse(_decimal(json['${prefix}Lng'])),
  ),
);

class RouteContext {
  const RouteContext({
    required this.driverLocation,
    required this.destination,
    required this.recommendedRefreshDistanceM,
    required this.recommendedRefreshSeconds,
  });

  final DriverLocation driverLocation;
  final Place destination;
  final int recommendedRefreshDistanceM;
  final int recommendedRefreshSeconds;

  factory RouteContext.fromJson(Map<String, dynamic> json) => RouteContext(
    driverLocation: DriverLocation.fromJson(
      json['driverLocation'] as Map<String, dynamic>,
    ),
    destination: Place(
      name: json['destinationName'] as String,
      address: json['destinationAddress'] as String?,
      amapPoiId: json['destinationPoiId'] as String?,
      location: GeoPoint(
        latitude: double.parse(_decimal(json['destinationLat'])),
        longitude: double.parse(_decimal(json['destinationLng'])),
      ),
    ),
    recommendedRefreshDistanceM: (json['recommendedRefreshDistanceM'] as num)
        .toInt(),
    recommendedRefreshSeconds: (json['recommendedRefreshSeconds'] as num)
        .toInt(),
  );
}

/// Final server-side settlement returned when a driver completes service.
/// The client displays these values and never recalculates them.
class OrderCompletionResult {
  const OrderCompletionResult({
    required this.orderNo,
    required this.status,
    required this.completedTime,
    required this.actualDurationS,
    required this.waitingDurationS,
    required this.fare,
  });

  final String orderNo;
  final OrderStatus status;
  final DateTime? completedTime;
  final int actualDurationS;
  final int waitingDurationS;
  final FareQuote fare;

  factory OrderCompletionResult.fromJson(Map<String, dynamic> json) =>
      OrderCompletionResult(
        orderNo: json['orderNo'] as String,
        status: orderStatusFromJson(json['status']),
        completedTime: _dateTime(json['completedTime']),
        actualDurationS: (json['actualDurationS'] as num).toInt(),
        waitingDurationS: (json['waitingDurationS'] as num).toInt(),
        fare: FareQuote.fromJson(json['fare'] as Map<String, dynamic>),
      );
}
