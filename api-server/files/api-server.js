const mysql = require('mysql');
const config = require('./api-server.config.js');
const http = require('http');
const url = require('url');

// Create a connection to the database
const connection = mysql.createConnection(config);

// Connect to the database
connection.connect(function (err) {
  if (err) {
    console.error('Error connecting to the database: ' + err.stack);
    return;
  }
  console.log('Connected to the database as id ' + connection.threadId);
});

// Sample endpoint - Replace with your actual API code
const server = http.createServer(function (req, res) {
  const parsedUrl = url.parse(req.url, true);

  // Check if the endpoint is '/data'
  if (parsedUrl.pathname === '/data' && req.method === 'GET') {
    connection.query('SELECT * FROM api_data', function (err, results) {
      if (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Error fetching data from database' }));
        return;
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(results)); // Send results from the database
    });
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not Found');
  }
});

server.listen(8080, () => {
  console.log('API Server is listening on port 8080');
});

