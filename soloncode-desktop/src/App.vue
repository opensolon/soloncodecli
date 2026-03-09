<script setup lang="ts">
import { ref, nextTick, onMounted } from "vue";

interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

interface Conversation {
  id: number;
  title: string;
  timestamp: string;
  status: string;
  isPermanent?: boolean;
  icon?: string;
}

interface Plugin {
  id: string;
  name: string;
  icon: string;
  description: string;
  enabled: boolean;
  version: string;
}

// 主题管理
type Theme = 'dark' | 'light';
const currentTheme = ref<Theme>('dark');

function toggleTheme() {
  currentTheme.value = currentTheme.value === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', currentTheme.value);
  localStorage.setItem('soloncode-theme', currentTheme.value);
}

function loadTheme() {
  const savedTheme = localStorage.getItem('soloncode-theme') as Theme | null;
  if (savedTheme) {
    currentTheme.value = savedTheme;
  } else {
    // 检测系统主题偏好
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    currentTheme.value = prefersDark ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-theme', currentTheme.value);
}

onMounted(() => {
  loadTheme();
});

// 插件列表
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

const messages = ref<Message[]>([
  {
    id: 1,
    role: 'assistant',
    content: '你好！我是 SolonCode 助手。有什么我可以帮助你的吗？',
    timestamp: new Date().toLocaleTimeString()
  }
]);

const currentConversation = ref<Conversation>({
  id: 1,
  title: '新建对话',
  timestamp: new Date().toLocaleString(),
  status: 'active'
});

// 会话列表（包括 SolonClaw 常驻会话）
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

const userInput = ref('');
const isLoading = ref(false);
const chatContainer = ref<HTMLElement>();

// 插件管理面板显示状态
const showPluginPanel = ref(false);

async function sendMessage() {
  if (!userInput.value.trim() || isLoading.value) return;

  const userMessage: Message = {
    id: Date.now(),
    role: 'user',
    content: userInput.value,
    timestamp: new Date().toLocaleTimeString()
  };

  messages.value.push(userMessage);
  userInput.value = '';
  isLoading.value = true;

  await scrollToBottom();

  setTimeout(async () => {
    const assistantMessage: Message = {
      id: Date.now() + 1,
      role: 'assistant',
      content: generateResponse(userMessage.content),
      timestamp: new Date().toLocaleTimeString()
    };

    messages.value.push(assistantMessage);
    isLoading.value = false;
    await nextTick();
    await scrollToBottom();
  }, 1000);
}

function generateResponse(userInput: string): string {
  const responses = [
    '我理解你的问题。让我来帮助你解决这个任务。',
    '这是一个很好的问题！让我详细解释一下。',
    '根据你的描述，我建议采用以下方案...',
    '我明白了。这里有几个可行的解决方案供你参考。',
    '你的想法很有创意！我们可以进一步探讨这个方向。'
  ];
  return responses[Math.floor(Math.random() * responses.length)];
}

async function scrollToBottom() {
  await nextTick();
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
  }
}

function selectConversation(conv: Conversation) {
  currentConversation.value = conv;
  // 加载对应会话的消息
  if (conv.id === 0 && conv.isPermanent) {
    // SolonClaw 会话
    loadSolonClawMessages();
  } else {
    loadConversationMessages(conv.id);
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
  // 模拟加载会话消息
  messages.value = [
    {
      id: 1,
      role: 'assistant',
      content: '你好！我是 SolonCode 助手。有什么我可以帮助你的吗？',
      timestamp: new Date().toLocaleTimeString()
    }
  ];
}

function togglePlugin(pluginId: string) {
  const plugin = plugins.value.find(p => p.id === pluginId);
  if (plugin) {
    plugin.enabled = !plugin.enabled;
  }
}

function togglePluginPanel() {
  showPluginPanel.value = !showPluginPanel.value;
}

function newConversation() {
  const newConv: Conversation = {
    id: Date.now(),
    title: '新建对话',
    timestamp: new Date().toLocaleString(),
    status: 'active'
  };
  conversations.value.unshift(newConv);
  selectConversation(newConv);
  messages.value = [
    {
      id: Date.now(),
      role: 'assistant',
      content: '你好！我是 SolonCode 助手。有什么我可以帮助你的吗？',
      timestamp: new Date().toLocaleTimeString()
    }
  ];
}

function handleKeyPress(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }
}
</script>

<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <!-- 顶部操作区 -->
      <div class="sidebar-top">
        <button class="action-btn" @click="newConversation">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M8 1V15M1 8H15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span>新建任务</span>
        </button>
        <button class="plugin-btn" @click="togglePluginPanel" :class="{ active: showPluginPanel }">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M8 2L10 6H6L8 2Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 8L6 10L6 6L2 8Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M14 8L10 6L10 10L14 8Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M8 14L6 10H10L8 14Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>插件管理</span>
        </button>
      </div>

      <!-- 插件面板 -->
      <div v-if="showPluginPanel" class="plugin-panel">
        <div class="plugin-list">
          <div
            v-for="plugin in plugins"
            :key="plugin.id"
            class="plugin-item"
          >
            <div class="plugin-info">
              <span class="plugin-icon">{{ plugin.icon }}</span>
              <div class="plugin-details">
                <div class="plugin-name">{{ plugin.name }}</div>
                <div class="plugin-description">{{ plugin.description }}</div>
              </div>
            </div>
            <div class="plugin-actions">
              <span class="plugin-version">v{{ plugin.version }}</span>
              <button
                class="plugin-toggle"
                :class="{ enabled: plugin.enabled }"
                @click="togglePlugin(plugin.id)"
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                  <path d="M3 7H11M3 7V5M3 7V9M11 7H13M11 7V5M11 7V9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 会话列表 -->
      <div class="conversations-section">
        <div class="section-header">
          <span class="section-title">会话</span>
          <span class="section-count">{{ conversations.length }}</span>
        </div>
        <div class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: conv.id === currentConversation.id, permanent: conv.isPermanent }"
            @click="selectConversation(conv)"
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
      </div>
    </aside>

    <!-- 主聊天区域 -->
    <main class="main-content">
      <!-- 聊天头部 -->
      <header class="chat-header">
        <div class="chat-title">
          <h2>{{ currentConversation.title }}</h2>
          <span class="chat-status">{{ currentConversation.status === 'active' ? '进行中' : '已完成' }}</span>
        </div>
        <button class="theme-toggle-btn" @click="toggleTheme" :title="currentTheme === 'dark' ? '切换到亮色模式' : '切换到暗色模式'">
          <svg v-if="currentTheme === 'dark'" width="20" height="20" viewBox="0 0 20 20" fill="none">
            <circle cx="10" cy="10" r="4" stroke="currentColor" stroke-width="2"/>
            <path d="M10 2V4M10 16V18M18 10H16M4 10H2M15.66 15.66L14.24 14.24M5.76 5.76L4.34 4.34M15.66 4.34L14.24 5.76M5.76 14.24L4.34 15.66" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M10 18C13.866 18 17 14.866 17 11C17 7.13401 13.866 4 10 4C10 4 10 4 10 4C6.13401 4 3 7.13401 3 11C3 14.866 6.13401 18 10 18Z" stroke="currentColor" stroke-width="2"/>
            <path d="M10 4V2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M10 18V16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M15.66 6.34L14.24 4.92" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M4.34 15.66L5.76 14.24" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M18 11H16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M4 11H2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M15.66 15.66L14.24 14.24" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M4.34 6.34L5.76 4.92" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </header>

      <!-- 消息区域 -->
      <div class="chat-messages" ref="chatContainer">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="message.role"
        >
          <div class="message-content">
            <div class="message-avatar">
              {{ message.role === 'user' ? '👤' : '🤖' }}
            </div>
            <div class="message-body">
              <div class="message-text">{{ message.content }}</div>
              <div class="message-time">{{ message.timestamp }}</div>
            </div>
          </div>
        </div>

        <!-- 加载指示器 -->
        <div v-if="isLoading" class="message assistant loading">
          <div class="message-content">
            <div class="message-avatar">🤖</div>
            <div class="message-body">
              <div class="loading-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-container">
          <textarea
            v-model="userInput"
            class="message-input"
            placeholder="输入你的问题..."
            rows="1"
            @keydown="handleKeyPress"
            :disabled="isLoading"
          ></textarea>
          <button
            class="send-button"
            @click="sendMessage"
            :disabled="!userInput.trim() || isLoading"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path
                d="M2.5 10L17.5 3L10 17.5M2.5 10L10 17.5M2.5 10H10"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </div>
        <div class="input-footer">
          <span class="input-hint">Enter 发送，Shift + Enter 换行</span>
        </div>
      </div>
    </main>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  /* 暗色主题变量（默认） */
  --cb-vscode-editor-background: #1e1e1e;
  --cb-vscode-sideBar-background: #252526;
  --cb-vscode-input-background: #3c3c3c;
  --cb-vscode-foreground: #cccccc;
  --cb-vscode-button-background: #0e639c;
  --cb-vscode-button-foreground: #ffffff;
  --cb-vscode-button-hoverBackground: #1177bb;
  --cb-vscode-list-hoverBackground: #2a2d2e;
  --cb-vscode-list-activeSelectionBackground: #094771;
  --cb-vscode-widget-border: #414141;
  --cb-vscode-panel-border: #3c3c3c;
  --cb-text-primary: #d2d3e0;
  --cb-text-secondary: #858699;
  --cb-border-color: #4a5568;
  --cb-accent: #6b7cff;
  --cb-accent-light: #7b8dff;
  --cb-success: #4ade80;
}

/* 亮色主题变量 */
[data-theme="light"] {
  --cb-vscode-editor-background: #ffffff;
  --cb-vscode-sideBar-background: #f3f3f3;
  --cb-vscode-input-background: #f5f5f5;
  --cb-vscode-foreground: #333333;
  --cb-vscode-button-background: #007acc;
  --cb-vscode-button-foreground: #ffffff;
  --cb-vscode-button-hoverBackground: #0062a3;
  --cb-vscode-list-hoverBackground: #e8e8e8;
  --cb-vscode-list-activeSelectionBackground: #e1e1e1;
  --cb-vscode-widget-border: #d0d7de;
  --cb-vscode-panel-border: #e1e1e1;
  --cb-text-primary: #24292f;
  --cb-text-secondary: #656d76;
  --cb-border-color: #d0d7de;
  --cb-accent: #0969da;
  --cb-accent-light: #0b57d0;
  --cb-success: #1a7f37;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Roboto", "Oxygen", "Ubuntu", "Cantarell", "Fira Sans", "Droid Sans", "Helvetica Neue", sans-serif;
  font-size: 14px;
  line-height: 1.5;
  color: var(--cb-vscode-foreground);
  background-color: var(--cb-vscode-editor-background);
  overflow: hidden;
  transition: background-color 0.3s, color 0.3s;
}

#app {
  width: 100vw;
  height: 100vh;
}

.app-container {
  display: flex;
  width: 100%;
  height: 100%;
}

.sidebar {
  width: 280px;
  background-color: var(--cb-vscode-sideBar-background);
  border-right: 1px solid var(--cb-vscode-panel-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: background-color 0.3s, border-color 0.3s;
}

/* 顶部操作区 */
.sidebar-top {
  padding: 12px;
  border-bottom: 1px solid var(--cb-vscode-panel-border);
  display: flex;
  gap: 8px;
}

.action-btn,
.plugin-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  padding: 10px 12px;
  background-color: var(--cb-vscode-input-background);
  color: var(--cb-text-primary);
  border: 1px solid var(--cb-vscode-widget-border);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover,
.plugin-btn:hover {
  background-color: var(--cb-vscode-list-hoverBackground);
  border-color: var(--cb-accent);
}

.plugin-btn.active {
  background-color: var(--cb-accent);
  color: white;
  border-color: var(--cb-accent-light);
}

.action-btn svg,
.plugin-btn svg {
  flex-shrink: 0;
}

/* 插件面板 */
.plugin-panel {
  padding: 8px;
  border-bottom: 1px solid var(--cb-vscode-panel-border);
  background-color: var(--cb-vscode-input-background);
  max-height: 300px;
  overflow-y: auto;
}

.plugin-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.plugin-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background-color: var(--cb-vscode-sideBar-background);
  border-radius: 6px;
  transition: background-color 0.2s;
}

.plugin-item:hover {
  background-color: var(--cb-vscode-list-hoverBackground);
}

.plugin-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.plugin-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.plugin-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.plugin-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--cb-text-primary);
}

.plugin-description {
  font-size: 11px;
  color: var(--cb-text-secondary);
}

.plugin-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.plugin-version {
  font-size: 11px;
  color: var(--cb-text-secondary);
  font-family: monospace;
}

.plugin-toggle {
  width: 28px;
  height: 20px;
  background-color: var(--cb-vscode-panel-border);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 2px;
  transition: background-color 0.2s;
}

.plugin-toggle.enabled {
  background-color: var(--cb-success);
  justify-content: flex-end;
}

.plugin-toggle svg {
  color: white;
  transition: transform 0.2s;
}

/* 会话列表区域 */
.conversations-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 8px;
  border-bottom: 1px solid var(--cb-vscode-panel-border);
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cb-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-count {
  font-size: 12px;
  color: var(--cb-text-secondary);
  background-color: var(--cb-vscode-input-background);
  padding: 2px 8px;
  border-radius: 10px;
}

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

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--cb-vscode-panel-border);
  background-color: var(--cb-vscode-editor-background);
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color 0.3s, border-color 0.3s;
}

.chat-title h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--cb-text-primary);
}

.chat-status {
  font-size: 12px;
  color: var(--cb-text-secondary);
  background-color: var(--cb-vscode-input-background);
  padding: 2px 8px;
  border-radius: 4px;
}

.theme-toggle-btn {
  width: 36px;
  height: 36px;
  background-color: var(--cb-vscode-input-background);
  color: var(--cb-text-primary);
  border: 1px solid var(--cb-vscode-widget-border);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.theme-toggle-btn:hover {
  background-color: var(--cb-vscode-list-hoverBackground);
  border-color: var(--cb-accent);
}

.theme-toggle-btn svg {
  color: var(--cb-text-primary);
  transition: transform 0.3s;
}

.theme-toggle-btn:hover svg {
  transform: rotate(180deg);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  display: flex;
  animation: fadeIn 0.3s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  justify-content: flex-end;
}

.message-content {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.message.user .message-content {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--cb-vscode-input-background);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  transition: background-color 0.3s;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message.user .message-body {
  align-items: flex-end;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: var(--cb-vscode-input-background);
  color: var(--cb-text-primary);
  line-height: 1.6;
  word-wrap: break-word;
  transition: background-color 0.3s, color 0.3s;
}

.message.user .message-text {
  background-color: var(--cb-accent);
  color: white;
}

.message-time {
  font-size: 11px;
  color: var(--cb-text-secondary);
  padding: 0 4px;
}

.message.loading {
  opacity: 0.7;
}

.loading-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
}

.loading-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--cb-text-secondary);
  animation: pulse 1.4s ease-in-out infinite;
}

.loading-indicator span:nth-child(1) {
  animation-delay: 0s;
}

.loading-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes pulse {
  0%, 80%, 100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}

.input-area {
  padding: 16px 24px;
  border-top: 1px solid var(--cb-vscode-panel-border);
  background-color: var(--cb-vscode-editor-background);
  transition: background-color 0.3s, border-color 0.3s;
}

.input-container {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background-color: var(--cb-vscode-input-background);
  border: 1px solid var(--cb-vscode-widget-border);
  border-radius: 8px;
  padding: 8px;
  transition: border-color 0.2s, background-color 0.3s;
}

.input-container:focus-within {
  border-color: var(--cb-accent);
}

.message-input {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--cb-text-primary);
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  min-height: 24px;
  max-height: 200px;
  overflow-y: auto;
  font-family: inherit;
  transition: color 0.3s;
}

.message-input:focus {
  outline: none;
}

.message-input::placeholder {
  color: var(--cb-text-secondary);
}

.send-button {
  width: 32px;
  height: 32px;
  background-color: var(--cb-accent);
  color: white;
  border: none;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.send-button:hover:not(:disabled) {
  background-color: var(--cb-accent-light);
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-footer {
  margin-top: 8px;
  text-align: center;
}

.input-hint {
  font-size: 11px;
  color: var(--cb-text-secondary);
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--cb-vscode-widget-border);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--cb-border-color);
}
</style>