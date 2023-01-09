CREATE TABLE Customer(
    id VARCHAR(5) PRIMARY KEY,
    name VARCHAR(300) NOT NULL,
    address VARCHAR(500) NOT NULL
);

CREATE TABLE Contact(
    contact VARCHAR(15) NOT NULL UNIQUE ,
    customer_id VARCHAR(5) NOT NULL,
    CONSTRAINT PRIMARY KEY (customer_id, contact),
    CONSTRAINT FOREIGN KEY (customer_id) REFERENCES Customer(id)
);

CREATE TABLE Item(
    code VARCHAR(5) PRIMARY KEY,
    description VARCHAR(50) NOT NULL,
    qty INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);

CREATE TABLE `Order`(
    id INT AUTO_INCREMENT PRIMARY KEY,
    date DATE NOT NULL,
    customer_id VARCHAR(5) NOT NULL,
    CONSTRAINT FOREIGN KEY (customer_id) REFERENCES Customer(id)
);

CREATE TABLE OrderDetail(
    order_id INT,
    item_code VARCHAR(5),
    qty INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    CONSTRAINT PRIMARY KEY (order_id, item_code),
    CONSTRAINT FOREIGN KEY (order_id) REFERENCES `Order`(id),
    CONSTRAINT FOREIGN KEY (item_code) REFERENCES Item(code)
);

INSERT INTO Customer (id, name, address) VALUES
                    ('C001', 'Tharindu', 'Colombo'),
                    ('C002', 'Pubudu', 'Galle'),
                    ('C003', 'Hasitha', 'Matara'),
                    ('C004', 'Amiya', 'Colombo'),
                    ('C005', 'Visal', 'Panadura');

INSERT INTO Contact (contact, customer_id) VALUES
                    ('077-1234567', 'C001'),
                    ('071-1234567', 'C001'),
                    ('072-1234567', 'C002'),
                    ('078-1234567', 'C003'),
                    ('070-1234567', 'C004'),
                    ('070-4567811', 'C005');

INSERT INTO Item (code, description, qty, unit_price) VALUES
              ('I001', 'Keyboard', 5, 850.00),
              ('I002', 'Mouse', 10, 750.00),
              ('I003', 'USB Pen Drive', 20, 2500.00),
              ('I004', 'Headset', 6, 1250.00);