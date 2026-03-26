import {
  autoFillPhaseNames,
  resolveScenePreset
} from './scenes/defaults.mjs'

function countByEntityType(items = [], keyName) {
  return items.reduce((counts, item) => {
    const entityType = item?.[keyName]

    if (!entityType) {
      return counts
    }

    counts[entityType] = (counts[entityType] ?? 0) + 1
    return counts
  }, {})
}

function sumTargetsByEntityType(targets = []) {
  return targets.reduce((counts, target) => {
    const entityType = target.entityType

    if (!entityType) {
      return counts
    }

    counts[entityType] = (counts[entityType] ?? 0) + Math.max(0, target.targetCount ?? 0)
    return counts
  }, {})
}

function buildDeficits(targetCounts, existingCounts) {
  return Object.fromEntries(
    Object.entries(targetCounts).map(([entityType, targetCount]) => [
      entityType,
      Math.max(0, targetCount - (existingCounts[entityType] ?? 0))
    ])
  )
}

function buildPhasePlan(name, targets, existingCounts) {
  const targetCounts = sumTargetsByEntityType(targets)
  const deficits = buildDeficits(targetCounts, existingCounts)
  const totalDeficitCount = Object.values(deficits).reduce((sum, value) => sum + value, 0)

  return {
    name,
    targetCounts,
    deficits,
    totalDeficitCount,
    needsWork: totalDeficitCount > 0
  }
}

export function createPlanner({
  config,
  targetRepository,
  entityRefRepository,
  resolveScenePresetImpl = resolveScenePreset
} = {}) {
  if (!targetRepository?.listByBatchId || !targetRepository?.replaceForBatch) {
    throw new Error('targetRepository.listByBatchId and targetRepository.replaceForBatch are required')
  }

  if (!entityRefRepository?.listByBatchId) {
    throw new Error('entityRefRepository.listByBatchId is required')
  }

  return {
    async planDefaultBatch({ batchId, sceneKey = config?.autoFill?.sceneKey } = {}) {
      if (batchId == null) {
        throw new Error('batchId is required')
      }

      let targets = await targetRepository.listByBatchId(batchId)

      if (targets.length === 0) {
        const preset = resolveScenePresetImpl({ config, sceneKey })
        targets = await targetRepository.replaceForBatch(batchId, preset.targets)
      }

      const refs = await entityRefRepository.listByBatchId(batchId)
      const existingCounts = countByEntityType(refs, 'entityType')
      const targetCounts = sumTargetsByEntityType(targets)
      const deficits = buildDeficits(targetCounts, existingCounts)
      const totalDeficitCount = Object.values(deficits).reduce((sum, value) => sum + value, 0)

      const phaseTargetMap = autoFillPhaseNames.reduce((map, phaseName) => {
        map.set(phaseName, [])
        return map
      }, new Map())

      for (const target of targets) {
        const phaseName = target?.payloadJson?.phase ?? 'community'
        if (!phaseTargetMap.has(phaseName)) {
          phaseTargetMap.set(phaseName, [])
        }
        phaseTargetMap.get(phaseName).push(target)
      }

      const phases = [...phaseTargetMap.entries()].map(([name, phaseTargets]) =>
        buildPhasePlan(name, phaseTargets, existingCounts)
      )

      return {
        batchId,
        sceneKey: sceneKey ?? config?.autoFill?.sceneKey ?? null,
        existingCounts,
        targetCounts,
        deficits,
        totalDeficitCount,
        needsWork: totalDeficitCount > 0,
        phases
      }
    }
  }
}
