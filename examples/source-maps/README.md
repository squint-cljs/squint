# Source maps demo

`src/app.cljs` throws on bad input. Run it with source maps and the stack
trace points at `src/app.cljs`:

```bash
npm start
```

```
Error: not a number: x
    at parse_age (.../src/app.cljs:6:14)
    at <anonymous> (.../src/app.cljs:10:27)
    ...
    at average_age (.../src/app.cljs:11:8)
```

Run `npm run start:no-maps` to compare with the compiled JavaScript locations.

Run `npm run serve` and open
`http://localhost:8137/examples/source-maps/` with DevTools open to see the
error mapped in the browser.
