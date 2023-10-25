# Vite-react example

To set up a project with vite + react, go through the following steps:

- Install dependencies:

  ```
  $ npm install --save-dev vite
  $ npm install react-dom
  $ npm install squint-cljs
  ```

- Create a `viteconfig.js` with the React plugin

  See [viteconfig.js](viteconfig.js)

- Create a `squint.edn` to specify the source directories and to use the `.jsx`
  extension for outputted files

  See [squint.edn](squint.edn)

- Run `npx squint watch` to start compiling `.cljs` -> `.jsx`

  E.g. see [src/MyComponent.cljs](src/MyComponent.cljs):

  ``` clojure
  (ns my-component
    (:require ["react" :refer [useState]]))

  (defn MyComponent []
    (let [[state setState] (useState 0)]
      #jsx [:div "You clicked " state "times"
            [:button {:onClick #(setState (inc state))}
             "Click me"]]))
  ```

- Run `npx vite --config viteconfig.js public` to start a webserver and to hot-reload your React project!

## Babashka tasks

To run all of the above using one command, run `bb dev`. See [bb.edn](bb.edn).

## Production

To build your production website:

```
$ npx vite --config viteconfig.js build public
```
