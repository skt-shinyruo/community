<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">我的购买</span>
        <h1>托管、交付、确认、申诉一屏看清</h1>
        <p>列表接口后续会补齐，当前先用状态层把关键状态文案固定下来。</p>
      </div>
    </section>

    <div class="market-order-list">
      <article v-for="item in state.orders" :key="item.orderId" class="market-order-row">
        <div>
          <strong>订单 #{{ item.orderId }}</strong>
          <p>{{ item.statusLabel }} · {{ item.autoConfirmText }}</p>
        </div>
        <strong>{{ item.totalAmountText }}</strong>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import { buildVirtualMarketState } from './virtualMarketState'

const sampleOrders = [
  { orderId: 101, status: 'ESCROWED', totalAmount: 1999 },
  { orderId: 102, status: 'DELIVERED', totalAmount: 3998, autoConfirmAt: '2026-04-04T12:00:00Z' },
  { orderId: 103, status: 'DISPUTED', totalAmount: 2400 }
]

const state = computed(() => buildVirtualMarketState({ orders: sampleOrders }))
</script>
