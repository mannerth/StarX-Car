import type {
  ComplaintResolutionType,
  ComplaintStatus,
  DriverApprovalStatus,
  FareChargeType,
  OrderEventType,
  OrderOperatorType,
  OrderStatus,
  UserStatus,
  UserType,
} from '@/types/api'

export const orderStatusLabel: Record<OrderStatus, string> = {
  SCHEDULED: '等待派单',
  PENDING: '待抢单',
  ACCEPTED: '已接单',
  DRIVER_ARRIVED: '司机已到达',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  TERMINATED: '中途结束',
  CANCELLED: '已取消',
}

export const userTypeLabel: Record<UserType, string> = {
  admin: '管理员',
  passenger: '乘客',
  driver: '司机',
}

export const userStatusLabel: Record<UserStatus, string> = {
  active: '正常',
  disabled: '已停用',
  cancelled: '已注销',
}

export const driverApprovalLabel: Record<DriverApprovalStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已驳回',
}

export const complaintStatusLabel: Record<ComplaintStatus, string> = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已结案',
}

export const complaintResolutionLabel: Record<ComplaintResolutionType, string> = {
  APOLOGY: '仅道歉',
  PARTIAL_REFUND: '部分退款',
  FULL_REFUND: '全额退款',
  REJECT: '驳回投诉',
}

const complaintTypeLabels: Record<string, string> = {
  ORDER_SERVICE: '订单服务投诉',
  SERVICE_QUALITY: '服务质量投诉',
  DRIVER_BEHAVIOR: '司机服务投诉',
  FARE_DISPUTE: '费用争议',
  SAFETY: '安全问题',
  LOST_ITEM: '遗失物品',
  OTHER: '其他投诉',
}

export function complaintTypeLabel(value: string): string {
  return complaintTypeLabels[value] ?? '其他投诉'
}

export const fareChargeTypeLabel: Record<FareChargeType, string> = {
  BASE: '基础费用',
  ARRIVAL_CANCEL: '到达后取消费',
  DISTANCE: '里程费',
  DRIVING_TIME: '行程时间费',
  WAITING_TIME: '等待费',
}

export const orderEventTypeLabel: Record<OrderEventType, string> = {
  CREATE: '创建订单',
  DISPATCH: '进入派单',
  ACCEPT: '司机接单',
  ARRIVE: '司机到达',
  START_SERVICE: '开始服务',
  COMPLETE: '完成订单',
  TERMINATE: '中途结束',
  CANCEL: '取消订单',
}

export const orderOperatorLabel: Record<OrderOperatorType, string> = {
  passenger: '乘客',
  driver: '司机',
  system: '系统',
  admin: '管理员',
}

export function money(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const amount = Number(value)
  return Number.isFinite(amount) ? `¥${amount.toFixed(2)}` : '—'
}
