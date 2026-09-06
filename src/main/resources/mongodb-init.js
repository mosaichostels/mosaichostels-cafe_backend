// MongoDB Initialization Script
// Run this script on requirement to insert config data
// Usage: mongo hostel_ordering mongodb-init.js


// Insert Categories (skip if already exists)
db.categories.findOne({ name: "Parathas" }) || db.categories.insertOne({ name: "Parathas", showOrder: 1, type: "MENU" });
db.categories.findOne({ name: "Maggie" }) || db.categories.insertOne({ name: "Maggie", showOrder: 2, type: "MENU" });
db.categories.findOne({ name: "Breakfast" }) || db.categories.insertOne({ name: "Breakfast", showOrder: 3, type: "MENU" });
db.categories.findOne({ name: "Sandwiches" }) || db.categories.insertOne({ name: "Sandwiches", showOrder: 4, type: "MENU" });
db.categories.findOne({ name: "Main Course" }) || db.categories.insertOne({ name: "Main Course", showOrder: 5, type: "MENU" });
db.categories.findOne({ name: "Rice & Indian Breads" }) || db.categories.insertOne({ name: "Rice & Indian Breads", showOrder: 6, type: "MENU" });
db.categories.findOne({ name: "Drinks" }) || db.categories.insertOne({ name: "Drinks", showOrder: 7, type: "MENU" });
db.categories.findOne({ name: "Toiletries" }) || db.categories.insertOne({ name: "Toiletries", showOrder: 1, type: "ESSENTIAL" });
db.categories.findOne({ name: "Beverages" }) || db.categories.insertOne({ name: "Beverages", showOrder: 2, type: "ESSENTIAL" });

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
db.order_status_config.findOne({ value: "ORDERED" }) || db.order_status_config.insertOne({ value: "ORDERED", label: "Ordered", color: "#f59e0b", locked: true });
db.order_status_config.findOne({ value: "DELIVERED" }) || db.order_status_config.insertOne({ value: "DELIVERED", label: "Delivered", color: "#10b981", locked: true });
db.order_status_config.findOne({ value: "CANCELLED" }) || db.order_status_config.insertOne({ value: "CANCELLED", label: "Cancelled", color: "#ef4444", locked: true });
db.order_status_config.findOne({ value: "CHECKED" }) || db.order_status_config.insertOne({ value: "CHECKED", label: "Checked", color: "#6366f1", locked: true });

print("Order statuses seeded successfully.");

// Insert Menu Items (skip if already exists)
db.menu_items.findOne({ name: "Aloo Paratha" }) || db.menu_items.insertOne({ name: "Aloo Paratha", price: 80, category: "Parathas", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Onion Paratha" }) || db.menu_items.insertOne({ name: "Onion Paratha", price: 90, category: "Parathas", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Paneer Paratha" }) || db.menu_items.insertOne({ name: "Paneer Paratha", price: 100, category: "Parathas", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Mix Paratha" }) || db.menu_items.insertOne({ name: "Mix Paratha", price: 100, category: "Parathas", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Paratha" }) || db.menu_items.insertOne({ name: "Egg Paratha", price: 100, category: "Parathas", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Plain Maggi" }) || db.menu_items.insertOne({ name: "Plain Maggi", price: 50, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Butter Maggi" }) || db.menu_items.insertOne({ name: "Butter Maggi", price: 60, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Mayo Maggi" }) || db.menu_items.insertOne({ name: "Mayo Maggi", price: 80, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Veg Maggi" }) || db.menu_items.insertOne({ name: "Veg Maggi", price: 80, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Veg Cheese Maggi" }) || db.menu_items.insertOne({ name: "Veg Cheese Maggi", price: 90, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Maggi" }) || db.menu_items.insertOne({ name: "Egg Maggi", price: 90, category: "Maggie", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Oats Plain" }) || db.menu_items.insertOne({ name: "Oats Plain", price: 60, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Banana Honey Oats" }) || db.menu_items.insertOne({ name: "Banana Honey Oats", price: 80, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Poha" }) || db.menu_items.insertOne({ name: "Poha", price: 60, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Muesli" }) || db.menu_items.insertOne({ name: "Muesli", price: 120, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Boiled" }) || db.menu_items.insertOne({ name: "Egg Boiled", price: 40, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Fried" }) || db.menu_items.insertOne({ name: "Egg Fried", price: 50, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Plain Bread Omelette" }) || db.menu_items.insertOne({ name: "Plain Bread Omelette", price: 80, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Masala Bread Omelette" }) || db.menu_items.insertOne({ name: "Masala Bread Omelette", price: 90, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Cheese Bread Omelette" }) || db.menu_items.insertOne({ name: "Cheese Bread Omelette", price: 100, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "French Toast" }) || db.menu_items.insertOne({ name: "French Toast", price: 100, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Healthy Fruit Bowl" }) || db.menu_items.insertOne({ name: "Healthy Fruit Bowl", price: 150, category: "Breakfast", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Veg Sandwich" }) || db.menu_items.insertOne({ name: "Veg Sandwich", price: 130, category: "Sandwiches", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Paneer Sandwich" }) || db.menu_items.insertOne({ name: "Paneer Sandwich", price: 150, category: "Sandwiches", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Sandwich" }) || db.menu_items.insertOne({ name: "Egg Sandwich", price: 150, category: "Sandwiches", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Aloo Jeera" }) || db.menu_items.insertOne({ name: "Aloo Jeera", price: 120, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Paneer Bhurji" }) || db.menu_items.insertOne({ name: "Paneer Bhurji", price: 140, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Egg Bhurji" }) || db.menu_items.insertOne({ name: "Egg Bhurji", price: 140, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Mix Veg" }) || db.menu_items.insertOne({ name: "Mix Veg", price: 140, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Aloo Palak" }) || db.menu_items.insertOne({ name: "Aloo Palak", price: 160, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Palak Paneer" }) || db.menu_items.insertOne({ name: "Palak Paneer", price: 180, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Matar Paneer" }) || db.menu_items.insertOne({ name: "Matar Paneer", price: 180, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Kadhai Paneer" }) || db.menu_items.insertOne({ name: "Kadhai Paneer", price: 200, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Paneer Butter Masala" }) || db.menu_items.insertOne({ name: "Paneer Butter Masala", price: 200, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Plain Dal" }) || db.menu_items.insertOne({ name: "Plain Dal", price: 100, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Dal Fry" }) || db.menu_items.insertOne({ name: "Dal Fry", price: 120, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Steam Rice" }) || db.menu_items.insertOne({ name: "Steam Rice", price: 60, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Jeera Rice" }) || db.menu_items.insertOne({ name: "Jeera Rice", price: 80, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Lemon Rice`" }) || db.menu_items.insertOne({ name: "Lemon Rice`", price: 90, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Curd Rice" }) || db.menu_items.insertOne({ name: "Curd Rice", price: 100, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Dal Khichdi" }) || db.menu_items.insertOne({ name: "Dal Khichdi", price: 90, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Dal Chawal" }) || db.menu_items.insertOne({ name: "Dal Chawal", price: 120, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Plain Roti" }) || db.menu_items.insertOne({ name: "Plain Roti", price: 12, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Paratha" }) || db.menu_items.insertOne({ name: "Paratha", price: 25, category: "Rice & Indian Breads", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Sabzi Roti" }) || db.menu_items.insertOne({ name: "Sabzi Roti", price: 120, category: "Main Course", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Lime Lemonade" }) || db.menu_items.insertOne({ name: "Lime Lemonade", price: 50, category: "Drinks", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Cold Coffee" }) || db.menu_items.insertOne({ name: "Cold Coffee", price: 80, category: "Drinks", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Tea" }) || db.menu_items.insertOne({ name: "Tea", price: 30, category: "Drinks", available: true, deleted: false, createdAt: Date.now() });
db.menu_items.findOne({ name: "Coffee" }) || db.menu_items.insertOne({ name: "Coffee", price: 50, category: "Drinks", available: true, deleted: false, createdAt: Date.now() });
print("Menu Items seeded successfully.");

// Insert Other Essentials (skip if already exists)
db.other_essentials.findOne({ name: "Water Bottle" }) || db.other_essentials.insertOne({ name: "Water Bottle", price: 15, category: "Beverages", available: true, deleted: false, createdAt: Date.now() });
db.other_essentials.findOne({ name: "Kingfisher Budweiser Magnum" }) || db.other_essentials.insertOne({ name: "Kingfisher Budweiser Magnum", price: 250, category: "Beverages", available: true, deleted: false, createdAt: Date.now() });
db.other_essentials.findOne({ name: "Kingfisher Ultra Max" }) || db.other_essentials.insertOne({ name: "Kingfisher Ultra Max", price: 180, category: "Beverages", available: true, deleted: false, createdAt: Date.now() });
db.other_essentials.findOne({ name: "Corona" }) || db.other_essentials.insertOne({ name: "Corona", price: 350, category: "Beverages", available: true, deleted: false, createdAt: Date.now() });
print("Other Essential seeded successfully.");

print("MongoDB initialization complete.");
