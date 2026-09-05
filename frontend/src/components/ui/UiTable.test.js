// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiTable from './UiTable.vue'

const columns = [
  { key: 'title', label: '内容' },
  { key: 'kind', label: '类型', sortable: true },
  { key: 'status', label: '状态', sortable: true },
  { key: 'amount', label: '积分', align: 'right' }
]

const rows = [
  { unitId: 'a', title: 'CODE-001', kind: '兑换码', status: '可售', amount: 10 },
  { unitId: 'b', title: 'CODE-002', kind: '链接', status: '已失效', amount: 20 }
]

function mountTable(props = {}, slots = {}) {
  return mount(UiTable, {
    props: { columns, rows, rowKey: 'unitId', caption: '库存内容列表', ...props },
    slots
  })
}

describe('UiTable', () => {
  it('renders a semantic table with caption, column scopes and cell text from rows', () => {
    const wrapper = mountTable()

    const table = wrapper.get('table')
    expect(table.get('caption').text()).toBe('库存内容列表')
    expect(table.get('caption').classes()).toContain('sr-only')

    const headers = wrapper.findAll('th')
    expect(headers).toHaveLength(4)
    expect(headers.every((header) => header.attributes('scope') === 'col')).toBe(true)
    expect(headers.map((header) => header.text())).toEqual(['内容', '类型', '状态', '积分'])

    const bodyRows = wrapper.findAll('tbody tr')
    expect(bodyRows).toHaveLength(2)
    expect(bodyRows[0].findAll('td').map((cell) => cell.text())).toEqual(['CODE-001', '兑换码', '可售', '10'])

    // 数字缺省时单元格回落为空文本而不是 undefined / null。
    expect(mountTable({ rows: [{ unitId: 'c' }] }).get('tbody td').text()).toBe('')
  })

  it('right-aligns columns marked with align: right', () => {
    const wrapper = mountTable()
    const amountHeader = wrapper.findAll('th')[3]
    expect(amountHeader.classes()).toContain('ui-table__cell--right')
    for (const row of wrapper.findAll('tbody tr')) {
      expect(row.findAll('td')[3].classes()).toContain('ui-table__cell--right')
    }
  })

  it('overrides cell content through named cell slots', () => {
    const wrapper = mountTable({}, {
      'cell-status': `<template #cell-status="{ row }"><em>{{ row.status }}!</em></template>`
    })
    expect(wrapper.findAll('tbody em')).toHaveLength(2)
    expect(wrapper.findAll('tbody em')[0].text()).toBe('可售!')
  })

  it('emits sort with the column key from sortable header buttons only', async () => {
    const wrapper = mountTable()

    const sortButtons = wrapper.findAll('th button')
    expect(sortButtons).toHaveLength(2)
    for (const button of sortButtons) {
      expect(button.attributes('type')).toBe('button')
    }

    await sortButtons[0].trigger('click')
    await sortButtons[1].trigger('click')
    expect(wrapper.emitted('sort')).toEqual([['kind'], ['status']])
  })

  it('reflects the controlled sort state through aria-sort on the active column only', async () => {
    const wrapper = mountTable({ sortKey: 'kind', sortDirection: 'asc' })
    const headers = wrapper.findAll('th')
    expect(headers[1].attributes('aria-sort')).toBe('ascending')
    expect(headers[2].attributes('aria-sort')).toBeUndefined()

    await wrapper.setProps({ sortKey: 'status', sortDirection: 'desc' })
    expect(headers[1].attributes('aria-sort')).toBeUndefined()
    expect(headers[2].attributes('aria-sort')).toBe('descending')
  })

  it('keys rows by the configured key field or function with an index fallback', () => {
    const keyed = mountTable({ rowKey: (row) => `unit-${row.unitId}` })
    expect(keyed.findAll('tbody tr')).toHaveLength(2)

    const fallback = mountTable({ rows: [{ title: 'no key' }, { title: 'still no key' }] })
    expect(fallback.findAll('tbody tr')).toHaveLength(2)
  })

  it('renders an empty body without rows and keeps the header intact', () => {
    const wrapper = mountTable({ rows: [] })
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(wrapper.findAll('th')).toHaveLength(4)
  })
})
