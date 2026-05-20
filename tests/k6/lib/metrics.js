import { Counter, Rate, Trend } from 'k6/metrics'

export const loginFailures = new Counter('community_login_failures')
export const unexpectedStatus = new Rate('community_unexpected_status')
export const flowDuration = new Trend('community_flow_duration', true)
export const createdPosts = new Counter('community_created_posts')
export const createdComments = new Counter('community_created_comments')
export const createdDriveFolders = new Counter('community_created_drive_folders')
export const imConnected = new Counter('community_im_connected')
export const imRejected = new Counter('community_im_rejected')

export function recordUnexpected(ok) {
  unexpectedStatus.add(!ok)
}
