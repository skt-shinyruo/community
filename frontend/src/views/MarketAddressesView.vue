<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载地址簿…</div>

    <template v-else>
      <UiPageHeader>
        <template #title>收货地址</template>
        <template #subtitle>实物订单下单前，先把地址簿整理好。订单使用地址快照；这里管理未来下单的默认收货信息。</template>
      </UiPageHeader>

      <UiCard class="market-panel">
        <UiPageHeader>
          <template #title>新增地址</template>
          <template #subtitle>保存常用收货信息，实物商品下单时会使用地址快照。</template>
        </UiPageHeader>

        <div class="market-form-grid market-form-grid--wide">
          <label class="market-field">
            <span>收货人</span>
            <input v-model="form.receiverName" class="input" />
          </label>
          <label class="market-field">
            <span>手机号</span>
            <input v-model="form.receiverPhone" class="input" />
          </label>
          <label class="market-field">
            <span>省份</span>
            <input v-model="form.province" class="input" />
          </label>
          <label class="market-field">
            <span>城市</span>
            <input v-model="form.city" class="input" />
          </label>
          <label class="market-field">
            <span>区县</span>
            <input v-model="form.district" class="input" />
          </label>
          <label class="market-field">
            <span>详细地址</span>
            <input v-model="form.detailAddress" class="input" />
          </label>
          <label class="market-field">
            <span>邮编</span>
            <input v-model="form.postalCode" class="input" />
          </label>
          <label class="market-field--inline ui-checkbox">
            <input v-model="form.defaultAddress" class="ui-checkbox-input" type="checkbox" />
            <span class="ui-checkbox-copy">设为默认地址</span>
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
              <UiButton variant="secondary" :disabled="submitting" data-test="address-edit" @click="startEdit(item)">编辑</UiButton>
              <UiButton :disabled="submitting" @click="submitDelete(item.addressId)">删除</UiButton>
            </div>
            <form
              v-if="editingAddressId === item.addressId"
              class="market-edit-form"
              data-test="address-edit-form"
              @submit.prevent="submitUpdate"
            >
              <UiPageHeader>
                <template #title>编辑地址</template>
                <template #subtitle>修改后的地址只会用于后续下单，已创建订单仍使用当时的地址快照。</template>
              </UiPageHeader>

              <div class="market-form-grid market-form-grid--wide">
                <label class="market-field">
                  <span>收货人</span>
                  <input v-model="editForm.receiverName" class="input" />
                </label>
                <label class="market-field">
                  <span>手机号</span>
                  <input v-model="editForm.receiverPhone" class="input" />
                </label>
                <label class="market-field">
                  <span>省份</span>
                  <input v-model="editForm.province" class="input" />
                </label>
                <label class="market-field">
                  <span>城市</span>
                  <input v-model="editForm.city" class="input" />
                </label>
                <label class="market-field">
                  <span>区县</span>
                  <input v-model="editForm.district" class="input" />
                </label>
                <label class="market-field">
                  <span>详细地址</span>
                  <input v-model="editForm.detailAddress" class="input" />
                </label>
                <label class="market-field">
                  <span>邮编</span>
                  <input v-model="editForm.postalCode" class="input" />
                </label>
                <label class="market-field--inline ui-checkbox">
                  <input v-model="editForm.defaultAddress" class="ui-checkbox-input" type="checkbox" />
                  <span class="ui-checkbox-copy">设为默认地址</span>
                </label>
              </div>

              <div class="market-inline-actions">
                <UiButton :disabled="submitting" type="submit" data-test="address-update-submit">
                  {{ submitting ? '保存中…' : '保存修改' }}
                </UiButton>
                <UiButton variant="secondary" :disabled="submitting" @click="cancelEdit">取消</UiButton>
              </div>
            </form>
          </article>
        </div>
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createMarketAddress,
  deleteMarketAddress,
  listMarketAddresses,
  updateMarketAddress
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'

const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('地址簿会在实物商品详情页直接复用。')
const addresses = ref([])
const editingAddressId = ref(null)
const form = ref(emptyAddressForm())
const editForm = ref(emptyAddressForm())
let requestGeneration = 0
let actionGeneration = 0

const state = computed(() => buildMarketState({ addresses: addresses.value }))
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

function isCurrentRequest(generation, scope) {
  return generation === requestGeneration && scope === sessionScope.value
}

function isCurrentAction(generation, scope) {
  return generation === actionGeneration && scope === sessionScope.value
}

function emptyAddressForm() {
  return {
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

function cloneAddressForm(source = {}) {
  return {
    receiverName: source.receiverName || '',
    receiverPhone: source.receiverPhone || '',
    province: source.province || '',
    city: source.city || '',
    district: source.district || '',
    detailAddress: source.detailAddress || '',
    postalCode: source.postalCode || '',
    defaultAddress: !!source.defaultAddress
  }
}

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
  form.value = emptyAddressForm()
}

function cancelEdit() {
  editingAddressId.value = null
  editForm.value = emptyAddressForm()
}

function startEdit(item) {
  editingAddressId.value = item.addressId
  editForm.value = cloneAddressForm(item)
  message.value = '正在编辑地址。'
}

async function reload() {
  const generation = ++requestGeneration
  const scope = sessionScope.value
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMarketAddresses()
    if (!isCurrentRequest(generation, scope)) return
    addresses.value = Array.isArray(data) ? data : []
    if (editingAddressId.value && !addresses.value.some((item) => item?.addressId === editingAddressId.value)) {
      cancelEdit()
    }
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    error.value = e?.message || '加载地址簿失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loading.value = false
  }
}

async function submitCreate() {
  if (!auth.authed || submitting.value) return
  const generation = ++actionGeneration
  const scope = sessionScope.value
  submitting.value = true
  message.value = ''
  try {
    await createMarketAddress(buildPayload(form.value))
    if (!isCurrentAction(generation, scope)) return
    message.value = '地址已创建。'
    resetForm()
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    message.value = e?.message || '创建地址失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

async function submitUpdate() {
  if (!auth.authed || submitting.value || !editingAddressId.value) return
  const generation = ++actionGeneration
  const scope = sessionScope.value
  const addressId = editingAddressId.value
  submitting.value = true
  message.value = ''
  try {
    await updateMarketAddress(addressId, buildPayload(editForm.value))
    if (!isCurrentAction(generation, scope)) return
    message.value = '地址已更新。'
    cancelEdit()
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    message.value = e?.message || '更新地址失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

async function submitDelete(addressId) {
  if (!auth.authed || submitting.value) return
  const generation = ++actionGeneration
  const scope = sessionScope.value
  submitting.value = true
  message.value = ''
  try {
    await deleteMarketAddress(addressId)
    if (!isCurrentAction(generation, scope)) return
    message.value = '地址已删除。'
    if (editingAddressId.value === addressId) {
      cancelEdit()
    }
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    message.value = e?.message || '删除地址失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

watch(
  sessionScope,
  () => {
    requestGeneration += 1
    actionGeneration += 1
    addresses.value = []
    loading.value = false
    submitting.value = false
    error.value = ''
    message.value = '地址簿会在实物商品详情页直接复用。'
    resetForm()
    cancelEdit()
    if (auth.authed) reload()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  requestGeneration += 1
  actionGeneration += 1
})
</script>
