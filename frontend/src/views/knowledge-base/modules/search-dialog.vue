<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'SearchDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const { formRef, restoreValidation } = useNaiveForm();

const store = useAuthStore();
const model = ref<Api.KnowledgeBase.SearchParams>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.SearchParams {
  return {
    userId: `${store.userInfo.id}`,
    query: '',
    topK: 10
  };
}

const list = ref<Api.KnowledgeBase.SearchResult[]>([]);

const patterns = ref<string[]>([]);
function highlight(text: string) {
  if (!model.value.query) return false;
  if (text.includes(model.value.query)) return true;
  return false;
}

async function search() {
  loading.value = true;
  const { error, data } = await request<Api.KnowledgeBase.SearchResult[]>({
    url: 'search/hybrid',
    params: model.value
  });
  if (!error) {
    list.value = data;
    patterns.value = [model.value.query];
  }
  loading.value = false;
}

function reset() {
  model.value = createDefaultModel();
  patterns.value = [];
  list.value = [];
  restoreValidation();
}
watch(visible, () => {
  if (visible.value) {
    reset();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    :title="$t('page.knowledgeBase.search.title')"
    :show-icon="false"
    :mask-closable="false"
    class="w-1000px!"
  >
    <NForm
      ref="formRef"
      :model="model"
      label-placement="left"
      :label-width="60"
      inline
      class="pb-2"
      :show-feedback="false"
    >
      <NGrid>
        <NFormItemGi label="topK" path="topK" class="pr-24px" span="6">
          <NInputNumber
            v-model:value="model.topK"
            :placeholder="$t('page.knowledgeBase.search.topKPlaceholder')"
            clearable
            :min="1"
            :precision="0"
            :step="10"
          />
        </NFormItemGi>
        <NFormItemGi :label="$t('page.knowledgeBase.search.keyword')" path="query" class="pr-24px" span="12">
          <NInput v-model:value="model.query" :placeholder="$t('page.knowledgeBase.search.keywordPlaceholder')" clearable />
        </NFormItemGi>
        <NFormItemGi span="6">
          <NSpace class="w-full" justify="end">
            <NButton @click="reset">
              <template #icon>
                <icon-ic-round-refresh class="text-icon" />
              </template>
              {{ $t('common.reset') }}
            </NButton>
            <NButton type="primary" ghost @click="search">
              <template #icon>
                <icon-ic-round-search class="text-icon" />
              </template>
              {{ $t('common.search') }}
            </NButton>
          </NSpace>
        </NFormItemGi>
      </NGrid>
    </NForm>
    <NSpin :show="loading">
      <NEmpty v-if="list.length === 0" :description="$t('common.noData')" class="py-100px" />
      <NScrollbar v-else class="max-h-500px">
        <NCard
          v-for="(item, index) in list"
          :key="index"
          class="my-8"
          embedded
          :segmented="{
            content: true,
            footer: 'soft'
          }"
        >
          <div class="relative">
            <NHighlight
              v-if="highlight(item.textContent)"
              highlight-class="bg-[rgb(var(--primary-400-color))] color-white px-2 mx-1 rd-sm"
              :text="item.textContent"
              :patterns="patterns"
            />
            <span v-else>{{ item.textContent }}</span>
            <NTag
              :bordered="false"
              draggable
              class="absolute right-0 top-0 bg-[rgb(var(--primary-color)/.9)] color-white hover:bg-transparent hover:color-transparent"
            >
              Score: {{ item.score }}
            </NTag>
          </div>
          <template #footer>
            <span>{{ $t('page.knowledgeBase.search.source', { fileName: item.fileName }) }}</span>
          </template>
        </NCard>
      </NScrollbar>
    </NSpin>
  </NModal>
</template>

<style scoped></style>
