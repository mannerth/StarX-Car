export type UserType = 'admin' | 'passenger' | 'driver'
export type Gender = 'male' | 'female'
export type UserStatus = 'active' | 'disabled' | 'cancelled'

export interface User {
  id: number
  username: string
  email: string | null
  gender: Gender | null
  type: UserType
  realName: string | null
  avatarUrl: string | null
  status: UserStatus
  createTime: string | null
  lastLoginTime: string | null
  updateTime: string | null
}

export interface UserPage {
  page: number
  size: number
  total: number
  records: User[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export type DriverApprovalStatus = 'pending' | 'approved' | 'rejected'

export interface AdminDriver {
  id: number
  username: string | null
  realName: string | null
  userStatus: UserStatus | null
  phoneNumber: string
  displayPhone: string
  licenseNumber: string
  idCardNo: string | null
  licenseExpireDate: string | null
  rating: number | null
  approvalStatus: DriverApprovalStatus
  workStatus: 'offline' | 'online' | 'busy'
  carId: number | null
  carNumber: string | null
  carBrand: string | null
  carColor: string | null
  createTime: string | null
}

export interface AdminDriverPage {
  page: number
  size: number
  total: number
  records: AdminDriver[]
}

export type PricingRuleStatus = 'draft' | 'active' | 'inactive' | 'archived'
export type FareChargeType = 'DISTANCE' | 'DRIVING_TIME' | 'WAITING_TIME'

export interface PricingTier {
  id: number
  chargeType: FareChargeType
  rangeStart: number
  rangeEnd: number | null
  unitSize: number
  unitPrice: number
  roundingMode: 'CEIL'
  sortNo: number
}

export interface PricingRule {
  id: number
  name: string
  version: number
  status: PricingRuleStatus
  defaultRule: boolean
  baseFare: number
  includedDistanceM: number
  minimumFare: number
  arrivalCancelFee: number
  createdBy: number
  updatedBy: number | null
  createdTime: string
  updatedTime: string
  tiers: PricingTier[]
}

export interface PricingRulePage {
  page: number
  size: number
  total: number
  records: PricingRule[]
}

export interface PricingRuleWriteRequest {
  name: string
  baseFare: number
  includedDistanceM: number
  minimumFare: number
  arrivalCancelFee: number
  tiers: Omit<PricingTier, 'id'>[]
}

export type OrderType = 'INSTANT' | 'SCHEDULED'
export type OrderStatus = 'SCHEDULED' | 'PENDING' | 'ACCEPTED' | 'DRIVER_ARRIVED' | 'IN_SERVICE' | 'COMPLETED' | 'TERMINATED' | 'CANCELLED'

export type ComplaintStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED'
export type ComplaintResolutionType = 'APOLOGY' | 'PARTIAL_REFUND' | 'FULL_REFUND' | 'REJECT'
export interface Complaint { complaintNo: string; orderNo: string; complaintType: string; content: string; status: ComplaintStatus; resolutionType: ComplaintResolutionType | null; refundAmount: number; createdTime: string; resolvedTime: string | null }
export interface ComplaintPage { page: number; size: number; total: number; records: Complaint[] }

export interface AdminOrderSummary {
  orderNo: string; orderType: OrderType; status: OrderStatus; pickupName: string; destinationName: string
  passengerName: string | null; passengerDisplayPhone: string | null; driverName: string | null; driverDisplayPhone: string | null
  scheduledPickupTime: string | null; dispatchTime: string | null; createdTime: string | null; acceptedTime: string | null; completedTime: string | null
  totalFare: number; estimatedFare: boolean
}
export interface AdminOrderPage { page: number; size: number; total: number; records: AdminOrderSummary[] }
export interface AdminOrderMetrics {
  total: number; instant: number; scheduled: number; scheduledWaitingDispatch: number; pending: number; accepted: number; inService: number; completed: number; cancelled: number; reservationAtRisk: number; completedFare: number
}
export interface AdminOrderNote { id: number; adminId: number; adminName: string; content: string; createdTime: string }
export interface AdminFareItem { chargeType: string; rangeStart: number | null; rangeEnd: number | null; actualUsage: number; unitSize: number | null; unitPrice: number; chargedUnits: number; amount: number }
export interface AdminDriverLocation { latitude: number; longitude: number; accuracyM: number | null; speedMps: number | null; bearing: number | null; recordedTime: string | null; receivedTime: string | null }
export interface OrderEvent { eventType: string; fromStatus: OrderStatus | null; toStatus: OrderStatus; operatorType: string; createdTime: string; remark: string | null }
export interface AdminOrderDetail extends AdminOrderSummary {
  pickupAddress: string | null; destinationAddress: string | null; pickupLat: number; pickupLng: number; destinationLat: number; destinationLng: number
  baseFare: number; distanceFare: number; drivingTimeFare: number; waitingFare: number; passengerPhoneTail: string | null; driver: { displayPhone: string | null; carNumber: string | null } | null; driverArrivedTime: string | null; serviceStartedTime: string | null; cancelledTime: string | null; cancelledBy: string | null; cancelReason: string | null; events: OrderEvent[]
}
