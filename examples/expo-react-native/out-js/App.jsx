import * as squint_core from 'squint-cljs/core.js';
import { Text, View, StyleSheet, StatusBar } from 'react-native';
import * as MyComponent from './MyComponent.jsx';
var styles = StyleSheet.create(({ "container": ({ "flex": 1, "backgroundColor": "#fff", "alignItems": "center", "justifyContent": "center" }) }));
var App = (function () {
return <View style={styles.container}><Text>Open up App.js to start working on your app</Text><MyComponent.MyComponent></MyComponent.MyComponent><StatusBar style="auto"></StatusBar></View>;
});
var default$ = App;

export { styles, App }
export default default$
