export interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export interface Conversation {
  id: number;
  title: string;
  timestamp: string;
  status: string;
  isPermanent?: boolean;
  icon?: string;
}

export interface Plugin {
  id: string;
  name: string;
  icon: string;
  description: string;
  enabled: boolean;
  version: string;
}

export type Theme = 'dark' | 'light';
