/// Role-neutral account details returned by `/api/users/me`.
class UserProfile {
  const UserProfile({
    required this.id,
    required this.username,
    required this.email,
    required this.gender,
    required this.type,
    required this.realName,
    required this.avatarUrl,
    required this.status,
  });

  final int id;
  final String username;
  final String? email;
  final String gender;
  final String type;
  final String? realName;
  final String? avatarUrl;
  final String status;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
    id: (json['id'] as num).toInt(),
    username: json['username'] as String,
    email: json['email'] as String?,
    gender: json['gender'] as String,
    type: json['type'] as String,
    realName: json['realName'] as String?,
    avatarUrl: json['avatarUrl'] as String?,
    status: json['status'] as String? ?? 'active',
  );
}
