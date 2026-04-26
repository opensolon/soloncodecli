# SolonCode Desktop

AI 驱动的桌面编程助手，基于 Tauri 2.0 + React 18 构建。

## 技术栈

| 层 | 技术 |
|---|---|
| 桌面框架 | Tauri 2.0 (Rust) |
| 前端 | React 18 + TypeScript + Vite 6 |
| 代码编辑器 | Monaco Editor |
| 后端通信 | WebSocket (连接 soloncode-cli) |
| 本地存储 | IndexedDB (Dexie) + localStorage |
| 消息渲染 | markdown-it + react-syntax-highlighter |

## 功能

- **AI 对话** — 通过 WebSocket 与后端 AI 服务实时交互，支持 Markdown 渲染和代码高亮
- **代码编辑器** — Monaco Editor 集成，支持多文件 Tab、语法高亮、自动补全、Ctrl+S 保存
- **资源管理器** — 文件树浏览、新建文件/文件夹、右键菜单（重命名、删除、复制、剪切、粘贴）
- **Git 集成** — 分支切换、暂存/取消暂存、提交、推送/拉取、提交历史查看、丢弃更改
- **工作区管理** — 打开文件夹、自动创建 `.soloncode/settings.json` 配置
- **主题切换** — 深色/浅色主题，编辑器和 UI 同步切换
- **会话管理** — 多会话切换，消息持久化到 IndexedDB

## 开发

### 环境要求

- Node.js >= 18
- Rust (通过 rustup 安装)
- Tauri CLI 2.0

### 安装依赖

```bash
cd soloncode-desktop
npm install
```

### 开发模式

```bash
npm run tauri:dev
```

前端热更新端口 `5173`，WebSocket 后端默认连接 `ws://localhost:18080`。

### 构建发布

```bash
npm run tauri:build
```

### 仅前端开发（浏览器模式）

```bash
npm run dev
```

非 Tauri 环境下会自动使用 Mock 数据，无需后端即可预览 UI。

## 环境变量

`.env.development` 配置后端连接：

```env
VITE_WS_HOST=localhost:8080
VITE_WS_PROTOCOL=ws
```

## 项目结构

```
soloncode-desktop/
├── src/                        # React 前端
│   ├── App.tsx                 # 主应用入口
│   ├── components/
│   │   ├── ChatView.tsx        # AI 对话面板
│   │   ├── ChatInput.tsx       # 消息输入框
│   │   ├── ChatMessages.tsx    # 消息列表渲染
│   │   ├── common/             # 通用组件（Icon、ContextMenu、ConfirmDialog）
│   │   ├── editor/             # Monaco 编辑器面板
│   │   ├── layout/             # 布局（ActivityBar、TitleBar、SidePanel、StatusBar）
│   │   └── sidebar/            # 侧边栏（Explorer、Search、Git、Extensions、Sessions、Settings）
│   ├── services/
│   │   ├── fileService.ts      # 文件操作（Tauri invoke 封装）
│   │   ├── gitService.ts       # Git 操作
│   │   ├── settingsService.ts  # 设置持久化
│   │   └── chatService.ts      # WebSocket 通信
│   ├── hooks/                  # 自定义 Hooks
│   └── types.ts                # 类型定义
├── src-tauri/
│   ├── src/lib.rs              # Rust 后端（文件系统操作、Git 命令）
│   └── Cargo.toml
└── package.json
```

## 后端

桌面端通过 WebSocket 连接 [soloncode-cli](../soloncode-cli)（Java/Solon 框架）。后端负责 AI 模型调用、Agent 执行等核心逻辑。


## Skill 扫描注入流程

### 目录约定
- **全局 Skills**: `~/.soloncode/skills/` — 每个 skill 是一个子目录，必须包含 `SKILL.md`
- **工作区 Skills**: `{workspacePath}/.soloncode/skills/` — 同结构

### SKILL.md 格式
```yaml
---
name: my-skill
description: 技能描述
---

技能的具体内容（Markdown 正文）
```

### 扫描时机
1. **打开工作区时**（App.tsx `openFolderByPath`）— 调用 `settingsService.scanSkillsDir(workspacePath)` 扫描工作区 `.soloncode/skills/`，去重后合并到 `settings.skills`
2. **恢复上次工作区时**（App.tsx `loadLastFolder`）— 同上逻辑
3. **SkillsPanel 挂载时** — 调用 Tauri 命令 `list_skills` 扫描全局 `~/.soloncode/skills/`，覆盖到 `settings.skills`
4. **手动刷新** — 点击 SkillsPanel 的刷新按钮，重新执行 `list_skills`

### 数据流
```
Tauri 后端 (list_skills)
  → 读取 ~/.soloncode/skills/ 子目录
  → 解析每个子目录的 SKILL.md frontmatter (name, description)
  → 检查 .disabled 标记文件判断 enabled
  → 返回 SkillInfo[]

前端 settingsService.scanSkillsDir(workspacePath)
  → 读取 {workspacePath}/.soloncode/skills/ 子目录
  → 检查每个子目录是否含 SKILL.md
  → 返回 SkillConfig[] (source: 'discovered')
```

### 持久化
- 前端: IndexedDB `skills` 表（v4 migration），字段: id, name, description, path, enabled, source, sortOrder
- 后端: 文件系统 `.disabled` 标记文件控制启用/禁用

### Tauri 命令
| 命令 | 参数 | 说明 |
|------|------|------|
| `list_skills` | 无 | 扫描 `~/.soloncode/skills/` 返回 SkillInfo[] |
| `toggle_skill` | skillPath: string, enabled: bool | 创建/删除 `.disabled` 标记文件 |

---

## Agent 注入流程

Agent 与 Skill 采用完全相同的扫描注入模式。

### 目录约定
- **全局 Agents**: `~/.soloncode/agents/` — 每个 agent 是一个子目录，必须包含 `AGENT.md`
- **工作区 Agents**: `{workspacePath}/.soloncode/agents/` — 同结构

### AGENT.md 格式
```yaml
---
name: my-agent
description: Agent 描述
---

Agent 的具体配置内容（Markdown 正文）
```

### 扫描时机
1. **打开工作区时**（App.tsx `openFolderByPath`）— 调用 `settingsService.scanAgentsDir(workspacePath)` 扫描工作区 `.soloncode/agents/`，去重后合并到 `settings.agents`
2. **恢复上次工作区时**（App.tsx `loadLastFolder`）— 同上逻辑
3. **AgentsPanel 挂载时** — 调用 Tauri 命令 `list_agents` 扫描全局 `~/.soloncode/agents/`，覆盖到 `settings.agents`
4. **手动刷新** — 点击 AgentsPanel 的刷新按钮，重新执行 `list_agents`

### 数据流
```
Tauri 后端 (list_agents)
  → 读取 ~/.soloncode/agents/ 子目录
  → 解析每个子目录的 AGENT.md frontmatter (name, description)
  → 检查 .disabled 标记文件判断 enabled
  → 返回 AgentInfo[]

前端 settingsService.scanAgentsDir(workspacePath)
  → 读取 {workspacePath}/.soloncode/agents/ 子目录
  → 检查每个子目录是否含 AGENT.md
  → 返回 AgentConfig[] (source: 'discovered')
```

### 持久化
- 前端: IndexedDB `agents` 表（v5 migration），字段: id, name, description, path, enabled, source, sortOrder
- 后端: 文件系统 `.disabled` 标记文件控制启用/禁用

### Tauri 命令
| 命令 | 参数 | 说明 |
|------|------|------|
| `list_agents` | 无 | 扫描 `~/.soloncode/agents/` 返回 AgentInfo[] |
| `toggle_agent` | agentPath: string, enabled: bool | 创建/删除 `.disabled` 标记文件 |

### 选择逻辑
- 用户在 AgentsPanel 点击 agent name → `App.tsx` 保存 `activeAgent` 状态
- 当前仅前端选择，尚未传递给后端

### 配置文件
工作区 `.soloncode/config.yml` 中 `agent.maxSteps` 字段通过 `settingsService.loadConfigFile()` 加载，通过文件监听自动热更新。
