/// A safe, user-presentable error returned by the StarX-Car API or network.
class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode, this.traceId});

  final String message;
  final int? statusCode;
  final String? traceId;

  @override
  String toString() => message;
}
