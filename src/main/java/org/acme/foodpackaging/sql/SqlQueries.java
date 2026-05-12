package org.acme.foodpackaging.sql;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * SQL queries for database operations.
 * NOTE: Schema names (e.g., "MES") are environment-specific and may vary
 * between different database instances. These queries use parameterized
 * statements to prevent SQL injection and do not expose sensitive data.
 */

@ApplicationScoped
public class SqlQueries {

    @ConfigProperty(name = "app.mes.schema")
    String mesSchema;

    @ConfigProperty(name = "app.prommark.schema")
    String prommarkSchema;

    public String loadJobs() {
        return """
            SELECT
                v.DTI, v.KMC, v.NP, v.KOLEV, v.MASSA, v.PDTN,
                v.PDTO, v.PDUR, v.SNPZ, v.UX, v.KRC, m.SNM, v.EMK, v.KOLMP, v.STICKER
            FROM [%s].[dbo].[BD_VZPMC] AS v
            JOIN [%s].[dbo].[NS_MC] AS m
                ON v.KMC = m.KMC
            WHERE
                v.DTI >= CAST(?1 AS datetime)
                AND v.DTI <  CAST(?2 AS datetime)
                AND v.KSK = ?3
                AND v.F_DEL = 0
                AND v.NP > 0
                AND (
                    v.PDTN IS NULL
                    OR v.PDTN >= CAST(?4 AS datetime)
                )
            ORDER BY
                v.KRC, v.PDTN
            """.formatted(mesSchema, mesSchema);
    }

    public String loadMaintenance() {
        return """
            SELECT
                v.F_ID, v.KRC,
                v.PDTN, v.PDTO,
                v.PDUR, v.SNPZ, v.F_DEL, v.EVTYPE, v.NOTE
            FROM [%s].[dbo].[OEE_PEV] v
            WHERE
                (v.SNPZ IN (0, 10) OR v.SNPZ IS NULL)
                AND v.F_DEL = 0
                AND (
                    (
                        v.PDTN >= ?1
                        AND v.PDTN < ?2
                    )
                    OR
                    (
                        v.PDTO >= ?3
                        AND v.PDTO < ?4
                    )
                )
            ORDER BY
                v.KRC,
                v.PDTN
            """.formatted(mesSchema);
    }

    public String loadDelayDuration() {
        return """
            SELECT
                v.F_ID, v.KRC,
                v.PDTN, v.PDTO,
                v.PDUR, v.SNPZ, v.F_DEL, v.EVTYPE, v.NOTE
            FROM [%s].[dbo].[OEE_PEV] v
            WHERE
                v.EVTYPE = 10
                AND v.F_DEL = 0
                AND (
                    (
                        v.PDTN >= ?1
                        AND v.PDTN < ?2
                    )
                    OR
                    (
                        v.PDTO >= ?3
                        AND v.PDTO < ?4
                    )
                )
            ORDER BY
                v.KRC,
                v.PDTN
            """.formatted(mesSchema);
    }

    public String loadFact() {
        return """
            SELECT
                v.IDBATCH, v.DTV, v.KMC, v.NP, v.KRC, v.DT, v.EVENT
            FROM [%s].[dbo].[MS_LOG] v
            WHERE
                v.DTV >= ?1
                AND v.DTV <= ?2
                AND v.EVENT <= 3
            ORDER BY
                v.DTV, v.KMC, v.NP
            """.formatted(mesSchema);
    }

    public String loadCameraFact() {
        return """
            SELECT
                MIN(DTS) AS DTSTART,
                MAX(DTS) AS DTEND
            FROM [%s].[dbo].[PM_LOG] WITH (NOLOCK)
            WHERE IDBATCH = ?
            """.formatted(prommarkSchema);
    }

    public String loadPmLogMarkingRowsByBatch() {
        return """
            SELECT F_ID, DTS
            FROM [%s].[dbo].[PM_LOG] WITH (NOLOCK)
            WHERE IDBATCH = ? AND F_DEL = 0
            ORDER BY F_ID
            """.formatted(prommarkSchema);
    }

    public String updateCameraEndEvent() {
        return """
            UPDATE [%s].[dbo].[MS_LOG]
            SET DT = ?
            WHERE IDBATCH = ?
              AND EVENT = ?
            """.formatted(mesSchema);
    }

    public String insertCameraEvent() {
        return """
            INSERT INTO [%s].[dbo].[MS_LOG]
            (IDBATCH, KMC, KRC, NP, EVENT, DTV, DT)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.formatted(mesSchema);
    }

    public String updateWork() {
        return """
            UPDATE [%s].[dbo].[BD_VZPMC]
            SET KRC = ?, PDTN = ?, PDTO = ?, PDUR = ?
            WHERE SNPZ = ?
            """.formatted(mesSchema);
    }

    public String refreshFasp() {
        return """
            DECLARE @pdt1 datetime = GETDATE()-2,
                    @pdt2 datetime = GETDATE()+7,
                    @pkrca char(20) = ?,
                    @pksk  char(10) = ?
            EXEC mes_refreshfasp @pdt1, @pdt2, @pkrca, 1, 14, @pksk
            """;
    }
}