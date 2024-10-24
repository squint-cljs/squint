function nreplWebSocket () {
  return window.ws_nrepl;
}

let ws = nreplWebSocket();

function handleNreplMessage(event) {
  console.log(event);
}

if (ws) {
  ws.onmessage = (event) => {
    handleNreplMessage(event);
  };
  ws.onerror = (event) => {
    console.error(event);
  };
}

const port = import.meta.env.VITE_SQUINT_NREPL_PORT;

console.log(port);

const ws_url = `ws://${window.location.hostname}:${port}/_nrepl`;
