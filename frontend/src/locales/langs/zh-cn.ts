const local = {
  system: {
    title: 'SourceWeave',
    updateTitle: '系统版本更新通知',
    updateContent: '检测到系统有新版本发布，是否立即刷新页面？',
    updateConfirm: '立即刷新',
    updateCancel: '稍后再说'
  },
  common: {
    action: '操作',
    add: '新增',
    addSuccess: '添加成功',
    backToHome: '返回首页',
    batchDelete: '批量删除',
    cancel: '取消',
    close: '关闭',
    check: '勾选',
    expandColumn: '展开列',
    columnSetting: '列设置',
    config: '配置',
    confirm: '确认',
    save: '保存',
    delete: '删除',
    deleteSuccess: '删除成功',
    confirmDelete: '确认删除吗？',
    edit: '编辑',
    warning: '警告',
    error: '错误',
    fileReadFailed: '文件读取失败',
    index: '序号',
    keywordSearch: '请输入关键词搜索',
    logout: '退出登录',
    logoutConfirm: '确认退出登录吗？',
    lookForward: '敬请期待',
    modify: '修改',
    modifySuccess: '修改成功',
    noData: '无数据',
    operate: '操作',
    pleaseCheckValue: '请检查输入的值是否合法',
    refresh: '刷新',
    reset: '重置',
    search: '搜索',
    switch: '切换',
    tip: '提示',
    trigger: '触发',
    update: '更新',
    updateSuccess: '更新成功',
    userCenter: '个人中心',
    yesOrNo: {
      yes: '是',
      no: '否'
    }
  },
  request: {
    logout: '请求失败后登出用户',
    logoutMsg: '用户状态失效，请重新登录',
    logoutWithModal: '请求失败后弹出模态框再登出用户',
    logoutWithModalMsg: '用户状态失效，请重新登录',
    refreshToken: '请求的token已过期，刷新token',
    tokenExpired: 'token已过期',
    registrationClosed: '当前暂未开放注册',
    usernameExists: '用户名已存在'
  },
  theme: {
    themeSchema: {
      title: '主题模式',
      light: '亮色模式',
      dark: '暗黑模式',
      auto: '跟随系统'
    },
    grayscale: '灰色模式',
    colourWeakness: '色弱模式',
    layoutMode: {
      title: '布局模式',
      vertical: '左侧菜单模式',
      'vertical-mix': '左侧菜单混合模式',
      horizontal: '顶部菜单模式',
      'horizontal-mix': '顶部菜单混合模式',
      reverseHorizontalMix: '一级菜单与子级菜单位置反转'
    },
    recommendColor: '应用推荐算法的颜色',
    recommendColorDesc: '推荐颜色的算法参照',
    themeColor: {
      title: '主题颜色',
      primary: '主色',
      info: '信息色',
      success: '成功色',
      warning: '警告色',
      error: '错误色',
      followPrimary: '跟随主色'
    },
    scrollMode: {
      title: '滚动模式',
      wrapper: '外层滚动',
      content: '主体滚动'
    },
    page: {
      animate: '页面切换动画',
      mode: {
        title: '页面切换动画类型',
        'fade-slide': '滑动',
        fade: '淡入淡出',
        'fade-bottom': '底部消退',
        'fade-scale': '缩放消退',
        'zoom-fade': '渐变',
        'zoom-out': '闪现',
        none: '无'
      }
    },
    fixedHeaderAndTab: '固定头部和标签栏',
    header: {
      height: '头部高度',
      breadcrumb: {
        visible: '显示面包屑',
        showIcon: '显示面包屑图标'
      },
      multilingual: {
        visible: '显示多语言按钮'
      }
    },
    tab: {
      visible: '显示标签栏',
      cache: '标签栏信息缓存',
      height: '标签栏高度',
      mode: {
        title: '标签栏风格',
        chrome: '谷歌风格',
        button: '按钮风格'
      }
    },
    sider: {
      inverted: '深色侧边栏',
      width: '侧边栏宽度',
      collapsedWidth: '侧边栏折叠宽度',
      mixWidth: '混合布局侧边栏宽度',
      mixCollapsedWidth: '混合布局侧边栏折叠宽度',
      mixChildMenuWidth: '混合布局子菜单宽度'
    },
    footer: {
      visible: '显示底部',
      fixed: '固定底部',
      height: '底部高度',
      right: '底部局右'
    },
    watermark: {
      visible: '显示全屏水印',
      text: '水印文本'
    },
    themeDrawerTitle: '主题配置',
    pageFunTitle: '页面功能',
    resetCacheStrategy: {
      title: '重置缓存策略',
      close: '关闭页面',
      refresh: '刷新页面'
    },
    configOperation: {
      copyConfig: '复制配置',
      copySuccessMsg: '复制成功，请替换 src/theme/settings.ts 中的变量 themeSettings',
      resetConfig: '重置配置',
      resetSuccessMsg: '重置成功'
    }
  },
  route: {
    login: '登录',
    '403': '无权限',
    '404': '页面不存在',
    '500': '服务器错误',
    'iframe-page': '外链页面',
    chat: '聊天助手',
    'chat-history': '聊天记录',
    'knowledge-base': '知识库',
    'model-provider': '模型配置',
    'org-tag': '组织标签',
    'usage-monitor': '用量监控',
    user: '用户管理',
    'personal-center': '个人中心'
  },
  page: {
    login: {
      common: {
        loginOrRegister: '登录 / 注册',
        login: '登录账号',
        register: '注册账号',
        userNamePlaceholder: '请输入用户名',
        phonePlaceholder: '请输入手机号',
        codePlaceholder: '请输入验证码',
        passwordPlaceholder: '请输入密码',
        confirmPasswordPlaceholder: '请再次输入密码',
        codeLogin: '验证码登录',
        confirm: '确定',
        back: '返回',
        validateSuccess: '验证成功',
        loginSuccess: '登录成功',
        welcomeBack: '欢迎回来，{userName} ！'
      },
      pwdLogin: {
        title: '密码登录',
        rememberMe: '记住用户名',
        forgetPassword: '忘记密码？',
        register: '注册账号',
        otherAccountLogin: '其他账号登录',
        otherLoginMode: '其他登录方式',
        superAdmin: '超级管理员',
        admin: '管理员',
        user: '普通用户'
      },
      codeLogin: {
        title: '验证码登录',
        getCode: '获取验证码',
        reGetCode: '{time}秒后重新获取',
        sendCodeSuccess: '验证码发送成功',
        imageCodePlaceholder: '请输入图片验证码'
      },
      register: {
        title: '注册账号',
        success: '注册成功',
        agreement: '注册即代表已阅读并同意我们的',
        protocol: '《用户协议》',
        and: '和',
        policy: '《隐私权政策》',
      },
      resetPwd: {
        title: '重置密码'
      },
      bindWeChat: {
        title: '绑定微信'
      }
    },
    chat: {
      expandConversationList: '展开对话列表',
      input: {
        placeholder: '给 SourceWeave 发送消息，Enter 发送，Shift+Enter 换行',
        newlineHint: 'Shift+Enter 换行',
        connected: '已连接',
        reconnecting: '重连中',
        connecting: '连接中',
        disconnected: '未连接',
        cooldown: '{seconds} 秒后可重新发送',
        rateLimited: '当前发送受限，{cooldown}',
        rateLimitDefault: '聊天请求过于频繁',
        retryAfter: '{message}，请在 {seconds} 秒后重试',
        retryLater: '{message}，请稍后再试',
        serverBusy: '服务器繁忙，请稍后再试',
        reconnectFailed: 'WebSocket 重连失败，请检查网络或刷新页面后重试',
        authFailed: '聊天连接鉴权失败，请重新登录后再试'
      },
      sidebar: {
        title: '对话列表',
        newChat: '新对话',
        active: '活跃',
        archived: '已归档',
        emptyActive: '暂无对话记录',
        emptyArchived: '暂无归档对话',
        archiveConfirm: '归档后可在「已归档」中找回'
      },
      list: {
        time: '时间',
        startNew: '开始新对话',
        selectOrCreate: '选择或创建一个对话',
        emptyHint: '在左侧选择一个对话，或点击「新对话」开始'
      },
      message: {
        copySuccess: '已复制',
        copy: '复制回答',
        like: '点赞',
        dislike: '点踩',
        feedbackError: '反馈记录失败',
        feedbackGood: '已记录点赞反馈',
        feedbackBad: '已记录点踩反馈',
        fallbackError: '服务器繁忙，请稍后再试',
        downloadError: '文件下载失败: {fileName}',
        source: '来源#{number}',
        page: '第{number}页',
        tool: {
          searchKnowledge: '检索知识库',
          generateSummary: '生成知识摘要',
          submitFeedback: '记录反馈',
          knowledgeStats: '读取知识库统计',
          executing: '执行中',
          success: '已完成',
          failed: '失败'
        }
      },
      reference: {
        loadFailed: '引用详情加载失败',
        loading: '正在加载引用详情...',
        empty: '没有拿到可预览的引用信息'
      }
    },
    chatHistory: {
      user: '用户',
      allUsers: '全部用户',
      time: '时间'
    },
    knowledgeBase: {
      title: '文件列表',
      searchKnowledge: '检索知识库',
      column: {
        fileName: '文件名',
        fileSize: '文件大小',
        estimatedEmbedding: '预估向量化',
        actualEmbedding: '实际向量化',
        uploadStatus: '上传状态',
        orgTag: '组织标签',
        publicStatus: '是否公开',
        uploadTime: '上传时间',
        operation: '操作'
      },
      md5Copied: 'MD5已复制',
      copyMd5: '点击复制MD5',
      public: '公开',
      private: '私有',
      preview: '预览',
      deleteConfirm: '确认删除当前文件吗？',
      completed: '已完成',
      uploadInterrupted: '上传中断',
      chunks: '{count} 个切片',
      retryVectorizationSubmitted: '已提交异步向量化重试任务',
      vectorizing: '向量化处理中',
      vectorizingHint: '完成后会回写实际 Tokens',
      vectorizationCompleted: '向量化已完成',
      historicalTokensMissing: '历史数据未统计实际 Tokens，可按需重试回写',
      vectorizationFailed: '向量化失败',
      vectorizationFailedHint: '请检查 Embedding 额度或稍后重试',
      noActualVectorization: '暂无实际向量化结果',
      noActualVectorizationHint: '可能仍在处理，或历史任务未回写结果',
      retryVectorization: '重试向量化',
      resumeUpload: '续传',
      resumeFileMismatch: '两次上传的文件不一致',
      upload: {
        title: '文件上传',
        orgTag: '组织标签',
        publicStatus: '是否公开',
        file: '上传文件',
        chooseFile: '选择文件',
        sizeLimit: '当前组织限制非管理员上传文件不超过 {limit} MB，当前文件大小为 {size} MB',
        sizeLimitHint: '当前组织限制非管理员上传文件不超过 {limit} MB',
        completeEstimate: '上传完成，预计向量化消耗 {tokens} Tokens（{chunks} 个切片）',
        fileExists: '文件已存在',
        fileUploading: '文件正在上传中'
      },
      search: {
        title: '知识库检索',
        topKPlaceholder: '请输入topK',
        keyword: '关键字',
        keywordPlaceholder: '请输入关键字',
        source: '来源：{fileName}'
      }
    },
    orgTag: {
      title: '组织标签',
      name: '标签名称',
      description: '描述',
      uploadLimit: '非Admin上传上限',
      unlimited: '不限制',
      operation: '操作',
      addChild: '新增下级',
      deleteConfirm: '确认删除当前标签吗？',
      operationSuccess: '操作成功',
      dialog: {
        add: '新增',
        edit: '编辑',
        addChild: '新增下级',
        tagId: '标签Id',
        autoGenerated: '自动生成',
        namePlaceholder: '请输入标签名称',
        parent: '所属标签',
        description: '标签描述',
        descriptionPlaceholder: '请输入标签描述',
        uploadLimit: '上传上限(MB)',
        unlimitedPlaceholder: '为空表示不限制'
      }
    },
    user: {
      title: '用户列表',
      column: {
        index: '序号',
        username: '用户名',
        tags: '标签',
        status: '是否启用',
        createdAt: '创建时间',
        chatCount: '聊天次数',
        llmQuota: 'LLM额度',
        embeddingQuota: 'Embedding额度',
        operation: '操作'
      },
      enabled: '已启用',
      disabled: '已禁用',
      enable: '启用',
      disable: '禁用',
      todayMessages: '今日消息数',
      count: '{count} 次',
      quotaDisabled: '未启用',
      remainingRequests: '剩余 {remaining} · {count} 次',
      assignOrgTags: '分配组织标签',
      addToken: '追加 Token',
      search: {
        keyword: '关键词',
        keywordPlaceholder: '请输入关键词',
        orgTag: '组织标签',
        status: '启用状态',
        statusPlaceholder: '请选择启用状态'
      },
      orgDialog: {
        title: '组织标签设置',
        username: '用户名',
        orgTags: '组织标签'
      },
      tokenDialog: {
        title: '追加 Token 额度',
        username: '用户名',
        optionalPlaceholder: '不追加可留空',
        reason: '原因',
        defaultReason: '管理员手动追加',
        add: '追加',
        negative: '追加 Token 数量不能为负数',
        empty: '请至少追加一种 Token 额度',
        success: 'Token 额度已追加'
      }
    },
    personalCenter: {
      todayQuota: '今日额度 · {day}',
      unreported: '未统计',
      used: '已用 {used} / {limit}',
      remaining: '剩余 {remaining}',
      requests: '请求 {count} 次',
      quotaDisabled: '当前未启用配额',
      primaryTag: '主标签',
      records: 'Token 变动记录',
      noRecords: '暂无记录',
      setPrimary: '设置主标签',
      setPrimaryConfirm: '确定将当前标签设置为主标签吗？',
      column: {
        date: '日期',
        tokenType: 'Token 类型',
        changeType: '变动类型',
        amount: '变动数量',
        balanceBefore: '变动前余额',
        balanceAfter: '变动后余额',
        reason: '原因',
        requestCount: '请求次数',
        createdAt: '创建时间'
      },
      increase: '充值',
      consume: '消耗'
    },
    usageMonitor: {
      rateLimitUpdated: '限流配置已更新',
      rateLimitTitle: '调用限流配置',
      effectiveImmediately: '保存后立即对新请求生效',
      saveConfig: '保存配置',
      rateLimitDescription: '这里集中管理聊天消息、LLM 全网 Token 预算，以及 Embedding 上传/查询两条链路的运行时限流配置。保存后对新请求立即生效，无需修改 application.yml。',
      chatMessages: '聊天消息',
      countLimit: '次数上限',
      windowSeconds: '窗口秒数',
      llmGlobalBudget: 'LLM 全网 Token 预算',
      embeddingUploadBudget: 'Embedding 上传 Token 预算',
      embeddingQuery: 'Embedding 查询',
      minuteTokenLimit: '分钟 Token 上限',
      minuteWindowSeconds: '分钟窗口秒数',
      dayTokenLimit: '日 Token 上限',
      dayWindowSeconds: '日窗口秒数',
      perUserMinuteRequests: '单用户分钟次数',
      perUserDayRequests: '单用户日次数',
      globalMinuteToken: '全网分钟 Token',
      queryMinuteWindow: '查询分钟窗口秒数',
      globalDayToken: '全网日 Token',
      queryDayWindow: '查询日窗口秒数',
      noRateLimit: '暂无限流配置',
      overview: '用量总览',
      todayAlerts: '今日告警 {count}',
      exceeded: '超额 {count}',
      lastDays: '近{days}天',
      todayChat: '今日聊天消息',
      passedMessages: '按通过限流的消息数统计',
      todayLlmTokens: '今日 LLM Tokens',
      todayEmbeddingTokens: '今日 Embedding Tokens',
      requests: '请求 {count} 次',
      highRiskUsers: '高风险用户',
      quotaExhausted: '额度已耗尽',
      totalAlerts: '总告警数',
      includesWarnings: '含 80% 以上预警',
      trends: '调用趋势',
      overageAndWarnings: '超额与预警',
      remainingRequests: '{used} / {limit}，剩余 {remaining}，{count} 次',
      noAlerts: '暂无告警',
      todayRanking: '今日用量排行',
      rankingUsage: '{used} / {limit} · {count} 次',
      chart: {
        requests: '请求数',
        chatMessages: '聊天消息',
        llmRequests: 'LLM 请求',
        embeddingRequests: 'Embedding 请求'
      }
    },
    modelProvider: {
      title: '模型 Provider 配置',
      immediateHint: 'LLM 保存后新请求立即生效，Embedding 暂不允许危险直切',
      description: '这里管理平台代付的模型接入配置。API Key 输入为空时保留现有密钥，不会回显明文。Embedding 如果切换 active provider，后端会拦截需要重嵌入的危险变更。',
      llmRouteHint: '聊天请求会按当前 active provider 路由',
      embeddingRouteHint: '当前版本只支持配置管理；切 active provider 若需要重嵌入会被后端拦截',
      saveLlm: '保存 LLM 配置',
      saveEmbedding: '保存 Embedding 配置',
      llmUpdated: 'LLM 模型配置已更新',
      embeddingUpdated: 'Embedding 配置已更新',
      apiAddress: 'API 地址',
      model: '模型',
      dimension: '维度',
      existingKey: '现有密钥',
      notConfigured: '未配置',
      newApiKey: '新 API Key',
      keepExistingKey: '留空则保留现有值',
      testConnection: '测试连接',
      connectionSuccess: '{provider} 连接成功，耗时 {latency}ms',
      connectionFailed: '{provider} 连接失败：{message}',
      empty: '暂未加载到模型配置'
    }
  },
  component: {
    filePreview: {
      loadingTitle: '正在装载引用文档',
      loadingHint: '整理线索、页码定位和可预览内容。',
      openFailed: '这份文档暂时没能打开',
      newWindow: '新窗口',
      download: '下载',
      close: '关闭',
      overview: '概览',
      retrievalQuery: '检索问题',
      clue: '线索',
      locationClue: '定位线索',
      unsupported: '当前格式暂不支持在线预览',
      unsupportedHint: '你可以先下载文件，或在新窗口中尝试打开原始资源。',
      openNewWindow: '新窗口打开',
      downloadToView: '下载后查看',
      keywordRetrieval: '关键词召回',
      hybridRetrieval: '混合召回（语义相关 + 关键词）',
      page: '第 {number} 页',
      relatedScore: '相关分数 {score}',
      score: '分数 {score}',
      pdfReady: '文档已定位到可阅读页',
      referenceReady: '已就绪的引用文档',
      descriptionQuery: '左侧展示的是本次 RAG 检索的问题与定位线索，右侧则直接打开原始文档，方便核对答案依据。',
      descriptionEvidence: '左侧展示的是这次检索的定位线索，右侧则直接打开原始文档，方便核对答案依据。',
      descriptionAnchor: '当前预览会优先围绕这条上下文线索定位，方便你核对答案和原文是否一致。',
      descriptionDefault: '这里展示的是引用来源的原始文档内容，你可以直接浏览、下载或在新窗口中打开。',
      previewFailed: '预览失败：{message}',
      unknownError: '未知错误',
      networkError: '网络错误',
      downloadFailed: '下载失败：{message}',
      downloadStarted: '开始下载文件'
    },
    pdfViewer: {
      singlePage: '单页定位',
      preview: 'PDF 预览',
      page: '第 {current} 页',
      pageTotal: '第 {current} / {total} 页',
      fitWidth: '适应宽度',
      newWindow: '新窗口',
      loading: '正在加载 PDF 文档',
      targetPage: '引用定位页',
      matched: '已匹配到相关文本',
      browsing: '浏览当前页',
      rendering: '正在渲染页面',
      singlePageHint: '当前是定位页快照，支持缩放；整本文档请点“新窗口”查看。',
      previewHint: '支持翻页、缩放和新窗口查看原文件。',
      loadFailed: 'PDF 加载失败，请尝试新窗口打开或重新预览。',
      canvasFailed: '无法初始化 PDF 画布。',
      renderFailed: 'PDF 页面渲染失败，请稍后重试。'
    },
    orgTagCascader: {
      placeholder: '请选择组织标签'
    }
  },
  form: {
    required: '不能为空',
    userName: {
      required: '请输入用户名',
      invalid: '用户名格式不正确'
    },
    phone: {
      required: '请输入手机号',
      invalid: '手机号格式不正确'
    },
    pwd: {
      required: '请输入密码',
      invalid: '密码格式不正确，6-18位字符，必须包含字母和数字'
    },
    confirmPwd: {
      required: '请输入确认密码',
      invalid: '两次输入密码不一致'
    },
    code: {
      required: '请输入验证码',
      invalid: '验证码格式不正确'
    },
    email: {
      required: '请输入邮箱',
      invalid: '邮箱格式不正确'
    }
  },
  dropdown: {
    closeCurrent: '关闭',
    closeOther: '关闭其它',
    closeLeft: '关闭左侧',
    closeRight: '关闭右侧',
    closeAll: '关闭所有'
  },
  icon: {
    themeConfig: '主题配置',
    themeSchema: '主题模式',
    lang: '切换语言',
    fullscreen: '全屏',
    fullscreenExit: '退出全屏',
    reload: '刷新页面',
    collapse: '折叠菜单',
    expand: '展开菜单',
    pin: '固定',
    unpin: '取消固定'
  },
  datatable: {
    itemCount: '共 {total} 条'
  }
};

export default local;
