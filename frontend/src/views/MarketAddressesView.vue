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
          <label class="market-field market-field--inline">
            <UiCheckbox v-model="form.defaultAddress">设为默认地址</UiCheckbox>
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
                  <UiInput v-model="editForm.receiverName" />
                </label>
                <label class="market-field">
                  <span>手机号</span>
                  <UiInput v-model="editForm.receiverPhone" />
                </label>
                <label class="market-field">
                  <span>省份</span>
                  <UiInput v-model="editForm.province" />
                </label>
                <label class="market-field">
                  <span>城市</span>
                  <UiInput v-model="editForm.city" />
                </label>
                <label class="market-field">
                  <span>区县</span>
                  <UiInput v-model="editForm.district" />
                </label>
                <label class="market-field">
                  <span>详细地址</span>
                  <UiInput v-model="editForm.detailAddress" />
                </label>
                <label class="market-field">
                  <span>邮编</span>
                  <UiInput v-model="editForm.postalCode" />
                </label>
                <label class="market-field market-field--inline">
                  <UiCheckbox v-model="editForm.defaultAddress">设为默认地址</UiCheckbox>
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
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiCheckbox from '../components/ui/UiCheckbox.vue'
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
const editingAddressId = ref(null)
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
const editForm = ref(emptyAddressForm())

const state = computed(() => buildMarketState({ addresses: addresses.value }))

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
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMarketAddresses()
    addresses.value = Array.isArray(data) ? data : []
    if (editingAddressId.value && !addresses.value.some((item) => item?.addressId === editingAddressId.value)) {
      cancelEdit()
    }
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

async function submitUpdate() {
  if (!editingAddressId.value) return
  submitting.value = true
  message.value = ''
  try {
    await updateMarketAddress(editingAddressId.value, buildPayload(editForm.value))
    message.value = '地址已更新。'
    cancelEdit()
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
    if (editingAddressId.value === addressId) {
      cancelEdit()
    }
    await reload()
  } catch (e) {
    message.value = e?.message || '删除地址失败'
  } finally {
    submitting.value = false
  }
}

onMounted(reload)
</script>
