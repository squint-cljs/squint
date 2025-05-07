# AJV


[AJV](https://ajv.js.org) is a js schema validation tool. This example shows how to use
it from squint. It also demonstrates importing a library that is formatted as a CommonJS module.

```sh
nmp i
npx squint run ajv.cljs


[squint] Running ajv.cljs
Valid user: ✅ Valid null
Invalid user: ❌ Invalid [
  {
    instancePath: '',
    schemaPath: '#/required',
    keyword: 'required',
    params: { missingProperty: 'email' },
    message: "must have required property 'email'"
  }
]
```



## CommonJS Import Notes

```cljs

(ns ajv (:require ["ajv-formats" :refer [addFormats]]))

;; [squint] Running ajv.cljs
;; file:///Users/zcpowers/Documents/Projects/squint/examples/ajv/ajv.mjs:3
;; import { addFormats } from 'ajv-formats';
;;          ^^^^^^^^^^
;; SyntaxError: Named export 'addFormats' not found. The requested module 'ajv-formats' is a CommonJS module, which may not support all module.exports as named exports.
;; CommonJS modules can always be imported via the default export, for example using:
;;
;; import pkg from 'ajv-formats';
;; const { addFormats } = pkg;
```

```cljs
;; correct. note the `$default
(ns ajv (:require ["ajv-formats$default" :as addFormats))
```
