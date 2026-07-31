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

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? value) dataFromJson,
  ) => ApiResponse(
    code: (json['code'] as num).toInt(),
    message: json['message'] as String? ?? '',
    traceId: json['traceId'] as String? ?? '',
    data: json['data'] == null ? null : dataFromJson(json['data']),
  );
}
