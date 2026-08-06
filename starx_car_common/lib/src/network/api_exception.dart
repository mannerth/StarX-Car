/// A safe, user-presentable error returned by the StarX-Car API or network.
class ApiException implements Exception {
  const ApiException(
    this.message, {
    this.statusCode,
    this.traceId,
    this.cancelled = false,
  });

  final String message;
  final int? statusCode;
  final String? traceId;

  /// Whether the caller explicitly cancelled the request.
  final bool cancelled;

  @override
  String toString() => message;
}
