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
  tiers: Omit<PricingTier, 'id'>[]
}
