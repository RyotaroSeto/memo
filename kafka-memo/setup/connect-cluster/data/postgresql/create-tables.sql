CREATE TABLE users (
  user_id serial NOT NULL,
  nickname varchar(50) NOT NULL,
  registered_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id)
);

CREATE TABLE contents (
  content_id serial NOT NULL,
  title varchar(100) NOT NULL,
  published_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (content_id)
);

CREATE TABLE ticket_orders (
  order_id serial NOT NULL,
  user_id integer NOT NULL,
  content_id integer NOT NULL,
  created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (order_id),
  FOREIGN KEY (user_id) REFERENCES users (user_id),
  FOREIGN KEY (content_id) REFERENCES contents (content_id)
);

