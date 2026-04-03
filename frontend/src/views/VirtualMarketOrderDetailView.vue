<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>订单详情</template>
        <template #subtitle>订单 ID：{{ route.params.orderId }}。这里会继续承接交付、确认与申诉动作。</template>
      </UiPageHeader>

      <div class="market-order-list">
        <article v-for="item in state.orders" :key="item.orderId" class="market-order-row">
          <div>
            <strong>{{ item.statusLabel }}</strong>
            <p>{{ item.autoConfirmText }}</p>
          </div>
          <strong>{{ item.totalAmountText }}</strong>
        </article>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { buildVirtualMarketState } from './virtualMarketState'

const route = useRoute()

const state = computed(() =>
  buildVirtualMarketState({
    orders: [{ orderId: Number(route.params.orderId || 0), status: 'DELIVERED', totalAmount: 3998, autoConfirmAt: '2026-04-04T12:00:00Z' }]
  })
)
</script>
