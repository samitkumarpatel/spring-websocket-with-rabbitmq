-- User table
CREATE TABLE IF NOT EXISTS users (
       id SERIAL PRIMARY KEY,
       username VARCHAR(50) NOT NULL UNIQUE,
       password VARCHAR(100) NOT NULL,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Group table
CREATE TABLE IF NOT EXISTS groups (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL UNIQUE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Group membership table (many-to-many relationship between users and groups)
CREATE TABLE IF NOT EXISTS group_memberships (
       user_id INT NOT NULL,
       group_id INT NOT NULL,
       joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (user_id, group_id),
       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
       FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

-- Messages table
CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    from_s TEXT NOT NULL,
    to_r TEXT,
    text TEXT NOT NULL,
    date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(from_s) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY(to_r) REFERENCES users(username) ON DELETE CASCADE
);

-- CREATE TABLE IF NOT EXISTS messages (
--       id SERIAL PRIMARY KEY,
--       sender_id INT NOT NULL,
--       receiver_id INT,
--       content TEXT NOT NULL,
--       sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--       FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
--       FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
-- );

-- Chat history table
-- CREATE TABLE IF NOT EXISTS chat_history (
--       id SERIAL PRIMARY KEY,
--       message_id INT NOT NULL,
--       group_id INT,
--       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--       FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
--       FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
-- );
