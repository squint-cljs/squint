# vite-react

Small experiment with squint + vite(st) + react

## Requirements
- [npm](https://www.npmjs.com/)
- [babashka](https://babashka.org/)

## Usage
To run this example, `run npm install` and then one of the following [babashka tasks](bb.edn):

### Development server
```bash
bb dev
```
Will start squint watch and vite dev server, the files will be 
generated on `public/js`.

### Tests watch
```bash
bb test:watch
```
Will start squint watch on tests and vitest test watcher, the files will be 
generated on `public/test`.

### Build
```bash
bb build
```
Will generate an production ready build on `public/dist` and a bundle status 
report in the root of the project `./bundle-visualization.html`.

## From scratch

To set up a project with vite + react from scratch, go through the following steps:

- Create a `package.json` file

- Install dependencies:

  ```
  $ npm install --save-dev vite @vitejs/plugin-react
  $ npm install react react-dom
  $ npm install squint-cljs
  ```

- Create a `public/index.html` page (see [public/index.html](public/index.html)).

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

  And [src/index.cljs](src/index.cljs):

  ```clojure
  (ns index
    (:require
     [MyComponent :as MyComponent]
     ["react-dom/client" :as rdom]))

  (def root (rdom/createRoot (js/document.getElementById "app")))
  (.render root #jsx [MyComponent/MyComponent])
  ```

- Run `npx vite --config viteconfig.js public` to start a webserver and to hot-reload your React project!
- Run `npx vite --config viteconfig.js build public` to build your production website.
