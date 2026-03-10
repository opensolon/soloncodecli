<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import axios from 'axios';
import { type Message, type Conversation, type Theme, type Plugin, type ContentItem, type ContentType } from '../types';
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
    timestamp: new Date().toLocaleTimeString(),
    contents: [{ type: 'text', text: messageText }]
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
    let currentAssistantMessage: Message | null = null;
    let currentAssistantMsgId: number | null = null;

    if (reader) {
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
              const type = data.type as ContentType;
              const text = data.text || '';

              if (text || type === 'action') {
                // 确保有当前的助手消息
                if (!currentAssistantMessage) {
                  currentAssistantMsgId = Date.now() + Math.floor(Math.random() * 1000);
                  currentAssistantMessage = {
                    id: currentAssistantMsgId,
                    role: 'assistant',
                    timestamp: new Date().toLocaleTimeString(),
                    contents: []
                  };
                  messages.value.push(currentAssistantMessage);
                }

                // 按类型处理并追加内容
                if (type === 'reason') {
                  // 查找最后一个 reason 内容项，追加或创建新的
                  const lastContent = currentAssistantMessage.contents[currentAssistantMessage.contents.length - 1];
                  if (lastContent && lastContent.type === 'reason') {
                    lastContent.text += text;
                  } else {
                    currentAssistantMessage.contents.push({ type: 'reason', text });
                  }
                } else if (type === 'action') {
                  // action 通常每次都是新的
                  const actionItem: ContentItem = {
                    type: 'action',
                    text: text,
                    toolName: data.toolName,
                    args: data.args
                  };
                  currentAssistantMessage.contents.push(actionItem);
                } else if (type === 'agent') {
                  // agent 是普通文本，查找最后一个 text 内容项追加
                  const lastContent = currentAssistantMessage.contents[currentAssistantMessage.contents.length - 1];
                  if (lastContent && lastContent.type === 'text') {
                    lastContent.text += text;
                  } else {
                    currentAssistantMessage.contents.push({ type: 'text', text });
                  }
                } else if (type === 'error') {
                  currentAssistantMessage.contents.push({ type: 'error', text });
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
      timestamp: new Date().toLocaleTimeString(),
      contents: [{ type: 'error', text: `请求失败: ${error instanceof Error ? error.message : '未知错误'}` }]
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
      timestamp: new Date().toLocaleTimeString(),
      contents: [{
        type: 'text',
        text: '🦊 SolonClaw 已启动\n\n这是一个强大的代码分析和管理工具。我可以帮助你：\n\n• 分析项目结构和依赖关系\n• 检测代码质量问题\n• 生成代码文档\n• 执行代码重构建议\n• 管理项目配置\n\n请告诉我你需要什么帮助？'
      }]
    }
  ];
}

function loadConversationMessages(convId: number) {
  messages.value = [
    {
      id: 1,
      role: 'assistant',
      timestamp: new Date().toLocaleTimeString(),
      contents: [{
        type: 'text',
        text: '你好！我是 SolonCode 助手。有什么我可以帮助你的吗？'
      }]
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
