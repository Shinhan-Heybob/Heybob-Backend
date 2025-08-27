-- Add meal_type column to meal_appointments table
ALTER TABLE meal_appointments 
ADD COLUMN meal_type VARCHAR(32) NOT NULL DEFAULT 'MEAL_APPOINTMENT';

-- Add index for better query performance
CREATE INDEX idx_meal_appointments_type ON meal_appointments(meal_type);
CREATE INDEX idx_meal_appointments_creator_type ON meal_appointments(creator_id, meal_type);