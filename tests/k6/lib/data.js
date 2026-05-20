import { config } from './config.js'

export const seededUsers = {
  primaryUserId: '00000000-0000-7000-8000-000000000001',
  secondaryUserId: '00000000-0000-7000-8000-000000000002',
  adminUserId: '00000000-0000-7000-8000-000000000003'
}

export function uniqueName(prefix) {
  return `${prefix} ${Date.now()} ${__VU}-${__ITER}-${Math.random().toString(16).slice(2, 8)}`
}

export function createPostPayload() {
  const payload = {
    title: uniqueName('k6 load post'),
    blocks: [
      {
        type: 'text',
        text: `k6 generated content vu=${__VU} iter=${__ITER}`,
        language: '',
        caption: '',
        displayName: '',
        metadata: {}
      }
    ],
    tags: [config.postTag]
  }
  if (config.postCategoryId) {
    payload.categoryId = config.postCategoryId
  }
  return payload
}

export function createCommentPayload(postId) {
  return {
    content: `k6 comment for ${postId} vu=${__VU} iter=${__ITER}`,
    entityType: 1,
    entityId: postId,
    targetId: null
  }
}

export function createDriveFolderPayload() {
  return {
    parentId: null,
    name: uniqueName('k6-folder')
  }
}

export function ossUploadSessionPayload() {
  return {
    usage: 'load-test',
    ownerService: 'community-k6',
    ownerDomain: 'load-test',
    ownerType: 'scenario',
    ownerId: `${__VU}-${__ITER}`,
    visibility: 'PRIVATE',
    fileName: uniqueName('k6-object') + '.txt',
    contentType: 'text/plain',
    contentLength: 64,
    checksumSha256: '',
    actorId: seededUsers.primaryUserId
  }
}
