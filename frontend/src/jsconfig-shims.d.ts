// Type shims for the `tsc -p jsconfig.checked.json` pass (checkJs).
// .vue SFCs are fully checked by `vue-tsc -p jsconfig.json`; in this JS-only
// pass they resolve to Vue's loose Component type so imports in checked .js
// files typecheck without re-checking templates.
declare module '*.vue' {
  import type { Component } from 'vue'
  const component: Component
  export default component
}
