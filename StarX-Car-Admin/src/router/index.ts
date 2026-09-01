import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const LoginView = () => import('@/views/LoginView.vue')
const UserManagementView = () => import('@/views/UserManagementView.vue')
const ProfileView = () => import('@/views/ProfileView.vue')
const DriverReviewView = () => import('@/views/DriverReviewView.vue')
const PricingManagementView = () => import('@/views/PricingManagementView.vue')
const OrderMonitoringView = () => import('@/views/OrderMonitoringView.vue')
const ReservationOperationsView = () => import('@/views/ReservationOperationsView.vue')
const OrderDetailView = () => import('@/views/OrderDetailView.vue')
const ComplaintOperationsView = () => import('@/views/ComplaintOperationsView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/orders' },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/users', name: 'users', component: UserManagementView },
    { path: '/drivers', name: 'drivers', component: DriverReviewView },
    { path: '/pricing', name: 'pricing', component: PricingManagementView },
    { path: '/orders', name: 'orders', component: OrderMonitoringView },
    { path: '/reservations', name: 'reservations', component: ReservationOperationsView },
    { path: '/complaints', name: 'complaints', component: ComplaintOperationsView },
    { path: '/orders/:orderNo', name: 'order-detail', component: OrderDetailView },
    { path: '/profile', name: 'profile', component: ProfileView },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAdmin) return { name: 'login' }
  if (to.name === 'login' && auth.isAdmin) return { name: 'orders' }
  return true
})

export default router
