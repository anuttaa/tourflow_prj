ALTER TABLE booking_travelers ADD COLUMN doc_number VARCHAR(50);
ALTER TABLE booking_travelers ADD COLUMN citizenship VARCHAR(80);
ALTER TABLE booking_travelers ADD COLUMN doc_status VARCHAR(30) DEFAULT 'PENDING_VERIFICATION';
