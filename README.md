plugin-socket.io
================

socket.io plugin for PhoneGap/Cordova.
Currently Android only.

```
cordova plugin add https://github.com/wf9a5m75/plugin-socket.io
```

This plugin uses these libraries:
- https://github.com/nkzawa/socket.io-client.java
- https://github.com/nkzawa/engine.io-client.java

###Usage

```js
var socket = plugin.socket.io.connect("http://yourhost.com:12345");
socket.on("connect", function() {
  alert("connected");
  
  socket.emit("hi", "My name is Hogehoge", function(res) {
    alert(JSON.stringify(res));
  });
});

socket.one("welcome", function(msg){
  alert(msg);
});

socket.on("error", function(err) {
  alert(err);
});
```

###Note
This plugin works with Socket.io >= 1.0

Sending binary is not available.
