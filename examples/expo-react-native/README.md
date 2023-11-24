# Expo React Native example

## Quick start 

To set up a project with Expo and React Native, go through the following steps:

- Install dependencies:

  ``` bash
  $ npm i
  ```

- Run `bb dev` to start expo dev server and squint watcher to hot-reload yor project

## Steps to reproduce by yourself

If you want to setup project from scratch:

- Initialize a new Expo app 

  ``` bash
  $ npx create-expo-app expo-react-native
  ```

- Create a `squint.edn` to specify the source directories, output directory for compiled
  js-files and to use the `.jsx` extension for outputted files

  See [squint.edn](squint.edn)
  
- Change the contents of the App.js file to:

  ``` js
  import App from './out-js/App';
  export default App;
  ```

 ​This is necessary so that the expo can see your app entry point.

- Run expo dev server

  ``` bash
  $ npx expo start
  ```

- Run squin watcher

  ``` bash
  $ npx squint watch
  ```

## Happy hacking
