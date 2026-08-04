DO $$
DECLARE
    cleared_count INTEGER := 0;
BEGIN
    IF to_regclass('public.patients') IS NOT NULL THEN
        UPDATE patients
        SET phone = NULL
        WHERE phone IS NOT NULL
          AND phone !~ '^\+[1-9][0-9]{7,14}$';

        GET DIAGNOSTICS cleared_count = ROW_COUNT;

        IF cleared_count > 0 THEN
            RAISE NOTICE 'Cleared % incompatible patient phone value(s) before enforcing E.164.', cleared_count;
        END IF;
    END IF;
END
$$;
