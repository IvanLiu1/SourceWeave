import { createLocalforage, createStorage } from '@sa/utils';

const storagePrefix = import.meta.env.VITE_STORAGE_PREFIX || '';

export const localStg = createStorage<StorageType.Local>('local', storagePrefix);

const rememberedLogin = localStg.get('rememberedLogin');

// Rewrite legacy entries at app startup so any previously persisted plaintext password is removed.
if (rememberedLogin) {
  localStg.set('rememberedLogin', { userName: rememberedLogin.userName });
}

export const sessionStg = createStorage<StorageType.Session>('session', storagePrefix);

export const localforage = createLocalforage<StorageType.Local>('local');
