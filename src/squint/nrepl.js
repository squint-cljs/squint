function nreplWebSocket () {
  return window.ws_nrepl;
}

let ws = nreplWebsocket();

function handleNreplMessage(event) {
  console.log(event);
}

if (ws) {
  ws.onmessage = (event) => {
    handleNreplMessage(event);
  }
  ws.onerror = (event) => {
    console.error(event);
  }
}
