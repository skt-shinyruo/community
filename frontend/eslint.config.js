import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  { ignores: ['dist/**', 'coverage/**', 'node_modules/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.node }
    },
    rules: {
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', caughtErrors: 'none' }],
      'no-useless-assignment': 'error',
      'no-useless-escape': 'error',
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
      'vue/require-default-prop': 'off',
      'vue/require-prop-types': 'off'
    }
  },
  {
    files: ['scripts/**/*.mjs', 'vite.config.js', 'eslint.config.js'],
    languageOptions: { globals: globals.node }
  },
  {
    files: ['**/*.test.js', 'src/test/**/*.js'],
    languageOptions: { globals: globals.vitest }
  },
  {
    files: ['src/views/PostDetailView.vue', 'src/views/PostsView.vue'],
    rules: { 'max-lines': ['error', 900] }
  },
  {
    files: ['src/views/DriveView.vue'],
    rules: {
      'no-restricted-imports': ['error', { paths: ['../api/services/driveService', '../stores/auth'] }]
    }
  },
  {
    files: ['src/views/drive/useDrivePageState.js'],
    rules: {
      'max-lines': ['error', 180],
      'no-restricted-imports': ['error', { paths: ['../../api/services/driveService'] }]
    }
  },
  {
    files: ['src/views/ConversationDetailView.vue'],
    rules: {
      'max-lines': ['error', 350],
      'no-restricted-imports': ['error', {
        paths: ['../api/services/imCoreChatService', '../im/imRealtimeClient', '../stores/auth']
      }]
    }
  },
  {
    files: ['src/views/useConversationDetailWorkflow.js'],
    rules: { 'max-lines': ['error', 475] }
  }
]
