import unusedImports from 'eslint-plugin-unused-imports';
import importX from 'eslint-plugin-import-x';
import tsParser from '@typescript-eslint/parser';

export default [
  { ignores: ['dist', 'src/components/ui'] },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { parser: tsParser },
    plugins: { 'unused-imports': unusedImports, 'import-x': importX },
    settings: {
      'import-x/resolver': { typescript: { project: './tsconfig.app.json' } },
    },
    rules: {
      'no-unused-vars': 'off',
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        { vars: 'all', varsIgnorePattern: '^_', args: 'after-used', argsIgnorePattern: '^_' }
      ],
      'import-x/no-cycle': ['error', { ignoreExternal: true }],
    },
  },
];
