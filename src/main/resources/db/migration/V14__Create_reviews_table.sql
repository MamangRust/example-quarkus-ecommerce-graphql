-- Create reviews table
CREATE TABLE IF NOT EXISTS "reviews" (
    "id" SERIAL PRIMARY KEY,
    "user_id" INT NOT NULL REFERENCES "users" ("id") ON DELETE CASCADE,
    "product_id" INT NOT NULL REFERENCES "products" ("id") ON DELETE CASCADE,
    "name" VARCHAR(255) NOT NULL,
    "comment" TEXT NOT NULL,
    "rating" INT NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "deleted_at" TIMESTAMP DEFAULT NULL
);

CREATE INDEX "idx_reviews_user_id" ON "reviews" ("user_id");
CREATE INDEX "idx_reviews_product_id" ON "reviews" ("product_id");
CREATE INDEX "idx_reviews_rating" ON "reviews" ("rating");
