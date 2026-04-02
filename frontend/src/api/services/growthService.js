import http from '../http'
import { unwrapResultBody } from '../result'

export async function getGrowthSummary() {
  const resp = await http.get('/api/growth/summary')
  const { data, traceId } = unwrapResultBody(resp.data, '查询成长概览')
  const optionalNumber = (value) => {
    const next = Number(value)
    return Number.isFinite(next) ? next : null
  }
  return {
    data: {
      score: Number(data?.score || 0),
      level: Number(data?.level || 1),
      userLevel: optionalNumber(data?.userLevel),
      signInDaysInWindow: optionalNumber(data?.signInDaysInWindow),
      windowDays: optionalNumber(data?.windowDays),
      rewardBalance: Number(data?.rewardBalance || 0),
      frozenBalance: Number(data?.frozenBalance || 0)
    },
    traceId
  }
}

export async function getGrowthTasks({ date } = {}) {
  const resp = await http.get('/api/growth/tasks', {
    params: date ? { date } : {}
  })
  const { data, traceId } = unwrapResultBody(resp.data, '查询任务中心')
  return { data: data || { items: [] }, traceId }
}

export async function getCheckInStatus({ date } = {}) {
  const resp = await http.get('/api/growth/check-in/status', {
    params: date ? { date } : {}
  })
  const { data, traceId } = unwrapResultBody(resp.data, '查询签到状态')
  return { data: data || {}, traceId }
}

export async function getCheckInCalendar({ year, month } = {}) {
  const params = {}
  if (Number.isFinite(Number(year)) && Number(year) > 0) params.year = Number(year)
  if (Number.isFinite(Number(month)) && Number(month) > 0) params.month = Number(month)

  const resp = await http.get('/api/growth/check-in/calendar', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询签到日历')
  return { data: data || { checkedInDates: [] }, traceId }
}

export async function submitCheckIn({ date } = {}) {
  const resp = await http.post('/api/growth/check-in', null, {
    params: date ? { date } : {}
  })
  const { data, traceId } = unwrapResultBody(resp.data, '执行签到')
  return { data: data || {}, traceId }
}
