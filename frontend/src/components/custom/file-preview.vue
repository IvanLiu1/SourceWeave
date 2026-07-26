<template>
  <div class="file-preview-container">
    <div class="preview-backdrop" />

    <div class="preview-content">
      <template v-if="loading">
        <div class="state-panel">
          <div class="state-orb">
            <NSpin size="large" />
          </div>
          <div class="state-copy">
            <strong>{{ $t('component.filePreview.loadingTitle') }}</strong>
            <span>{{ $t('component.filePreview.loadingHint') }}</span>
          </div>
        </div>
      </template>
      <template v-else-if="error">
        <div class="state-panel state-panel--error">
          <div class="state-orb state-orb--error">
            <icon-mdi-alert-circle class="text-34" />
          </div>
          <div class="state-copy">
            <strong>{{ $t('component.filePreview.openFailed') }}</strong>
            <span>{{ error }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="content-wrapper" :class="{ 'content-wrapper--immersive': previewType === 'pdf' && previewUrl }">
          <aside class="insight-rail">
            <section class="info-card source-card">
              <div class="source-card-top">
                <div class="file-badge-shell">
                  <div class="file-badge-icon">
                    <SvgIcon :local-icon="getFileIcon(fileName)" class="text-18" />
                  </div>
                  <div class="file-badge-copy">
                    <h2 class="preview-title">{{ fileName }}</h2>
                    <p v-if="headerMetaLine" class="preview-subtitle">{{ headerMetaLine }}</p>
                  </div>
                </div>
              </div>
              <div class="source-actions">
                <NButton
                  v-if="previewType !== 'pdf'"
                  size="small"
                  secondary
                  @click="openPreviewInNewTab"
                  :disabled="!canOpenInNewTab"
                >
                  <template #icon>
                    <icon-mdi-open-in-new />
                  </template>
                  {{ $t('component.filePreview.newWindow') }}
                </NButton>
                <NButton size="small" secondary @click="downloadFile" :loading="downloading">
                  <template #icon>
                    <icon-mdi-download />
                  </template>
                  {{ $t('component.filePreview.download') }}
                </NButton>
                <NButton size="small" quaternary @click="closePreview">
                  <template #icon>
                    <icon-mdi-close />
                  </template>
                  {{ $t('component.filePreview.close') }}
                </NButton>
              </div>
            </section>

            <section class="info-card info-card--hero">
              <span class="info-label">{{ $t('component.filePreview.overview') }}</span>
              <strong class="info-title">{{ heroHeadline }}</strong>
              <p class="info-copy">{{ heroDescription }}</p>
              <div v-if="retrievalQuery" class="info-inline-block">
                <span class="info-label">{{ $t('component.filePreview.retrievalQuery') }}</span>
                <p class="support-copy">{{ retrievalQuery }}</p>
              </div>
            </section>

            <section v-if="evidenceSnippet" class="info-card">
              <span class="info-label">{{ $t('component.filePreview.clue') }}</span>
              <p class="support-copy">{{ evidenceSnippet }}</p>
            </section>

            <section v-else-if="resolvedHighlightAnchor" class="info-card">
              <span class="info-label">{{ $t('component.filePreview.locationClue') }}</span>
              <p class="support-copy">{{ resolvedHighlightAnchor }}</p>
            </section>

          </aside>

          <section class="preview-stage">
            <div class="stage-body">
              <template v-if="previewType === 'pdf' && previewUrl">
                <div class="pdf-preview-stack">
                  <PdfDocumentViewer
                    :url="resolvedPreviewUrl"
                    :source-url="resolvedSourceUrl"
                    :file-name="fileName"
                    :page-number="pageNumber"
                    :single-page-mode="singlePageMode"
                    :source-page-number="sourcePageNumber"
                    :anchor-text="resolvedHighlightAnchor"
                    :search-text="resolvedHighlightSearchText"
                    :visible="visible"
                  />
                </div>
              </template>
              <template v-else-if="previewType === 'image' && resolvedPreviewUrl">
                <div class="image-preview-shell">
                  <img :src="resolvedPreviewUrl" :alt="fileName" class="preview-image" />
                </div>
              </template>
              <template v-else-if="previewType === 'text'">
                <div class="text-preview-shell">
                  <pre class="preview-text">{{ content }}</pre>
                </div>
              </template>
              <template v-else>
                <div class="download-placeholder">
                  <div class="placeholder-icon">
                    <SvgIcon :local-icon="getFileIcon(fileName)" class="text-28" />
                  </div>
                  <div class="state-copy">
                    <strong>{{ $t('component.filePreview.unsupported') }}</strong>
                    <span>{{ $t('component.filePreview.unsupportedHint') }}</span>
                  </div>
                  <div class="placeholder-actions">
                    <NButton secondary @click="openPreviewInNewTab" :disabled="!canOpenInNewTab">
                      <template #icon>
                        <icon-mdi-open-in-new />
                      </template>
                      {{ $t('component.filePreview.openNewWindow') }}
                    </NButton>
                    <NButton type="primary" @click="downloadFile">
                      <template #icon>
                        <icon-mdi-download />
                      </template>
                      {{ $t('component.filePreview.downloadToView') }}
                    </NButton>
                  </div>
                </div>
              </template>
            </div>
          </section>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NSpin } from 'naive-ui';
import { request } from '@/service/request';
import { getFileExt } from '@/utils/common';
import { getServiceBaseURL } from '@/utils/service';
import { $t } from '@/locales';
import SvgIcon from '@/components/custom/svg-icon.vue';
import PdfDocumentViewer from '@/components/custom/pdf-document-viewer.vue';

interface Props {
  fileName: string;
  fileMd5?: string;
  pageNumber?: number;
  anchorText?: string;
  searchText?: string;
  retrievalMode?: Api.Chat.ReferenceEvidence['retrievalMode'];
  retrievalLabel?: string;
  retrievalQuery?: string;
  evidenceSnippet?: string;
  matchedChunkText?: string;
  score?: number | null;
  chunkId?: number | null;
  visible: boolean;
}

interface Emits {
  (e: 'close'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const loading = ref(false);
const downloading = ref(false);
const content = ref('');
const error = ref('');
const previewType = ref<'pdf' | 'image' | 'text' | 'download'>('text');
const previewUrl = ref('');
const sourceUrl = ref('');
const singlePageMode = ref(false);
const sourcePageNumber = ref<number | undefined>(undefined);
const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL: serviceBaseUrl } = getServiceBaseURL(import.meta.env, isHttpProxy);

const resolvedPreviewUrl = computed(() => resolveFileAccessUrl(previewUrl.value));
const resolvedSourceUrl = computed(() => resolveFileAccessUrl(sourceUrl.value));
const fileExtensionLabel = computed(() => {
  const extension = getFileExt(props.fileName)?.toUpperCase();
  return extension || 'FILE';
});
const fallbackRetrievalLabel = computed(() => {
  if (props.retrievalMode === 'TEXT_ONLY') {
    return $t('component.filePreview.keywordRetrieval');
  }
  if (props.retrievalMode === 'HYBRID') {
    return $t('component.filePreview.hybridRetrieval');
  }
  return '';
});
const resolvedHighlightAnchor = computed(() => props.anchorText || '');
const resolvedHighlightSearchText = computed(() => {
  return [props.matchedChunkText, props.searchText, props.anchorText]
    .map(text => text?.trim())
    .filter((text, index, values): text is string => Boolean(text) && values.indexOf(text) === index)
    .join('\n');
});
const displayScore = computed(() => {
  if (typeof props.score !== 'number' || Number.isNaN(props.score)) {
    return '';
  }
  return props.score.toFixed(3);
});
const displayPage = computed(() => sourcePageNumber.value || props.pageNumber || undefined);
const displayPageLabel = computed(() =>
  displayPage.value ? $t('component.filePreview.page', { number: displayPage.value }) : ''
);
const headerMetaLine = computed(() => {
  if (previewType.value === 'pdf') {
    return [
      displayPageLabel.value,
      displayScore.value ? $t('component.filePreview.score', { score: displayScore.value }) : ''
    ]
      .filter(Boolean)
      .join(' / ');
  }
  if (previewType.value === 'image') {
    return [
      fileExtensionLabel.value,
      displayScore.value ? $t('component.filePreview.score', { score: displayScore.value }) : ''
    ]
      .filter(Boolean)
      .join(' / ');
  }
  if (previewType.value === 'text') {
    return [
      fileExtensionLabel.value,
      displayScore.value ? $t('component.filePreview.score', { score: displayScore.value }) : ''
    ]
      .filter(Boolean)
      .join(' / ');
  }
  return [
    fileExtensionLabel.value,
    displayScore.value ? $t('component.filePreview.score', { score: displayScore.value }) : ''
  ]
    .filter(Boolean)
    .join(' / ');
});
const heroHeadline = computed(() => {
  if (props.retrievalLabel || fallbackRetrievalLabel.value) {
    return props.retrievalLabel || fallbackRetrievalLabel.value;
  }
  if (previewType.value === 'pdf') {
    return $t('component.filePreview.pdfReady');
  }
  return $t('component.filePreview.referenceReady');
});
const heroDescription = computed(() => {
  if (props.retrievalQuery) {
    return $t('component.filePreview.descriptionQuery');
  }
  if (props.evidenceSnippet) {
    return $t('component.filePreview.descriptionEvidence');
  }
  if (resolvedHighlightAnchor.value) {
    return $t('component.filePreview.descriptionAnchor');
  }
  return $t('component.filePreview.descriptionDefault');
});
const canOpenInNewTab = computed(() => Boolean(resolvedSourceUrl.value || resolvedPreviewUrl.value));

function resolveFileAccessUrl(url: string) {
  if (!url) return '';
  if (/^(https?:)?\/\//i.test(url) || /^(blob:|data:)/i.test(url)) {
    return url;
  }

  if (url.startsWith('/api/')) {
    if (serviceBaseUrl.startsWith('/proxy-')) {
      return `${serviceBaseUrl}${url.replace(/^\/api\/v\d+/, '')}`;
    }

    if (/^https?:\/\//i.test(serviceBaseUrl)) {
      return `${new URL(serviceBaseUrl).origin}${url}`;
    }

    const serviceOrigin = serviceBaseUrl.replace(/\/api(?:\/v\d+)?\/?$/, '');
    return `${serviceOrigin}${url}`;
  }

  if (url.startsWith('/')) {
    return url;
  }

  return `${serviceBaseUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}

// 获取文件图标
function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

// 监听文件名变化，加载预览内容
watch(() => props.fileName, async (newFileName) => {
  if (newFileName && props.visible) {
    await loadPreviewContent();
  }
}, { immediate: true });

// 监听可见性变化
watch(() => props.visible, async (visible) => {
  if (visible && props.fileName) {
    await loadPreviewContent();
  }
});

// 加载预览内容
async function loadPreviewContent() {
  if (!props.fileName) return;

  console.log('[文件预览] 开始加载预览内容:', {
    fileName: props.fileName,
    fileMd5: props.fileMd5,
    visible: props.visible
  });

  loading.value = true;
  error.value = '';
  content.value = '';
  previewUrl.value = '';
  sourceUrl.value = '';
  singlePageMode.value = false;
  sourcePageNumber.value = undefined;
  previewType.value = 'text';

  try {
    // 优先使用 MD5 预览（如果存在）
    if (props.fileMd5) {
      console.log('[文件预览] 使用MD5模式预览，请求参数:', {
        fileName: props.fileName,
        fileMd5: props.fileMd5,
        pageNumber: props.pageNumber
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          fileMd5: props.fileMd5,
          pageNumber: props.pageNumber
        }
      });

      console.log('[文件预览] MD5模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = $t('component.filePreview.previewFailed', {
          message: requestError.message || $t('component.filePreview.unknownError')
        });
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    } else {
      // 降级：使用文件名预览（向后兼容）
      console.log('[文件预览] 使用文件名模式预览（降级）, 请求参数:', {
        fileName: props.fileName,
        pageNumber: props.pageNumber
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          pageNumber: props.pageNumber
        }
      });

      console.log('[文件预览] 文件名模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = $t('component.filePreview.previewFailed', {
          message: requestError.message || $t('component.filePreview.unknownError')
        });
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    }
  } catch (err: any) {
    error.value = $t('component.filePreview.previewFailed', {
      message: err.message || $t('component.filePreview.networkError')
    });
  } finally {
    loading.value = false;
  }
}

// 下载文件
async function downloadFile() {
  if (!props.fileName) return;

  downloading.value = true;

  try {
    // 优先使用 MD5 下载（如果存在）
    if (props.fileMd5) {
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
        fileMd5?: string;
      }>({
        url: '/documents/download-by-md5',
        params: {
          fileMd5: props.fileMd5
        }
      });

      if (requestError) {
        window.$message?.error(
          $t('component.filePreview.downloadFailed', {
            message: requestError.message || $t('component.filePreview.unknownError')
          })
        );
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success($t('component.filePreview.downloadStarted'));
      }
    } else {
      // 降级：使用文件名下载（向后兼容）
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download',
        params: {
          fileName: props.fileName
        }
      });

      if (requestError) {
        window.$message?.error(
          $t('component.filePreview.downloadFailed', {
            message: requestError.message || $t('component.filePreview.unknownError')
          })
        );
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success($t('component.filePreview.downloadStarted'));
      }
    }
  } catch (err: any) {
    window.$message?.error(
      $t('component.filePreview.downloadFailed', {
        message: err.message || $t('component.filePreview.networkError')
      })
    );
  } finally {
    downloading.value = false;
  }
}

function openPreviewInNewTab() {
  const targetUrl = resolvedSourceUrl.value || resolvedPreviewUrl.value;
  if (!targetUrl) return;

  if (previewType.value === 'pdf' && displayPage.value) {
    window.open(`${targetUrl}#page=${displayPage.value}`, '_blank', 'noopener,noreferrer');
    return;
  }

  window.open(targetUrl, '_blank', 'noopener,noreferrer');
}

// 关闭预览
function closePreview() {
  emit('close');
}

</script>

<style scoped lang="scss">
.file-preview-container {
  @apply relative flex h-full min-h-0 flex-col overflow-hidden bg-white;
  height: min(92vh, calc(100vh - 20px));
  min-height: min(760px, calc(100vh - 20px));

  .preview-backdrop {
    display: none;
  }

  .file-badge-shell {
    @apply flex items-start gap-4;
  }

  .file-badge-icon {
    @apply flex h-13 w-13 shrink-0 items-center justify-center rounded-14px border border-stone-200 bg-stone-50 text-primary shadow-sm;
  }

  .file-badge-copy {
    @apply min-w-0;
  }

  .preview-title {
    @apply m-0 truncate text-[17px] font-700 leading-tight text-stone-800;
  }

  .preview-subtitle {
    @apply mt-1 text-sm text-stone-500;
  }

  .preview-content {
    @apply relative z-1 min-h-0 flex-1 overflow-hidden bg-white px-3 py-3;

    .content-wrapper {
      @apply grid h-full min-h-0 grid-cols-[240px_minmax(0,1fr)] gap-3 overflow-hidden;
    }

    .content-wrapper--immersive {
      grid-template-columns: 240px minmax(0, 1fr);
    }

    .state-panel {
      @apply flex h-full min-h-[420px] flex-col items-center justify-center gap-5 rounded-16px border border-stone-200 bg-white px-10 text-center shadow-sm;
    }

    .state-panel--error {
      @apply border-rose-200/60 bg-rose-50/72;
    }

    .state-orb {
      @apply flex h-16 w-16 items-center justify-center rounded-full border border-stone-200 bg-stone-50 text-stone-700;
    }

    .state-orb--error {
      @apply border-rose-200 bg-rose-100 text-rose-600;
    }

    .state-copy {
      @apply flex max-w-520px flex-col gap-2 text-stone-600;
    }

    .state-copy strong {
      @apply text-lg text-stone-800;
      font-family: 'Avenir Next', 'Segoe UI', sans-serif;
    }

    .insight-rail {
      @apply flex min-h-0 min-w-0 flex-col gap-4 overflow-y-auto overflow-x-hidden pr-1;
    }

    .info-card {
      @apply rounded-12px bg-stone-50 p-4 text-stone-700;
    }

    .info-card--hero {
      @apply bg-transparent p-0;
    }

    .source-card {
      @apply gap-0 rounded-16px border border-stone-200 bg-white p-4 shadow-sm;
    }

    .source-card-top {
      @apply min-w-0 overflow-hidden;
    }

    .source-actions {
      @apply mt-4 flex flex-wrap gap-2;
    }

    .info-card--quiet {
      @apply bg-stone-50;
    }

    .info-row {
      @apply mb-3 flex items-center justify-between gap-3;
    }

    .info-label {
      @apply text-[11px] uppercase tracking-[0.16em] text-stone-500;
    }

    .info-title {
      @apply mt-3 block whitespace-nowrap text-sm font-700 leading-tight text-stone-900;
    }

    .info-copy,
    .support-copy,
    .spotlight-copy {
      @apply mb-0 mt-3 text-sm leading-7 break-words;
      overflow-wrap: anywhere;
    }

    .info-inline-block {
      @apply mt-4 rounded-12px bg-primary/4 px-4 py-3;
    }

    .spotlight-copy {
      color: inherit;
    }

    .preview-stage {
      @apply min-h-0 overflow-hidden bg-white;
    }

    .stage-body {
      @apply h-full min-h-0 overflow-hidden rounded-16px border border-stone-200 bg-white;
    }

    .pdf-preview-stack {
      @apply flex h-full min-h-0 flex-col;
    }

    .text-preview-shell {
      @apply h-full bg-white p-4;
    }

    .preview-text {
      @apply m-0 h-full overflow-auto text-[14px] whitespace-pre-wrap break-words text-stone-700;
      font-family: 'SFMono-Regular', 'Menlo', 'Monaco', monospace;
      line-height: 1.68;
    }

    .image-preview-shell {
      @apply flex h-full min-h-0 items-center justify-center overflow-auto bg-white p-4;
    }

    .preview-image {
      @apply max-h-full max-w-full rounded-12px object-contain shadow-sm;
    }

    .download-placeholder {
      @apply flex h-full min-h-[320px] flex-col items-center justify-center gap-5 rounded-12px bg-stone-50 px-8 text-center text-stone-500;
    }

    .placeholder-icon {
      @apply flex h-16 w-16 items-center justify-center rounded-full bg-stone-100 text-stone-700;
    }

    .placeholder-actions {
      @apply flex flex-wrap items-center justify-center gap-3;
    }
  }

  @media (max-width: 960px) {
    height: min(92vh, calc(100vh - 24px));
    min-height: auto;

    .preview-content {
      @apply px-4 pb-4;
    }

    .preview-content .content-wrapper,
    .preview-content .content-wrapper--immersive {
      @apply grid-cols-1;
    }

    .preview-content .insight-rail {
      @apply max-h-[30vh] pr-0;
    }

    .preview-content .preview-stage {
      min-height: 58vh;
    }
  }
}
</style>
