import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import UserManagementView from '@/views/UserManagementView.vue'
import ProfileView from '@/views/ProfileView.vue'
import DriverReviewView from '@/views/DriverReviewView.vue'
import PricingManagementView from '@/views/PricingManagementView.vue'
import OrderMonitoringView from '@/views/OrderMonitoringView.vue'
import ReservationOperationsView from '@/views/ReservationOperationsView.vue'
import OrderDetailView from '@/views/OrderDetailView.vue'
import ComplaintOperationsView from '@/views/ComplaintOperationsView.vue'

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
