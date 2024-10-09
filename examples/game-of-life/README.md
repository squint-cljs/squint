# Game-of-life example

To set up a project with vite, go through the following steps:

- Create a `package.json` file

- Install dependencies:

  ```  
  $ npm install squint-cljs
  ```

- Create a `public/index.html` page (see [public/index.html](public/index.html)).

- Create a `squint.edn` to specify the source directories and to use the `.js`
  extension for outputted files

  See [squint.edn](squint.edn)

- Run `npx squint watch` to start compiling `.cljs` -> `.js`

- Run `npx vite public` to start a webserver and to hot-reload your React project!

## Babashka tasks

To run all of the above using one command, run `bb dev`. See [bb.edn](bb.edn).

## Production

To build your production website:

```
$ npx vite build public
```
