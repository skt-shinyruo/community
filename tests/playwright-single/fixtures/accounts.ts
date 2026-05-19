export type TestAccount = {
  key: 'aaa' | 'bbb' | 'admin'
  username: string
  password: string
  userId: string
  storageStatePath: string
}

export const accounts: Record<TestAccount['key'], TestAccount> = {
  aaa: {
    key: 'aaa',
    username: process.env.SINGLE_USER_A_USERNAME || 'aaa',
    password: process.env.SINGLE_USER_A_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000001',
    storageStatePath: '.auth/aaa.json'
  },
  bbb: {
    key: 'bbb',
    username: process.env.SINGLE_USER_B_USERNAME || 'bbb',
    password: process.env.SINGLE_USER_B_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000002',
    storageStatePath: '.auth/bbb.json'
  },
  admin: {
    key: 'admin',
    username: process.env.SINGLE_ADMIN_USERNAME || 'admin',
    password: process.env.SINGLE_ADMIN_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000003',
    storageStatePath: '.auth/admin.json'
  }
}
