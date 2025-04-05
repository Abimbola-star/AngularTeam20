const mysql = require('mysql');
const config = require('./api-server.config.js');

// Create a connection to the database
const connection = mysql.createConnection(config);

// Connect to the database
connection.connect(function(err) {
  if (err) {
    console.error('error connecting to the database: ' + err.stack);
    return;
  }
  console.log('connected to the database as id ' + connection.threadId);
});

// Sample endpoint - replace with your actual API code
const http = require('http');
const server = http.createServer(function(req, res) {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('API Server is running!\n');
});

server.listen(8080, () => {
  console.log('API Server is listening on port 8080');
});
