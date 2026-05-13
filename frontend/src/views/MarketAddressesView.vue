<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载地址簿…</div>

    <template v-else>
      <UiPageHeader>
        <template #title>收货地址</template>
        <template #subtitle>实物订单下单前，先把地址簿整理好。地址簿是可变资料，订单使用的是地址快照，所以这里管理的是未来下单的默认收货信息。</template>
      </UiPageHeader>

      <UiCard class="market-panel">
        <UiPageHeader>
          <template #title>新增地址</template>
          <template #subtitle>保存常用收货信息，实物商品下单时会使用地址快照。</template>
        </UiPageHeader>

        <div class="market-form-grid market-form-grid--wide">
          <label class="market-field">
            <span>收货人</span>
            <UiInput v-model="form.receiverName" />
          </label>
          <label class="market-field">
            <span>手机号</span>
            <UiInput v-model="form.receiverPhone" />
          </label>
          <label class="market-field">
            <span>省份</span>
            <UiInput v-model="form.province" />
          </label>
          <label class="market-field">
            <span>城市</span>
            <UiInput v-model="form.city" />
          </label>
          <label class="market-field">
            <span>区县</span>
            <UiInput v-model="form.district" />
          </label>
          <label class="market-field">
            <span>详细地址</span>
            <UiInput v-model="form.detailAddress" />
          </label>
          <label class="market-field">
            <span>邮编</span>
            <UiInput v-model="form.postalCode" />
          </label>
        </div>

        <div class="market-inline-actions">
          <UiButton :disabled="submitting" @click="submitCreate">
            {{ submitting ? '保存中…' : '新增地址' }}
          </UiButton>
          <span class="muted">{{ message }}</span>
        </div>

        <UiState v-if="state.addresses.length === 0">
          暂无收货地址
          <template #description>创建第一条地址后，实物商品详情页就可以直接选择它下单。</template>
        </UiState>

        <div v-else class="market-admin-list">
          <article v-for="item in state.addresses" :key="item.addressId" class="market-admin-row">
            <div>
              <strong>{{ item.receiverName }}</strong>
              <p>{{ item.receiverPhone }} · {{ item.addressLine }}</p>
              <p v-if="item.defaultLabel">{{ item.defaultLabel }}</p>
            </div>
            <div class="market-inline-actions">
              <UiButton variant="secondary" :disabled="submitting" @click="submitUpdate(item)">更新</UiButton>
              <UiButton :disabled="submitting" @click="submitDelete(item.addressId)">删除</UiButton>
            </div>
          </article>
        </div>
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createMarketAddress,
  deleteMarketAddress,
  listMarketAddresses,
  updateMarketAddress
} from '../api/services/marketService'
import { buildMarketState } from './marketState'

const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('地址簿会在实物商品详情页直接复用。')
const addresses = ref([])
const form = ref({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  postalCode: '',
  defaultAddress: true
})

const state = computed(() => buildMarketState({ addresses: addresses.value }))

function buildPayload(source) {
  return {
    receiverName: source.receiverName,
    receiverPhone: source.receiverPhone,
    province: source.province,
    city: source.city,
    district: source.district,
    detailAddress: source.detailAddress,
    postalCode: source.postalCode,
    defaultAddress: !!source.defaultAddress
  }
}

function resetForm() {
  form.value = {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    postalCode: '',
    defaultAddress: true
  }
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMarketAddresses()
    addresses.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载地址簿失败'
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  submitting.value = true
  message.value = ''
  try {
    await createMarketAddress(buildPayload(form.value))
    message.value = '地址已创建。'
    resetForm()
    await reload()
  } catch (e) {
    message.value = e?.message || '创建地址失败'
  } finally {
    submitting.value = false
  }
}

async function submitUpdate(item) {
  submitting.value = true
  message.value = ''
  try {
    await updateMarketAddress(item.addressId, buildPayload(item))
    message.value = '地址已更新。'
    await reload()
  } catch (e) {
    message.value = e?.message || '更新地址失败'
  } finally {
    submitting.value = false
  }
}

async function submitDelete(addressId) {
  submitting.value = true
  message.value = ''
  try {
    await deleteMarketAddress(addressId)
    message.value = '地址已删除。'
    await reload()
  } catch (e) {
    message.value = e?.message || '删除地址失败'
  } finally {
    submitting.value = false
  }
}

onMounted(reload)
</script>
