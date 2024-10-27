const port = import.meta.env.VITE_SQUINT_NREPL_PORT;

const ws_url = `ws://${window.location.hostname}:${port}/_nrepl`;

function nreplWebSocket () {
  return window.ws_nrepl;
}

async function evalMe(code) {
  const updatedCode = code.replace(/import\('(.+?)'\)/g, 'import(\'/@resolve-deps/$1\')');
  console.log(updatedCode);
  var res;
  try {
    res = await eval(updatedCode);
  } catch (e) {
    console.log('ex', e);
    res = e;
  }
  console.log(res);
  return res;
}

async function handleNreplMessage(event) {
  let data = event.data;
  data = JSON.parse(data);
  const id = data.id;
  console.log('data', data);
  const op = data.op;
  var code, ws, res, msg;
  switch (op) {
  case 'eval':
    code = data.code;
    console.log(code);
    res = await evalMe(code);
    console.log(res);
    msg = JSON.stringify({op: 'eval', value: res, id: id});
    console.log(msg);
    ws = nreplWebSocket();
    ws.send(msg);
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
