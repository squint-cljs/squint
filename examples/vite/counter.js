import { myCoolFn } from './cherry.mjs'

export function setupCounter(element) {
  let counter = 0
  const setCounter = (count) => {
    counter = count
    element.innerHTML = `Click! ${myCoolFn(counter)}`
  }
  element.addEventListener('click', () => setCounter(++counter))
  setCounter(0)
}
