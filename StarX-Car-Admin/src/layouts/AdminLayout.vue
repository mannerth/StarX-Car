<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Calendar, ChatDotRound, Document, Fold, Setting, Tickets, UserFilled, Van } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const activePath = computed(() => router.currentRoute.value.path.startsWith('/orders/') ? '/orders' : router.currentRoute.value.path)
const navigation = [
  { path: '/orders', label: '订单监控', icon: Tickets },
  { path: '/reservations', label: '预约运营', icon: Calendar },
  { path: '/complaints', label: '投诉工单', icon: ChatDotRound },
  { path: '/users', label: '用户管理', icon: UserFilled },
  { path: '/drivers', label: '司机审核', icon: Van },
  { path: '/pricing', label: '计费管理', icon: Document },
]
function navigate(path: string) { void router.push(path) }
function logout() { auth.logout(); void router.replace({ name: 'login' }) }
</script>

<template>
  <el-container class="admin-layout">
    <el-aside class="admin-sidebar" :width="collapsed ? '68px' : '236px'">
      <div class="layout-brand"><span class="brand-mark">S</span><strong v-show="!collapsed">StarX-Car</strong></div>
      <el-menu :default-active="activePath" class="layout-menu" :collapse="collapsed" :collapse-transition="false" @select="navigate">
        <el-menu-item v-for="item in navigation" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></el-menu-item>
      </el-menu>
      <div class="sidebar-bottom"><el-button text class="collapse-button" :title="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed"><el-icon><Fold /></el-icon><span v-show="!collapsed">收起导航</span></el-button></div>
    </el-aside>
    <el-container>
      <el-header class="layout-header"><el-breadcrumb separator="/"><el-breadcrumb-item>StarX-Car</el-breadcrumb-item><el-breadcrumb-item>运营管理</el-breadcrumb-item></el-breadcrumb><div class="header-actions"><el-button circle text title="通知"><el-icon><Bell /></el-icon></el-button><el-dropdown @command="(command: string) => command === 'logout' ? logout() : navigate('/profile')"><span class="user-menu"><el-avatar :size="30">{{ auth.user?.username?.slice(0, 1).toUpperCase() || 'A' }}</el-avatar><span>{{ auth.user?.username || '管理员' }}</span></span><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile"><el-icon><Setting /></el-icon>个人设置</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></el-header>
      <el-main class="layout-main"><slot /></el-main>
    </el-container>
  </el-container>
</template>
