ALTER TABLE student_pickup_checkin DROP COLUMN picked_up_by;
ALTER TABLE student_pickup_checkin CHANGE COLUMN picked_up_at arrived_at VARCHAR(5) NOT NULL DEFAULT '';
RENAME TABLE student_pickup_checkin TO student_arrival_checkin;
