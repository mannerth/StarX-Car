import '../models/wallet_models.dart';
import 'api_client.dart';

/// Role-neutral wallet transport. Role-specific recharge and withdrawal flows
/// remain in their respective applications.
class WalletApi {
  const WalletApi(this._client);
  final ApiClient _client;

  Future<WalletBalance> balance() async => WalletBalance.fromJson(
    await _client.get('/wallet/me') as Map<String, dynamic>,
  );

  Future<WalletBalance> recharge(double amount) async => WalletBalance.fromJson(
    await _client.post('/passengers/me/wallet/recharge', data: {'amount': amount})
        as Map<String, dynamic>,
  );

  Future<WalletBalance> withdraw(double amount) async => WalletBalance.fromJson(
    await _client.post('/drivers/me/wallet/withdrawals', data: {'amount': amount})
        as Map<String, dynamic>,
  );
}
