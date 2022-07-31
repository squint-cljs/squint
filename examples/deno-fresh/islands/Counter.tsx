/** @jsx h */
import { h } from 'preact'
import { useState } from 'preact/hooks'
import { IS_BROWSER } from '$fresh/runtime.ts'
import { incCounter, decCounter } from './cherry.mjs'

export default function Counter(props: CounterProps) {
  const [count, setCount] = useState(props.start);
  return (
      <div>
      <p>{count}</p>
      <button onClick={() => setCount(incCounter(count))} disabled={!IS_BROWSER}>
      -1
    </button>
      <button onClick={() => setCount(decCounter(count))} disabled={!IS_BROWSER}>
      +1
    </button>
      </div>
  );
}
