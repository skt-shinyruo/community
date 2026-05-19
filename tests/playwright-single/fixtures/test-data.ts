export const runId = process.env.SINGLE_TEST_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)

export function uniqueName(prefix: string): string {
  return `${prefix} ${runId}`
}

export const data = {
  postTitle: uniqueName('Playwright single 功能测试帖'),
  postBody: `这是一条由 Playwright single 本地测试创建的测试内容，run=${runId}，用于验证帖子、评论和收藏链路。`,
  postComment: `Playwright 评论 ${runId}：详情页评论提交链路正常。`,
  postTag: 'playwright',
  virtualListingTitle: uniqueName('Playwright 自动交付兑换码'),
  virtualListingDescription: 'single 环境 Playwright 发布的虚拟商品，用于验证市场发布、列表、详情和订单。',
  virtualListingInventory: `CODE-PW-${runId}`,
  virtualListingExtraInventory: `CODE-PW-EXTRA-${runId}`,
  driveFolder: uniqueName('Playwright 文件夹'),
  driveFolderRenamed: uniqueName('Playwright 文件夹 renamed'),
  retainedShareFolder: uniqueName('Playwright 分享保留'),
  knownIssueShareFolder: uniqueName('Playwright 已知问题分享'),
  shareCode: '1234',
  addressReceiver: '测试收件人',
  addressPhone: '13800000000'
}
