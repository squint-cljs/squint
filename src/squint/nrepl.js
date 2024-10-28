const port = import.meta.env.VITE_SQUINT_NREPL_PORT;

const ws_url = `ws://${window.location.hostname}:${port}/_nrepl`;

function nreplWebSocket () {
  return window.ws_nrepl;
}

async function evalMe(code) {
  const updatedCode = code.replace(/import\('(.+?)'\)/g, 'import(\'/@resolve-deps/$1\')');
  console.log(updatedCode);
  var res, ex;
  try {
    res = await eval(updatedCode);
  } catch (e) {
    ex = e;
  }
  return [res, ex];
}

async function handleNreplMessage(event) {
  let data = event.data;
  data = JSON.parse(data);
  const id = data.id;
  const session = data.session;
  console.log('data', data);
  const op = data.op;
  var code, ws, res, ex, msg;
  switch (op) {
  case 'eval':
    code = data.code;
    console.log(code);
    [res, ex] = await evalMe(code);
    if (ex) {
      msg = JSON.stringify({op: 'eval', ex: ex.toString, id: id, session: session});
    } else {
      msg = JSON.stringify({op: 'eval', value: res?.toString(), id: id, session: session});
    }
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
