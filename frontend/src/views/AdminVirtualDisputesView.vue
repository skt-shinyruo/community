<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">争议裁定</span>
        <h1>管理员只处理最终裁定，不处理普通卖家动作</h1>
        <p>这里专门承接卖家拒绝后的争议收口，不再混进钱包后台或旧奖励后台。</p>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载争议…</div>

    <div v-else class="market-admin-list">
      <article v-for="item in state.disputes" :key="item.disputeId" class="market-admin-row">
        <div>
          <strong>争议 #{{ item.disputeId }}</strong>
          <p>{{ item.reason }} · {{ item.statusLabel }}</p>
        </div>
        <div class="market-inline-actions">
          <UiButton variant="secondary" :disabled="submittingId === item.disputeId" @click="resolve(item.disputeId, 'refund')">
            退回买家
          </UiButton>
          <UiButton :disabled="submittingId === item.disputeId" @click="resolve(item.disputeId, 'release')">
            放款卖家
          </UiButton>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import { adminResolveVirtualDispute, listAdminVirtualDisputes } from '../api/services/virtualMarketService'
import { buildVirtualMarketState } from './virtualMarketState'

const loading = ref(false)
const error = ref('')
const submittingId = ref(0)
const disputes = ref([])

const state = computed(() => buildVirtualMarketState({ disputes: disputes.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listAdminVirtualDisputes()
    disputes.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载争议失败'
  } finally {
    loading.value = false
  }
}

async function resolve(disputeId, action) {
  submittingId.value = disputeId
  try {
    await adminResolveVirtualDispute(disputeId, action, { note: action === 'refund' ? 'refund' : 'release' })
    await reload()
  } catch (e) {
    error.value = e?.message || '处理争议失败'
  } finally {
    submittingId.value = 0
  }
}

onMounted(reload)
</script>
