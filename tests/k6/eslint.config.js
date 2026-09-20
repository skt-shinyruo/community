import js from '@eslint/js'
import globals from 'globals'

export default [
  { ignores: ['node_modules/**', 'temp/**'] },
  js.configs.recommended,
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.node, __ENV: 'readonly', __VU: 'readonly', __ITER: 'readonly' }
    },
    rules: {
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', caughtErrors: 'none', ignoreRestSiblings: true }]
    }
  },
  {
    files: ['tests/**/*.mjs'],
    languageOptions: {
      globals: { ...globals.node }
    }
  }
]
