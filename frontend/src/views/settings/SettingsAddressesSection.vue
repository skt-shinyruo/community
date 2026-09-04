<template>
  <UiCard class="settings-panel">
    <section class="settings-section">
      <div class="settings-section-head">
        <h2>收货地址</h2>
        <p>实物订单下单前，先把地址簿整理好。订单使用地址快照；这里管理未来下单的默认收货信息。</p>
      </div>

      <UiState v-if="error" variant="error">
        {{ error }}
        <template #description>地址簿暂时无法读取，可重试加载。</template>
        <template #actions>
          <UiButton variant="secondary" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <UiSkeleton v-else-if="loading" variant="list" :rows="2" label="正在加载地址簿" />

      <div v-else class="settings-addresses-body">
        <div class="settings-block-head">
          <h3>新增地址</h3>
          <p>保存常用收货信息，实物商品下单时会使用地址快照。</p>
        </div>

        <div class="settings-form-grid">
          <UiField label="收货人">
            <UiInput v-model="form.receiverName" name="address-receiver-name" autocomplete="off" />
          </UiField>
          <UiField label="手机号">
            <UiInput v-model="form.receiverPhone" name="address-receiver-phone" autocomplete="off" />
          </UiField>
          <UiField label="省份">
            <UiInput v-model="form.province" name="address-province" autocomplete="off" />
          </UiField>
          <UiField label="城市">
            <UiInput v-model="form.city" name="address-city" autocomplete="off" />
          </UiField>
          <UiField label="区县">
            <UiInput v-model="form.district" name="address-district" autocomplete="off" />
          </UiField>
          <UiField label="详细地址">
            <UiInput v-model="form.detailAddress" name="address-detail" autocomplete="off" />
          </UiField>
          <UiField label="邮编">
            <UiInput v-model="form.postalCode" name="address-postal-code" autocomplete="off" />
          </UiField>
          <label class="settings-checkbox">
            <input v-model="form.defaultAddress" class="settings-checkbox-input" type="checkbox" />
            <span class="settings-checkbox-copy">设为默认地址</span>
          </label>
        </div>

        <div class="settings-form-actions">
          <UiButton :disabled="submitting" @click="submitCreate">
            {{ submitting ? '保存中…' : '新增地址' }}
          </UiButton>
          <span class="muted" role="status">{{ message }}</span>
        </div>

        <UiState v-if="state.addresses.length === 0">
          暂无收货地址
          <template #description>创建第一条地址后，实物商品详情页就可以直接选择它下单。</template>
        </UiState>

        <div v-else class="settings-address-list">
          <article v-for="item in state.addresses" :key="item.addressId" class="settings-address-row">
            <div class="settings-address-main">
              <strong>{{ item.receiverName }}</strong>
              <p>{{ item.receiverPhone }} · {{ item.addressLine }}</p>
              <p v-if="item.defaultLabel" class="settings-address-default">{{ item.defaultLabel }}</p>
            </div>
            <div class="settings-row-actions">
              <UiButton variant="secondary" :disabled="submitting" data-test="address-edit" @click="startEdit(item)">编辑</UiButton>
              <UiButton variant="dangerSecondary" :disabled="submitting" @click="submitDelete(item.addressId)">删除</UiButton>
            </div>
            <form
              v-if="editingAddressId === item.addressId"
              class="settings-address-edit"
              data-test="address-edit-form"
              @submit.prevent="submitUpdate"
            >
              <div class="settings-block-head">
                <h3>编辑地址</h3>
                <p>修改后的地址只会用于后续下单，已创建订单仍使用当时的地址快照。</p>
              </div>

              <div class="settings-form-grid">
                <UiField label="收货人">
                  <UiInput v-model="editForm.receiverName" name="edit-address-receiver-name" autocomplete="off" />
                </UiField>
                <UiField label="手机号">
                  <UiInput v-model="editForm.receiverPhone" name="edit-address-receiver-phone" autocomplete="off" />
                </UiField>
                <UiField label="省份">
                  <UiInput v-model="editForm.province" name="edit-address-province" autocomplete="off" />
                </UiField>
                <UiField label="城市">
                  <UiInput v-model="editForm.city" name="edit-address-city" autocomplete="off" />
                </UiField>
                <UiField label="区县">
                  <UiInput v-model="editForm.district" name="edit-address-district" autocomplete="off" />
                </UiField>
                <UiField label="详细地址">
                  <UiInput v-model="editForm.detailAddress" name="edit-address-detail" autocomplete="off" />
                </UiField>
                <UiField label="邮编">
                  <UiInput v-model="editForm.postalCode" name="edit-address-postal-code" autocomplete="off" />
                </UiField>
                <label class="settings-checkbox">
                  <input v-model="editForm.defaultAddress" class="settings-checkbox-input" type="checkbox" />
                  <span class="settings-checkbox-copy">设为默认地址</span>
                </label>
              </div>

              <div class="settings-form-actions">
                <UiButton :disabled="submitting" type="submit" data-test="address-update-submit">
                  {{ submitting ? '保存中…' : '保存修改' }}
                </UiButton>
                <UiButton variant="secondary" :disabled="submitting" @click="cancelEdit">取消</UiButton>
              </div>
            </form>
          </article>
        </div>
      </div>
    </section>
  </UiCard>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import UiButton from '../../components/ui/UiButton.vue'
import UiCard from '../../components/ui/UiCard.vue'
import UiField from '../../components/ui/UiField.vue'
import UiInput from '../../components/ui/UiInput.vue'
import UiSkeleton from '../../components/ui/UiSkeleton.vue'
import UiState from '../../components/ui/UiState.vue'
import {
  createMarketAddress,
  deleteMarketAddress,
  listMarketAddresses,
  updateMarketAddress
} from '../../api/services/marketService'
import { useAuthStore } from '../../stores/auth'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { buildMarketState } from '../marketState'

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

<style scoped>
.settings-panel {
  display: grid;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.settings-section {
  padding: var(--space-6);
  display: grid;
  gap: var(--space-5);
}

.settings-section-head {
  display: grid;
  gap: var(--space-1);
}

.settings-section-head h2 {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--line-tight);
}

.settings-section-head p {
  margin: 0;
  color: var(--text-2);
  line-height: var(--line-normal);
}

.settings-addresses-body {
  display: grid;
  gap: var(--space-4);
}

.settings-block-head {
  display: grid;
  gap: var(--space-1);
}

.settings-block-head h3 {
  margin: 0;
  font-size: var(--text-md);
  line-height: var(--line-tight);
}

.settings-block-head p {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
}

.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.settings-checkbox {
  display: flex;
  align-items: center;
  align-self: end;
  gap: var(--space-2);
  min-height: var(--control-height);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  color: var(--text-1);
  cursor: pointer;
}

.settings-checkbox-input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.settings-checkbox:has(.settings-checkbox-input:focus-visible) {
  box-shadow: var(--focus-ring);
}

.settings-form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.settings-address-list {
  display: grid;
  gap: var(--space-3);
}

.settings-address-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.settings-address-row:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 88%, var(--surface-2) 12%);
}

.settings-address-main {
  display: grid;
  gap: var(--space-1);
}

.settings-address-main p {
  margin: 0;
  color: var(--text-2);
}

.settings-address-default {
  font-weight: 600;
  color: var(--accent-text);
}

.settings-row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.settings-address-edit {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

@media (max-width: 900px) {
  .settings-section {
    padding: var(--space-5);
  }

  .settings-form-grid {
    grid-template-columns: 1fr;
  }

  .settings-address-row {
    grid-template-columns: 1fr;
  }
}
</style>
