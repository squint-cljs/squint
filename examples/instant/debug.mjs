import { init, tx, id } from '@instantdb/core';
var APP_ID=process.env.VITE_APP_ID;
var db = init(({ "appId": APP_ID }));
var add_name = function (n) {
  const v1 = (db.transact(tx.names[id()].update({name: 'dude'})));
  return console.log("v", v1);
};

add_name("hello");
db.subscribeQuery({ names: {} }, (resp) => {
  if (resp.error) {
    console.log('error');
    renderError(resp.error.message); // Pro-tip: Check you have the right appId!
    return;
  }
  if (resp.data) {
    render(resp.data);
  }
});
