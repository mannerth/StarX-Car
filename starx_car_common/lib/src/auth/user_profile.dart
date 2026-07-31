/// Role-neutral account details returned by `/api/users/me`.
class UserProfile {
  const UserProfile({
    required this.id,
    required this.username,
    required this.email,
    required this.gender,
    required this.type,
  });

  final int id;
  final String username;
  final String? email;
  final String gender;
  final String type;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
    id: (json['id'] as num).toInt(),
    username: json['username'] as String,
    email: json['email'] as String?,
    gender: json['gender'] as String,
    type: json['type'] as String,
  );
}
