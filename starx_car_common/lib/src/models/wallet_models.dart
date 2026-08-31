class WalletBalance {
  const WalletBalance(this.balance);
  final double balance;

  factory WalletBalance.fromJson(Map<String, dynamic> json) => WalletBalance(
    (json['balance'] as num?)?.toDouble() ?? 0,
  );
}
