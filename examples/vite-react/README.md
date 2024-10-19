# vite-react

An example react project including tests with vite(st) + react.

## Requirements
- [npm](https://www.npmjs.com/)
- [babashka](https://babashka.org/)

## Usage

To run this example, `run npm install` and then one of the following [babashka tasks](bb.edn):

### Development server
```bash
bb dev
```

This will start `squint watch` and `vite dev server`. The compiled files will be
generated in `public/js`.

### Tests watch

```bash
bb test:watch
```

This will start `squint watch` on tests and `vitest test watcher`. The files
will be generated in `public/test`.

### Build

```bash
bb build
```

This will generate a production ready build in `public/dist` and a bundle status
report in the root of the project `./bundle-visualization.html`.
