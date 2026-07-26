<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'TokenQuotaDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);

type Model = {
  llmToken: number | null;
  embeddingToken: number | null;
  reason: string;
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return {
    llmToken: null,
    embeddingToken: null,
    reason: $t('page.user.tokenDialog.defaultReason')
  };
}

function close() {
  visible.value = false;
}

function normalizeToken(value: number | null) {
  return value === null ? 0 : Math.trunc(value);
}

async function handleSubmit() {
  const llmToken = normalizeToken(model.value.llmToken);
  const embeddingToken = normalizeToken(model.value.embeddingToken);
  if (llmToken < 0 || embeddingToken < 0) {
    window.$message?.warning($t('page.user.tokenDialog.negative'));
    return;
  }
  if (llmToken === 0 && embeddingToken === 0) {
    window.$message?.warning($t('page.user.tokenDialog.empty'));
    return;
  }

  try {
    loading.value = true;
    const res = await request({
      method: 'POST',
      url: `/admin/users/${props.rowData.userId}/tokens/add`,
      data: {
        llmToken,
        embeddingToken,
        reason: model.value.reason
      }
    });
    if (!res.error) {
      window.$message?.success($t('page.user.tokenDialog.success'));
      close();
      emit('submitted');
    }
  } finally {
    loading.value = false;
  }
}

watch(visible, () => {
  if (visible.value) {
    model.value = createDefaultModel();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    :title="$t('page.user.tokenDialog.title')"
    :show-icon="false"
    :mask-closable="false"
    class="w-520px!"
  >
    <NForm :model="model" label-placement="left" :label-width="128" mt-10>
      <NFormItem :label="$t('page.user.tokenDialog.username')">
        <NInput :value="rowData.username" readonly />
      </NFormItem>
      <NFormItem label="LLM Token">
        <NInputNumber
          v-model:value="model.llmToken"
          :min="0"
          :step="10000"
          :precision="0"
          class="w-full"
          :placeholder="$t('page.user.tokenDialog.optionalPlaceholder')"
        />
      </NFormItem>
      <NFormItem label="Embedding Token">
        <NInputNumber
          v-model:value="model.embeddingToken"
          :min="0"
          :step="10000"
          :precision="0"
          class="w-full"
          :placeholder="$t('page.user.tokenDialog.optionalPlaceholder')"
        />
      </NFormItem>
      <NFormItem :label="$t('page.user.tokenDialog.reason')">
        <NInput v-model:value="model.reason" maxlength="200" show-count :placeholder="$t('page.user.tokenDialog.defaultReason')" />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">{{ $t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">{{ $t('page.user.tokenDialog.add') }}</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
