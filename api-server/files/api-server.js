const mysql = require('mysql');
const express = require('express');
const app = express();
const port = 3000;

// Create a connection to the MariaDB database
const db = mysql.createConnection({
  host: 'localhost',  // Database is on the same server
  user: '{{ api_db_user }}',  // From defaults/main.yml
  password: '{{ api_db_password }}',  // From defaults/main.yml
  database: '{{ api_db_name }}'  // From defaults/main.yml
});

db.connect(function(err) {
  if (err) {
    console.error('Could not connect to database: ' + err.stack);
    return;
  }
  console.log('Connected to the database');
});

// Example API route to fetch data from the database
app.get('/api/data', (req, res) => {
  db.query('SELECT * FROM your_table', function (err, results) {
    if (err) {
      res.status(500).send('Error fetching data');
    } else {
      res.json(results);
    }
  });
});

// Start the server
app.listen(port, () => {
  console.log(`API server listening at http://localhost:${port}`);
});

