/// The common response envelope returned by the StarX-Car backend.
///
/// A successful response uses [code] `0`; failed HTTP responses use their HTTP
/// status as the code and may omit [data].
class ApiResponse<T> {
  const ApiResponse({
    required this.code,
    required this.message,
    required this.traceId,
    this.data,
  });

  final int code;
  final String message;
  final String traceId;
  final T? data;

  bool get isSuccess => code == 0;
}
