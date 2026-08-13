<template>
  <FollowRelationListView relation-kind="followers" :user-id="userId" @trace="$emit('trace', $event)" />
</template>

<script setup>
import FollowRelationListView from './FollowRelationListView.vue'

defineEmits(['trace'])
defineProps({ userId: String })
</script>
