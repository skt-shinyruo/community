<template>
  <div class="user-card-wrapper" @mouseenter="show = true" @mouseleave="hide">
    <slot />
    
    <Transition name="pop">
      <div v-if="show && user" class="user-card-popover" :style="{ left: align === 'right' ? 'auto' : '0', right: align === 'right' ? '0' : 'auto' }">
        <div class="row" style="gap: 12px; align-items: flex-start">
           <UiAvatar :src="user.headerUrl" :name="user.username" :size="48" />
           <div style="flex: 1">
              <div style="font-weight: 700; font-size: 16px; color: var(--text-1)">{{ user.username }}</div>
              <div class="muted" style="font-size: 12px">User Since {{ new Date(user.createTime || Date.now()).getFullYear() }}</div>
              
              <div class="row" style="gap: 12px; margin-top: 8px; font-size: 13px">
                 <div class="stat-item">
                    <b>{{ user.postCount || 0 }}</b> Posts
                 </div>
                 <div class="stat-item">
                    <b>{{ user.likeCount || 0 }}</b> Likes
                 </div>
              </div>
           </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import UiAvatar from './UiAvatar.vue'

const props = defineProps({
  user: { type: Object, default: null },
  align: { type: String, default: 'left' }
})

const show = ref(false)
let timer = null

function hide() {
  timer = setTimeout(() => {
    show.value = false
  }, 300)
}
</script>

<style scoped>
.user-card-wrapper {
  position: relative;
  display: inline-block;
}

.user-card-popover {
  position: absolute;
  top: 100%;
  margin-top: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-lg);
  border-radius: var(--radius-md);
  padding: 16px;
  width: 280px;
  z-index: 1000;
}

.stat-item b {
  color: var(--text-1);
}
.stat-item {
  color: var(--text-2);
}

.pop-enter-active,
.pop-leave-active {
  transition: all 0.2s;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}
</style>
