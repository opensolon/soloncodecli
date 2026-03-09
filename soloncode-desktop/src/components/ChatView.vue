<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import axios from 'axios';
import { type Message, type Conversation, type Theme, type Plugin } from '../types';
import ChatHeader from './ChatHeader.vue';
import ChatMessages from './ChatMessages.vue';
import ChatInput from './ChatInput.vue';

interface Props {
  currentConversation: Conversation;
  plugins: Plugin[];
}

interface Emits {
  (e: 'toggle-theme'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const currentTheme = ref<Theme>('dark');
const messages = ref<Message[]>([]);
const isLoading = ref(false);
const chatMessagesRef = ref<InstanceType<typeof ChatMessages>>();
const API_BASE_URL = '/cli';

function toggleTheme() {
  currentTheme.value = currentTheme.value === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', currentTheme.value);
  localStorage.setItem('soloncode-theme', currentTheme.value);
  emit('toggle-theme');
}

function loadTheme() {
  const savedTheme = localStorage.getItem('soloncode-theme') as Theme | null;
  if (savedTheme) {
    currentTheme.value = savedTheme;
  } else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    currentTheme.value = prefersDark ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-theme', currentTheme.value);
}

async function sendMessage(messageText: string) {
  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content: messageText,
    timestamp: new Date().toLocaleTimeString()
  };

  messages.value.push(userMessage);
  isLoading.value = true;

  await chatMessagesRef.value?.scrollToBottom();

  try {
    const sessionId = props.currentConversation.id.toString();
    const url = `${API_BASE_URL}?input=${encodeURIComponent(messageText)}&m=stream`;

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'X-Session-Id': sessionId
      }
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const reader = response.body?.getReader();
    const decoder = new TextDecoder();
    let assistantMessage: Message | null = null;

    if (reader) {
      const assistantMsgId = Date.now() + 1;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (line.trim() === '[DONE]') {
            isLoading.value = false;
            await chatMessagesRef.value?.scrollToBottom();
            return;
          }

          if (line.trim()) {
            try {
              let jsonStr = line.trim();
              if (jsonStr.startsWith('data:')) {
                jsonStr = jsonStr.substring(5).trim();
              }

              if (jsonStr === '[DONE]') {
                isLoading.value = false;
                await chatMessagesRef.value?.scrollToBottom();
                return;
              }

              const data = JSON.parse(jsonStr);
              const type = data.type;
              const text = data.text || '';

              if (type === 'reason') {
                assistantMessage = {
                  id: assistantMsgId,
                  role: 'reason',
                  content: text,
                  timestamp: new Date().toLocaleTimeString()
                };
              } else if (type === 'action') {
                assistantMessage = {
                  id: assistantMsgId,
                  role: 'action',
                  content: text,
                  timestamp: new Date().toLocaleTimeString(),
                  toolName: data.toolName,
                  args: data.args
                };
              } else if (type === 'agent') {
                assistantMessage = {
                  id: assistantMsgId,
                  role: 'assistant',
                  content: text,
                  timestamp: new Date().toLocaleTimeString()
                };
              } else if (type === 'error') {
                assistantMessage = {
                  id: assistantMsgId,
                  role: 'error',
                  content: text,
                  timestamp: new Date().toLocaleTimeString()
                };
              }

              if (assistantMessage && text) {
                const existingIndex = messages.value.findIndex(m => m.id === assistantMsgId);
                if (existingIndex !== -1) {
                  messages.value[existingIndex] = assistantMessage;
                } else {
                  messages.value.push(assistantMessage);
                }
                await chatMessagesRef.value?.scrollToBottom();
              }
            } catch (e) {
              console.warn('Failed to parse chunk:', line, e);
            }
          }
        }
      }
    }
  } catch (error) {
    console.error('Failed to send message:', error);
    const errorMessage: Message = {
      id: Date.now() + 1,
      role: 'error',
      content: `请求失败: ${error instanceof Error ? error.message : '未知错误'}`,
      timestamp: new Date().toLocaleTimeString()
    };
    messages.value.push(errorMessage);
  } finally {
    isLoading.value = false;
    await chatMessagesRef.value?.scrollToBottom();
  }
}

function loadSolonClawMessages() {
  messages.value = [
    {
      id: 1,
      role: 'assistant',
      content: '🦊 SolonClaw 已启动\n\n这是一个强大的代码分析和管理工具。我可以帮助你：\n\n• 分析项目结构和依赖关系\n• 检测代码质量问题\n• 生成代码文档\n• 执行代码重构建议\n• 管理项目配置\n\n请告诉我你需要什么帮助？',
      timestamp: new Date().toLocaleTimeString()
    }
  ];
}

function loadConversationMessages(convId: number) {
  messages.value = [
    {
      id: 1,
      role: 'assistant',
      content: '你好！我是 SolonCode 助手。有什么我可以帮助你的吗？',
      timestamp: new Date().toLocaleTimeString()
    }
  ];
}

watch(() => props.currentConversation.id, (newId) => {
  if (newId === 0 && props.currentConversation.isPermanent) {
    loadSolonClawMessages();
  } else {
    loadConversationMessages(newId);
  }
}, { immediate: true });

onMounted(() => {
  loadTheme();
});
</script>

<template>
  <main class="main-content">
    <ChatHeader
      :title="props.currentConversation.title"
      :status="props.currentConversation.status"
      :theme="currentTheme"
      @toggle-theme="toggleTheme"
    />
    <ChatMessages
      ref="chatMessagesRef"
      :messages="messages"
      :is-loading="isLoading"
    />
    <ChatInput @send="sendMessage" />
  </main>
</template>

<style scoped>
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
