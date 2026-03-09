<script setup lang="ts">
import { type Conversation } from '../types';

interface Props {
  conversations: Conversation[];
  currentId: number;
}

interface Emits {
  (e: 'select', conv: Conversation): void;
}

defineProps<Props>();
defineEmits<Emits>();
</script>

<template>
  <div class="conversation-list">
    <div
      v-for="conv in conversations"
      :key="conv.id"
      class="conversation-item"
      :class="{ active: conv.id === currentId, permanent: conv.isPermanent }"
      @click="$emit('select', conv)"
    >
      <div class="conversation-title">
        <span v-if="conv.icon" class="conversation-icon">{{ conv.icon }}</span>
        {{ conv.title }}
      </div>
      <div class="conversation-time">{{ conv.timestamp }}</div>
    </div>
    <div v-if="conversations.length === 0" class="empty-conversations">
      <span>暂无会话</span>
    </div>
  </div>
</template>

<style scoped>
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  padding: 12px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
  margin-bottom: 4px;
}

.conversation-item:hover {
  background-color: var(--cb-vscode-list-hoverBackground);
}

.conversation-item.active {
  background-color: var(--cb-vscode-list-activeSelectionBackground);
}

.conversation-item.permanent {
  border-left: 3px solid var(--cb-accent);
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.conversation-time {
  font-size: 12px;
  color: var(--cb-text-secondary);
}

.empty-conversations {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  color: var(--cb-text-secondary);
  font-size: 13px;
  text-align: center;
}

.empty-conversations span {
  opacity: 0.7;
}
</style>
