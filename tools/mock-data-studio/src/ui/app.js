const state = {
  health: null,
  runtime: null,
  history: null,
  selectedBatchId: null,
  selectedDetail: null,
  activeJobId: null,
  pollTimer: null
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
  elements.aiEnhancement.checked = Boolean(preset.aiEnhancement)
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
  const aiAvailable = Boolean(getGenerateFormMetadata()?.ai?.enabled)
  elements.aiEnhancement.disabled = !aiAvailable || mode !== 'manual-generate'
  if (elements.aiEnhancement.disabled) {
    elements.aiEnhancement.checked = false
  }
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
    elements.aiEnhancement.checked = Boolean(defaultDraft.aiEnhancement)
    const aiAvailable = Boolean(state.health.ui?.generateForm?.ai?.enabled)
    elements.aiEnhancement.disabled = !aiAvailable || defaultDraft.mode !== 'manual-generate'
    setCounts(defaultDraft.counts)
    renderModes()
    syncDraftFromPreset(findPresetById(defaultDraft.scenePresetId))
  } else {
    renderModes()
    renderPreview()
  }
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
}

async function init() {
  bindEvents()

  try {
    await loadHealth()
    await Promise.all([loadRuntimeStatus(), loadHistory({ preserveSelection: false })])
    setJobStatus('Studio 已就绪。')
  } catch (error) {
    setJobStatus(`初始化失败：${error.message}`)
    elements.detail.innerHTML = `<div class="empty-state">${escapeHtml(error.message)}</div>`
  }
}

init()
