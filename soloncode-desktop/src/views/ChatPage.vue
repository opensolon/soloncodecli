<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { type Conversation, type Plugin, type Message } from '../types';
import Sidebar from '../components/Sidebar.vue';
import ChatView from '../components/ChatView.vue';

const plugins = ref<Plugin[]>([
  {
    id: 'solonclaw',
    name: 'SolonClaw',
    icon: '🦊',
    description: '代码分析和项目管理工具',
    enabled: true,
    version: '1.0.0'
  },
  {
    id: 'copilot',
    name: 'Code Copilot',
    icon: '🤖',
    description: 'AI 代码助手',
    enabled: true,
    version: '2.1.0'
  },
  {
    id: 'formatter',
    name: 'Code Formatter',
    icon: '✨',
    description: '代码格式化工具',
    enabled: false,
    version: '1.5.0'
  },
  {
    id: 'linter',
    name: 'Code Linter',
    icon: '🔍',
    description: '代码质量检查',
    enabled: true,
    version: '3.0.0'
  }
]);

const currentConversation = ref<Conversation>({
  id: 1,
  title: '新建对话',
  timestamp: new Date().toLocaleString(),
  status: 'active'
});

const conversations = ref<Conversation[]>([
  {
    id: 0,
    title: 'SolonClaw',
    timestamp: new Date().toLocaleString(),
    status: 'active',
    isPermanent: true,
    icon: '🦊'
  },
  {
    id: 1,
    title: 'Vue 3 开发问题',
    timestamp: new Date(Date.now() - 3600000).toLocaleString(),
    status: 'completed'
  },
  {
    id: 2,
    title: 'TypeScript 类型定义',
    timestamp: new Date(Date.now() - 7200000).toLocaleString(),
    status: 'completed'
  },
  {
    id: 3,
    title: '项目架构设计讨论',
    timestamp: new Date(Date.now() - 10800000).toLocaleString(),
    status: 'completed'
  }
]);

function newConversation() {
  const newConv: Conversation = {
    id: Date.now(),
    title: '新建对话',
    timestamp: new Date().toLocaleString(),
    status: 'active'
  };
  conversations.value.unshift(newConv);
  currentConversation.value = newConv;
}

function selectConversation(conv: Conversation) {
  currentConversation.value = conv;
}

function togglePlugin(pluginId: string) {
  const plugin = plugins.value.find(p => p.id === pluginId);
  if (plugin) {
    plugin.enabled = !plugin.enabled;
  }
}

onMounted(() => {
  selectConversation(conversations.value[0]);
});
</script>

<template>
  <div class="app-container">
    <Sidebar
      :conversations="conversations"
      :current-conversation="currentConversation"
      :plugins="plugins"
      @new-conversation="newConversation"
      @select-conversation="selectConversation"
      @toggle-plugin="togglePlugin"
    />
    <ChatView
      :current-conversation="currentConversation"
      :plugins="plugins"
    />
  </div>
</template>

<style>
/* 全局样式保持不变，在 App.vue 中定义 */
</style>
