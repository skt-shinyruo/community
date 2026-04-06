const state = {
  health: null,
  runtime: null,
  history: null,
  selectedBatchId: null,
  selectedDetail: null,
  activeJobId: null,
  pollTimer: null,
  aiConfigs: [],
  editingAiConfig: null
}

const elements = {
  refreshAll: document.getElementById('refresh-all'),
  refreshRuntime: document.getElementById('refresh-runtime'),
  refreshHistory: document.getElementById('refresh-history'),
  runtimeSummary: document.getElementById('runtime-summary'),
  runtimeGrid: document.getElementById('runtime-status-grid'),
  modeSwitch: document.getElementById('mode-switch'),
  scenePreset: document.getElementById('scene-preset'),
  requestedBy: document.getElementById('requested-by'),
  aiEnhancement: document.getElementById('ai-enhancement'),
  preview: document.getElementById('generate-preview'),
  jobStatus: document.getElementById('job-status'),
  historySummary: document.getElementById('history-summary'),
  historyBody: document.getElementById('batch-history-body'),
  detail: document.getElementById('batch-detail'),
  generateForm: document.getElementById('generate-form'),
  countInputs: {
    users: document.getElementById('count-users'),
    posts: document.getElementById('count-posts'),
    comments: document.getElementById('count-comments'),
    socialFollows: document.getElementById('count-socialFollows'),
    socialLikes: document.getElementById('count-socialLikes')
  },
  aiConfig: {
    listEl: document.getElementById('ai-config-list'),
    editorPanel: document.getElementById('ai-editor-panel'),
    editorTitle: document.getElementById('ai-editor-title'),
    newBtn: document.getElementById('ai-new-btn'),
    closeBtn: document.getElementById('ai-editor-close'),
    editId: document.getElementById('ai-edit-id'),
    name: document.getElementById('ai-name'),
    provider: document.getElementById('ai-provider'),
    model: document.getElementById('ai-model'),
    baseUrl: document.getElementById('ai-base-url'),
    apiKey: document.getElementById('ai-api-key'),
    timeoutMs: document.getElementById('ai-timeout-ms'),
    maxItems: document.getElementById('ai-max-items'),
    testBtn: document.getElementById('ai-test-btn'),
    saveBtn: document.getElementById('ai-save-btn'),
    form: document.getElementById('ai-config-form'),
    result: document.getElementById('ai-test-result')
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function formatDate(value) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

function statusLabel(status) {
  const labels = {
    pending: '等待中',
    running: '运行中',
    succeeded: '成功',
    failed: '失败'
  }

  return labels[status] ?? status ?? '未知'
}

function statusBadgeClass(status) {
  return `badge badge-${status ?? 'pending'}`
}

function formatCounts(summary = {}) {
  const entries = Object.entries(summary.byEntityType ?? {})

  if (entries.length === 0) {
    return `0 / ${summary.totalCount ?? 0}`
  }

  return entries
    .map(([key, count]) => `${key}:${count}`)
    .join(' · ')
}

function buildHistoryEntries(history) {
  const entries = []

  if (history?.defaultBatch) {
    entries.push({
      kind: 'default',
      label: '默认',
      ...history.defaultBatch
    })
  }

  for (const entry of history?.manualBatches ?? []) {
    entries.push({
      kind: 'manual',
      label: '手动',
      ...entry
    })
  }

  return entries
}

function getGenerateFormMetadata() {
  return state.health?.ui?.generateForm ?? null
}

function getCurrentMode() {
  return elements.modeSwitch.dataset.mode ?? 'manual-generate'
}

function findPresetById(presetId) {
  return getGenerateFormMetadata()?.scenePresets?.find((preset) => preset.id === presetId) ?? null
}

function getPresetForMode(mode) {
  return getGenerateFormMetadata()?.scenePresets?.find((preset) => preset.mode === mode) ?? null
}

function readCounts() {
  return Object.fromEntries(
    Object.entries(elements.countInputs).map(([key, input]) => [
      key,
      Math.max(0, Number.parseInt(input.value || '0', 10) || 0)
    ])
  )
}

function getPreviewPayload() {
  const preset = findPresetById(elements.scenePreset.value) ?? getPresetForMode(getCurrentMode())
  const mode = getCurrentMode()
  const aiAvailable = Boolean(getGenerateFormMetadata()?.ai?.enabled)
  const aiEnhancement = mode === 'manual-generate' && aiAvailable && elements.aiEnhancement.checked
  const requestedBy = elements.requestedBy.value.trim() || preset?.jobRequest?.requestedBy || 'local-dev'

  return {
    mode,
    scenePresetId: preset?.id ?? null,
    requestedBy,
    counts: readCounts(),
    aiEnhancement,
    jobRequest: {
      requestedBy,
      batchType: preset?.jobRequest?.batchType ?? 'demo-seed',
      jobType: preset?.jobRequest?.jobType ?? 'demo-seed',
      mode,
      aiEnhancement
    }
  }
}

function renderPreview() {
  elements.preview.textContent = JSON.stringify(getPreviewPayload(), null, 2)
}

function setCounts(counts = {}) {
  for (const [key, input] of Object.entries(elements.countInputs)) {
    input.value = counts[key] ?? 0
  }
}

function syncDraftFromPreset(preset) {
  if (!preset) {
    renderPreview()
    return
  }

  elements.scenePreset.value = preset.id
  elements.requestedBy.value = preset.jobRequest?.requestedBy ?? 'local-dev'
  setCounts(preset.counts)
  renderPreview()
}

function renderModes() {
  const metadata = getGenerateFormMetadata()
  const modes = metadata?.modes ?? []
  const currentMode = getCurrentMode()

  elements.modeSwitch.innerHTML = modes
    .map(
      (mode) => `
        <button
          type="button"
          class="mode-pill ${mode.id === currentMode ? 'mode-pill-active' : ''}"
          data-mode="${escapeHtml(mode.id)}"
        >
          ${escapeHtml(mode.label)}
        </button>
      `
    )
    .join('')
}

function setMode(mode) {
  elements.modeSwitch.dataset.mode = mode

  const currentPreset = findPresetById(elements.scenePreset.value)

  if (!currentPreset || currentPreset.mode !== mode) {
    const replacementPreset = getPresetForMode(mode)

    if (replacementPreset) {
      syncDraftFromPreset(replacementPreset)
    }
  }

  renderModes()
  updateAiEnhancementToggle()
  renderPreview()
}

function renderPresetOptions() {
  const presets = getGenerateFormMetadata()?.scenePresets ?? []

  elements.scenePreset.innerHTML = presets
    .map(
      (preset) => `
        <option value="${escapeHtml(preset.id)}">${escapeHtml(preset.label)}</option>
      `
    )
    .join('')
}

function renderRuntimeStatus() {
  const summary = state.runtime?.summary
  const cards = state.runtime?.cards ?? []

  elements.runtimeSummary.innerHTML = summary
    ? `
        <div class="summary-chip">
          <strong>${escapeHtml(summary.status === 'ready' ? '就绪' : '需关注')}</strong>
          <span>${summary.readyCount}/${summary.totalCount} checks ready</span>
        </div>
        <div class="summary-chip">
          <strong>必需项</strong>
          <span>${summary.requiredReadyCount}/${summary.requiredCount} ready</span>
        </div>
      `
    : '<div class="empty-state">运行态摘要暂不可用。</div>'

  elements.runtimeGrid.innerHTML = cards
    .map(
      (card) => `
        <article class="runtime-card ${card.ready ? 'runtime-card-ready' : 'runtime-card-blocked'}">
          <strong>${escapeHtml(card.label)}</strong>
          <div class="${statusBadgeClass(card.ready ? 'succeeded' : 'failed')}">
            ${escapeHtml(card.ready ? 'Ready' : 'Blocked')}
          </div>
          <p class="muted">${escapeHtml(card.required ? '必需检查' : '可选检查')}</p>
          <p>${escapeHtml(card.detail ?? '无额外信息')}</p>
        </article>
      `
    )
    .join('')
}

function renderHistory() {
  const entries = buildHistoryEntries(state.history)
  const historySummary = state.history?.history

  elements.historySummary.innerHTML = historySummary
    ? `
        <div class="summary-chip">
          <strong>总批次</strong>
          <span>${historySummary.totalBatchCount}</span>
        </div>
        <div class="summary-chip">
          <strong>手动批次</strong>
          <span>${historySummary.manualBatchCount}</span>
        </div>
        <div class="summary-chip">
          <strong>默认批次</strong>
          <span>${historySummary.defaultBatchId ?? '—'}</span>
        </div>
      `
    : ''

  if (entries.length === 0) {
    elements.historyBody.innerHTML = '<tr><td colspan="6">暂无批次记录。</td></tr>'
    return
  }

  elements.historyBody.innerHTML = entries
    .map((entry) => {
      const isSelected = entry.batch.id === state.selectedBatchId

      return `
        <tr class="${isSelected ? 'is-selected' : ''}" data-batch-id="${entry.batch.id}">
          <td>
            <strong>#${entry.batch.id}</strong>
            <div class="muted">${escapeHtml(entry.label)}</div>
          </td>
          <td>${escapeHtml(entry.batch.batchType)}</td>
          <td><span class="${statusBadgeClass(entry.batch.status)}">${escapeHtml(statusLabel(entry.batch.status))}</span></td>
          <td>${escapeHtml(`${entry.targetSummary.totalCount} / ${entry.actualSummary.totalCount}`)}</td>
          <td>${escapeHtml(entry.latestJob?.jobKey ?? '—')}</td>
          <td>${escapeHtml(formatDate(entry.batch.createdAt))}</td>
        </tr>
      `
    })
    .join('')
}

function renderBatchDetail() {
  const detail = state.selectedDetail

  if (!detail) {
    elements.detail.innerHTML = '<div class="empty-state">请选择一个批次查看详情。</div>'
    return
  }

  elements.detail.innerHTML = `
    <div class="detail-stack">
      <div>
        <h3>Batch #${escapeHtml(detail.batch.id)}</h3>
        <p class="muted">${escapeHtml(detail.batch.batchType)} · ${escapeHtml(detail.batch.requestedBy)}</p>
      </div>
      <div class="detail-grid">
        <article class="detail-card">
          <strong>目标数量</strong>
          <span>${escapeHtml(String(detail.targetSummary.totalCount))}</span>
          <p class="muted">${escapeHtml(formatCounts(detail.targetSummary))}</p>
        </article>
        <article class="detail-card">
          <strong>实际数量</strong>
          <span>${escapeHtml(String(detail.actualSummary.totalCount))}</span>
          <p class="muted">${escapeHtml(formatCounts(detail.actualSummary))}</p>
        </article>
        <article class="detail-card">
          <strong>失败缺口</strong>
          <span>${escapeHtml(String(detail.failureSummary.totalCount))}</span>
          <p class="muted">${escapeHtml(detail.failureSummary.lastErrorMessage ?? '无')}</p>
        </article>
        <article class="detail-card">
          <strong>删除条件</strong>
          <span>${escapeHtml(detail.detail.canDelete ? '可删除' : '暂不可删除')}</span>
          <p class="muted">${escapeHtml(detail.detail.isDefaultBatch ? '默认批次' : '手动批次')}</p>
        </article>
      </div>
      <div class="detail-block">
        <p class="panel-kicker">Job Timeline</p>
        <div class="jobs-list">
          ${(detail.jobs ?? [])
            .map(
              (job) => `
                <div class="job-row">
                  <div class="generate-actions">
                    <strong>${escapeHtml(job.jobKey)}</strong>
                    <span class="${statusBadgeClass(job.status)}">${escapeHtml(statusLabel(job.status))}</span>
                  </div>
                  <p class="muted">创建于 ${escapeHtml(formatDate(job.createdAt))}</p>
                  <p>${escapeHtml(job.errorMessage ?? '无错误信息')}</p>
                </div>
              `
            )
            .join('')}
        </div>
      </div>
      <div class="detail-block">
        <p class="panel-kicker">Raw Detail</p>
        <pre>${escapeHtml(JSON.stringify(detail.detail, null, 2))}</pre>
      </div>
    </div>
  `
}

async function fetchJson(path, options) {
  const response = await fetch(path, options)
  const data = await response.json()

  if (!response.ok) {
    throw new Error(data.message ?? data.error ?? `Request failed: ${response.status}`)
  }

  return data
}

function stopPolling() {
  if (state.pollTimer) {
    window.clearTimeout(state.pollTimer)
    state.pollTimer = null
  }
}

function setJobStatus(message) {
  elements.jobStatus.textContent = message
}

async function loadRuntimeStatus() {
  state.runtime = await fetchJson('/api/runtime-status')
  renderRuntimeStatus()
}

async function loadHistory({ preserveSelection = true } = {}) {
  state.history = await fetchJson('/api/batches')

  if (!preserveSelection || !state.selectedBatchId) {
    state.selectedBatchId =
      state.history.defaultBatch?.batch?.id ??
      state.history.manualBatches?.[0]?.batch?.id ??
      null
  }

  renderHistory()

  if (state.selectedBatchId != null) {
    await loadBatchDetail(state.selectedBatchId)
  } else {
    state.selectedDetail = null
    renderBatchDetail()
  }
}

async function loadBatchDetail(batchId) {
  state.selectedBatchId = batchId
  state.selectedDetail = await fetchJson(`/api/batches/${batchId}`)
  renderHistory()
  renderBatchDetail()
}

async function loadHealth() {
  state.health = await fetchJson('/health')
  document.title = state.health.ui?.title ?? 'mock-data-studio'
  renderPresetOptions()

  const defaultDraft = state.health.ui?.generateForm?.defaultDraft

  if (defaultDraft) {
    elements.modeSwitch.dataset.mode = defaultDraft.mode
    elements.requestedBy.value = defaultDraft.requestedBy
    elements.aiEnhancement.checked = true
    setCounts(defaultDraft.counts)
    renderModes()
    syncDraftFromPreset(findPresetById(defaultDraft.scenePresetId))
  } else {
    renderModes()
    renderPreview()
  }
}

async function loadAiConfigs() {
  try {
    const response = await fetchJson('/api/ai-config')
    state.aiConfigs = response.data
    renderAiConfigList()
  } catch {
    state.aiConfigs = []
    renderAiConfigList()
  }
}

function renderAiConfigList() {
  const configs = state.aiConfigs
  if (!configs || configs.length === 0) {
    elements.aiConfig.listEl.innerHTML = '<div class="empty-state">暂无 AI 配置，点击「新建配置」添加。</div>'
    return
  }

  elements.aiConfig.listEl.innerHTML = configs
    .map((cfg) => {
      const metaParts = [cfg.provider, cfg.model]
      if (cfg.baseUrl) metaParts.push(cfg.baseUrl)
      return `
        <div class="ai-config-card ${cfg.isActive ? 'ai-config-active' : ''}" data-id="${cfg.id}">
          <div class="ai-config-card-info">
            <div class="ai-config-card-name">
              ${escapeHtml(cfg.name)}
              ${cfg.isActive ? '<span class="ai-badge ai-badge-active">使用中</span>' : ''}
              ${!cfg.enabled ? '<span class="ai-badge ai-badge-disabled">已禁用</span>' : ''}
            </div>
            <div class="ai-config-card-meta">${escapeHtml(metaParts.join(' · '))}</div>
          </div>
          <div class="ai-config-card-actions">
            <button class="ai-card-btn" data-action="test" data-id="${cfg.id}">测试</button>
            ${!cfg.isActive ? `<button class="ai-card-btn ai-card-btn-activate" data-action="activate" data-id="${cfg.id}">启用</button>` : ''}
            <button class="ai-card-btn" data-action="edit" data-id="${cfg.id}">编辑</button>
            <button class="ai-card-btn ai-card-btn-delete" data-action="delete" data-id="${cfg.id}">删除</button>
          </div>
        </div>
      `
    })
    .join('')
}

function showAiEditor(config = null) {
  state.editingAiConfig = config
  elements.aiConfig.editorPanel.style.display = 'block'
  elements.aiConfig.editorTitle.textContent = config ? `编辑: ${config.name}` : '新建配置'
  elements.aiConfig.editId.value = config ? config.id : ''
  elements.aiConfig.name.value = config ? config.name : ''
  elements.aiConfig.provider.value = config ? config.provider : 'openai'
  elements.aiConfig.model.value = config ? config.model : ''
  elements.aiConfig.baseUrl.value = config ? (config.baseUrl || '') : ''
  elements.aiConfig.apiKey.value = ''
  elements.aiConfig.timeoutMs.value = config ? config.timeoutMs : 8000
  elements.aiConfig.maxItems.value = config ? config.maxItemsPerJob : 20
  elements.aiConfig.result.className = 'ai-test-result'
  elements.aiConfig.result.textContent = ''

  if (elements.aiConfig.provider.value === 'ollama') {
    elements.aiConfig.baseUrl.placeholder = 'http://host.docker.internal:11434/v1'
    elements.aiConfig.model.placeholder = 'llama3, qwen2, ...'
  } else {
    elements.aiConfig.baseUrl.placeholder = 'https://api.openai.com/v1'
    elements.aiConfig.model.placeholder = ''
  }
}

function hideAiEditor() {
  elements.aiConfig.editorPanel.style.display = 'none'
  state.editingAiConfig = null
}

function getAiConfigFormPayload() {
  return {
    name: elements.aiConfig.name.value.trim(),
    provider: elements.aiConfig.provider.value,
    model: elements.aiConfig.model.value.trim(),
    baseUrl: elements.aiConfig.baseUrl.value.trim() || null,
    apiKey: elements.aiConfig.apiKey.value.trim() || null,
    timeoutMs: Number(elements.aiConfig.timeoutMs.value) || 8000,
    maxItemsPerJob: Number(elements.aiConfig.maxItems.value) || 20,
    enabled: true
  }
}

async function testAiConfigFromCard(cfg) {
  const payload = {
    provider: cfg.provider,
    model: cfg.model,
    baseUrl: cfg.baseUrl || null,
    apiKey: cfg.apiKey || null,
    timeoutMs: cfg.timeoutMs || 8000
  }
  const btn = document.querySelector(`[data-action="test"][data-id="${cfg.id}"]`)
  if (btn) {
    btn.disabled = true
    btn.textContent = '测试中…'
  }
  try {
    const response = await fetch('/api/ai-config/test', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const data = await response.json()
    if (data.ok) {
      alert(`✓ ${data.message}`)
    } else {
      alert(`✗ ${data.message}`)
    }
  } catch (error) {
    alert(`✗ 请求失败: ${error.message}`)
  } finally {
    if (btn) {
      btn.disabled = false
      btn.textContent = '测试'
    }
  }
}

async function testAiConfig() {
  const payload = getAiConfigFormPayload()
  if (!payload.provider || !payload.model) {
    elements.aiConfig.result.className = 'ai-test-result error'
    elements.aiConfig.result.textContent = '✗ Provider 和 Model 必填'
    return
  }
  elements.aiConfig.testBtn.disabled = true
  elements.aiConfig.testBtn.textContent = '测试中...'
  elements.aiConfig.result.className = 'ai-test-result'
  elements.aiConfig.result.textContent = ''

  try {
    const response = await fetch('/api/ai-config/test', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const data = await response.json()

    if (data.ok) {
      elements.aiConfig.result.className = 'ai-test-result success'
      elements.aiConfig.result.textContent = `✓ ${data.message}`
    } else {
      elements.aiConfig.result.className = 'ai-test-result error'
      elements.aiConfig.result.textContent = `✗ ${data.message}`
    }
  } catch (error) {
    elements.aiConfig.result.className = 'ai-test-result error'
    elements.aiConfig.result.textContent = `✗ 请求失败: ${error.message}`
  } finally {
    elements.aiConfig.testBtn.disabled = false
    elements.aiConfig.testBtn.textContent = '测试连接'
  }
}

async function saveAiConfig() {
  const payload = getAiConfigFormPayload()
  if (!payload.name || !payload.provider || !payload.model) {
    elements.aiConfig.result.className = 'ai-test-result error'
    elements.aiConfig.result.textContent = '✗ 名称、Provider 和 Model 必填'
    return
  }
  elements.aiConfig.saveBtn.disabled = true
  elements.aiConfig.saveBtn.textContent = '保存中...'

  try {
    const editId = elements.aiConfig.editId.value
    let response
    if (editId) {
      response = await fetch(`/api/ai-config/${editId}`, {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload)
      })
    } else {
      response = await fetch('/api/ai-config', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload)
      })
    }
    const data = await response.json()

    if (data.ok) {
      elements.aiConfig.result.className = 'ai-test-result success'
      elements.aiConfig.result.textContent = '✓ 已保存'
      await loadAiConfigs()
      await loadRuntimeStatus()
      await loadHealth()
      renderPresetOptions()
      updateAiEnhancementToggle()
      setTimeout(hideAiEditor, 600)
    } else {
      elements.aiConfig.result.className = 'ai-test-result error'
      elements.aiConfig.result.textContent = `✗ ${data.message}`
    }
  } catch (error) {
    elements.aiConfig.result.className = 'ai-test-result error'
    elements.aiConfig.result.textContent = `✗ 保存失败: ${error.message}`
  } finally {
    elements.aiConfig.saveBtn.disabled = false
    elements.aiConfig.saveBtn.textContent = '保存'
  }
}

async function activateAiConfig(id) {
  try {
    await fetch(`/api/ai-config/${id}/activate`, { method: 'POST' })
    await loadAiConfigs()
    await loadRuntimeStatus()
    await loadHealth()
    renderPresetOptions()
    updateAiEnhancementToggle()
  } catch (error) {
    setJobStatus(`启用失败: ${error.message}`)
  }
}

async function deleteAiConfig(id) {
  if (!confirm('确定删除此配置？')) return
  try {
    await fetch(`/api/ai-config/${id}`, { method: 'DELETE' })
    await loadAiConfigs()
  } catch (error) {
    setJobStatus(`删除失败: ${error.message}`)
  }
}

function updateAiEnhancementToggle() {
  const aiInfo = state.health?.ui?.generateForm?.ai
  const statusEl = document.getElementById('ai-status-msg')
  const wrapper = document.getElementById('ai-enhancement-wrapper')

  if (!aiInfo || !aiInfo.enabled) {
    elements.aiEnhancement.checked = false
    elements.aiEnhancement.disabled = true
    statusEl.className = 'ai-status-msg ai-status-error'
    statusEl.textContent = '未配置 AI，请前往「AI 配置」Tab 添加配置。'
    return
  }

  elements.aiEnhancement.disabled = false
  elements.aiEnhancement.checked = true
  statusEl.className = 'ai-status-msg ai-status-ok'
  statusEl.textContent = `AI 已就绪：${aiInfo.provider} · ${aiInfo.model}`

  if (getCurrentMode() !== 'manual-generate') {
    elements.aiEnhancement.checked = false
    elements.aiEnhancement.disabled = true
    statusEl.className = 'ai-status-msg'
    statusEl.style.display = 'none'
  } else {
    statusEl.style.display = 'block'
  }
}

function switchTab(tabId) {
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.classList.toggle('tab-btn-active', btn.dataset.tab === tabId)
  })
  document.querySelectorAll('.tab-pane').forEach((pane) => {
    pane.classList.toggle('tab-pane-active', pane.id === `tab-${tabId}`)
  })
}

async function pollJob(jobId) {
  stopPolling()
  state.activeJobId = jobId

  try {
    const response = await fetchJson(`/api/jobs/${jobId}`)
    const polling = response.polling
    setJobStatus(`Job #${polling.jobId} · ${statusLabel(polling.status)}`)

    if (polling.batchId != null) {
      await loadHistory({ preserveSelection: false })
      await loadBatchDetail(polling.batchId)
    }

    if (!polling.isTerminal) {
      state.pollTimer = window.setTimeout(() => {
        pollJob(jobId).catch((error) => {
          setJobStatus(`轮询失败：${error.message}`)
        })
      }, 1500)
    }
  } catch (error) {
    setJobStatus(`轮询失败：${error.message}`)
  }
}

async function submitGenerate(event) {
  event.preventDefault()
  const previewPayload = getPreviewPayload()

  setJobStatus('正在提交 job…')

  try {
    const response = await fetchJson('/api/jobs', {
      method: 'POST',
      headers: {
        'content-type': 'application/json'
      },
      body: JSON.stringify(previewPayload.jobRequest)
    })

    setJobStatus(`已创建 Job #${response.job.id}，开始轮询状态。`)
    await loadHistory({ preserveSelection: false })

    if (response.polling?.batchId != null) {
      await loadBatchDetail(response.polling.batchId)
    }

    await pollJob(response.job.id)
  } catch (error) {
    setJobStatus(`提交失败：${error.message}`)
  }
}

async function refreshAll() {
  try {
    await Promise.all([loadRuntimeStatus(), loadHistory({ preserveSelection: true })])
    setJobStatus(state.activeJobId ? `Job #${state.activeJobId} 已同步最新状态。` : '状态已刷新。')
  } catch (error) {
    setJobStatus(`刷新失败：${error.message}`)
  }
}

function bindEvents() {
  elements.refreshAll.addEventListener('click', () => {
    refreshAll().catch((error) => {
      setJobStatus(`刷新失败：${error.message}`)
    })
  })

  elements.refreshRuntime.addEventListener('click', () => {
    loadRuntimeStatus().catch((error) => {
      setJobStatus(`状态刷新失败：${error.message}`)
    })
  })

  elements.refreshHistory.addEventListener('click', () => {
    loadHistory({ preserveSelection: true }).catch((error) => {
      setJobStatus(`历史刷新失败：${error.message}`)
    })
  })

  elements.modeSwitch.addEventListener('click', (event) => {
    const button = event.target.closest('[data-mode]')

    if (!button) {
      return
    }

    setMode(button.dataset.mode)
  })

  elements.aiConfig.newBtn.addEventListener('click', () => {
    showAiEditor(null)
  })

  elements.aiConfig.closeBtn.addEventListener('click', () => {
    hideAiEditor()
  })

  elements.aiConfig.listEl.addEventListener('click', (event) => {
    const btn = event.target.closest('[data-action]')
    if (!btn) return
    const id = Number(btn.dataset.id)
    const action = btn.dataset.action
    if (action === 'test') {
      const cfg = state.aiConfigs.find((c) => c.id === id)
      if (cfg) testAiConfigFromCard(cfg)
    } else if (action === 'edit') {
      const cfg = state.aiConfigs.find((c) => c.id === id)
      if (cfg) showAiEditor(cfg)
    } else if (action === 'activate') {
      activateAiConfig(id)
    } else if (action === 'delete') {
      deleteAiConfig(id)
    }
  })

  elements.aiConfig.testBtn.addEventListener('click', () => {
    testAiConfig().catch((error) => {
      elements.aiConfig.result.className = 'ai-test-result error'
      elements.aiConfig.result.textContent = `✗ ${error.message}`
    })
  })

  elements.aiConfig.form.addEventListener('submit', (event) => {
    event.preventDefault()
    saveAiConfig().catch((error) => {
      elements.aiConfig.result.className = 'ai-test-result error'
      elements.aiConfig.result.textContent = `✗ ${error.message}`
    })
  })

  elements.aiConfig.provider.addEventListener('change', () => {
    const provider = elements.aiConfig.provider.value
    if (provider === 'ollama') {
      elements.aiConfig.baseUrl.placeholder = 'http://host.docker.internal:11434/v1'
      elements.aiConfig.model.placeholder = 'llama3, qwen2, ...'
    } else {
      elements.aiConfig.baseUrl.placeholder = 'https://api.openai.com/v1'
      elements.aiConfig.model.placeholder = ''
    }
  })

  elements.scenePreset.addEventListener('change', () => {
    const preset = findPresetById(elements.scenePreset.value)

    if (preset) {
      elements.modeSwitch.dataset.mode = preset.mode
      renderModes()
      syncDraftFromPreset(preset)
    }
  })

  elements.generateForm.addEventListener('input', () => {
    renderPreview()
  })

  elements.generateForm.addEventListener('submit', (event) => {
    submitGenerate(event).catch((error) => {
      setJobStatus(`提交失败：${error.message}`)
    })
  })

  elements.historyBody.addEventListener('click', (event) => {
    const row = event.target.closest('[data-batch-id]')

    if (!row) {
      return
    }

    loadBatchDetail(Number(row.dataset.batchId)).catch((error) => {
      setJobStatus(`详情加载失败：${error.message}`)
    })
  })

  document.getElementById('tab-bar').addEventListener('click', (event) => {
    const btn = event.target.closest('.tab-btn')
    if (!btn) return
    switchTab(btn.dataset.tab)
  })
}

async function init() {
  bindEvents()

  try {
    await loadHealth()
    await Promise.all([loadRuntimeStatus(), loadHistory({ preserveSelection: false }), loadAiConfigs()])
    updateAiEnhancementToggle()
    setJobStatus('Studio 已就绪。')
  } catch (error) {
    setJobStatus(`初始化失败：${error.message}`)
    elements.detail.innerHTML = `<div class="empty-state">${escapeHtml(error.message)}</div>`
  }
}

init()
