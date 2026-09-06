ALTER TABLE task_template ADD COLUMN teacher_id BIGINT NULL AFTER institution_id;
UPDATE task_template SET teacher_id = (SELECT id FROM teacher WHERE phone = '13644096649')
WHERE teacher_id IS NULL;
ALTER TABLE task_template ADD CONSTRAINT fk_task_template_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id);
