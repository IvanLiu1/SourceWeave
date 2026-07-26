<script setup lang="ts">
import type { FormRules } from 'naive-ui';
import { $t } from '@/locales';

defineOptions({
  name: 'OrgTagSettingDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });
const loading = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type Model = {
  orgTags: string[];
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return {
    orgTags: []
  };
}

const rules = ref<FormRules>({
  orgTags: defaultRequiredRule
});

const privateOrgTag = ref<string[]>([]);
async function handleUpdateModelWhenEdit() {
  model.value = createDefaultModel();
  model.value.orgTags = props.rowData.orgTags.map(tag => tag.tagId!);
  // 备份默认的私人组织标签，防止被误删
  privateOrgTag.value = props.rowData.orgTags.filter(tag => tag.tagId!.startsWith('PRIVATE_')).map(tag => tag.tagId!);
}

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  model.value.orgTags = Array.from(new Set([...model.value.orgTags, ...privateOrgTag.value]));
  const res = await request({
    method: 'PUT',
    url: `/admin/users/${props.rowData.userId}/org-tags`,
    data: model.value
  });
  if (!res.error) {
    window.$message?.success($t('page.orgTag.operationSuccess'));
    close();
    emit('submitted');
  }
  loading.value = false;
}

watch(visible, () => {
  if (visible.value) {
    handleUpdateModelWhenEdit();
    restoreValidation();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    :title="$t('page.user.orgDialog.title')"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem :label="$t('page.user.orgDialog.username')" path="username">
        <NInput :value="rowData.username" readonly />
      </NFormItem>
      <NFormItem :label="$t('page.user.orgDialog.orgTags')" path="orgTags">
        <OrgTagCascader v-model:value="model.orgTags" multiple exclude-private />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">{{ $t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSubmit">{{ $t('common.save') }}</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped></style>
