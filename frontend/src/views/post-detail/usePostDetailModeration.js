export function isDangerConfirmation(type) {
  return type === 'delete' || type === 'authorDelete'
}

export function resolvePostDetailConfirmation(type, postId) {
  if (type === 'top') {
    return {
      title: '确认置顶',
      message: `是否将帖子 #${postId} 置顶？`
    }
  }
  if (type === 'wonderful') {
    return {
      title: '确认加精',
      message: `是否将帖子 #${postId} 加精？`
    }
  }
  if (type === 'delete') {
    return {
      title: '确认删除',
      message: `是否删除帖子 #${postId}？删除后列表将不再展示。`
    }
  }
  if (type === 'authorDelete') {
    return {
      title: '确认删除',
      message: `是否删除帖子 #${postId}？该操作会将帖子标记为已删除。`
    }
  }
  return {
    title: '确认操作',
    message: '是否继续？'
  }
}
