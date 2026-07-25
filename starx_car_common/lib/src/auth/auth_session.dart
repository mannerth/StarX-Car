/// The authenticated session returned by the StarX-Car backend.
///
/// Both passenger and driver applications persist this value locally. Token
/// storage and refresh requests will be implemented here when the two apps
/// start integrating with the backend.
class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.userId,
    required this.userType,
  });

  /// JWT used in the `Authorization: Bearer` request header.
  final String accessToken;

  /// JWT used only to obtain a new [accessToken].
  final String refreshToken;

  /// ID of the currently authenticated account.
  final int userId;

  /// Backend role value, such as `passenger` or `driver`.
  final String userType;
}
