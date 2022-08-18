import { nth, range } from 'clavascript/core.js'
import styles from './App.module.css';
import logo from './logo.svg';
import { createSignal } from 'solid-js';
var Counter = function () {
let vec__14 = createSignal(0);
let counter5 = nth(vec__14, 0, null);
let setCount6 = nth(vec__14, 1, null);
return <div>Count: {range(counter5()).join(" ")} <div><button onClick={function () {
return setCount6((counter5() + 1));
}}>Click me</button></div></div>;
};
var App = function () {
return <div class={styles.App}><header class={styles.header}><img src={logo} class={styles.logo} alt="logo"></img> <Counter></Counter></header></div>;
};
var default$ = App;

export { Counter, App }
export default default$
