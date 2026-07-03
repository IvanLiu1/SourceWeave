<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);
const isRegisterModule = computed(() => (props.module || 'pwd-login') === 'register');
</script>

<template>
  <div class="login-bg relative size-full flex-center overflow-hidden" :class="{ 'login-bg--dark': themeStore.darkMode }">
    <div class="login-blob login-blob--1"></div>
    <div class="login-blob login-blob--2"></div>
    <NCard :bordered="false" class="login-card relative z-4 w-auto card-wrapper">
      <div :class="isRegisterModule ? 'login-panel login-panel--register' : 'login-panel'">
        <header class="flex-y-center justify-between">
          <SystemLogo class="text-64px text-primary lt-sm:text-48px" />
          <h3 class="flex-y-center gap-2 text-28px text-primary font-500 lt-sm:text-22px">
            <span>{{ $t('system.title') }}</span>
            <span v-if="isRegisterModule" class="text-18px font-medium opacity-80 lt-sm:text-16px">
              {{ $t(activeModule.label) }}
            </span>
          </h3>
          <div class="i-flex-col">
            <ThemeSchemaSwitch
              :theme-schema="themeStore.themeScheme"
              :show-tooltip="false"
              class="text-20px lt-sm:text-18px"
              @switch="themeStore.toggleThemeScheme"
            />
            <LangSwitch
              v-if="themeStore.header.multilingual.visible"
              :lang="appStore.locale"
              :lang-options="appStore.localeOptions"
              :show-tooltip="false"
              @change-lang="appStore.changeLocale"
            />
          </div>
        </header>
        <main class="pt-24px">
          <h3 v-if="!isRegisterModule" class="text-18px text-primary font-medium">{{ $t(activeModule.label) }}</h3>
          <div class="pt-24px">
            <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
              <component :is="activeModule.component" />
            </Transition>
          </div>
        </main>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.login-bg {
  background:
    radial-gradient(circle at 16% 18%, rgb(var(--primary-color) / 0.16), transparent 42%),
    radial-gradient(circle at 84% 82%, rgb(var(--primary-color) / 0.12), transparent 46%),
    #f7fafc;
}

.login-bg--dark {
  background:
    radial-gradient(circle at 16% 18%, rgb(var(--primary-color) / 0.2), transparent 42%),
    radial-gradient(circle at 84% 82%, rgb(var(--primary-color) / 0.14), transparent 46%),
    #101014;
}

.login-blob {
  position: absolute;
  z-index: 1;
  border-radius: 9999px;
  filter: blur(64px);
  pointer-events: none;
  background: rgb(var(--primary-color) / 0.26);
}

.login-blob--1 {
  top: -120px;
  left: -100px;
  width: 360px;
  height: 360px;
}

.login-blob--2 {
  right: -140px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  background: rgb(var(--primary-color) / 0.18);
}

.login-card {
  border-radius: 16px;
  box-shadow:
    0 18px 48px -16px rgb(var(--primary-color) / 0.3),
    0 8px 24px -12px rgb(0 0 0 / 0.12);
}

.login-panel {
  width: 400px;
}

.login-panel--register {
  width: min(860px, calc(100vw - 72px));
}

@media (max-width: 640px) {
  .login-panel,
  .login-panel--register {
    width: 300px;
  }
}
</style>
