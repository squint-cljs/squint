import {  } from './App.module.css'
import {  } from './logo.svg'
import { join as str_join } from 'squint-cljs/string.js'
import { get, nth, range } from 'squint-cljs/core.js'
import styles from './App.module.css';
import logo from './logo.svg';
import { createSignal } from 'solid-js';
var Counter = function (p__1) {
let map__23 = p__1;
let init4 = get(map__23, "init");
let vec__58 = createSignal(init4);
let counter9 = nth(vec__58, 0, null);
let setCount10 = nth(vec__58, 1, null);
return <div>Count: {str_join(" ", range(counter9()))} <div><button onClick={function () {
return setCount10((counter9() + 1));
}}>Click me</button></div></div>;
}
;
var App = function () {
return <div class={styles.App}><header class={styles.header}><img src={logo} class={styles.logo} alt="logo"></img> <Counter init={5}></Counter></header></div>;
}
;
var default$ = App
;

export { Counter, App }
export default default$
