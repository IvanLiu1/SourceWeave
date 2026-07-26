<script setup lang="tsx">
import { ref } from 'vue';
import { NButton, NTag } from 'naive-ui';
import { $t } from '@/locales';
import UserSearch from './modules/user-search.vue';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';
import TokenQuotaDialog from './modules/token-quota-dialog.vue';

const appStore = useAppStore();
const authStore = useAuthStore();

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const { columns, columnChecks, data, getData, loading, mobilePagination, searchParams, resetSearchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    status: null
  },
  columns: () => [
    {
      key: 'index',
      title: $t('page.user.column.index'),
      width: 64
    },
    {
      key: 'username',
      title: $t('page.user.column.username'),
      minWidth: 100
    },
    {
      key: 'orgTags',
      title: $t('page.user.column.tags'),
      render: row => (
        <div class="flex flex-wrap gap-2">
          {row.orgTags.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          ))}
        </div>
      )
    },
    {
      key: 'status',
      title: $t('page.user.column.status'),
      width: 100,
      render: row => (
        <NTag type={row.status ? 'success' : 'warning'}>
          {row.status ? $t('page.user.enabled') : $t('page.user.disabled')}
        </NTag>
      )
    },
    {
      key: 'createdAt',
      title: $t('page.user.column.createdAt'),
      width: 200,
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'chatUsage',
      title: $t('page.user.column.chatCount'),
      width: 130,
      render: row => (
        <div class="flex flex-col gap-1 text-xs">
          <span>{$t('page.user.count', { count: Number(row.usage?.chatRequestCount || 0).toLocaleString(appStore.locale) })}</span>
          <span class="text-stone-400">{$t('page.user.todayMessages')}</span>
        </div>
      )
    },
    {
      key: 'llmUsage',
      title: $t('page.user.column.llmQuota'),
      width: 220,
      render: row => {
        const quota = row.usage?.llm;
        if (!quota?.enabled) {
          return <span class="text-stone-400">{$t('page.user.quotaDisabled')}</span>;
        }
        return (
          <div class="flex flex-col gap-1 text-xs">
            <span>
              {Number(quota.usedTokens || 0).toLocaleString(appStore.locale)} / {Number(quota.limitTokens || 0).toLocaleString(appStore.locale)}
            </span>
            <span class="text-stone-400">
              {$t('page.user.remainingRequests', {
                remaining: Number(quota.remainingTokens || 0).toLocaleString(appStore.locale),
                count: Number(quota.requestCount || 0).toLocaleString(appStore.locale)
              })}
            </span>
          </div>
        );
      }
    },
    {
      key: 'embeddingUsage',
      title: $t('page.user.column.embeddingQuota'),
      width: 220,
      render: row => {
        const quota = row.usage?.embedding;
        if (!quota?.enabled) {
          return <span class="text-stone-400">{$t('page.user.quotaDisabled')}</span>;
        }
        return (
          <div class="flex flex-col gap-1 text-xs">
            <span>
              {Number(quota.usedTokens || 0).toLocaleString(appStore.locale)} / {Number(quota.limitTokens || 0).toLocaleString(appStore.locale)}
            </span>
            <span class="text-stone-400">
              {$t('page.user.remainingRequests', {
                remaining: Number(quota.remainingTokens || 0).toLocaleString(appStore.locale),
                count: Number(quota.requestCount || 0).toLocaleString(appStore.locale)
              })}
            </span>
          </div>
        );
      }
    },
    {
      key: 'operate',
      title: $t('page.user.column.operation'),
      width: 230,
      render: row => (
        <div class="flex gap-2">
          <NButton type="primary" ghost size="small" onClick={() => handleOrgTag(row)}>
            {$t('page.user.assignOrgTags')}
          </NButton>
          {authStore.isAdmin ? (
            <NButton type="warning" ghost size="small" onClick={() => handleTokenQuota(row)}>
              {$t('page.user.addToken')}
            </NButton>
          ) : null}
        </div>
      )
    }
  ]
});

const visible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
const tokenVisible = ref(false);
const tokenEditingData = ref<Api.User.Item | null>(null);

function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}

function handleTokenQuota(row: Api.User.Item) {
  tokenEditingData.value = row;
  tokenVisible.value = true;
}
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <Teleport defer to="#header-extra">
      <UserSearch v-model:model="searchParams" @reset="resetSearchParams" @search="getData" />
    </Teleport>

    <NCard :title="$t('page.user.title')" :bordered="false" size="small" class="sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :addable="false" :loading="loading" @refresh="getData" />
      </template>
      <NDataTable
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="1400"
        :loading="loading"
        remote
        :row-key="row => row.userId"
        :pagination="mobilePagination"
        class="sm:h-full"
      />
    </NCard>

    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
    <TokenQuotaDialog v-model:visible="tokenVisible" :row-data="tokenEditingData!" @submitted="getData" />
  </div>
</template>

<style scoped lang="scss"></style>
