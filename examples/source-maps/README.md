# Source maps demo

Run `npm start` to show source locations in the error stack trace:

```bash
npm start
```

```text
Error: not a number: x
    at parse_age (.../src/app.cljs:6:14)
    at <anonymous> (.../src/app.cljs:10:27)
    ...
    at average_age (.../src/app.cljs:11:8)
```

Run `npm run start:no-maps` to compare with the compiled JavaScript locations.

Run `npm run serve`.
Open `http://localhost:8137/examples/source-maps/` in your browser.
Open DevTools to view the error stack trace.
