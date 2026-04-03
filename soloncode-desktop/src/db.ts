import Dexie, { Table } from 'dexie';

export interface DbMessage {
  id?: number;
  conversationId: string | number;
  role: string;
  timestamp: string;
  contents: string;
}

export interface DbConversation {
  id?: number; // 建议与 schema ++id 保持一致，统一为 number
  title: string;
  timestamp: string;
  status: string;
  isPermanent?: boolean;
  icon?: string;
}

class SolonCodeDatabase extends Dexie {
  messages!: Table<DbMessage>;
  conversations!: Table<DbConversation>;

  constructor() {
    super('SolonCodeDB');
    this.version(1).stores({
      messages: '++id, conversationId, timestamp',
      conversations: '++id, title, timestamp, status'
    });
  }
}

export const db = new SolonCodeDatabase();

export async function saveMessage(message: Omit<DbMessage, 'id'>): Promise<number> {
  return await db.messages.add(message);
}

export async function getMessagesByConversation(conversationId: string | number): Promise<DbMessage[]> {
  return await db.messages
    .where('conversationId')
    .equals(conversationId)
    .toArray();
}

export async function saveConversation(conversation: DbConversation): Promise<number> {
  // 统一返回数据库生成的 ID (number)，避免类型不一致
  if (conversation.id) {
    await db.conversations.update(conversation.id, conversation);
    return conversation.id;
  }
  const newId = await db.conversations.add(conversation);
  return newId;
}

export async function getAllConversations(): Promise<DbConversation[]> {
  return await db.conversations.toArray();
}

export async function deleteConversation(id: string | number): Promise<void> {
  await db.messages.where('conversationId').equals(id).delete();
  // 直接使用 where 查询删除，避免全量加载
  await db.conversations.where('id').equals(id).delete();
}

export async function updateConversation(id: string | number, updates: Partial<DbConversation>): Promise<void> {
  // 直接使用 modify 更新，避免全量加载
  await db.conversations.where('id').equals(id).modify(updates);
}