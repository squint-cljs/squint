const port = import.meta.env.VITE_SQUINT_NREPL_PORT;

const ws_url = `ws://${window.location.hostname}:${port}/_nrepl`;

function nreplWebSocket () {
  return window.ws_nrepl;
}

function handleNreplMessage(event) {
  console.log(event);
}

if (port) {
  var ws;
  window.ws_nrepl = ws = window.ws_nrepl || new WebSocket(ws_url);
  ws.onmessage = (event) => {
    handleNreplMessage(event);
  };
  ws.onerror = (event) => {
    console.error(event);
  };
 }
