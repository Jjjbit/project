INSERT INTO global_categories (name, parent_id, type) VALUES
                                                          ('Food', NULL, 'EXPENSE'),
                                                          ('Transport', NULL, 'EXPENSE'),
                                                          ('Entertainment', NULL, 'EXPENSE'),
                                                          ('Healthy', NULL, 'EXPENSE'),
                                                          ('Education', NULL, 'EXPENSE'),
                                                          ('Shopping', NULL, 'EXPENSE'),
                                                          ('Gifts', NULL, 'EXPENSE'),
                                                          ('Electronics', NULL, 'EXPENSE'),
                                                          ('Housing', NULL, 'EXPENSE'),
                                                          ('Salary', NULL, 'INCOME'),
                                                          ('Freelance', NULL, 'INCOME'),
                                                          ('Bonus', NULL, 'INCOME');



INSERT INTO global_categories (name, parent_id, type) VALUES
                                                          ('Breakfast', 1, 'EXPENSE'),
                                                          ('Lunch', 1, 'EXPENSE'),
                                                          ('Dinner', 1, 'EXPENSE'),
                                                          ('Taxi', 2, 'EXPENSE'),
                                                          ('Bus', 2, 'EXPENSE');