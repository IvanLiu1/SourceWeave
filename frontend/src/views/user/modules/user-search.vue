<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'UserSearch'
});

const emit = defineEmits<{
  search: [];
}>();

const { formRef } = useNaiveForm();

const model = defineModel<Api.User.SearchParams>('model', { required: true });

const enableStatusOptions = computed(() => [
  { label: $t('page.user.enable'), value: 1 },
  { label: $t('page.user.disable'), value: 0 }
]);

watchEffect(() => {
  search();
});
async function search() {
  emit('search');
}
</script>

<template>
  <NCard :bordered="false" size="small" class="rd-full px-6">
    <NForm ref="formRef" :model="model" label-placement="left" :show-feedback="false" inline>
      <NFormItem :label="$t('page.user.search.keyword')" path="keyword">
        <NInput v-model:value="model.keyword" :placeholder="$t('page.user.search.keywordPlaceholder')" clearable />
      </NFormItem>
      <NFormItem :label="$t('page.user.search.orgTag')" path="userGender">
        <OrgTagCascader v-model:value="model.orgTag" clearable class="w-200px!" />
      </NFormItem>
      <NFormItem :label="$t('page.user.search.status')" path="status">
        <NSelect
          v-model:value="model.status"
          :placeholder="$t('page.user.search.statusPlaceholder')"
          :options="enableStatusOptions"
          clearable
          class="w-200px!"
        />
      </NFormItem>
    </NForm>
  </NCard>
</template>

<style scoped></style>
