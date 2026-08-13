<template>
  <FollowRelationListView relation-kind="followees" :user-id="userId" @trace="$emit('trace', $event)" />
</template>

<script setup>
import FollowRelationListView from './FollowRelationListView.vue'

defineEmits(['trace'])
defineProps({ userId: String })
</script>
