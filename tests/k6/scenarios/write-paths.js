import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { authenticatedParams } from '../lib/auth.js'
import { config } from '../lib/config.js'
import { createCommentPayload, createDriveFolderPayload, createPostPayload } from '../lib/data.js'
import { createdComments, createdDriveFolders, createdPosts, flowDuration } from '../lib/metrics.js'
import { get, idempotencyKey, postJson, putJson, randomThinkTime, resultData } from '../lib/http.js'

export const options = buildOptions('write-paths', { exec: 'writepaths' })

function maybeCreatePost(accessToken) {
  const start = Date.now()
  const response = postJson('/api/posts', createPostPayload(), authenticatedParams(accessToken, {
    'Idempotency-Key': idempotencyKey('post')
  }), 200)
  const data = resultData(response, {})
  const postId = data.postId || data.id
  if (postId) {
    createdPosts.add(1)
    flowDuration.add(Date.now() - start, { flow: 'create-post' })
  }
  return postId
}

function maybeComment(accessToken, postId) {
  if (!postId) {
    return
  }
  const response = postJson(`/api/posts/${postId}/comments`, createCommentPayload(postId), authenticatedParams(accessToken, {
    'Idempotency-Key': idempotencyKey('comment')
  }), 200)
  if (response.status === 200) {
    createdComments.add(1)
  }
}

function maybeBookmarkAndLike(accessToken, postId) {
  if (!postId) {
    return
  }
  const params = authenticatedParams(accessToken)
  putJson(`/api/posts/${postId}/bookmark`, {}, params, 200)
  postJson('/api/likes', {
    entityType: 1,
    entityId: postId,
    liked: true
  }, params, 200)
}

function maybeCreateDriveFolder(accessToken) {
  const response = postJson('/api/drive/folders', createDriveFolderPayload(), authenticatedParams(accessToken), 200)
  if (response.status === 200) {
    createdDriveFolders.add(1)
  }
}

function authenticatedReadBack(accessToken) {
  const params = authenticatedParams(accessToken)
  get('/api/auth/me', params)
  get('/api/notices/summary', params)
  get('/api/wallet/summary', params)
  get('/api/drive/entries', params)
}

export function writepaths() {
  const accessToken = token()
  if (!accessToken) {
    randomThinkTime()
    return
  }

  authenticatedReadBack(accessToken)
  if (config.allowWrites && Math.random() * 100 < config.writeRatio) {
    const postId = maybeCreatePost(accessToken)
    maybeComment(accessToken, postId)
    maybeBookmarkAndLike(accessToken, postId)
    maybeCreateDriveFolder(accessToken)
  }

  randomThinkTime()
}

export default writepaths
