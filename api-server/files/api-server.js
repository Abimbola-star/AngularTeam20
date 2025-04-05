const mysql = require('mysql');
const config = require('./api-server.config.js');
const http = require('http');
const url = require('url');

console.log('Database Config:', config);  // Log the config object to check its content

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
  }  else if (parsedUrl.pathname === '/data' && req.method === 'POST') {
    let body = '';
    
    req.on('data', chunk => {
      body += chunk;
    });

    req.on('end', () => {
      try {
        const postData = JSON.parse(body);
        
        // Insert the new data into the database
        const query = 'INSERT INTO api_data (name, description) VALUES (?, ?)';
        connection.query(query, [postData.name, postData.description], function (err, results) {
          if (err) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Error inserting data into database' }));
            return;
          }

          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ message: 'Data inserted successfully', id: results.insertId }));
        });
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid data format' }));
      }
    });
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not Found');
  }
});

server.listen(8080, () => {
  console.log('API Server is listening on port 8080');
});

