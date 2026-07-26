const local: App.I18n.Schema = {
  system: {
    title: 'SourceWeave',
    updateTitle: 'System Version Update Notification',
    updateContent: 'A new version of the system has been detected. Do you want to refresh the page immediately?',
    updateConfirm: 'Refresh immediately',
    updateCancel: 'Later'
  },
  common: {
    action: 'Action',
    add: 'Add',
    addSuccess: 'Add Success',
    backToHome: 'Back to home',
    batchDelete: 'Batch Delete',
    cancel: 'Cancel',
    close: 'Close',
    check: 'Check',
    expandColumn: 'Expand Column',
    columnSetting: 'Column Setting',
    config: 'Config',
    confirm: 'Confirm',
    save: 'Save',
    delete: 'Delete',
    deleteSuccess: 'Delete Success',
    confirmDelete: 'Are you sure you want to delete?',
    edit: 'Edit',
    warning: 'Warning',
    error: 'Error',
    fileReadFailed: 'Failed to read the file',
    index: 'Index',
    keywordSearch: 'Please enter keyword',
    logout: 'Logout',
    logoutConfirm: 'Are you sure you want to log out?',
    lookForward: 'Coming soon',
    modify: 'Modify',
    modifySuccess: 'Modify Success',
    noData: 'No Data',
    operate: 'Operate',
    pleaseCheckValue: 'Please check whether the value is valid',
    refresh: 'Refresh',
    reset: 'Reset',
    search: 'Search',
    switch: 'Switch',
    tip: 'Tip',
    trigger: 'Trigger',
    update: 'Update',
    updateSuccess: 'Update Success',
    userCenter: 'User Center',
    yesOrNo: {
      yes: 'Yes',
      no: 'No'
    }
  },
  request: {
    logout: 'Logout user after request failed',
    logoutMsg: 'User status is invalid, please log in again',
    logoutWithModal: 'Pop up modal after request failed and then log out user',
    logoutWithModalMsg: 'User status is invalid, please log in again',
    refreshToken: 'The requested token has expired, refresh the token',
    tokenExpired: 'The requested token has expired',
    registrationClosed: 'Registration is currently closed',
    usernameExists: 'Username already exists'
  },
  theme: {
    themeSchema: {
      title: 'Theme Schema',
      light: 'Light',
      dark: 'Dark',
      auto: 'Follow System'
    },
    grayscale: 'Grayscale',
    colourWeakness: 'Colour Weakness',
    layoutMode: {
      title: 'Layout Mode',
      vertical: 'Vertical Menu Mode',
      horizontal: 'Horizontal Menu Mode',
      'vertical-mix': 'Vertical Mix Menu Mode',
      'horizontal-mix': 'Horizontal Mix menu Mode',
      reverseHorizontalMix: 'Reverse first level menus and child level menus position'
    },
    recommendColor: 'Apply Recommended Color Algorithm',
    recommendColorDesc: 'The recommended color algorithm refers to',
    themeColor: {
      title: 'Theme Color',
      primary: 'Primary',
      info: 'Info',
      success: 'Success',
      warning: 'Warning',
      error: 'Error',
      followPrimary: 'Follow Primary'
    },
    scrollMode: {
      title: 'Scroll Mode',
      wrapper: 'Wrapper',
      content: 'Content'
    },
    page: {
      animate: 'Page Animate',
      mode: {
        title: 'Page Animate Mode',
        fade: 'Fade',
        'fade-slide': 'Slide',
        'fade-bottom': 'Fade Zoom',
        'fade-scale': 'Fade Scale',
        'zoom-fade': 'Zoom Fade',
        'zoom-out': 'Zoom Out',
        none: 'None'
      }
    },
    fixedHeaderAndTab: 'Fixed Header And Tab',
    header: {
      height: 'Header Height',
      breadcrumb: {
        visible: 'Breadcrumb Visible',
        showIcon: 'Breadcrumb Icon Visible'
      },
      multilingual: {
        visible: 'Display multilingual button'
      }
    },
    tab: {
      visible: 'Tab Visible',
      cache: 'Tag Bar Info Cache',
      height: 'Tab Height',
      mode: {
        title: 'Tab Mode',
        chrome: 'Chrome',
        button: 'Button'
      }
    },
    sider: {
      inverted: 'Dark Sider',
      width: 'Sider Width',
      collapsedWidth: 'Sider Collapsed Width',
      mixWidth: 'Mix Sider Width',
      mixCollapsedWidth: 'Mix Sider Collapse Width',
      mixChildMenuWidth: 'Mix Child Menu Width'
    },
    footer: {
      visible: 'Footer Visible',
      fixed: 'Fixed Footer',
      height: 'Footer Height',
      right: 'Right Footer'
    },
    watermark: {
      visible: 'Watermark Full Screen Visible',
      text: 'Watermark Text'
    },
    themeDrawerTitle: 'Theme Configuration',
    pageFunTitle: 'Page Function',
    resetCacheStrategy: {
      title: 'Reset Cache Strategy',
      close: 'Close Page',
      refresh: 'Refresh Page'
    },
    configOperation: {
      copyConfig: 'Copy Config',
      copySuccessMsg: 'Copy Success, Please replace the variable "themeSettings" in "src/theme/settings.ts"',
      resetConfig: 'Reset Config',
      resetSuccessMsg: 'Reset Success'
    }
  },
  route: {
    login: 'Login',
    '403': 'No Permission',
    '404': 'Page Not Found',
    '500': 'Server Error',
    'iframe-page': 'Iframe',
    chat: 'Chat Assistant',
    'chat-history': 'Chat History',
    'knowledge-base': 'Knowledge Base',
    'model-provider': 'Model Providers',
    'org-tag': 'Organization Tag',
    'usage-monitor': 'Usage Monitor',
    user: 'User Management',
    'personal-center': 'Personal Center'
  },
  page: {
    login: {
      common: {
        loginOrRegister: 'Login / Register',
        login: 'Sign in',
        register: 'Sign up',
        userNamePlaceholder: 'Please enter user name',
        phonePlaceholder: 'Please enter phone number',
        codePlaceholder: 'Please enter verification code',
        passwordPlaceholder: 'Please enter password',
        confirmPasswordPlaceholder: 'Please enter password again',
        codeLogin: 'Verification code login',
        confirm: 'Confirm',
        back: 'Back',
        validateSuccess: 'Verification passed',
        loginSuccess: 'Login successfully',
        welcomeBack: 'Welcome back, {userName} !'
      },
      pwdLogin: {
        title: 'Password Login',
        rememberMe: 'Remember username and password',
        forgetPassword: 'Forget password?',
        register: 'Register',
        otherAccountLogin: 'Other Account Login',
        otherLoginMode: 'Other Login Mode',
        superAdmin: 'Super Admin',
        admin: 'Admin',
        user: 'User'
      },
      codeLogin: {
        title: 'Verification Code Login',
        getCode: 'Get verification code',
        reGetCode: 'Reacquire after {time}s',
        sendCodeSuccess: 'Verification code sent successfully',
        imageCodePlaceholder: 'Please enter image verification code'
      },
      register: {
        title: 'Register',
        success: 'Registration successful',
        agreement: 'By registering, you agree to our',
        protocol: '《User Agreement》',
        and: 'and',
        policy: '《Privacy Policy》',
      },
      resetPwd: {
        title: 'Reset Password'
      },
      bindWeChat: {
        title: 'Bind WeChat'
      }
    },
    chat: {
      expandConversationList: 'Expand conversation list',
      input: {
        placeholder: 'Message SourceWeave. Enter to send, Shift+Enter for a new line',
        newlineHint: 'Shift+Enter for a new line',
        connected: 'Connected',
        reconnecting: 'Reconnecting',
        connecting: 'Connecting',
        disconnected: 'Disconnected',
        cooldown: 'Send again in {seconds}s',
        rateLimited: 'Sending is temporarily limited. {cooldown}',
        rateLimitDefault: 'Chat requests are too frequent',
        retryAfter: '{message}. Try again in {seconds}s',
        retryLater: '{message}. Please try again later',
        serverBusy: 'The server is busy. Please try again later',
        reconnectFailed: 'WebSocket reconnection failed. Check your network or refresh the page and try again.',
        authFailed: 'Chat connection authentication failed. Please sign in again.'
      },
      sidebar: {
        title: 'Conversations',
        newChat: 'New chat',
        active: 'Active',
        archived: 'Archived',
        emptyActive: 'No conversations yet',
        emptyArchived: 'No archived conversations',
        archiveConfirm: 'You can restore it later from Archived'
      },
      list: {
        time: 'Time',
        startNew: 'Start a new conversation',
        selectOrCreate: 'Select or create a conversation',
        emptyHint: 'Select a conversation on the left, or click New chat to begin'
      },
      message: {
        copySuccess: 'Copied',
        copy: 'Copy answer',
        like: 'Like',
        dislike: 'Dislike',
        feedbackError: 'Failed to record feedback',
        feedbackGood: 'Like feedback recorded',
        feedbackBad: 'Dislike feedback recorded',
        fallbackError: 'The server is busy. Please try again later',
        downloadError: 'Failed to download {fileName}',
        source: 'Source #{number}',
        page: 'Page {number}',
        tool: {
          searchKnowledge: 'Search knowledge base',
          generateSummary: 'Generate knowledge summary',
          submitFeedback: 'Record feedback',
          knowledgeStats: 'Read knowledge-base statistics',
          executing: 'Running',
          success: 'Completed',
          failed: 'Failed'
        }
      },
      reference: {
        loadFailed: 'Failed to load reference details',
        loading: 'Loading reference details...',
        empty: 'No previewable reference information was found'
      }
    },
    chatHistory: {
      user: 'User',
      allUsers: 'All users',
      time: 'Time'
    },
    knowledgeBase: {
      title: 'Files',
      searchKnowledge: 'Search knowledge base',
      column: {
        fileName: 'File name',
        fileSize: 'File size',
        estimatedEmbedding: 'Estimated embedding',
        actualEmbedding: 'Actual embedding',
        uploadStatus: 'Upload status',
        orgTag: 'Organization tag',
        publicStatus: 'Visibility',
        uploadTime: 'Uploaded at',
        operation: 'Actions'
      },
      md5Copied: 'MD5 copied',
      copyMd5: 'Click to copy MD5',
      public: 'Public',
      private: 'Private',
      preview: 'Preview',
      deleteConfirm: 'Delete this file?',
      completed: 'Completed',
      uploadInterrupted: 'Upload interrupted',
      chunks: '{count} chunks',
      retryVectorizationSubmitted: 'The vectorization retry task was submitted',
      vectorizing: 'Vectorizing',
      vectorizingHint: 'Actual token usage will be recorded when processing completes',
      vectorizationCompleted: 'Vectorization completed',
      historicalTokensMissing: 'Actual token usage is unavailable for this historical task; retry to backfill it',
      vectorizationFailed: 'Vectorization failed',
      vectorizationFailedHint: 'Check the Embedding quota or try again later',
      noActualVectorization: 'No actual vectorization result',
      noActualVectorizationHint: 'Processing may still be running, or a historical task did not write back its result',
      retryVectorization: 'Retry vectorization',
      resumeUpload: 'Resume',
      resumeFileMismatch: 'The selected file does not match the original upload',
      upload: {
        title: 'Upload file',
        orgTag: 'Organization tag',
        publicStatus: 'Visibility',
        file: 'File',
        chooseFile: 'Choose file',
        sizeLimit: 'This organization limits non-admin uploads to {limit} MB; the selected file is {size} MB',
        sizeLimitHint: 'This organization limits non-admin uploads to {limit} MB',
        completeEstimate: 'Upload complete. Estimated vectorization usage: {tokens} Tokens across {chunks} chunks',
        fileExists: 'The file already exists',
        fileUploading: 'The file is already uploading'
      },
      search: {
        title: 'Search knowledge base',
        topKPlaceholder: 'Enter topK',
        keyword: 'Keyword',
        keywordPlaceholder: 'Enter a keyword',
        source: 'Source: {fileName}'
      }
    },
    orgTag: {
      title: 'Organization tags',
      name: 'Tag name',
      description: 'Description',
      uploadLimit: 'Non-admin upload limit',
      unlimited: 'Unlimited',
      operation: 'Actions',
      addChild: 'Add child',
      deleteConfirm: 'Delete this tag?',
      operationSuccess: 'Operation successful',
      dialog: {
        add: 'Add',
        edit: 'Edit',
        addChild: 'Add child',
        tagId: 'Tag ID',
        autoGenerated: 'Generated automatically',
        namePlaceholder: 'Enter a tag name',
        parent: 'Parent tag',
        description: 'Tag description',
        descriptionPlaceholder: 'Enter a tag description',
        uploadLimit: 'Upload limit (MB)',
        unlimitedPlaceholder: 'Leave empty for no limit'
      }
    },
    user: {
      title: 'Users',
      column: {
        index: 'Index',
        username: 'Username',
        tags: 'Tags',
        status: 'Status',
        createdAt: 'Created at',
        chatCount: 'Chat requests',
        llmQuota: 'LLM quota',
        embeddingQuota: 'Embedding quota',
        operation: 'Actions'
      },
      enabled: 'Enabled',
      disabled: 'Disabled',
      enable: 'Enable',
      disable: 'Disable',
      todayMessages: "Today's messages",
      count: '{count} requests',
      quotaDisabled: 'Disabled',
      remainingRequests: '{remaining} remaining · {count} requests',
      assignOrgTags: 'Assign organization tags',
      addToken: 'Add Tokens',
      search: {
        keyword: 'Keyword',
        keywordPlaceholder: 'Enter a keyword',
        orgTag: 'Organization tag',
        status: 'Status',
        statusPlaceholder: 'Select a status'
      },
      orgDialog: {
        title: 'Organization tag settings',
        username: 'Username',
        orgTags: 'Organization tags'
      },
      tokenDialog: {
        title: 'Add Token quota',
        username: 'Username',
        optionalPlaceholder: 'Leave empty to skip',
        reason: 'Reason',
        defaultReason: 'Manual administrator adjustment',
        add: 'Add',
        negative: 'The Token amount cannot be negative',
        empty: 'Add at least one Token quota',
        success: 'Token quota added'
      }
    },
    personalCenter: {
      todayQuota: "Today's quota · {day}",
      unreported: 'Not reported',
      used: 'Used {used} / {limit}',
      remaining: '{remaining} remaining',
      requests: '{count} requests',
      quotaDisabled: 'Quota is currently disabled',
      primaryTag: 'Primary',
      records: 'Token activity',
      noRecords: 'No records',
      setPrimary: 'Set primary tag',
      setPrimaryConfirm: 'Set this tag as the primary tag?',
      column: {
        date: 'Date',
        tokenType: 'Token type',
        changeType: 'Change type',
        amount: 'Amount',
        balanceBefore: 'Balance before',
        balanceAfter: 'Balance after',
        reason: 'Reason',
        requestCount: 'Request count',
        createdAt: 'Created at'
      },
      increase: 'Added',
      consume: 'Consumed'
    },
    usageMonitor: {
      rateLimitUpdated: 'Rate-limit settings updated',
      rateLimitTitle: 'Runtime rate limits',
      effectiveImmediately: 'Changes apply immediately to new requests',
      saveConfig: 'Save settings',
      rateLimitDescription: 'Manage runtime limits for chat messages, global LLM Token budgets, and Embedding upload and query paths. Changes apply to new requests immediately without editing application.yml.',
      chatMessages: 'Chat messages',
      countLimit: 'Request limit',
      windowSeconds: 'Window (seconds)',
      llmGlobalBudget: 'Global LLM Token budget',
      embeddingUploadBudget: 'Embedding upload Token budget',
      embeddingQuery: 'Embedding queries',
      minuteTokenLimit: 'Per-minute Token limit',
      minuteWindowSeconds: 'Minute window (seconds)',
      dayTokenLimit: 'Daily Token limit',
      dayWindowSeconds: 'Daily window (seconds)',
      perUserMinuteRequests: 'Per-user minute requests',
      perUserDayRequests: 'Per-user daily requests',
      globalMinuteToken: 'Global minute Tokens',
      queryMinuteWindow: 'Query minute window (seconds)',
      globalDayToken: 'Global daily Tokens',
      queryDayWindow: 'Query daily window (seconds)',
      noRateLimit: 'No rate-limit settings',
      overview: 'Usage overview',
      todayAlerts: "Today's alerts {count}",
      exceeded: 'Exceeded {count}',
      lastDays: 'Last {days} days',
      todayChat: "Today's chat messages",
      passedMessages: 'Messages that passed rate limiting',
      todayLlmTokens: "Today's LLM Tokens",
      todayEmbeddingTokens: "Today's Embedding Tokens",
      requests: '{count} requests',
      highRiskUsers: 'High-risk users',
      quotaExhausted: 'Quota exhausted',
      totalAlerts: 'Total alerts',
      includesWarnings: 'Includes usage at or above 80%',
      trends: 'Usage trends',
      overageAndWarnings: 'Overages and warnings',
      remainingRequests: '{used} / {limit}, {remaining} remaining, {count} requests',
      noAlerts: 'No alerts',
      todayRanking: "Today's usage ranking",
      rankingUsage: '{used} / {limit} · {count} requests',
      chart: {
        requests: 'Requests',
        chatMessages: 'Chat Messages',
        llmRequests: 'LLM Requests',
        embeddingRequests: 'Embedding Requests'
      }
    },
    modelProvider: {
      title: 'Model Provider settings',
      immediateHint: 'LLM changes apply to new requests immediately; unsafe direct Embedding switches remain blocked',
      description: 'Manage platform-funded model connections. Leaving API Key empty preserves the current secret; plaintext keys are never displayed. The backend blocks active Embedding provider changes that require re-embedding.',
      llmRouteHint: 'Chat requests use the currently active provider',
      embeddingRouteHint: 'This version manages configuration only; the backend blocks active-provider changes that require re-embedding',
      saveLlm: 'Save LLM settings',
      saveEmbedding: 'Save Embedding settings',
      llmUpdated: 'LLM model settings updated',
      embeddingUpdated: 'Embedding settings updated',
      apiAddress: 'API URL',
      model: 'Model',
      dimension: 'Dimension',
      existingKey: 'Current key',
      notConfigured: 'Not configured',
      newApiKey: 'New API Key',
      keepExistingKey: 'Leave empty to keep the current value',
      testConnection: 'Test connection',
      connectionSuccess: '{provider} connected successfully in {latency}ms',
      connectionFailed: '{provider} connection failed: {message}',
      empty: 'No model settings were loaded'
    }
  },
  component: {
    filePreview: {
      loadingTitle: 'Loading reference document',
      loadingHint: 'Preparing evidence, page location, and preview content.',
      openFailed: 'This document could not be opened',
      newWindow: 'New window',
      download: 'Download',
      close: 'Close',
      overview: 'Overview',
      retrievalQuery: 'Retrieval query',
      clue: 'Evidence',
      locationClue: 'Location clue',
      unsupported: 'Online preview is not available for this format',
      unsupportedHint: 'Download the file or try opening the original resource in a new window.',
      openNewWindow: 'Open in new window',
      downloadToView: 'Download to view',
      keywordRetrieval: 'Keyword retrieval',
      hybridRetrieval: 'Hybrid retrieval (semantic + keyword)',
      page: 'Page {number}',
      relatedScore: 'Relevance score {score}',
      score: 'Score {score}',
      pdfReady: 'Document opened at the relevant page',
      referenceReady: 'Reference document ready',
      descriptionQuery: 'The retrieval query and location clues appear on the left, with the original document on the right for source verification.',
      descriptionEvidence: 'The retrieval clues appear on the left, with the original document on the right for source verification.',
      descriptionAnchor: 'The preview prioritizes this context clue so you can compare the answer with the source.',
      descriptionDefault: 'This is the original referenced document. You can browse, download, or open it in a new window.',
      previewFailed: 'Preview failed: {message}',
      unknownError: 'Unknown error',
      networkError: 'Network error',
      downloadFailed: 'Download failed: {message}',
      downloadStarted: 'File download started'
    },
    pdfViewer: {
      singlePage: 'Located page',
      preview: 'PDF preview',
      page: 'Page {current}',
      pageTotal: 'Page {current} / {total}',
      fitWidth: 'Fit width',
      newWindow: 'New window',
      loading: 'Loading PDF document',
      targetPage: 'Referenced page',
      matched: 'Related text matched',
      browsing: 'Browsing current page',
      rendering: 'Rendering page',
      singlePageHint: 'This is a snapshot of the referenced page. Zoom here or open the full document in a new window.',
      previewHint: 'Turn pages, zoom, or open the original file in a new window.',
      loadFailed: 'The PDF could not be loaded. Try opening it in a new window or previewing it again.',
      canvasFailed: 'The PDF canvas could not be initialized.',
      renderFailed: 'The PDF page could not be rendered. Please try again later.'
    },
    orgTagCascader: {
      placeholder: 'Select organization tags'
    }
  },
  form: {
    required: 'Cannot be empty',
    userName: {
      required: 'Please enter user name',
      invalid: 'User name format is incorrect'
    },
    phone: {
      required: 'Please enter phone number',
      invalid: 'Phone number format is incorrect'
    },
    pwd: {
      required: 'Please enter password',
      invalid: '6-18 characters and must include letters and numbers'
    },
    confirmPwd: {
      required: 'Please enter password again',
      invalid: 'The two passwords are inconsistent'
    },
    code: {
      required: 'Please enter verification code',
      invalid: 'Verification code format is incorrect'
    },
    email: {
      required: 'Please enter email',
      invalid: 'Email format is incorrect'
    }
  },
  dropdown: {
    closeCurrent: 'Close Current',
    closeOther: 'Close Other',
    closeLeft: 'Close Left',
    closeRight: 'Close Right',
    closeAll: 'Close All'
  },
  icon: {
    themeConfig: 'Theme Configuration',
    themeSchema: 'Theme Schema',
    lang: 'Switch Language',
    fullscreen: 'Fullscreen',
    fullscreenExit: 'Exit Fullscreen',
    reload: 'Reload Page',
    collapse: 'Collapse Menu',
    expand: 'Expand Menu',
    pin: 'Pin',
    unpin: 'Unpin'
  },
  datatable: {
    itemCount: 'Total {total} items'
  }
};

export default local;
