<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

const route = useRoute()
const isPublic = computed(() => route.meta.public === true)
</script>

<template>
  <RouterView v-if="isPublic" />
  <AdminLayout v-else><RouterView /></AdminLayout>
</template>
