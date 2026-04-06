import { useState, useCallback, useRef, useEffect } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { ActivityBar, type ActivityType } from './components/layout/ActivityBar';
import { TitleBar } from './components/layout/TitleBar';
import { SidePanel } from './components/layout/SidePanel';
import { StatusBar } from './components/layout/StatusBar';
import { ExplorerPanel } from './components/sidebar/ExplorerPanel';
import { GitPanel } from './components/sidebar/GitPanel';
import { ExtensionsPanel } from './components/sidebar/ExtensionsPanel';
import { SettingsPanel, type Settings } from './components/sidebar/SettingsPanel';
import { EditorPanel } from './components/editor/EditorPanel';
import { ChatView } from './components/ChatView';
import { TerminalPanel } from './components/terminal/TerminalPanel';
import { fileService, type FileInfo } from './services/fileService';
import { gitService, type GitStatus, type DiffLine } from './services/gitService';
import { settingsService } from './services/settingsService';
import { backendService } from './services/backendService';
import { setBackendPort as setChatBackendPort, setWorkspacePath as setChatWorkspacePath } from './components/ChatView';
import { useFileWatcher } from './hooks/useFileWatcher';
import type { Plugin } from './types';
import { saveLastFolder, loadLastFolder } from './db';
import './App.css';

interface FileTreeNode {
  name: string;
  type: 'folder' | 'file';
  path: string;
  children?: FileTreeNode[];
}

function convertToFileTree(files: FileInfo[]): FileTreeNode[] {
  return files.map(f => ({
    name: f.name,
    type: f.isDir ? 'folder' as const : 'file' as const,
    path: f.path,
    children: f.children ? convertToFileTree(f.children) : undefined,
  }));
}

const emptyGitStatus: GitStatus = {
  branch: '',
  ahead: 0,
  behind: 0,
  files: [],
};

const mockExtensions = [
  { id: '1', name: 'Markdown 渲染器', description: '增强 Markdown 渲染', version: '1.0.0', installed: true, enabled: true, author: 'SolonCode' },
  { id: '2', name: '代码格式化', description: '自动格式化代码', version: '2.1.0', installed: true, enabled: true, author: 'SolonCode' },
];

const plugins: Plugin[] = [
  { id: 'none', name: '插件暂不支持', icon: 'cube', description: '插件暂不支持', enabled: true, version: '1.0.0' }
];

const defaultSettings: Settings = settingsService.load();

type PanelPosition = 'editor' | 'chat';

interface PanelState {
  editorVisible: boolean;
  chatVisible: boolean;
  editorWidth: number;
  chatWidth: number;
  terminalHeight: number;
  panelOrder: PanelPosition[];
}

function App() {
  const [activeActivity, setActiveActivity] = useState<ActivityType>('explorer');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  const [settingsVisible, setSettingsVisible] = useState(false);

  const handleSettingsChange = useCallback((newSettings: Settings) => {
    setSettings(newSettings);
    settingsService.save(newSettings as any);
  }, []);

  const [workspacePath, setWorkspacePath] = useState<string | null>(null);
  const [workspaceName, setWorkspaceName] = useState<string>('');
  const [workspaceFiles, setWorkspaceFiles] = useState<FileTreeNode[]>([]);

  const [gitStatus, setGitStatus] = useState<GitStatus>(emptyGitStatus);
  const [diffLines, setDiffLines] = useState<DiffLine[]>([]);
  const [backendPort, setBackendPortState] = useState<number | null>(null);

  useEffect(() => {
    setChatBackendPort(backendPort);
  }, [backendPort]);

  const refreshGitStatus = useCallback(async () => {
    if (workspacePath) {
      const status = await gitService.status(workspacePath);
      setGitStatus(status);
    } else {
      setGitStatus(emptyGitStatus);
    }
  }, [workspacePath]);

  useEffect(() => {
    refreshGitStatus();
    const timer = setInterval(refreshGitStatus, 5000);
    return () => clearInterval(timer);
  }, [refreshGitStatus]);

  const [panelState, setPanelState] = useState<PanelState>({
    editorVisible: true,
    chatVisible: true,
    editorWidth: 0,
    chatWidth: 0,
    terminalHeight: 200,
    panelOrder: ['editor', 'chat'],
  });

  const [sidebarWidth, setSidebarWidth] = useState(260);

  useEffect(() => {
    const updatePanelWidths = () => {
      if (!containerRef.current) return;
      const containerWidth = containerRef.current.clientWidth;
      const activityBarWidth = 48;
      const sw = sidebarCollapsed ? 0 : Math.floor(containerWidth * 0.20);
      setSidebarWidth(sw);
      const remainingWidth = containerWidth - activityBarWidth - (sidebarCollapsed ? 0 : sw);
      const editorWidth = Math.floor(remainingWidth * 0.45 / 0.75);
      const chatWidth = remainingWidth - editorWidth;
      setPanelState(prev => ({
        ...prev,
        editorWidth: Math.max(300, editorWidth),
        chatWidth: Math.max(200, chatWidth),
      }));
    };
    updatePanelWidths();
    window.addEventListener('resize', updatePanelWidths);
    return () => window.removeEventListener('resize', updatePanelWidths);
  }, [sidebarCollapsed]);

  const [openFiles, setOpenFiles] = useState<Array<{
    path: string;
    name: string;
    content: string;
    modified: boolean;
    language: string;
  }>>([]);
  const [activeFilePath, setActiveFilePath] = useState<string | null>(null);

  const [chatTabs, setChatTabs] = useState<Array<{ id: string; title: string }>>([
    { id: 'default', title: '聊天' }
  ]);
  const [activeChatTabId, setActiveChatTabId] = useState('default');

  useEffect(() => {
    loadLastFolder().then(async (lastFolder) => {
      if (!lastFolder) return;
      try {
        console.log('[App] 恢复上次工作区:', lastFolder);
        await fileService.initWorkspaceConfig(lastFolder);
        const info = await fileService.getWorkspaceInfo(lastFolder);
        setWorkspacePath(lastFolder);
        setChatWorkspacePath(lastFolder);
        setWorkspaceName(info.name);

        backendService.start(lastFolder).then((port) => {
          if (port) {
            setBackendPortState(port);
            setChatBackendPort(port);
          } else {
            setBackendPortState(null);
          }
        }).catch(() => setBackendPortState(null));

        const files = await fileService.listDirectoryTree(lastFolder, 10);
        setWorkspaceFiles(convertToFileTree(files));
        setActiveActivity('explorer');
      } catch (err) {
        console.warn('[App] 恢复工作区失败:', err);
      }
    });
  }, []);

  const activeFile = openFiles.find(f => f.path === activeFilePath);

  const [isResizing, setIsResizing] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useFileWatcher({
    workspacePath,
    onChange: async (_changedPaths) => {
      if (workspacePath) {
        const files = await fileService.listDirectoryTree(workspacePath, 10);
        setWorkspaceFiles(convertToFileTree(files));
      }
    },
    enabled: !!workspacePath,
  });

  useEffect(() => {
    if (!workspacePath || !activeFilePath) {
      setDiffLines([]);
      return;
    }
    const relPath = activeFilePath.replace(workspacePath.replace(/\\/g, '/').replace(/\/$/, '') + '/', '');
    gitService.diffFile(workspacePath, relPath).then(setDiffLines).catch(() => setDiffLines([]));
  }, [workspacePath, activeFilePath, gitStatus]);

  const togglePanel = useCallback((panel: 'editor' | 'chat') => {
    setPanelState(prev => ({
      ...prev,
      [`${panel}Visible`]: !prev[`${panel}Visible`],
    }));
  }, []);

  const swapPanels = useCallback(() => {
    setPanelState(prev => ({
      ...prev,
      panelOrder: [...prev.panelOrder].reverse(),
    }));
  }, []);

  const startResize = useCallback((panel: string, e: React.MouseEvent) => {
    e.preventDefault();
    setIsResizing(panel);
  }, []);

  useEffect(() => {
    if (!isResizing) return;
    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const containerRect = containerRef.current.getBoundingClientRect();
      const sw = sidebarCollapsed ? 48 : 48 + sidebarWidth;
      const relativeX = e.clientX - containerRect.left - sw;
      if (isResizing === 'editor') {
        const newEditorWidth = Math.max(300, relativeX);
        setPanelState(prev => ({ ...prev, editorWidth: newEditorWidth }));
      } else if (isResizing === 'chat') {
        const totalWidth = containerRect.width - sw;
        const newChatWidth = Math.max(200, totalWidth - relativeX);
        setPanelState(prev => ({ ...prev, chatWidth: newChatWidth }));
      } else if (isResizing === 'terminal') {
        const newTerminalHeight = Math.max(100, containerRect.height - (e.clientY - containerRect.top));
        setPanelState(prev => ({ ...prev, terminalHeight: newTerminalHeight }));
      }
    };
    const handleMouseUp = () => {
      setIsResizing(null);
    };
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing, sidebarCollapsed, sidebarWidth, panelState.terminalHeight]);

  const handleFileSelect = useCallback(async (path: string) => {
    const existingFile = openFiles.find(f => f.path === path);
    if (existingFile) {
      setActiveFilePath(path);
      return;
    }
    const fileName = path.split(/[/\\]/).pop() || '';
    const ext = fileName.split('.').pop() || '';
    const langMap: Record<string, string> = {
      'ts': 'TypeScript', 'tsx': 'TypeScript React', 'js': 'JavaScript', 'jsx': 'JavaScript React',
      'json': 'JSON', 'css': 'CSS', 'html': 'HTML', 'md': 'Markdown', 'java': 'Java',
      'rs': 'Rust', 'py': 'Python', 'go': 'Go',
    };
    try {
      const file = await fileService.openFile(path);
      setOpenFiles(prev => [...prev, file]);
      setActiveFilePath(path);
    } catch (err) {
      console.error('[App] 读取文件失败:', err);
      setOpenFiles(prev => [...prev, {
        path, name: fileName, content: `// 无法读取文件: ${fileName}`,
        modified: false, language: langMap[ext] || 'Plain Text',
      }]);
      setActiveFilePath(path);
    }
  }, [openFiles]);

  const handleFileDoubleClick = useCallback((path: string, type: 'file' | 'folder') => {
    const contextRef = type === 'folder' ? `@folder ${path}` : `@file ${path}`;
    window.dispatchEvent(new CustomEvent('set-chat-context', { detail: contextRef }));
  }, []);

  const handleFileClose = useCallback((path: string) => {
    setOpenFiles(prev => {
      const newFiles = prev.filter(f => f.path !== path);
      if (activeFilePath === path && newFiles.length > 0) {
        setActiveFilePath(newFiles[newFiles.length - 1].path);
      } else if (newFiles.length === 0) {
        setActiveFilePath(null);
      }
      return newFiles;
    });
  }, [activeFilePath]);

  const handleContentChange = useCallback((path: string, content: string) => {
    setOpenFiles(prev => prev.map(f => f.path === path ? { ...f, content, modified: true } : f));
  }, []);

  const handleFileSave = useCallback(async (path: string) => {
    const file = openFiles.find(f => f.path === path);
    if (file && workspacePath) {
      try {
        await fileService.writeFile(path, file.content);
        setOpenFiles(prev => prev.map(f => f.path === path ? { ...f, modified: false } : f));
      } catch (err) {
        console.error('保存文件失败:', err);
      }
    } else {
      setOpenFiles(prev => prev.map(f => f.path === path ? { ...f, modified: false } : f));
    }
  }, [openFiles, workspacePath]);

  const handleOpenFile = useCallback(async () => {
    try {
      const selectedPath = await fileService.openFileDialog({
        multiple: false,
        filters: [
          { name: '所有文件', extensions: ['*'] },
          { name: 'TypeScript', extensions: ['ts', 'tsx'] },
          { name: 'JavaScript', extensions: ['js', 'jsx'] },
          { name: '文本文件', extensions: ['txt', 'md', 'json'] },
        ],
      });
      if (selectedPath && typeof selectedPath === 'string') {
        const file = await fileService.openFile(selectedPath);
        setOpenFiles(prev => {
          if (prev.some(f => f.path === selectedPath)) return prev;
          return [...prev, file];
        });
        setActiveFilePath(selectedPath);
      }
    } catch (err) {
      console.error('打开文件失败:', err);
    }
  }, []);

  const openFolderByPath = useCallback(async (selectedPath: string) => {
    try {
      if (workspacePath) {
        try { await backendService.stop(); } catch (_) {}
        setChatBackendPort(null);
        setChatWorkspacePath(null);
        setBackendPortState(null);
        setOpenFiles([]);
        setActiveFilePath(null);
        setGitStatus(emptyGitStatus);
      }
      await fileService.initWorkspaceConfig(selectedPath);
      const info = await fileService.getWorkspaceInfo(selectedPath);
      setWorkspacePath(selectedPath);
      setChatWorkspacePath(selectedPath);
      setWorkspaceName(info.name);
      saveLastFolder(selectedPath);
      backendService.start(selectedPath).then((port) => {
        if (port) {
          setBackendPortState(port);
          setChatBackendPort(port);
        } else {
          setBackendPortState(null);
        }
      }).catch(() => setBackendPortState(null));
      const files = await fileService.listDirectoryTree(selectedPath, 10);
      setWorkspaceFiles(convertToFileTree(files));
      setActiveActivity('explorer');
      return true;
    } catch (err) {
      console.error('[App] 打开文件夹失败:', err);
      return false;
    }
  }, [workspacePath]);

  const handleOpenFolder = useCallback(async () => {
    const selectedPath = await fileService.openFolderDialog();
    if (selectedPath) {
      await openFolderByPath(selectedPath);
    }
  }, [openFolderByPath]);

  const refreshFileTree = useCallback(async () => {
    if (workspacePath) {
      const files = await fileService.listDirectoryTree(workspacePath, 10);
      setWorkspaceFiles(convertToFileTree(files));
    }
  }, [workspacePath]);

  const handleNewFile = useCallback(async () => {
    if (!workspacePath) return;
    const name = 'untitled';
    let path = `${workspacePath}/${name}`;
    let counter = 1;
    while (await fileService.pathExists(path)) {
      path = `${workspacePath}/${name}-${counter}`;
      counter++;
    }
    await fileService.createFile(path);
    await refreshFileTree();
    handleFileSelect(path);
  }, [workspacePath, refreshFileTree, handleFileSelect]);

  const handleNewFolder = useCallback(async () => {
    if (!workspacePath) return;
    const name = 'new-folder';
    let path = `${workspacePath}/${name}`;
    let counter = 1;
    while (await fileService.pathExists(path)) {
      path = `${workspacePath}/${name}-${counter}`;
      counter++;
    }
    await fileService.createDirectory(path);
    await refreshFileTree();
  }, [workspacePath, refreshFileTree]);

  const handleRename = useCallback(async (oldPath: string, newPath: string) => {
    await fileService.renameItem(oldPath, newPath);
    setOpenFiles(prev => prev.map(f =>
      f.path === oldPath ? { ...f, path: newPath, name: newPath.split(/[/\\]/).pop() || f.name } : f
    ));
    if (activeFilePath === oldPath) {
      setActiveFilePath(newPath);
    }
    await refreshFileTree();
  }, [refreshFileTree, activeFilePath]);

  const handleDelete = useCallback(async (path: string, type: 'file' | 'folder') => {
    if (type === 'folder') {
      await fileService.deleteDirectory(path);
    } else {
      await fileService.deleteFile(path);
    }
    setOpenFiles(prev => {
      const remaining = prev.filter(f => !f.path.startsWith(path));
      if (activeFilePath?.startsWith(path) && remaining.length > 0) {
        setActiveFilePath(remaining[remaining.length - 1].path);
      } else if (remaining.length === 0) {
        setActiveFilePath(null);
      }
      return remaining;
    });
    await refreshFileTree();
  }, [refreshFileTree, activeFilePath]);

  const handleCopy = useCallback(async (sourcePath: string, destPath: string) => {
    await fileService.copyItem(sourcePath, destPath);
    await refreshFileTree();
  }, [refreshFileTree]);

  const handleMove = useCallback(async (sourcePath: string, destPath: string) => {
    await fileService.moveItem(sourcePath, destPath);
    setOpenFiles(prev => prev.map(f =>
      f.path === sourcePath ? { ...f, path: destPath, name: destPath.split(/[/\\]/).pop() || f.name } : f
    ));
    if (activeFilePath === sourcePath) {
      setActiveFilePath(destPath);
    }
    await refreshFileTree();
  }, [refreshFileTree, activeFilePath]);

  const handleSaveCurrentFile = useCallback(() => {
    if (activeFilePath) {
      handleFileSave(activeFilePath);
    }
  }, [activeFilePath, handleFileSave]);

  const [terminalVisible, setTerminalVisible] = useState(false);
  const [terminalTabs, setTerminalTabs] = useState<Array<{ id: string; title: string }>>([
    { id: 'default', title: '终端' }
  ]);
  const [activeTerminalId, setActiveTerminalId] = useState('default');

  const renderSidebarContent = () => {
    if (sidebarCollapsed) return null;
    switch (activeActivity) {
      case 'explorer':
        return (
          <ExplorerPanel
            files={workspaceFiles}
            workspaceName={workspaceName}
            hasWorkspace={!!workspacePath}
            workspacePath={workspacePath || undefined}
            onFileSelect={handleFileSelect}
            onFileDoubleClick={handleFileDoubleClick}
            onOpenFolder={handleOpenFolder}
            onRefresh={refreshFileTree}
            onNewFile={handleNewFile}
            onNewFolder={handleNewFolder}
            onRename={handleRename}
            onDelete={handleDelete}
            onCopy={handleCopy}
            onMove={handleMove}
          />
        );
      case 'git':
        return (
          <GitPanel
            status={gitStatus}
            cwd={workspacePath || undefined}
            onCommit={async (msg) => {
              if (workspacePath) { await gitService.commit(workspacePath, msg); refreshGitStatus(); }
            }}
            onStage={async (path) => {
              if (workspacePath) { await gitService.add(workspacePath, [path]); refreshGitStatus(); }
            }}
            onUnstage={async (path) => {
              if (workspacePath) { await gitService.reset(workspacePath, [path]); refreshGitStatus(); }
            }}
            onPush={async () => {
              if (workspacePath) { await gitService.push(workspacePath); refreshGitStatus(); }
            }}
            onPull={async () => {
              if (workspacePath) { await gitService.pull(workspacePath); refreshGitStatus(); }
            }}
            onDiscard={async (path) => {
              if (workspacePath) { await gitService.discard(workspacePath, [path]); refreshGitStatus(); }
            }}
            onFileClick={handleFileSelect}
          />
        );
      case 'extensions':
        return (
          <ExtensionsPanel
            extensions={mockExtensions}
            onInstall={async (id) => console.log('安装:', id)}
            onUninstall={async (id) => console.log('卸载:', id)}
            onToggle={(id) => console.log('切换:', id)}
          />
        );
      default:
        return null;
    }
  };

  const renderPanel = (panel: PanelPosition) => {
    if (panel === 'editor') {
      if (!panelState.editorVisible) return null;
      return (
        <div key="editor" className={`panel-wrapper editor-wrapper${panelState.chatVisible ? '' : ' expand'}`} style={{
          width: panelState.chatVisible ? panelState.editorWidth : undefined,
        }}>
          <EditorPanel
            files={openFiles}
            activeFilePath={activeFilePath}
            onFileSelect={setActiveFilePath}
            onFileClose={handleFileClose}
            onContentChange={handleContentChange}
            onFileSave={handleFileSave}
            theme={settings.theme}
            diffLines={diffLines}
          />
          <div className="resize-handle vertical" onMouseDown={(e) => startResize('editor', e)} />
        </div>
      );
    }
    if (panel === 'chat') {
      return (
        <div key="chat" className="panel-wrapper chat-wrapper" style={{
          width: panelState.chatVisible ? panelState.chatWidth : 0,
          flex: panelState.chatVisible ? '1 1 auto' : '0 0 0',
          overflow: 'hidden',
          opacity: panelState.chatVisible ? 1 : 0,
          pointerEvents: panelState.chatVisible ? 'auto' : 'none',
          transition: 'width 0.2s, opacity 0.2s',
          display: 'flex',
          flexDirection: 'column',
        }}>
          {panelState.chatVisible && (
            <div className="chat-tabs-header">
              {chatTabs.map(tab => (
                <div key={tab.id} className={`chat-tab ${activeChatTabId === tab.id ? 'active' : ''}`} onClick={() => setActiveChatTabId(tab.id)}>
                  <span>{tab.title}</span>
                  {chatTabs.length > 1 && (
                    <button className="chat-tab-close" onClick={(e) => {
                      e.stopPropagation();
                      setChatTabs(prev => prev.filter(t => t.id !== tab.id));
                      if (activeChatTabId === tab.id) {
                        setActiveChatTabId(chatTabs[0].id);
                      }
                    }}>×</button>
                  )}
                </div>
              ))}
              <button className="chat-tab-add" onClick={() => {
                const newId = `chat-${Date.now()}`;
                setChatTabs(prev => [...prev, { id: newId, title: '新会话' }]);
                setActiveChatTabId(newId);
              }}>+</button>
            </div>
          )}
          {panelState.chatVisible && chatTabs.map(tab => (
            <ChatView
              key={tab.id}
              sessionId={tab.id}
              title={tab.title}
              visible={activeChatTabId === tab.id}
              plugins={plugins}
              workspacePath={workspacePath || undefined}
              onUpdateSessionTitle={() => {}}
              onNewSession={() => ''}
              availableFiles={workspaceFiles}
            />
          ))}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="app-container" ref={containerRef}>
      <TitleBar
        workspacePath={workspacePath || undefined}
        workspaceName={workspaceName}
        onNewFile={handleNewFile}
        onOpenFile={handleOpenFile}
        onOpenFolder={handleOpenFolder}
        onSave={handleSaveCurrentFile}
        onSaveAll={() => openFiles.forEach(f => handleFileSave(f.path))}
        editorVisible={panelState.editorVisible}
        chatVisible={panelState.chatVisible}
        onToggleEditor={() => togglePanel('editor')}
        onToggleChat={() => togglePanel('chat')}
        onToggleTerminal={() => setTerminalVisible(v => !v)}
        onSwapPanels={swapPanels}
      />
      <div className="main-area">
        <ActivityBar
          activeActivity={activeActivity}
          onActivityChange={(activity) => {
            if (activity === 'settings') {
              setSettingsVisible(true);
              return;
            }
            if (activeActivity === activity) {
              setSidebarCollapsed(!sidebarCollapsed);
            } else {
              setSidebarCollapsed(false);
              setActiveActivity(activity);
            }
          }}
        />
        <div className={`sidebar-container${sidebarCollapsed ? ' collapsed' : ''}`} style={!sidebarCollapsed ? { width: sidebarWidth } : undefined}>
          {!sidebarCollapsed && (
            <SidePanel title="" width={sidebarWidth} minWidth={200} maxWidth={600}>
              {renderSidebarContent()}
            </SidePanel>
          )}
        </div>
        <div className="right-area">
          <div className="panels-container">
            {panelState.panelOrder.map(panel => renderPanel(panel))}
          </div>
          <div className="terminal-wrapper" style={{ height: panelState.terminalHeight, display: terminalVisible ? 'flex' : 'none' }}>
            <div className="resize-handle horizontal" onMouseDown={(e) => startResize('terminal', e)} />
            <div className="terminal-tabs-container">
              <div className="terminal-tabs-header">
                {terminalTabs.map(tab => (
                  <div key={tab.id} className={`terminal-tab ${activeTerminalId === tab.id ? 'active' : ''}`} onClick={() => setActiveTerminalId(tab.id)}>
                    <span>{tab.title}</span>
                    {terminalTabs.length > 1 && (
                      <button className="terminal-tab-close" onClick={async (e) => {
                        e.stopPropagation();
                        try {
                          await invoke('terminal_kill', { terminalId: tab.id });
                        } catch (e) {
                          console.error('[Terminal] kill error:', e);
                        }
                        setTerminalTabs(prev => prev.filter(t => t.id !== tab.id));
                        if (activeTerminalId === tab.id) {
                          setActiveTerminalId(terminalTabs[0].id);
                        }
                      }}>×</button>
                    )}
                  </div>
                ))}
                <button className="terminal-tab-add" onClick={() => {
                  const newId = `terminal-${Date.now()}`;
                  setTerminalTabs(prev => [...prev, { id: newId, title: '终端' }]);
                  setActiveTerminalId(newId);
                }}>+</button>
              </div>
              {terminalTabs.map(tab => (
                <TerminalPanel key={tab.id} terminalId={tab.id} visible={activeTerminalId === tab.id} cwd={workspacePath || undefined} />
              ))}
            </div>
          </div>
        </div>
      </div>
      <StatusBar
        branch={gitStatus.branch}
        ahead={gitStatus.ahead}
        behind={gitStatus.behind}
        warningCount={0}
        errorCount={0}
        cursorLine={activeFile ? activeFile.content.split('\n').length : undefined}
        cursorColumn={1}
        encoding="UTF-8"
        language={activeFile?.language}
        hasUnsavedChanges={openFiles.some(f => f.modified)}
      />
      <SettingsPanel visible={settingsVisible} settings={settings} onSettingsChange={handleSettingsChange} onClose={() => setSettingsVisible(false)} />
    </div>
  );
}

export default App;
