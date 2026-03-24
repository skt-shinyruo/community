<template>
  <div class="page reward-shop-page">
    <UiBreadcrumb />

    <section class="reward-shop-head">
      <div class="reward-shop-actions">
        <UiButton variant="secondary" :disabled="loading || redeeming" @click="reload">
          {{ loading ? '刷新中…' : '刷新' }}
        </UiButton>
        <UiButton variant="secondary" @click="goOrders">兑换记录</UiButton>
      </div>
      <div class="reward-shop-hero-grid">
        <div class="reward-shop-hero-card">
          <span class="reward-shop-label">可用奖励</span>
          <strong>{{ state.rewardBalance }}</strong>
          <p>商城只消耗奖励积分，不会影响你的公开等级和排行榜位置。</p>
        </div>
        <div class="reward-shop-hero-card">
          <span class="reward-shop-label">上架商品</span>
          <strong>{{ state.items.length }}</strong>
          <p>自动发放与人工发放分开标识，避免兑换后不知道进入了哪条履约路径。</p>
        </div>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading && state.items.length === 0" class="muted reward-shop-state">正在加载奖励商城…</div>

    <div v-else class="reward-item-grid">
      <UiCard v-for="item in state.items" :key="item.id" class="reward-item-card">
        <div class="reward-item-top">
          <div>
            <div class="reward-shop-label">{{ item.fulfillmentLabel }}</div>
            <h2>{{ item.itemName }}</h2>
          </div>
          <UiTag>{{ item.costBalance }} 积分</UiTag>
        </div>

        <p class="reward-item-desc">{{ item.itemDesc || '暂无说明' }}</p>

        <div class="reward-item-meta">
          <span>库存 {{ item.stock }}</span>
          <span>每人限兑 {{ item.perUserLimit || '不限' }}</span>
        </div>

        <div class="reward-item-foot">
          <span
            class="reward-item-status"
            :class="{
              'is-danger': item.soldOut || item.insufficientBalance,
              'is-success': item.canRedeem
            }"
          >
            {{
              item.soldOut
                ? '已售罄'
                : item.insufficientBalance
                  ? '积分不足'
                  : '可兑换'
            }}
          </span>
          <UiButton
            :disabled="redeeming || !item.canRedeem"
            @click="redeem(item)"
          >
            {{ redeemingItemId === item.id ? '兑换中…' : '立即兑换' }}
          </UiButton>
        </div>
      </UiCard>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getGrowthSummary } from '../api/services/growthService'
import { listRewardItems, redeemReward } from '../api/services/rewardShopService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiTag from '../components/ui/UiTag.vue'
import { buildRewardShopState } from './rewardShopState'

const router = useRouter()

const loading = ref(false)
const redeeming = ref(false)
const redeemingItemId = ref(0)
const error = ref('')
const summary = ref({})
const items = ref([])

const state = computed(() =>
  buildRewardShopState({
    rewardBalance: summary.value?.rewardBalance || 0,
    items: items.value,
    orders: []
  })
)

function buildRequestId(itemId) {
  if (globalThis.crypto?.randomUUID) {
    return `reward-redeem:${itemId}:${globalThis.crypto.randomUUID()}`
  }
  return `reward-redeem:${itemId}:${Date.now()}`
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const [{ data: growthSummary }, { data: catalogItems }] = await Promise.all([
      getGrowthSummary(),
      listRewardItems()
    ])
    summary.value = growthSummary || {}
    items.value = Array.isArray(catalogItems) ? catalogItems : []
  } catch (e) {
    error.value = e?.message || '加载奖励商城失败'
  } finally {
    loading.value = false
  }
}

async function redeem(item) {
  if (!item?.canRedeem || redeeming.value) return
  redeeming.value = true
  redeemingItemId.value = Number(item.id || 0)
  error.value = ''
  try {
    await redeemReward({
      itemId: item.id,
      requestId: buildRequestId(item.id)
    })
    await reload()
  } catch (e) {
    error.value = e?.message || '兑换失败'
  } finally {
    redeeming.value = false
    redeemingItemId.value = 0
  }
}

function goOrders() {
  router.push({ name: 'rewardOrders' })
}

onMounted(reload)
</script>

<style scoped>
.reward-shop-page {
  max-width: 1080px;
  margin: 0 auto;
  gap: var(--space-5);
}

.reward-shop-head {
  display: grid;
  gap: var(--space-4);
}

.reward-shop-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.reward-shop-hero-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.reward-shop-hero-card,
.reward-item-card {
  display: grid;
  gap: 10px;
}

.reward-shop-hero-card {
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 84%, var(--accent) 16%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, white 8%), var(--surface));
}

.reward-shop-hero-card strong {
  font-size: clamp(1.7rem, 3vw, 2.3rem);
  line-height: 1;
}

.reward-shop-hero-card p,
.reward-item-desc {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.reward-shop-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.reward-shop-state {
  padding: 24px 0;
}

.reward-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.reward-item-top,
.reward-item-foot,
.reward-item-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.reward-item-top h2 {
  margin: 6px 0 0;
  font-size: 1.15rem;
}

.reward-item-meta {
  color: var(--text-3);
  font-size: 13px;
  flex-wrap: wrap;
}

.reward-item-status.is-danger {
  color: var(--danger);
}

.reward-item-status.is-success {
  color: var(--success);
}

@media (max-width: 900px) {
  .reward-shop-hero-grid,
  .reward-item-grid {
    grid-template-columns: 1fr;
  }
}
</style>
