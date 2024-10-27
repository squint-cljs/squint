const port = import.meta.env.VITE_SQUINT_NREPL_PORT;

const ws_url = `ws://${window.location.hostname}:${port}/_nrepl`;

function nreplWebSocket () {
  return window.ws_nrepl;
}

async function evalMe(code) {
  const updatedCode = code.replace(/import\('(.+?)'\)/g, 'import(\'/@resolve-deps/$1\')');
  console.log(updatedCode);
  const res = await eval(updatedCode);
  console.log(res);
}

function handleNreplMessage(event) {
  let data = event.data;
  data = JSON.parse(data);
  const op = data.op;
  switch (op) {
  case 'eval':
    const code = data.code;
    console.log(code);
    evalMe(code);
    break;
  default: break;
  }
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
