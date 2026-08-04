SELECT format('CREATE DATABASE %I', required_database.name)
FROM (
    VALUES
        ('clinicflow_auth'),
        ('clinicflow_patient'),
        ('clinicflow_appointment'),
        ('clinicflow_clinical'),
        ('clinicflow_notification')
) AS required_database(name)
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = required_database.name
)
\gexec
