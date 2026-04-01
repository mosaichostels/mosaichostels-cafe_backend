// MongoDB Initialization Script
// Run this script on requirement to insert config data
// Usage: mongo hostel_ordering mongodb-init.js

// Insert Categories (skip if already exists)
db.categories.findOne({ name: "Parathas" }) || db.categories.insertOne({ name: "Parathas", showOrder: 1 });
db.categories.findOne({ name: "Maggie" }) || db.categories.insertOne({ name: "Maggie", showOrder: 2 });
db.categories.findOne({ name: "Breakfast" }) || db.categories.insertOne({ name: "Breakfast", showOrder: 3 });
db.categories.findOne({ name: "Sandwiches" }) || db.categories.insertOne({ name: "Sandwiches", showOrder: 4 });
db.categories.findOne({ name: "Main Course" }) || db.categories.insertOne({ name: "Main Course", showOrder: 5 });
db.categories.findOne({ name: "Rice & Indian Breads" }) || db.categories.insertOne({ name: "Rice & Indian Breads", showOrder: 6 });
db.categories.findOne({ name: "Drinks" }) || db.categories.insertOne({ name: "Drinks", showOrder: 7 });
db.categories.findOne({ name: "Toiletries" }) || db.categories.insertOne({ name: "Toiletries", showOrder: 8 });
db.categories.findOne({ name: "Beverages" }) || db.categories.insertOne({ name: "Beverages", showOrder: 9 });

print("Categories seeded successfully.");

// Insert Dormitories (skip if already exists)
db.dormitories.findOne({ name: "8 - Bed Mixed Dorm" }) || db.dormitories.insertOne({ name: "8 - Bed Mixed Dorm" });
db.dormitories.findOne({ name: "6 - Bed Mixed Dorm" }) || db.dormitories.insertOne({ name: "6 - Bed Mixed Dorm" });
db.dormitories.findOne({ name: "6 - Bed Female Dorm" }) || db.dormitories.insertOne({ name: "6 - Bed Female Dorm" });
db.dormitories.findOne({ name: "4 - Bed Mixed Dorm" }) || db.dormitories.insertOne({ name: "4 - Bed Mixed Dorm" });
db.dormitories.findOne({ name: "101 - Private Room" }) || db.dormitories.insertOne({ name: "101 - Private Room" });
db.dormitories.findOne({ name: "201 - Private Room" }) || db.dormitories.insertOne({ name: "201 - Private Room" });

print("Dormitories seeded successfully.");

// Insert Order Statuses (skip if already exists)
db.order_status_config.findOne({ value: "ORDERED" }) || db.order_status_config.insertOne({ value: "ORDERED", label: "Ordered", color: "preparing", locked: true });
db.order_status_config.findOne({ value: "DELIVERED" }) || db.order_status_config.insertOne({ value: "DELIVERED", label: "Delivered", color: "delivered", locked: true });
db.order_status_config.findOne({ value: "CANCELLED" }) || db.order_status_config.insertOne({ value: "CANCELLED", label: "Cancelled", color: "cancelled", locked: true });

print("Order statuses seeded successfully.");
print("MongoDB initialization complete.");
