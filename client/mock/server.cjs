const jsonServer = require('json-server');
const { randomUUID } = require('crypto');

const server = jsonServer.create();
const router = jsonServer.router('mock/db.json');
const middlewares = jsonServer.defaults();

server.use(middlewares);
server.use(jsonServer.bodyParser);

server.post('/users/signup', async (req, res) => {
  await new Promise((resolve) => setTimeout(resolve, 1000));
  const { email, password } = req.body || {};

  if (!email || !password) {
    return res.status(400).json({
      message: 'email and password are required',
    });
  }

  const db = router.db;

  const exists = db.get('users').find({ email }).value();

  if (exists) {
    return res.status(409).json({
      message: 'user already exists',
    });
  }

  const newUser = {
    id: randomUUID(),
    email,
    password,
  };

  db.get('users').push(newUser).write();

  res.status(201).json(newUser);
});

server.use(router);

server.listen(3001, () => {
  console.log('JSON Server is running at http://localhost:3001');
});
